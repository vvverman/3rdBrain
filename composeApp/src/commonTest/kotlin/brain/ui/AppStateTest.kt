package brain.ui

import brain.domain.*
import brain.model.*
import kotlinx.coroutines.test.runTest
import kotlin.test.*

class AppStateTest {
    private class Repository : BrainRepository {
        var failDraft = false; var distributions = 0
        override suspend fun snapshot() = AppSnapshot()
        override suspend fun createProject(draft: ProjectDraft) = Project("p", draft.title)
        override suspend fun updateProject(id: String, update: ProjectUpdate) = Project(id, update.title)
        override suspend fun pinProject(id: String, pinned: Boolean) = Project(id, "P", pinned = pinned)
        override suspend fun orderPins(ids: List<String>) {}
        override suspend fun updateCaptureDraft(id: String, update: CaptureDraftUpdate): Capture { check(!failDraft); return Capture(id, 0, preparedText = update.text, status = CaptureStatus.READY) }
        override suspend fun distribute(id: String, request: DistributionRequest): Note { distributions++; return Note("n", "p", "N", "text", 0, 0) }
        override suspend fun updateNote(id: String, update: NoteUpdate) = Note(id, "p", update.title, update.body, 0, 0)
        override suspend fun reprocess(id: String) = Capture(id, 0)
    }
    private class Recorder : RecorderGateway {
        var starts = 0; var state = "idle"; var pending = false; var consent = true
        override suspend fun hasConsent() = consent
        override suspend fun hasPending() = pending
        override fun phase() = state
        override suspend fun start() { starts++; state = "recording" }
        override suspend fun pause() { state = "paused" }
        override suspend fun resume() { state = "recording" }
        override suspend fun stopAndUpload(): Capture { state = "idle"; pending = true; error("Сервис недоступен") }
        override suspend fun recoverPending(): Capture { pending = false; return Capture("c", 0) }
    }
    private class Audio : AudioGateway {
        var plays = 0; var stops = 0
        override suspend fun playCapture(captureId: String, compact: Boolean, fromSeconds: Double, rate: Double) { plays++ }
        override fun stop() { stops++ }
    }
    @Test fun launchStartsOnlyOnce() = runTest {
        val recorder = Recorder(); val s = BrainAppState(Repository(), recorder, Audio())
        s.launch(); s.launch(); assertEquals(1, recorder.starts)
    }
    @Test fun pendingAudioPreventsAutomaticNewRecording() = runTest {
        val recorder = Recorder().apply { pending = true }; val s = BrainAppState(Repository(), recorder, Audio())
        s.launch(); assertTrue(s.pendingUpload); assertEquals(0, recorder.starts)
    }
    @Test fun failedUploadShowsStoppedMicAndRecovery() = runTest {
        val s = BrainAppState(Repository(), Recorder(), Audio()); s.launch(); s.stopRecording()
        assertFalse(s.isRecording); assertTrue(s.pendingUpload); assertNotNull(s.error)
    }
    @Test fun draftFailureDoesNotDistributeStaleText() = runTest {
        val repo = Repository().apply { failDraft = true }; val s = BrainAppState(repo, Recorder(), Audio())
        s.saveAndDistribute(Capture("c", 0, transcript = "old", status = CaptureStatus.READY), Project("p", "P"), null, "T", "new")
        assertEquals(0, repo.distributions)
    }
    @Test fun playbackPausesRecording() = runTest {
        val r = Recorder(); val a = Audio(); val s = BrainAppState(Repository(), r, a)
        s.launch(); s.playSource(Capture("c", 0)); assertEquals("paused", r.state); assertEquals(1, a.plays)
        s.resumeRecording(); assertTrue(s.isRecording); assertTrue(a.stops >= 2)
    }
    @Test fun consentCanBeSkippedWithoutMic() = runTest {
        val r = Recorder().apply { consent = false }; val s = BrainAppState(Repository(), r, Audio())
        s.launch(); s.browseOnly(); assertFalse(s.onboarding); assertEquals(0, r.starts)
    }
}
