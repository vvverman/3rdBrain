package brain.runtime

import brain.domain.NoteText
import brain.model.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.UUID
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.time.Clock

@Serializable
private data class PersistedState(
    val projects: List<Project> = emptyList(),
    val notes: List<Note> = emptyList(),
    val captures: List<Capture> = emptyList(),
)

class FileBrainStore(
    val root: Path,
    private val runtimeStatus: () -> RuntimeStatus,
) {
    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true; encodeDefaults = true }
    private val mutex = Mutex()
    private val stateFile = root.resolve("brain.json")
    private val audioRoot = root.resolve("audio")
    private var state: PersistedState

    init {
        root.createDirectories()
        audioRoot.createDirectories()
        state = load()
    }

    suspend fun snapshot(): AppSnapshot = mutex.withLock {
        AppSnapshot(state.projects, state.notes, state.captures, runtimeStatus())
    }

    suspend fun createProject(draft: ProjectDraft): Project = mutex.withLock {
        require(draft.title.isNotBlank()) { "Введите название проекта" }
        val project = Project(
            id = UUID.randomUUID().toString(),
            title = draft.title.trim(),
            description = draft.description.trim(),
            instruction = draft.instruction.trim(),
            createdAt = now(),
        )
        state = state.copy(projects = state.projects + project)
        save(); project
    }

    suspend fun updateProject(id: String, update: ProjectUpdate): Project = mutex.withLock {
        require(update.title.isNotBlank()) { "Введите название проекта" }
        val existing = state.projects.firstOrNull { it.id == id } ?: error("Проект не найден")
        val changed = existing.copy(title = update.title.trim(), description = update.description.trim(), instruction = update.instruction.trim())
        state = state.copy(projects = state.projects.map { if (it.id == id) changed else it })
        save(); changed
    }

    suspend fun pinProject(id: String, pinned: Boolean): Project = mutex.withLock {
        val existing = state.projects.firstOrNull { it.id == id } ?: error("Проект не найден")
        val order = if (pinned && !existing.pinned) (state.projects.filter { it.pinned }.maxOfOrNull { it.pinOrder } ?: -1) + 1 else existing.pinOrder
        val changed = existing.copy(pinned = pinned, pinOrder = order)
        state = state.copy(projects = state.projects.map { if (it.id == id) changed else it })
        save(); changed
    }

    suspend fun updateNote(id: String, update: NoteUpdate): Note = mutex.withLock {
        val old = state.notes.firstOrNull { it.id == id } ?: error("Заметка не найдена")
        val changed = old.copy(title = update.title.trim().ifBlank { "Без названия" }, body = update.body, updatedAt = now())
        state = state.copy(notes = state.notes.map { if (it.id == id) changed else it })
        save(); changed
    }

    suspend fun createCapture(fileName: String, bytes: ByteArray): Capture = mutex.withLock {
        require(bytes.isNotEmpty()) { "Пустая аудиозапись" }
        val id = UUID.randomUUID().toString()
        val ext = fileName.substringAfterLast('.', "webm").lowercase().filter { it.isLetterOrDigit() }.take(8).ifBlank { "webm" }
        val dir = audioRoot.resolve(id).also { it.createDirectories() }
        val target = dir.resolve("original.$ext")
        Files.write(target, bytes)
        val capture = Capture(id = id, createdAt = now(), status = CaptureStatus.QUEUED, audioFileName = root.relativize(target).toString())
        state = state.copy(captures = state.captures + capture)
        save(); capture
    }

    suspend fun capture(id: String): Capture? = mutex.withLock { state.captures.firstOrNull { it.id == id } }

    suspend fun updateCapture(id: String, transform: (Capture) -> Capture): Capture = mutex.withLock {
        val old = state.captures.firstOrNull { it.id == id } ?: error("Запись не найдена")
        val changed = transform(old)
        state = state.copy(captures = state.captures.map { if (it.id == id) changed else it })
        save(); changed
    }

    suspend fun updateDraft(id: String, update: CaptureDraftUpdate): Capture = updateCapture(id) { old ->
        old.copy(title = update.title.ifBlank { NoteText.title(update.text) }, preparedText = update.text)
    }

    suspend fun distribute(id: String, request: DistributionRequest): Note = mutex.withLock {
        val capture = state.captures.firstOrNull { it.id == id } ?: error("Запись не найдена")
        capture.noteId?.let { already ->
            return@withLock state.notes.firstOrNull { it.id == already } ?: error("Запись ссылается на отсутствующую заметку")
        }
        val project = state.projects.firstOrNull { it.id == request.projectId } ?: error("Проект не найден")
        val addition = capture.textToSave.trim()
        require(addition.isNotEmpty()) { "В записи пока нет текста" }
        val timestamp = now()
        val note = if (request.noteId == null) {
            Note(
                id = UUID.randomUUID().toString(), projectId = project.id,
                title = request.title?.takeIf { it.isNotBlank() } ?: capture.title.ifBlank { NoteText.title(addition) },
                body = addition, createdAt = timestamp, updatedAt = timestamp,
            )
        } else {
            val old = state.notes.firstOrNull { it.id == request.noteId } ?: error("Заметка не найдена")
            require(old.projectId == project.id) { "Заметка относится к другому проекту" }
            old.copy(body = NoteText.append(old.body, addition), updatedAt = timestamp)
        }
        val notes = if (request.noteId == null) state.notes + note else state.notes.map { if (it.id == note.id) note else it }
        val changedCapture = capture.copy(noteId = note.id, appendedAt = timestamp)
        state = state.copy(notes = notes, captures = state.captures.map { if (it.id == id) changedCapture else it })
        save(); note
    }

    fun resolveAudio(capture: Capture): Path {
        val rel = capture.audioFileName ?: error("У записи нет аудиофайла")
        val candidate = root.resolve(rel).normalize()
        require(candidate.startsWith(audioRoot.normalize())) { "Некорректный путь аудиофайла" }
        require(candidate.exists()) { "Аудиофайл не найден" }
        return candidate
    }

    private fun load(): PersistedState = try {
        if (!stateFile.exists()) PersistedState() else json.decodeFromString(Files.readString(stateFile))
    } catch (e: Exception) {
        throw IllegalStateException("Не удалось прочитать локальную базу $stateFile", e)
    }

    private fun save() {
        val temp = root.resolve("brain.json.tmp")
        Files.writeString(temp, json.encodeToString(state))
        try { Files.move(temp, stateFile, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE) }
        catch (_: Exception) { Files.move(temp, stateFile, StandardCopyOption.REPLACE_EXISTING) }
    }

    private fun now(): Long = Clock.System.now().toEpochMilliseconds()
}
