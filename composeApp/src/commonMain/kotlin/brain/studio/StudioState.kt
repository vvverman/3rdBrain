package brain.studio

import androidx.compose.runtime.*
import brain.domain.*
import brain.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.TimeMark
import kotlin.time.TimeSource

enum class Tab { HOME, PROJECTS, SETTINGS }

class StudioState(val repository: StudioRepository, val recorder: RecorderGateway, val audio: AudioGateway, val systemLanguage: String = "ru") {
    var snapshot by mutableStateOf(AppSnapshot()); private set
    var preferences by mutableStateOf(Preferences()); private set
    val language: String get() = Languages.resolve(preferences.language, systemLanguage)
    val current: Capture? get() = snapshot.captures.firstOrNull { it.noteId == null }
    var tab by mutableStateOf(Tab.HOME); private set
    var selectedProjectId by mutableStateOf<String?>(null)
    var selectedNoteId by mutableStateOf<String?>(null)
    var editingProjectId by mutableStateOf<String?>(null)
    var choosingProject by mutableStateOf(false)
    var targetProjectId by mutableStateOf<String?>(null)
    var languagePage by mutableStateOf(false)
    var title by mutableStateOf(""); private set
    var text by mutableStateOf(""); private set
    var editRevision by mutableStateOf(0L); private set
    var busy by mutableStateOf(false); private set
    var controlBusy by mutableStateOf(false); private set
    var error by mutableStateOf<String?>(null)
    var confirmDelete by mutableStateOf(false)
    var confirmListenId by mutableStateOf<String?>(null)
    var recordPhase by mutableStateOf("idle"); private set
    var elapsed by mutableStateOf(0L); private set
    var liveWave by mutableStateOf(List(80) { 0f }); private set
    var playback by mutableStateOf(AudioTelemetry()); private set
    var playbackRate by mutableStateOf(1.0); private set
    var loadedAudioId by mutableStateOf<String?>(null); private set
    var pending by mutableStateOf(false); private set
    var initialized by mutableStateOf(false); private set
    private var loadedCurrentId: String? = null
    private var dirty = false
    private var started = false
    private var recordedMillis = 0L
    private var mark: TimeMark? = null
    private var autoRouteFor: String? = null
    private val editLock = Mutex()
    val recording get() = recordPhase == "recording" || recordPhase == "paused"
    val working get() = current?.status?.isWorking == true
    val loadedAudio get() = snapshot.captures.firstOrNull { it.id == loadedAudioId }
    fun tr(key: String) = Copy.text(language, key)
    fun editTitle(value: String) { title = value; dirty = true; editRevision++ }
    fun editText(value: String) { text = value; dirty = true; editRevision++ }
    fun navigate(value: Tab) { tab = value; choosingProject = false; targetProjectId = null; editingProjectId = null; languagePage = false }

