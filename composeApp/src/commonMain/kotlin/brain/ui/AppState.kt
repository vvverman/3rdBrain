package brain.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import brain.domain.ProjectOrder
import brain.model.AppSnapshot
import brain.model.Capture
import brain.model.CaptureDraftUpdate
import brain.model.DistributionRequest
import brain.model.Note
import brain.model.NoteUpdate
import brain.model.Project
import brain.model.ProjectDraft
import brain.model.ProjectUpdate
import kotlinx.coroutines.delay

interface AudioGateway {
    suspend fun playCapture(captureId: String)
    fun stop()
}

interface RecorderGateway {
    suspend fun hasConsent(): Boolean
    suspend fun start()
    suspend fun pause()
    suspend fun resume()
    suspend fun stopAndUpload(): Capture
}

interface BrainRepository {
    suspend fun snapshot(): AppSnapshot
    suspend fun createProject(draft: ProjectDraft): Project
    suspend fun updateProject(id: String, update: ProjectUpdate): Project
    suspend fun pinProject(id: String, pinned: Boolean): Project
    suspend fun updateCaptureDraft(id: String, update: CaptureDraftUpdate): Capture
    suspend fun distribute(id: String, request: DistributionRequest): Note
    suspend fun updateNote(id: String, update: NoteUpdate): Note
    suspend fun reprocess(id: String): Capture
}

enum class MainTab { INBOX, PROJECTS, SETTINGS }
data class RouteStep(val captureId: String, val projectId: String? = null)

class BrainAppState(
    val repository: BrainRepository,
    val recorder: RecorderGateway,
    val audio: AudioGateway,
) {
    var snapshot by mutableStateOf(AppSnapshot())
        private set
    var tab by mutableStateOf(MainTab.INBOX)
    var onboarding by mutableStateOf(false)
        private set
    var isRecording by mutableStateOf(false)
        private set
    var isPaused by mutableStateOf(false)
        private set
    var elapsedSeconds by mutableStateOf(0L)
        private set
    var busy by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var route by mutableStateOf<RouteStep?>(null)
    var editingProject by mutableStateOf<Project?>(null)
    var selectedProjectId by mutableStateOf<String?>(null)
    var selectedNoteId by mutableStateOf<String?>(null)
    var creatingProject by mutableStateOf(false)

    private var startedAtMillis = 0L

    suspend fun launch() {
        refresh()
        onboarding = !recorder.hasConsent()
        if (!onboarding) runCatching { startRecording() }.onFailure { error = it.message }
    }

    suspend fun refresh() {
        runCatching { snapshot = repository.snapshot() }.onFailure { error = it.message }
    }

    suspend fun consentAndStart() {
        runAction {
            recorder.start()
            onboarding = false
            markRecordingStarted()
        }
    }

    suspend fun startRecording() {
        if (isRecording || isPaused) return
        runAction {
            recorder.start()
            markRecordingStarted()
        }
    }

    suspend fun pauseRecording() {
        if (!isRecording) return
        runAction {
            recorder.pause()
            isRecording = false
            isPaused = true
        }
    }

    suspend fun resumeRecording() {
        if (!isPaused) return
        runAction {
            recorder.resume()
            isRecording = true
            isPaused = false
            startedAtMillis = nowMillis() - elapsedSeconds * 1000
        }
    }

    suspend fun stopRecording() {
        if (!isRecording && !isPaused) return
        runAction {
            val capture = recorder.stopAndUpload()
            isRecording = false
            isPaused = false
            elapsedSeconds = 0
            refresh()
            route = RouteStep(capture.id)
        }
    }

    suspend fun tickRecordingClock() {
        while (isRecording) {
            elapsedSeconds = ((nowMillis() - startedAtMillis) / 1000).coerceAtLeast(0)
            delay(500)
        }
    }

    suspend fun createProject(title: String, description: String, instruction: String) {
        runAction {
            repository.createProject(ProjectDraft(title.trim(), description.trim(), instruction.trim()))
            creatingProject = false
            refresh()
        }
    }

    suspend fun updateProject(project: Project, title: String, description: String, instruction: String) {
        runAction {
            repository.updateProject(project.id, ProjectUpdate(title.trim(), description.trim(), instruction.trim()))
            editingProject = null
            refresh()
        }
    }

    suspend fun togglePin(project: Project) {
        runAction {
            repository.pinProject(project.id, !project.pinned)
            refresh()
        }
    }

    suspend fun saveCaptureDraft(capture: Capture, title: String, text: String) {
        runAction {
            repository.updateCaptureDraft(capture.id, CaptureDraftUpdate(title.trim(), text))
            refresh()
        }
    }

    suspend fun distribute(capture: Capture, project: Project, note: Note?) {
        runAction {
            repository.distribute(capture.id, DistributionRequest(projectId = project.id, noteId = note?.id, title = capture.title))
            route = null
            refresh()
        }
    }

    suspend fun updateNote(note: Note, title: String, body: String) {
        runAction {
            repository.updateNote(note.id, NoteUpdate(title.trim().ifBlank { "Без названия" }, body))
            refresh()
        }
    }

    suspend fun playSource(capture: Capture) {
        if (isRecording) pauseRecording()
        runAction { audio.playCapture(capture.id) }
    }

    suspend fun pollProcessing() {
        while (true) {
            delay(1400)
            if (snapshot.captures.any { it.status.name in setOf("QUEUED", "TRANSCRIBING", "POLISHING") }) refresh()
        }
    }

    suspend fun reprocess(capture: Capture) {
        runAction {
            repository.reprocess(capture.id)
            refresh()
        }
    }

    fun orderedProjects(capture: Capture?): List<Project> = ProjectOrder.sorted(snapshot.projects, capture?.relevance ?: emptyMap())
    fun notes(project: Project): List<Note> = snapshot.notes.filter { it.projectId == project.id }.sortedByDescending { it.updatedAt }
    fun inbox(): List<Capture> = snapshot.captures.filter { it.noteId == null }.sortedByDescending { it.createdAt }
    fun capture(id: String): Capture? = snapshot.captures.firstOrNull { it.id == id }
    fun project(id: String): Project? = snapshot.projects.firstOrNull { it.id == id }
    fun clearError() { error = null }

    private suspend fun runAction(block: suspend () -> Unit) {
        if (busy) return
        busy = true
        try { block() } catch (t: Throwable) { error = t.message ?: t::class.simpleName ?: "Ошибка" }
        finally { busy = false }
    }

    private fun markRecordingStarted() {
        isRecording = true
        isPaused = false
        elapsedSeconds = 0
        startedAtMillis = nowMillis()
    }
}

private fun nowMillis(): Long = kotlin.time.Clock.System.now().toEpochMilliseconds()
