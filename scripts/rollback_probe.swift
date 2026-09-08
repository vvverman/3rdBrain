import Foundation
import SwiftData

@main struct StorageProbe {
    @MainActor static func main() throws {
        var failures = 0
        for mode in ["edit", "append", "new", "delete"] {
            let directory = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString)
            let url = directory.appendingPathComponent("probe.store")
            defer { try? FileManager.default.removeItem(at: directory) }
            let secondID: UUID = try autoreleasepool {
                let store = try LocalStore(storeURL: url)
                try store.addProject(title: "Проект", details: "Описание", instruction: "Тема")
                if mode != "new" {
                    let first = Capture(originalPath: "Audio/\(UUID())/original.caf")
                    first.phase = .ready; first.preparedText = "Старое"
                    try store.insert(first)
                    try store.distribute(first, project: store.projects[0], existing: nil)
                }
                let second = Capture(originalPath: "Audio/\(UUID())/original.caf")
                second.phase = .ready; second.preparedText = "Добавление"
                try store.insert(second)
                return second.id
            }
            let store = try LocalStore(storeURL: url, allowsSave: false)
            let project = store.projects[0]
            let capture = store.captures.first { $0.id == secondID }!
            let note = store.notes.first
            do {
                switch mode {
                case "edit": project.title = "Не сохранено"; try store.save()
                case "append": try store.distribute(capture, project: project, existing: note)
                case "new": try store.distribute(capture, project: project, existing: nil)
                default: try store.deleteNote(note!)
                }
                print("PROBE FAILED: неожиданное сохранение", mode); failures += 1
            } catch {
                let ok = project.title == "Проект" && capture.noteID == nil
                    && (mode == "new" ? store.notes.isEmpty : store.notes.count == 1 && note?.body == "Старое")
                print("PROBE", mode, ok ? "PASS" : "FAIL", "notes=", store.notes.count, "capture.noteID=", String(describing: capture.noteID))
                if !ok { failures += 1 }
            }
        }
        guard failures == 0 else { throw BrainError("Сбой восстановления: \(failures)") }
    }
}
