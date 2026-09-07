import XCTest
@testable import ThirdBrain

@MainActor final class StoreTests: XCTestCase {
    func testAppendIsIdempotentAndKeepsSources() throws {
        let store = try LocalStore(inMemory: true)
        try store.addProject(title: "Проект", details: "", instruction: "")
        let project = try XCTUnwrap(store.projects.first)
        let first = Capture(originalPath: "Audio/test/first.caf")
        first.preparedText = "Старый текст  \n"; first.title = "Заметка"; first.phase = .ready
        try store.insert(first)
        let note = try store.distribute(first, project: project, existing: nil)
        let second = Capture(originalPath: "Audio/test/second.caf")
        second.preparedText = "Новый текст"; second.phase = .ready; try store.insert(second)
        try store.distribute(second, project: project, existing: note)
        try store.distribute(second, project: project, existing: note)
        XCTAssertEqual(note.body, "Старый текст  \n\n\nНовый текст")
        XCTAssertEqual(store.sources(for: note).count, 2)
        XCTAssertEqual(store.notes.count, 1)
        try store.reload()
        XCTAssertEqual(store.captures.filter { $0.noteID == note.id }.count, 2)
    }
    func testWrongProjectDoesNotModifyNote() throws {
        let store = try LocalStore(inMemory: true)
        try store.addProject(title: "Первый", details: "", instruction: "")
        try store.addProject(title: "Второй", details: "", instruction: "")
        let source = Capture(originalPath: "Audio/test/original.caf")
        source.preparedText = "Исходный"; try store.insert(source)
        let note = try store.distribute(source, project: store.projects[0], existing: nil)
        let addition = Capture(originalPath: "Audio/test/add.caf")
        addition.preparedText = "Добавка"; try store.insert(addition)
        XCTAssertThrowsError(try store.distribute(addition, project: store.projects[1], existing: note))
        XCTAssertEqual(note.body, "Исходный"); XCTAssertNil(addition.noteID)
    }
    func testDeletingNoteReturnsSourcesToInbox() throws {
        let store = try LocalStore(inMemory: true)
        try store.addProject(title: "Проект", details: "", instruction: "")
        let source = Capture(originalPath: "Audio/test/original.caf")
        source.preparedText = "Текст"; try store.insert(source)
        let note = try store.distribute(source, project: store.projects[0], existing: nil)
        try store.deleteNote(note)
        XCTAssertNil(source.noteID); XCTAssertEqual(store.captures.count, 1)
    }
}
