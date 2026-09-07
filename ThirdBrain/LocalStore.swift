import Foundation
import Observation
import SwiftData

@MainActor @Observable final class LocalStore {
    @ObservationIgnored let container: ModelContainer
    @ObservationIgnored let context: ModelContext
    private(set) var projects: [BrainProject] = []
    private(set) var notes: [BrainNote] = []
    private(set) var captures: [Capture] = []
    init(inMemory: Bool = false) throws {
        let config = ModelConfiguration(isStoredInMemoryOnly: inMemory, cloudKitDatabase: .none)
        container = try ModelContainer(for: BrainProject.self, BrainNote.self, Capture.self, configurations: config)
        context = container.mainContext; context.autosaveEnabled = false
        try reload()
    }
    func reload() throws {
        projects = try context.fetch(FetchDescriptor<BrainProject>(sortBy: [SortDescriptor(\.createdAt)]))
        notes = try context.fetch(FetchDescriptor<BrainNote>(sortBy: [SortDescriptor(\.updatedAt, order: .reverse)]))
        captures = try context.fetch(FetchDescriptor<Capture>(sortBy: [SortDescriptor(\.createdAt, order: .reverse)]))
    }
    func save() throws { try context.save(); try reload() }
    func insert(_ capture: Capture) throws { context.insert(capture); try save() }
    func addProject(title: String, details: String, instruction: String) throws {
        guard !title.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else { throw BrainError("Введите название проекта.") }
        context.insert(BrainProject(title: title, details: details, instruction: instruction)); try save()
    }
    func orderedProjects(scores: [UUID: Int] = [:]) -> [BrainProject] {
        let byID = Dictionary(uniqueKeysWithValues: projects.map { ($0.id, $0) })
        return ProjectOrder.sorted(projects.map(\.candidate), scores: scores).compactMap { byID[$0.id] }
    }
    func notes(in project: BrainProject) -> [BrainNote] {
        notes.filter { $0.projectID == project.id }.sorted { $0.updatedAt > $1.updatedAt }
    }
    func sources(for note: BrainNote) -> [Capture] {
        captures.filter { $0.noteID == note.id }.sorted { ($0.appendedAt ?? $0.createdAt) < ($1.appendedAt ?? $1.createdAt) }
    }
    func setPinned(_ project: BrainProject, _ pinned: Bool) throws {
        project.pinned = pinned
        if pinned { project.pinOrder = (projects.map(\.pinOrder).max() ?? 0) + 1 }
        try save()
    }
    func orderPins(_ ids: [UUID]) throws {
        for (index, id) in ids.enumerated() { projects.first { $0.id == id }?.pinOrder = index }
        try save()
    }
    @discardableResult func distribute(_ capture: Capture, project: BrainProject, existing: BrainNote?) throws -> BrainNote {
        // Квитанция проверяется до изменения текста: повторное сохранение не создаёт дубликат.
        if let id = capture.noteID, let saved = notes.first(where: { $0.id == id }) { return saved }
        guard projects.contains(where: { $0.id == project.id }) else { throw BrainError("Проект больше не существует.") }
        let text = capture.textToSave
        guard !text.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else { throw BrainError("Сначала добавьте текст или распознайте запись.") }
        let target: BrainNote
        if let existing {
            guard existing.projectID == project.id else { throw BrainError("Эта заметка находится в другом проекте.") }
            target = existing
        } else {
            target = BrainNote(projectID: project.id, title: capture.title, body: ""); context.insert(target)
        }
        target.body = NoteText.appending(text, to: target.body); target.updatedAt = .now
        capture.noteID = target.id; capture.appendedAt = .now
        do { try save() } catch { context.rollback(); try reload(); throw error }
        return target
    }
    func deleteNote(_ note: BrainNote) throws {
        for capture in sources(for: note) { capture.noteID = nil; capture.appendedAt = nil }
        context.delete(note); try save()
    }
    func deleteEmptyProject(_ project: BrainProject) throws {
        guard notes(in: project).isEmpty else { throw BrainError("Сначала удалите заметки из проекта. Их источники останутся во «Входящих».") }
        context.delete(project); try save()
    }
    func deleteCapture(_ capture: Capture) throws {
        guard capture.noteID == nil else { throw BrainError("Источник используется в заметке.") }
        let path = capture.originalPath
        context.delete(capture); try save()
        try LocalFiles.deleteRecording(containing: path)
    }
}

enum LocalFiles {
    static func root() throws -> URL {
        let fm = FileManager.default
        let base = try fm.url(for: .applicationSupportDirectory, in: .userDomainMask, appropriateFor: nil, create: true)
        let root = base.appendingPathComponent("3rdBrain", isDirectory: true)
        try fm.createDirectory(at: root, withIntermediateDirectories: true,
                               attributes: [.protectionKey: FileProtectionType.completeUntilFirstUserAuthentication])
        return root
    }
    static func url(_ relativePath: String) throws -> URL {
        let root = try root().standardizedFileURL
        let url = root.appendingPathComponent(relativePath).standardizedFileURL
        guard url.path.hasPrefix(root.path + "/") else { throw BrainError("Некорректный путь аудиофайла.") }
        return url
    }
    static func createRecording(id: UUID) throws -> String {
        let path = "Audio/\(id.uuidString)/original.caf"
        let directory = try url(path).deletingLastPathComponent()
        try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true,
            attributes: [.protectionKey: FileProtectionType.completeUntilFirstUserAuthentication])
        return path
    }
    static func deleteRecording(containing path: String) throws {
        let directory = try url(path).deletingLastPathComponent()
        if FileManager.default.fileExists(atPath: directory.path) { try FileManager.default.removeItem(at: directory) }
    }
}
