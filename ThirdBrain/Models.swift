import Foundation
import SwiftData

@Model final class BrainProject {
    @Attribute(.unique) var id: UUID
    var title: String
    var details: String
    var instruction: String
    var pinned: Bool
    var pinOrder: Int
    var createdAt: Date
    init(title: String, details: String = "", instruction: String = "") {
        id = UUID(); self.title = title; self.details = details; self.instruction = instruction
        pinned = false; pinOrder = 0; createdAt = .now
    }
    var candidate: ProjectCandidate {
        ProjectCandidate(id: id, title: title, details: details, instruction: instruction, pinned: pinned, pinOrder: pinOrder)
    }
}

@Model final class BrainNote {
    @Attribute(.unique) var id: UUID
    var projectID: UUID
    var title: String
    var body: String
    var createdAt: Date
    var updatedAt: Date
    init(projectID: UUID, title: String, body: String) {
        id = UUID(); self.projectID = projectID; self.title = title; self.body = body
        createdAt = .now; updatedAt = .now
    }
}

enum CapturePhase: String {
    case recording, paused, queued, transcribing, compacting, polishing, ready, failed
    var label: String {
        switch self {
        case .recording: "Запись"
        case .paused: "Пауза"
        case .queued: "Ожидает обработки"
        case .transcribing: "Распознавание речи"
        case .compacting: "Сокращение пауз"
        case .polishing: "Оформление текста"
        case .ready: "Готово"
        case .failed: "Нужна повторная обработка"
        }
    }
    var isWorking: Bool { [.queued, .transcribing, .compacting, .polishing].contains(self) }
}

@Model final class Capture {
    @Attribute(.unique) var id: UUID
    var createdAt: Date
    var originalPath: String
    var compactPath: String?
    var duration: Double
    var compactDuration: Double
    var transcript: String
    var preparedText: String
    var title: String
    var phaseRaw: String
    var message: String
    var llmApplied: Bool
    var draftEdited: Bool
    var speechEngine: String
    var localeID: String
    var noteID: UUID?
    var appendedAt: Date?
    var transcriptData: Data
    var timelineData: Data
    init(id: UUID = UUID(), originalPath: String, localeID: String = "ru-RU") {
        self.id = id; createdAt = .now; self.originalPath = originalPath; self.localeID = localeID
        compactPath = nil; duration = 0; compactDuration = 0; transcript = ""; preparedText = ""
        title = "Новая запись"; phaseRaw = CapturePhase.recording.rawValue; message = ""
        llmApplied = false; draftEdited = false; speechEngine = ""; noteID = nil; appendedAt = nil
        transcriptData = Data(); timelineData = Data()
    }
    var phase: CapturePhase {
        get { CapturePhase(rawValue: phaseRaw) ?? .failed }
        set { phaseRaw = newValue.rawValue }
    }
    var pieces: [TranscriptPiece] { (try? JSONDecoder().decode([TranscriptPiece].self, from: transcriptData)) ?? [] }
    var spans: [AudioSpan] { (try? JSONDecoder().decode([AudioSpan].self, from: timelineData)) ?? [] }
    var textToSave: String { preparedText.isEmpty ? transcript : preparedText }
}

struct BrainError: LocalizedError {
    let message: String
    var errorDescription: String? { message }
    init(_ message: String) { self.message = message }
}
