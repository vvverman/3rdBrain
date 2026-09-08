package brain.model

import kotlinx.serialization.Serializable

@Serializable
data class Project(
    val id: String,
    val title: String,
    val description: String = "",
    val instruction: String = "",
    val pinned: Boolean = false,
    val pinOrder: Int = 0,
    val createdAt: Long = 0,
)

@Serializable
data class Note(
    val id: String,
    val projectId: String,
    val title: String,
    val body: String,
    val createdAt: Long,
    val updatedAt: Long,
)

@Serializable
enum class CaptureStatus {
    RECORDING,
    QUEUED,
    TRANSCRIBING,
    COMPACTING,
    POLISHING,
    READY,
    NEEDS_MODEL,
    FAILED;
    val isWorking: Boolean get() = this in setOf(RECORDING, QUEUED, TRANSCRIBING, COMPACTING, POLISHING)
}

@Serializable
data class Capture(
    val id: String,
    val createdAt: Long,
    val title: String = "Новая запись",
    val transcript: String = "",
    val preparedText: String = "",
    val status: CaptureStatus = CaptureStatus.QUEUED,
    val message: String = "",
    val audioFileName: String? = null,
    val noteId: String? = null,
    val appendedAt: Long? = null,
    val relevance: Map<String, Int> = emptyMap(),
    val draftEdited: Boolean = false,
    val llmApplied: Boolean = false,
    val rankingApplied: Boolean = false,
    val durationSeconds: Double = 0.0,
    val compactAudioFileName: String? = null,
    val compactDurationSeconds: Double = 0.0,
    val pieces: List<TranscriptPiece> = emptyList(),
    val spans: List<AudioSpan> = emptyList(),
) {
    val textToSave: String get() = if (draftEdited) preparedText else preparedText.ifBlank { transcript }
    val isInbox: Boolean get() = noteId == null
}

@Serializable
data class RuntimeStatus(
    val whisperConfigured: Boolean = false,
    val llmConfigured: Boolean = false,
    val localOnly: Boolean = true,
    val message: String = "",
)

@Serializable
data class AppSnapshot(
    val projects: List<Project> = emptyList(),
    val notes: List<Note> = emptyList(),
    val captures: List<Capture> = emptyList(),
    val runtime: RuntimeStatus = RuntimeStatus(),
)

@Serializable data class ProjectDraft(val title: String, val description: String = "", val instruction: String = "")
@Serializable data class ProjectUpdate(val title: String, val description: String = "", val instruction: String = "")
@Serializable data class PinRequest(val pinned: Boolean)
@Serializable data class PinOrderRequest(val ids: List<String>)
@Serializable data class CaptureDraftUpdate(val title: String, val text: String)
@Serializable data class NoteUpdate(val title: String, val body: String)
@Serializable data class DistributionRequest(val projectId: String, val noteId: String? = null, val title: String? = null)

@Serializable data class TranscriptPiece(val start: Double, val end: Double, val text: String)
@Serializable data class AudioSpan(val originalStart: Double, val duration: Double, val compactStart: Double)
@Serializable data class ApiError(val error: String)
