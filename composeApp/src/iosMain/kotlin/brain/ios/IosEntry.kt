package brain.ios

import androidx.compose.runtime.remember
import androidx.compose.ui.window.ComposeUIViewController
import brain.domain.*
import brain.model.*
import brain.studio.*
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import platform.Foundation.NSUUID
import platform.Foundation.NSUserDefaults
import kotlin.time.Clock

private const val DATA_KEY = "kasha.test.brain.v1"
private const val PREFS_KEY = "kasha.test.preferences.v1"

/**
 * Тонкий iOS-адаптер для бесплатной SideStore-сборки.
 *
 * Это именно тестовый build без моделей: UI/Core настоящие, AI и аудио — явные
 * локальные заглушки. Данные заметок/задач/проектов сохраняются в NSUserDefaults,
 * поэтому приложение уже пригодно для проверки всего продуктового flow на iPhone.
 */
private class IosTestRepository : StudioRepository {
    override val simulated: Boolean = true

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
    private val defaults = NSUserDefaults.standardUserDefaults
    private val intelligence = DemoIntelligence(latencyMillis = 120)

    private var data: BrainData = defaults.stringForKey(DATA_KEY)
        ?.let { runCatching { json.decodeFromString<BrainData>(it) }.getOrNull() }
        ?: BrainData()

    private var prefs: Preferences = defaults.stringForKey(PREFS_KEY)
        ?.let { runCatching { json.decodeFromString<Preferences>(it) }.getOrNull() }
        ?.copy(autoRecord = false)
        ?: Preferences(autoRecord = false)

    private fun id(): String = NSUUID().UUIDString.lowercase()
    private fun now(): Long = Clock.System.now().toEpochMilliseconds()
    private fun language(): String = Languages.resolve(prefs.language, "ru-RU")

    private fun persistData() {
        defaults.setObject(json.encodeToString(data), forKey = DATA_KEY)
    }

    private fun persistPreferences() {
        defaults.setObject(json.encodeToString(prefs), forKey = PREFS_KEY)
    }

    override suspend fun snapshot(): AppSnapshot = AppSnapshot(
        projects = data.projects,
        notes = data.notes,
        captures = data.captures,
        runtime = RuntimeStatus(
            whisperConfigured = false,
            llmConfigured = false,
            localOnly = true,
            message = "Kasha Test · AI работает в режиме заглушек",
            simulated = true,
        ),
        tasks = data.tasks,
    )

    override suspend fun preferences(): Preferences = prefs

    override suspend fun savePreferences(value: Preferences) {
        prefs = value.validated().copy(autoRecord = false)
        persistPreferences()
    }

    override suspend fun createProject(draft: ProjectDraft): Project {
        val projectId = id()
        data = data.addProject(projectId, now(), draft)
        persistData()
        return data.projects.first { it.id == projectId }
    }

    override suspend fun updateProject(id: String, update: ProjectUpdate): Project {
        data = data.updateProject(id, update, now())
        persistData()
        return data.projects.first { it.id == id }
    }

    override suspend fun pinProject(id: String, pinned: Boolean): Project {
        data = data.pinProject(id, pinned)
        persistData()
        return data.projects.first { it.id == id }
    }

    override suspend fun orderPins(ids: List<String>) {
        data = data.orderPins(ids)
        persistData()
    }

    override suspend fun orderProjects(ids: List<String>) {
        data = data.orderProjects(ids)
        persistData()
    }

    override suspend fun updateCaptureDraft(id: String, update: CaptureDraftUpdate): Capture {
        data = data.updateDraft(id, update)
        persistData()
        return data.captures.first { it.id == id }
    }

    override suspend fun distribute(id: String, request: DistributionRequest): Note {
        val noteId = this.id()
        val result = data.distribute(id, request, noteId, now())
        data = result.first
        persistData()
        return result.second
    }

    override suspend fun distributeTask(id: String, request: TaskDistributionRequest): Task {
        val taskId = this.id()
        val result = data.distributeTask(id, request, taskId, now())
        data = result.first
        persistData()
        return result.second
    }

    override suspend fun updateNote(id: String, update: NoteUpdate): Note {
        data = data.updateNote(id, update, now())
        persistData()
        return data.notes.first { it.id == id }
    }

    override suspend fun pinNote(id: String, pinned: Boolean): Note {
        data = data.pinNote(id, pinned)
        persistData()
        return data.notes.first { it.id == id }
    }

    override suspend fun orderNotes(projectId: String, ids: List<String>) {
        data = data.orderNotes(projectId, ids)
        persistData()
    }

    override suspend fun updateTask(id: String, update: TaskUpdate): Task {
        data = data.updateTask(id, update, now())
        persistData()
        return data.tasks.first { it.id == id }
    }

