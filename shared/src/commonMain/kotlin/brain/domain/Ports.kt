package brain.domain

import brain.model.*

interface BrainRepository {
    suspend fun snapshot(): AppSnapshot
    suspend fun createProject(draft: ProjectDraft): Project
    suspend fun updateProject(id: String, update: ProjectUpdate): Project
    suspend fun pinProject(id: String, pinned: Boolean): Project
    suspend fun orderPins(ids: List<String>)
    suspend fun updateCaptureDraft(id: String, update: CaptureDraftUpdate): Capture
    suspend fun distribute(id: String, request: DistributionRequest): Note
    suspend fun updateNote(id: String, update: NoteUpdate): Note
    suspend fun reprocess(id: String): Capture
}

interface RecorderGateway {
    suspend fun hasConsent(): Boolean
    suspend fun hasPending(): Boolean
    fun phase(): String
    suspend fun start()
    suspend fun pause()
    suspend fun resume()
    /** Сначала сохраняет источник; не удаляет его при ошибке передачи в хранилище. */
    suspend fun stopAndUpload(): Capture
    suspend fun recoverPending(): Capture
}

interface AudioGateway {
    suspend fun playCapture(captureId: String, compact: Boolean = false, fromSeconds: Double = 0.0, rate: Double = 1.0)
    fun stop()
}