    suspend fun launch() {
        if (started) return
        started = true
        action {
            preferences = repository.preferences()
            refresh()
            pending = recorder.hasPending()
            initialized = true
        }
        if (!initialized) return
        if (pending && current == null) recover()
        else if (preferences.autoRecord && current == null && !pending) startRecording()
    }
    suspend fun refresh() {
        val previous = current
        snapshot = repository.snapshot()
        val c = current
        if (loadedCurrentId != c?.id || !dirty) {
            loadedCurrentId = c?.id; title = c?.title.orEmpty(); text = c?.textToSave.orEmpty(); dirty = false
        }
        if (c != null && c.audioFinalized && loadedAudioId == null && !recording) loadedAudioId = c.id
        if (c != null && previous?.status?.isWorking == true && !c.status.isWorking && c.status == CaptureStatus.READY && autoRouteFor != c.id) {
            autoRouteFor = c.id
            if (preferences.autoRoute) { choosingProject = true; targetProjectId = null }
        }
    }
    suspend fun flush() = editLock.withLock {
        val c = current ?: return@withLock
        if (!dirty || c.status.isWorking) return@withLock
        val version = editRevision
        val saved = repository.updateCaptureDraft(c.id, CaptureDraftUpdate(title, text))
        snapshot = snapshot.copy(captures = snapshot.captures.map { if (it.id == c.id) saved else it })
        if (editRevision == version) dirty = false
    }
    suspend fun autosave() { try { flush() } catch (e: CancellationException) { throw e } catch (_: Exception) { error = "saveFailed" } }
    suspend fun poll() {
        var tick = 0
        while (currentCoroutineContext().isActive) {
            delay(65)
            val phase = recorder.phase()
            if (recording && phase == "idle") { recordPhase = phase; mark = null; pending = recorder.hasPending() }
            if (recording) {
                elapsed = (recordedMillis + (mark?.elapsedNow()?.inWholeMilliseconds ?: 0L)) / 1000
                val level = if (recordPhase == "recording") recorder.level().coerceIn(0f, 1f) else 0f
                liveWave = liveWave.drop(1) + level
            }
            playback = audio.telemetry()
            if (++tick % 12 == 0 && working) try { refresh() } catch (e: CancellationException) { throw e } catch (_: Exception) { error = "actionFailed" }
        }
    }
    suspend fun savePreferences(value: Preferences) = action { repository.savePreferences(value.validated()); preferences = value }
    suspend fun startRecording() = controls {
        check(current == null && !pending) { "currentExists" }
        check(playback.phase == "idle") { "stopPlayback" }
        recorder.start(); recordPhase = recorder.phase(); recordedMillis = 0; elapsed = 0; mark = TimeSource.Monotonic.markNow()
        liveWave = List(80) { 0f }; loadedAudioId = null
    }
    suspend fun pauseRecording() = controls {
        recorder.pause(); recordedMillis += mark?.elapsedNow()?.inWholeMilliseconds ?: 0; mark = null; recordPhase = "paused"
    }
    suspend fun resumeRecording() = controls { recorder.resume(); recordPhase = "recording"; mark = TimeSource.Monotonic.markNow() }
    private suspend fun finishRecording() {
        try {
            recorder.stopAndUpload(); mark = null; elapsed = 0; refresh()
        } finally { recordPhase = recorder.phase(); pending = recorder.hasPending() }
    }
    suspend fun stopRecording() = controls { finishRecording() }
    suspend fun recover() = controls {
        try { recorder.recoverPending(); refresh() } finally { pending = recorder.hasPending() }
    }
    suspend fun demo() = action {
        check(!recording && current == null && !pending && playback.phase == "idle") { "currentExists" }
        repository.createDemo(); refresh(); loadedAudioId = null
    }
    suspend fun retry() = action { current?.let { repository.reprocess(it.id); refresh() } }
    suspend fun tidy() = action {
        flush(); val c = current ?: return@action
        repository.tidy(c.id); refresh()
    }
    suspend fun send() = action {
        flush(); val c = current ?: return@action
        check(c.textToSave.isNotBlank()) { "emptyText" }
        repository.rank(c.id); refresh(); choosingProject = true; targetProjectId = null
    }
    suspend fun distribute(projectId: String, noteId: String? = null) = action {
        flush(); val c = current ?: return@action
        repository.distribute(c.id, DistributionRequest(projectId, noteId, title))
        dirty = false; choosingProject = false; targetProjectId = null; tab = Tab.HOME; refresh()
    }
    suspend fun discard() = action {
        val c = current ?: return@action
        if (loadedAudioId == c.id) { audio.stop(); loadedAudioId = null; playback = AudioTelemetry() }
        repository.discard(c.id); dirty = false; confirmDelete = false; choosingProject = false; tab = Tab.HOME; refresh()
    }
    fun orderedProjects(): List<Project> = ProjectOrder.sorted(snapshot.projects, current?.relevance.orEmpty())
    fun projectNotes(id: String) = snapshot.notes.filter { it.projectId == id }.sortedByDescending { it.updatedAt }
    fun noteSources(id: String) = snapshot.captures.filter { it.noteId == id }.sortedBy { it.appendedAt }
    fun openNote(id: String) {
        selectedNoteId = id
        if (!recording && playback.phase == "idle") loadedAudioId = noteSources(id).firstOrNull()?.id
    }
    suspend fun requestListen(id: String) {
        if (recording) { confirmListenId = id; return }
        controls { startPlayback(id) }
    }
    suspend fun confirmStopAndListen() = controls {
        val id = confirmListenId ?: return@controls
        finishRecording() // Сначала закрыт микрофон и сохранён журнал; обработка может продолжаться.
        confirmListenId = null
        startPlayback(id)
    }
    private suspend fun startPlayback(id: String, position: Double = 0.0) {
        check(!recording && recorder.phase() == "idle") { "stopRecording" }
        audio.playCapture(id, false, position, playbackRate); loadedAudioId = id; playback = audio.telemetry()
    }
    suspend fun play() = controls { loadedAudioId?.let { startPlayback(it) } }
    suspend fun pausePlayback() = controls { audio.pause(); playback = audio.telemetry() }
    suspend fun resumePlayback() = controls { check(!recording); audio.resume(); playback = audio.telemetry() }
    fun stopPlayback() { audio.stop(); playback = AudioTelemetry() }
    suspend fun changePlaybackRate() = controls {
        val position = playback.position; val wasPlaying = playback.phase == "playing"
        playbackRate = when (playbackRate) { 1.0 -> 1.25; 1.25 -> 1.5; 1.5 -> 2.0; else -> 1.0 }
        if (wasPlaying) loadedAudioId?.let { startPlayback(it, position) }
        else if (playback.phase == "paused") { audio.stop(); playback = AudioTelemetry() }
    }
    suspend fun createProject(title: String, instruction: String) = action {
        repository.createProject(ProjectDraft(title, instruction = instruction)); editingProjectId = null; refresh()
    }
    suspend fun updateProject(id: String, title: String, instruction: String) = action {
        val p = snapshot.projects.first { it.id == id }
        repository.updateProject(id, ProjectUpdate(title, p.description, instruction)); editingProjectId = null; refresh()
    }
    suspend fun pin(p: Project) = action { repository.pinProject(p.id, !p.pinned); refresh() }
    suspend fun movePin(p: Project, delta: Int) = action {
        val ids = ProjectOrder.sorted(snapshot.projects).filter { it.pinned }.map { it.id }.toMutableList()
        val old = ids.indexOf(p.id); val next = old + delta
        if (next in ids.indices) { ids.removeAt(old); ids.add(next, p.id); repository.orderPins(ids); refresh() }
    }
    private suspend fun action(block: suspend () -> Unit): Boolean {
        if (busy) return false
        busy = true
        return try { block(); true } catch (e: CancellationException) { throw e }
        catch (e: Exception) { error = e.message?.takeIf { Copy.has(it) } ?: "actionFailed"; false } finally { busy = false }
    }
    private suspend fun controls(block: suspend () -> Unit): Boolean {
        if (controlBusy) return false
        controlBusy = true
        return try { block(); true } catch (e: CancellationException) { throw e }
        catch (e: Exception) { error = e.message?.takeIf { Copy.has(it) } ?: "audioFailed"; false } finally { controlBusy = false }
    }
}
