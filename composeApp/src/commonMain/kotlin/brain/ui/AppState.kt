package brain.ui

import androidx.compose.runtime.*
import brain.domain.*
import brain.model.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlin.time.TimeMark
import kotlin.time.TimeSource

enum class MainTab { INBOX, PROJECTS, SETTINGS }
data class RouteStep(val captureId: String, val projectId: String? = null)

class BrainAppState(val repository: BrainRepository, val recorder: RecorderGateway, val audio: AudioGateway) {
    var snapshot by mutableStateOf(AppSnapshot()); private set
    var connected by mutableStateOf(false); private set
    var tab by mutableStateOf(MainTab.INBOX)
    var onboarding by mutableStateOf(false); private set
    var isRecording by mutableStateOf(false); private set
    var isPaused by mutableStateOf(false); private set
    var pendingUpload by mutableStateOf(false); private set
    var elapsedSeconds by mutableStateOf(0L); private set
    var busy by mutableStateOf(false); private set
    var recordingBusy by mutableStateOf(false); private set
    var error by mutableStateOf<String?>(null); private set
    var route by mutableStateOf<RouteStep?>(null)
    var editingProject by mutableStateOf<Project?>(null)
    var selectedProjectId by mutableStateOf<String?>(null)
    var selectedNoteId by mutableStateOf<String?>(null)
    var creatingProject by mutableStateOf(false)
    private var started = false
    private var mark: TimeMark? = null
    private var previousMillis = 0L

