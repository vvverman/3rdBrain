package brain.runtime

import brain.domain.BrainData
import brain.model.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.file.*
import java.util.UUID
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.time.Clock

class FileBrainStore(val root: Path, private val runtimeStatus: () -> RuntimeStatus) {
    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true; encodeDefaults = true }
    private val mutex = Mutex()
    private val stateFile = root.resolve("brain.json")
    private val audioRoot = root.resolve("audio")
    private var state: BrainData

    init {
        root.createDirectories(); audioRoot.createDirectories()
        require(!Files.isSymbolicLink(stateFile) && !Files.isSymbolicLink(audioRoot)) { "Хранилище не должно быть символической ссылкой" }
        state = if (!stateFile.exists()) BrainData() else json.decodeFromString(Files.readString(stateFile))
        // Прерванную обработку не выдаём за продолжающуюся после перезапуска.
        val recovered = state.copy(captures = state.captures.map {
            if (it.status.isWorking) it.copy(status = CaptureStatus.FAILED, message = "Обработка прервалась. Источник сохранён; нажмите повторить.") else it
        })
        if (recovered != state) commit(recovered)
    }

    suspend fun snapshot(): AppSnapshot = mutex.withLock { AppSnapshot(state.projects, state.notes, state.captures, runtimeStatus()) }
    suspend fun createProject(draft: ProjectDraft): Project = mutex.withLock {
        val id = UUID.randomUUID().toString(); commit(state.addProject(id, now(), draft)); state.projects.first { it.id == id }
    }
    suspend fun updateProject(id: String, update: ProjectUpdate): Project = mutex.withLock {
        commit(state.updateProject(id, update)); state.projects.first { it.id == id }
    }
    suspend fun pinProject(id: String, pinned: Boolean): Project = mutex.withLock {
        commit(state.pinProject(id, pinned)); state.projects.first { it.id == id }
    }
    suspend fun orderPins(ids: List<String>): List<Project> = mutex.withLock { commit(state.orderPins(ids)); state.projects }
    suspend fun updateNote(id: String, update: NoteUpdate): Note = mutex.withLock {
        commit(state.updateNote(id, update, now())); state.notes.first { it.id == id }
    }
    suspend fun createCapture(fileName: String, bytes: ByteArray, requestedId: String? = null): Capture = mutex.withLock {
        require(bytes.isNotEmpty() && bytes.size <= 64 * 1024 * 1024) { "Допустим непустой аудиофайл до 64 МБ" }
        val id = requestedId?.also { require(UUID.fromString(it).toString() == it.lowercase()) { "Некорректный идентификатор записи" } }
            ?: UUID.randomUUID().toString()
        state.captures.firstOrNull { it.id == id }?.let { saved ->
            require(Files.readAllBytes(resolveAudio(saved)).contentEquals(bytes)) { "Этот идентификатор принадлежит другому аудио" }
            return@withLock saved
        }
        val ext = fileName.substringAfterLast('.', "webm").lowercase()
        require(ext in setOf("webm", "m4a", "mp4", "ogg", "wav", "caf")) { "Неподдерживаемый формат аудио" }
        val dir = audioRoot.resolve(id)
        require(!dir.exists() && !Files.isSymbolicLink(dir)) { "Каталог записи уже существует" }
        Files.createDirectory(dir)
        val target = dir.resolve("original.$ext")
        try {
            Files.write(target, bytes, StandardOpenOption.CREATE_NEW)
            val capture = Capture(id = id, createdAt = now(), audioFileName = root.relativize(target).toString().replace('\\', '/'))
            commit(state.addCapture(capture)); capture
        } catch (e: Exception) { Files.deleteIfExists(target); Files.deleteIfExists(dir); throw e }
    }
    suspend fun capture(id: String): Capture? = mutex.withLock { state.captures.firstOrNull { it.id == id } }
    suspend fun updateCapture(id: String, transform: (Capture) -> Capture): Capture = mutex.withLock {
        commit(state.updateCapture(id, transform)); state.captures.first { it.id == id }
    }
    suspend fun updateDraft(id: String, update: CaptureDraftUpdate): Capture = mutex.withLock {
        commit(state.updateDraft(id, update)); state.captures.first { it.id == id }
    }
    suspend fun distribute(id: String, request: DistributionRequest): Note = mutex.withLock {
        val (next, note) = state.distribute(id, request, UUID.randomUUID().toString(), now()); commit(next); note
    }
    fun resolveAudio(capture: Capture, compact: Boolean = false): Path {
        val rel = (if (compact) capture.compactAudioFileName else capture.audioFileName) ?: error("У записи нет этой версии аудио")
        val candidate = root.resolve(rel).normalize().toAbsolutePath()
        val allowed = audioRoot.toAbsolutePath().normalize().resolve(capture.id)
        require(candidate.parent == allowed && !Files.isSymbolicLink(allowed) && !Files.isSymbolicLink(candidate)) { "Некорректный путь аудиофайла" }
        require(Files.isRegularFile(candidate) && candidate.toRealPath().startsWith(audioRoot.toRealPath())) { "Аудиофайл не найден" }
        return candidate
    }

    /** Публикуем состояние в памяти только ПОСЛЕ успешной атомарной записи. */
    private fun commit(next: BrainData) {
        if (next == state) return
        val temp = Files.createTempFile(root, ".brain-", ".tmp")
        try {
            FileChannel.open(temp, StandardOpenOption.WRITE).use { channel ->
                val buffer = ByteBuffer.wrap(json.encodeToString(next).toByteArray(Charsets.UTF_8))
                while (buffer.hasRemaining()) channel.write(buffer)
                channel.force(true)
            }
            try { Files.move(temp, stateFile, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE) }
            catch (_: AtomicMoveNotSupportedException) { Files.move(temp, stateFile, StandardCopyOption.REPLACE_EXISTING) }
            state = next
        } finally { Files.deleteIfExists(temp) }
    }
    private fun now(): Long = Clock.System.now().toEpochMilliseconds()
}
