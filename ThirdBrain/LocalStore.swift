import Foundation
import Observation
import SwiftData

@MainActor @Observable final class LocalStore {
    @ObservationIgnored let container: ModelContainer
    @ObservationIgnored let context: ModelContext
    private(set) var projects: [BrainProject] = []
    private(set) var notes: [BrainNote] = []
    private(set) var captures: [Capture] = []
    init(inMemory: Bool = false, storeURL: URL? = nil, allowsSave: Bool = true) throws {
        let config: ModelConfiguration
        if let storeURL {
            try FileManager.default.createDirectory(at: storeURL.deletingLastPathComponent(), withIntermediateDirectories: true)
            config = ModelConfiguration(url: storeURL, allowsSave: allowsSave, cloudKitDatabase: .none)
        } else {
            if !inMemory {
                _ = try FileManager.default.url(for: .applicationSupportDirectory, in: .userDomainMask, appropriateFor: nil, create: true)
            }
            config = ModelConfiguration(isStoredInMemoryOnly: inMemory, allowsSave: allowsSave, cloudKitDatabase: .none)
        }
        container = try ModelContainer(for: BrainProject.self, BrainNote.self, Capture.self, configurations: config)
        context = container.mainContext; context.autosaveEnabled = false
        try reload()
    }
    func reload() throws {
        let projects = try context.fetch(FetchDescriptor<BrainProject>(sortBy: [SortDescriptor(\.createdAt)]))
        let notes = try context.fetch(FetchDescriptor<BrainNote>(sortBy: [SortDescriptor(\.updatedAt, order: .reverse)]))
        let captures = try context.fetch(FetchDescriptor<Capture>(sortBy: [SortDescriptor(\.createdAt, order: .reverse)]))
        self.projects = projects; self.notes = notes; self.captures = captures
    }
    func save() throws {
        do { try context.save() }
        catch { context.rollback(); try? reload(); throw error }
        try reload()
    }
    func insert(_ capture: Capture) throws {
        guard !captures.contains(where: { $0.id == capture.id }) else { throw BrainError("Запись с таким идентификатором уже сохранена.") }
        context.insert(capture); try save()
    }
    func addProject(title: String, details: String, instruction: String) throws {
        guard !title.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else { throw BrainError("Введите название проекта.") }
        context.insert(BrainProject(title: title, details: details, instruction: instruction)); try save()
    }
    func addNote(project: BrainProject, title: String, body: String) throws {
        guard projects.contains(where: { $0 === project }) else { throw BrainError("Проект больше не существует.") }
        guard !title.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty,
              !body.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else { throw BrainError("Введите заголовок и текст заметки.") }
        context.insert(BrainNote(projectID: project.id, title: title, body: body)); try save()
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
        guard projects.contains(where: { $0 === project }) else { throw BrainError("Проект больше не существует.") }
        guard project.pinned != pinned else { return }
        let ordered = orderedProjects().filter { $0.pinned && $0.id != project.id }
        for (index, item) in ordered.enumerated() { item.pinOrder = index }
        project.pinned = pinned
        project.pinOrder = pinned ? ordered.count : 0
        try save()
    }
    func orderPins(_ ids: [UUID]) throws {
        let pinned = projects.filter(\.pinned)
        guard ids.count == pinned.count, Set(ids) == Set(pinned.map(\.id)) else {
            throw BrainError("Список закреплённых проектов изменился. Повторите сортировку.")
        }
        for (index, id) in ids.enumerated() { pinned.first { $0.id == id }?.pinOrder = index }
        try save()
    }
    @discardableResult func distribute(_ capture: Capture, project: BrainProject, existing: BrainNote?) throws -> BrainNote {
        guard captures.contains(where: { $0 === capture }) else { throw BrainError("Запись больше не существует.") }
        // Квитанция проверяется до изменения текста: повторное сохранение не создаёт дубликат.
        if let id = capture.noteID {
            guard let saved = notes.first(where: { $0.id == id }) else { throw BrainError("Не найдена заметка, связанная с источником.") }
            return saved
        }
        guard capture.phase.allowsEditing else { throw BrainError("Сначала завершите запись и её обработку.") }
        guard projects.contains(where: { $0 === project }) else { throw BrainError("Проект больше не существует.") }
        let text = capture.textToSave
        guard !text.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else { throw BrainError("Сначала добавьте текст или распознайте запись.") }
        let target: BrainNote
        if let existing {
            guard notes.contains(where: { $0 === existing }) else { throw BrainError("Заметка больше не существует. Выберите другую.") }
            guard existing.projectID == project.id else { throw BrainError("Эта заметка находится в другом проекте.") }
            target = existing
        } else {
            target = BrainNote(projectID: project.id, title: capture.title, body: ""); context.insert(target)
        }
        target.body = NoteText.appending(text, to: target.body); target.updatedAt = .now
        capture.noteID = target.id; capture.appendedAt = .now
        try save()
        return target
    }
    func deleteNote(_ note: BrainNote) throws {
        guard notes.contains(where: { $0 === note }) else { throw BrainError("Заметка больше не существует.") }
        for capture in sources(for: note) { capture.noteID = nil; capture.appendedAt = nil }
        context.delete(note); try save()
    }
    func deleteEmptyProject(_ project: BrainProject) throws {
        guard projects.contains(where: { $0 === project }) else { throw BrainError("Проект больше не существует.") }
        guard notes(in: project).isEmpty else { throw BrainError("Сначала удалите заметки из проекта. Их источники останутся во «Входящих».") }
        context.delete(project); try save()
    }
    func deleteCapture(_ capture: Capture) throws {
        guard captures.contains(where: { $0 === capture }) else { throw BrainError("Запись больше не существует.") }
        guard capture.phase.allowsEditing else { throw BrainError("Нельзя удалять активную или обрабатываемую запись.") }
        guard capture.noteID == nil else { throw BrainError("Источник используется в заметке.") }
        let path = capture.originalPath
        _ = try LocalFiles.recordingDirectory(containing: path)
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
        guard !relativePath.hasPrefix("/"), !relativePath.split(separator: "/").contains("..") else {
            throw BrainError("Некорректный путь аудиофайла.")
        }
        let root = try root().resolvingSymlinksInPath().standardizedFileURL
        let url = root.appendingPathComponent(relativePath).resolvingSymlinksInPath().standardizedFileURL
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
    static func recordingDirectory(containing path: String) throws -> URL {
        let components = path.split(separator: "/", omittingEmptySubsequences: false)
        guard components.count == 3, components[0] == "Audio",
              UUID(uuidString: String(components[1])) != nil, components[2] == "original.caf" else {
            throw BrainError("Некорректный каталог записи. Файлы не удалены.")
        }
        let directory = try url(path).deletingLastPathComponent()
        let expected = try root().resolvingSymlinksInPath().appendingPathComponent("Audio").appendingPathComponent(String(components[1])).standardizedFileURL
        guard directory == expected else { throw BrainError("Нельзя удалить каталог по символической ссылке.") }
        return directory
    }
    static func deleteRecording(containing path: String) throws {
        let directory = try recordingDirectory(containing: path)
        if FileManager.default.fileExists(atPath: directory.path) { try FileManager.default.removeItem(at: directory) }
    }
}