    override suspend fun rescheduleTask(id: String, update: TaskScheduleUpdate): Task {
        data = data.rescheduleTask(id, update, now())
        persistData()
        return data.tasks.first { it.id == id }
    }

    override suspend fun completeTask(id: String): Task {
        data = data.completeTask(id, now())
        persistData()
        return data.tasks.first { it.id == id }
    }

    override suspend fun deleteTask(id: String) {
        data = data.deleteTask(id)
        persistData()
    }

    override suspend fun orderTasks(ids: List<String>) {
        data = data.orderTasks(ids)
        persistData()
    }

    override suspend fun claimTaskReminders(now: Long, zoneId: String): List<Task> {
        val result = data.claimDueReminders(now, zoneId)
        data = result.first
        if (result.second.isNotEmpty()) persistData()
        return result.second
    }

    override suspend fun reprocess(id: String): Capture {
        val current = data.captures.first { it.id == id }
        val text = current.textToSave.ifBlank {
            intelligence.transcribe("", language(), prefs.demoExample)
        }
        data = data.updateCapture(id) {
            it.copy(
                title = NoteText.title(text),
                transcript = text,
                preparedText = text,
                status = CaptureStatus.READY,
                message = "",
                simulated = true,
                audioFinalized = true,
            )
        }
        persistData()
        return data.captures.first { it.id == id }
    }

    override suspend fun tidy(id: String): Capture {
        val current = data.captures.first { it.id == id }
        val text = intelligence.tidy(current.textToSave, language())
        data = data.updateCapture(id) {
            it.copy(
                title = NoteText.title(text),
                preparedText = text,
                draftEdited = true,
                llmApplied = true,
                rankingApplied = false,
                relevance = emptyMap(),
                status = CaptureStatus.READY,
                simulated = true,
            )
        }
        persistData()
        return data.captures.first { it.id == id }
    }

    override suspend fun rank(id: String): Capture {
        val current = data.captures.first { it.id == id }
        val scores = intelligence.rank(current.textToSave, data.projects, language())
        data = data.updateCapture(id) {
            it.copy(
                title = NoteText.title(it.textToSave),
                relevance = scores,
                rankingApplied = true,
                simulated = true,
            )
        }
        persistData()
        return data.captures.first { it.id == id }
    }

    override suspend fun discard(id: String) {
        data = data.copy(captures = data.captures.filterNot { it.id == id })
        persistData()
    }

    override suspend fun createDemo(): Capture {
        val text = intelligence.transcribe("", language(), prefs.demoExample)
        val capture = Capture(
            id = id(),
            createdAt = now(),
            title = NoteText.title(text),
            transcript = text,
            preparedText = text,
            status = CaptureStatus.READY,
            simulated = true,
            audioFinalized = true,
            durationSeconds = 8.0,
            waveform = List(64) { index -> if (index % 5 == 0) .72f else .24f + (index % 3) * .12f },
        )
        data = data.addCapture(capture)
        persistData()
        return capture
    }
}

private class IosTestRecorder(private val repository: IosTestRepository) : RecorderGateway {
    private var currentPhase = "idle"

    override suspend fun hasConsent(): Boolean = true
    override suspend fun hasPending(): Boolean = false
    override fun phase(): String = currentPhase
    override fun level(): Float = if (currentPhase == "recording") .42f else 0f

    override suspend fun start() {
        currentPhase = "recording"
    }

    override suspend fun pause() {
        currentPhase = "paused"
    }

    override suspend fun resume() {
        currentPhase = "recording"
    }

    override suspend fun stopAndUpload(): Capture {
        currentPhase = "idle"
        return repository.createDemo()
    }

    override suspend fun recoverPending(): Capture = repository.createDemo()
}

private class IosTestAudio : AudioGateway {
    private var state = AudioTelemetry()

    override suspend fun playCapture(captureId: String, compact: Boolean, fromSeconds: Double, rate: Double) {
        state = AudioTelemetry(phase = "playing", position = fromSeconds, duration = 8.0)
    }

    override suspend fun pause() {
        state = state.copy(phase = "paused")
    }

    override suspend fun resume() {
        state = state.copy(phase = "playing")
    }

    override fun telemetry(): AudioTelemetry = state

    override fun stop() {
        state = AudioTelemetry()
    }
}

@Suppress("FunctionName")
fun MainViewController() = ComposeUIViewController {
    val repository = remember { IosTestRepository() }
    val recorder = remember(repository) { IosTestRecorder(repository) }
    val audio = remember { IosTestAudio() }
    val state = remember(repository, recorder, audio) {
        StudioState(
            repository = repository,
            recorder = recorder,
            audio = audio,
            systemLanguage = "ru-RU",
        )
    }
    StudioApp(state)
}
