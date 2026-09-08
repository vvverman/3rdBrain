import Foundation

// Последнее успешно сохранённое состояние. SwiftData на ошибке save() может
// очистить признаки изменений раньше записи на диск, и одного rollback() недостаточно.
// Значения String/Data используют copy-on-write; аудиофайлы сюда не копируются.
@MainActor extension BrainProject {
    func restorePoint() -> @MainActor () -> Void {
        let values = (title, details, instruction, pinned, pinOrder, createdAt)
        return { (self.title, self.details, self.instruction, self.pinned, self.pinOrder, self.createdAt) = values }
    }
}

@MainActor extension BrainNote {
    func restorePoint() -> @MainActor () -> Void {
        let values = (projectID, title, body, createdAt, updatedAt)
        return { (self.projectID, self.title, self.body, self.createdAt, self.updatedAt) = values }
    }
}

@MainActor extension Capture {
    func restorePoint() -> @MainActor () -> Void {
        let audio = (createdAt, originalPath, compactPath, duration, compactDuration)
        let text = (transcript, preparedText, title, phaseRaw, message)
        let model = (llmApplied, draftEdited, speechEngine, localeID)
        let source = (noteID, appendedAt, transcriptData, timelineData)
        return {
            (self.createdAt, self.originalPath, self.compactPath, self.duration, self.compactDuration) = audio
            (self.transcript, self.preparedText, self.title, self.phaseRaw, self.message) = text
            (self.llmApplied, self.draftEdited, self.speechEngine, self.localeID) = model
            (self.noteID, self.appendedAt, self.transcriptData, self.timelineData) = source
        }
    }
}