    suspend fun launch() {
        if (started) return
        started = true
        refresh()
        runAction {
            pendingUpload = recorder.hasPending()
            onboarding = !recorder.hasConsent()
        }
        if (!onboarding && !pendingUpload) startRecording()
    }
    fun browseOnly() { onboarding = false }
    suspend fun refresh(silent: Boolean = false) {
        try { snapshot = repository.snapshot(); connected = true }
        catch (e: CancellationException) { throw e }
        catch (e: Exception) { connected = false; if (!silent) error = "Локальное хранилище недоступно. ${e.message.orEmpty()}" }
    }
    suspend fun consentAndStart() { if (startRecording()) onboarding = false }
    suspend fun startRecording(): Boolean {
        if (isRecording || isPaused) return false
        return recordingAction {
            check(!pendingUpload) { "Сначала сохраните предыдущую запись кнопкой «Повторить отправку»" }
            audio.stop(); recorder.start(); isRecording = true; isPaused = false
            previousMillis = 0; elapsedSeconds = 0; mark = TimeSource.Monotonic.markNow()
        }
    }
    suspend fun pauseRecording(): Boolean {
        if (!isRecording) return false
        return recordingAction {
            recorder.pause(); previousMillis += mark?.elapsedNow()?.inWholeMilliseconds ?: 0
            mark = null; elapsedSeconds = previousMillis / 1000; isRecording = false; isPaused = true
        }
    }
    suspend fun resumeRecording() {
        if (!isPaused) return
        recordingAction { audio.stop(); recorder.resume(); mark = TimeSource.Monotonic.markNow(); isRecording = true; isPaused = false }
    }
    suspend fun stopRecording() {
        if (!isRecording && !isPaused) return
        recordingAction {
            try {
                val capture = recorder.stopAndUpload()
                pendingUpload = false; refresh(); route = RouteStep(capture.id)
            } finally {
                val phase = recorder.phase()
                isRecording = phase == "recording"; isPaused = phase == "paused"
                pendingUpload = recorder.hasPending(); if (!isRecording && !isPaused) { mark = null; elapsedSeconds = 0 }
            }
        }
    }
    suspend fun recoverPending() {
        recordingAction {
            try {
                val capture = recorder.recoverPending()
                refresh(); route = RouteStep(capture.id)
            } finally { pendingUpload = recorder.hasPending() }
        }
    }
    suspend fun tickRecordingClock() {
        while (isRecording || isPaused) {
            val phase = recorder.phase()
            if (phase != "recording" && phase != "paused") {
                isRecording = false; isPaused = false; pendingUpload = recorder.hasPending()
                error = "Запись прервана. Доступную часть можно восстановить во «Входящих»"; break
            }
            elapsedSeconds = (previousMillis + (mark?.elapsedNow()?.inWholeMilliseconds ?: 0)).coerceAtLeast(0) / 1000
            delay(500)
        }
    }
    suspend fun pollProcessing() {
        while (true) {
            delay(1400)
            if (!connected || snapshot.captures.any { it.status.isWorking }) refresh(silent = true)
        }
    }
    suspend fun createProject(title: String, description: String, instruction: String) = runAction {
        repository.createProject(ProjectDraft(title.trim(), description.trim(), instruction.trim())); creatingProject = false; refresh()
    }
    suspend fun updateProject(project: Project, title: String, description: String, instruction: String) = runAction {
        repository.updateProject(project.id, ProjectUpdate(title.trim(), description.trim(), instruction.trim())); editingProject = null; refresh()
    }
    suspend fun togglePin(project: Project) = runAction { repository.pinProject(project.id, !project.pinned); refresh() }
    suspend fun movePin(project: Project, delta: Int) = runAction {
        val ids = orderedProjects(null).filter { it.pinned }.map { it.id }.toMutableList()
        val index = ids.indexOf(project.id); val target = index + delta
        if (index in ids.indices && target in ids.indices) { ids.removeAt(index); ids.add(target, project.id); repository.orderPins(ids); refresh() }
    }
    suspend fun saveCaptureDraft(capture: Capture, title: String, text: String) = runAction {
        repository.updateCaptureDraft(capture.id, CaptureDraftUpdate(title.trim(), text)); refresh()
    }
    suspend fun saveAndDistribute(capture: Capture, project: Project, note: Note?, title: String, text: String) = runAction {
        // Не продолжаем распределение, если сохранение редактируемого черновика не удалось.
        val updated = if (capture.title != title.trim() || capture.textToSave != text)
            repository.updateCaptureDraft(capture.id, CaptureDraftUpdate(title.trim(), text)) else capture
        repository.distribute(updated.id, DistributionRequest(project.id, note?.id, updated.title)); route = null; refresh()
    }
    suspend fun updateNote(note: Note, title: String, body: String) = runAction {
        repository.updateNote(note.id, NoteUpdate(title.trim().ifBlank { "Без названия" }, body)); refresh()
    }
    suspend fun playSource(capture: Capture, compact: Boolean = false, time: Double = 0.0, rate: Double = 1.0) {
        if (isRecording && !pauseRecording()) return
        runAction { audio.playCapture(capture.id, compact, if (compact) AudioTimeline.compactTime(time, capture.spans) else time, rate) }
    }
    suspend fun reprocess(capture: Capture) = runAction { repository.reprocess(capture.id); refresh() }
    fun orderedProjects(capture: Capture?): List<Project> = ProjectOrder.sorted(snapshot.projects, capture?.relevance ?: emptyMap())
    fun notes(project: Project): List<Note> = SnapshotQueries.notes(project.id, snapshot.notes)
    fun inbox(): List<Capture> = SnapshotQueries.inbox(snapshot.captures)
    fun capture(id: String): Capture? = snapshot.captures.firstOrNull { it.id == id }
    fun project(id: String): Project? = snapshot.projects.firstOrNull { it.id == id }
    fun clearError() { error = null }
    private suspend fun runAction(block: suspend () -> Unit): Boolean {
        if (busy) return false
        busy = true
        return try { block(); true } catch (e: CancellationException) { throw e }
        catch (e: Exception) { error = e.message ?: "Ошибка"; false } finally { busy = false }
    }
    private suspend fun recordingAction(block: suspend () -> Unit): Boolean {
        if (recordingBusy) return false
        recordingBusy = true
        return try { block(); true } catch (e: CancellationException) { throw e }
        catch (e: Exception) { error = e.message ?: "Ошибка записи"; false } finally { recordingBusy = false }
    }
}
