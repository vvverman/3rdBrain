import Foundation
import SwiftData

@Model final class ProbeRecord {
    var title: String
    init(title: String) { self.title = title }
}

@main struct Probe {
    @MainActor static func main() throws {
        for mode in ["main", "separate", "pending", "undo", "undoRollback", "transaction"] {
            let dir = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString)
            try FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
            defer { try? FileManager.default.removeItem(at: dir) }
            let url = dir.appendingPathComponent("probe.store")
            try autoreleasepool {
                let container = try ModelContainer(for: ProbeRecord.self, configurations: ModelConfiguration(url: url))
                let context = ModelContext(container)
                context.insert(ProbeRecord(title: "Проект"))
                try context.save()
            }
            let container = try ModelContainer(for: ProbeRecord.self, configurations: ModelConfiguration(url: url, allowsSave: false))
            let context = mode == "main" ? container.mainContext : ModelContext(container)
            context.autosaveEnabled = false
            if mode.hasPrefix("undo") {
                context.undoManager = UndoManager()
                context.undoManager!.groupsByEvent = false
                context.undoManager!.beginUndoGrouping()
            }
            let record = try context.fetch(FetchDescriptor<ProbeRecord>())[0]
            do {
                if mode == "transaction" {
                    try context.transaction { record.title = "Изменено" }
                } else {
                    record.title = "Изменено"
                    if mode == "pending" || mode.hasPrefix("undo") { context.processPendingChanges() }
                    if mode.hasPrefix("undo") { context.undoManager!.endUndoGrouping() }
                    try context.save()
                }
                print("UNEXPECTED SAVE SUCCESS", mode)
            } catch {
                if mode == "undo" || mode == "undoRollback", let undo = context.undoManager, undo.canUndo { undo.undo() }
                if mode != "undo" { context.rollback() }
                let fetched = try context.fetch(FetchDescriptor<ProbeRecord>())[0]
                let persisted = try ModelContext(container).fetch(FetchDescriptor<ProbeRecord>())[0]
                print("PROBE", mode, "held=", record.title, "fetched=", fetched.title, "persisted=", persisted.title, "dirty=", context.hasChanges)
            }
        }
    }
}
