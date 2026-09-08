import Foundation
import XCTest
@testable import ThirdBrain

@MainActor final class StoreRobustnessTests: XCTestCase {
    private func ready(_ store: LocalStore, text: String = "Новый текст") throws -> Capture {
        let capture = Capture(originalPath: "Audio/\(UUID().uuidString)/original.caf")
        capture.phase = .ready; capture.preparedText = text; capture.title = "Запись"
        try store.insert(capture)
        return capture
    }
    private func project(_ store: LocalStore) throws -> BrainProject {
        try store.addProject(title: "Проект", details: "Описание", instruction: "Что сюда складывать")
        return try XCTUnwrap(store.projects.last)
    }
    func testDeletedDestinationCannotSwallowAnAddition() throws {
        let store = try LocalStore(inMemory: true)
        let project = try project(store)
        let first = try ready(store, text: "Старый текст")
        let note = try store.distribute(first, project: project, existing: nil)
        let second = try ready(store)
        try store.deleteNote(note)
        XCTAssertThrowsError(try store.distribute(second, project: project, existing: note))
        XCTAssertNil(second.noteID)
        XCTAssertEqual(second.preparedText, "Новый текст")
        XCTAssertEqual(store.captures.count, 2)
        XCTAssertTrue(store.notes.isEmpty)
    }
    func testDetachedDestinationIsRejected() throws {
        let store = try LocalStore(inMemory: true)
        let project = try project(store)
        let capture = try ready(store)
        let missing = BrainNote(projectID: project.id, title: "Чужой объект", body: "Не менять")
        XCTAssertThrowsError(try store.distribute(capture, project: project, existing: missing))
        XCTAssertEqual(missing.body, "Не менять")
        XCTAssertNil(capture.noteID)
    }
    func testDeletedProjectRejectsNewNoteAndPin() throws {
        let store = try LocalStore(inMemory: true)
        let project = try project(store)
        let capture = try ready(store)
        try store.deleteEmptyProject(project)
        XCTAssertThrowsError(try store.distribute(capture, project: project, existing: nil))
        XCTAssertThrowsError(try store.addNote(project: project, title: "Новая", body: "Текст"))
        XCTAssertThrowsError(try store.setPinned(project, true))
        XCTAssertNil(capture.noteID)
    }
    func testActiveCaptureCannotBeDistributedOrDeleted() throws {
        let store = try LocalStore(inMemory: true)
        let project = try project(store)
        let capture = try ready(store)
        for phase: CapturePhase in [.recording, .paused, .queued, .transcribing, .compacting, .polishing] {
            capture.phase = phase; try store.save()
            XCTAssertThrowsError(try store.distribute(capture, project: project, existing: nil))
            XCTAssertThrowsError(try store.deleteCapture(capture))
            XCTAssertEqual(store.captures.count, 1)
            XCTAssertTrue(store.notes.isEmpty)
        }
    }
    func testFailedCaptureWithManualTextCanBeSaved() throws {
        let store = try LocalStore(inMemory: true)
        let project = try project(store)
        let capture = try ready(store)
        capture.phase = .failed; capture.draftEdited = true; try store.save()
        let note = try store.distribute(capture, project: project, existing: nil)
        XCTAssertEqual(note.body, capture.preparedText)
    }
    func testUnsavedCaptureIsRejected() throws {
        let store = try LocalStore(inMemory: true)
        let project = try project(store)
        let capture = Capture(originalPath: "Audio/test/original.caf")
        capture.phase = .ready; capture.preparedText = "Не потерять"
        XCTAssertThrowsError(try store.distribute(capture, project: project, existing: nil))
        XCTAssertTrue(store.notes.isEmpty)
    }
    func testDuplicateCaptureIDDoesNotOverwriteSource() throws {
        let store = try LocalStore(inMemory: true)
        let first = try ready(store, text: "Исходное")
        let duplicate = Capture(id: first.id, originalPath: "Audio/other/original.caf")
        duplicate.preparedText = "Подмена"
        XCTAssertThrowsError(try store.insert(duplicate))
        XCTAssertEqual(store.captures.count, 1)
        XCTAssertEqual(store.captures[0].preparedText, "Исходное")
    }
    func testPinningIsIdempotentAndInvalidOrderIsRejected() throws {
        let store = try LocalStore(inMemory: true)
        let first = try project(store)
        let second = try project(store)
        try store.setPinned(first, true); try store.setPinned(second, true)
        try store.orderPins([second.id, first.id])
        try store.setPinned(second, true)
        XCTAssertEqual(store.orderedProjects().map(\.id), [second.id, first.id])
        XCTAssertThrowsError(try store.orderPins([first.id, first.id]))
        XCTAssertThrowsError(try store.orderPins([first.id]))
        XCTAssertEqual(store.orderedProjects().map(\.id), [second.id, first.id])
    }
    func testBlankTextCannotCreateNote() throws {
        let store = try LocalStore(inMemory: true)
        let project = try project(store)
        let capture = try ready(store, text: " \n\t")
        XCTAssertThrowsError(try store.distribute(capture, project: project, existing: nil))
        XCTAssertThrowsError(try store.addNote(project: project, title: " ", body: "Текст"))
        XCTAssertTrue(store.notes.isEmpty)
    }
    func testAllProjectsRemainInPickerWithPinsAboveScores() throws {
        let store = try LocalStore(inMemory: true)
        let first = try project(store)
        let second = try project(store)
        let third = try project(store)
        try store.setPinned(third, true)
        XCTAssertEqual(store.orderedProjects(scores: [first.id: 1, second.id: 4]).map(\.id), [third.id, second.id, first.id])
    }
    func testProtectedSourceAndNonemptyProjectCannotBeDeleted() throws {
        let store = try LocalStore(inMemory: true)
        let project = try project(store)
        let capture = try ready(store)
        _ = try store.distribute(capture, project: project, existing: nil)
        XCTAssertThrowsError(try store.deleteCapture(capture))
        XCTAssertThrowsError(try store.deleteEmptyProject(project))
        XCTAssertEqual(store.captures.count, 1)
        XCTAssertEqual(store.notes.count, 1)
    }
    func testActualDiskStoreReopensWithoutLosingSourcesAndPins() throws {
        let directory = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString)
        defer { try? FileManager.default.removeItem(at: directory) }
        let url = directory.appendingPathComponent("brain.store")
        let ids: (UUID, UUID) = try autoreleasepool {
            let store = try LocalStore(storeURL: url)
            let project = try project(store)
            try store.setPinned(project, true)
            let first = try ready(store, text: "Первое  \n")
            first.transcript = "Полная расшифровка"; try store.save()
            let note = try store.distribute(first, project: project, existing: nil)
            let second = try ready(store, text: "Второе")
            try store.distribute(second, project: project, existing: note)
            return (project.id, note.id)
        }
        let reopened = try LocalStore(storeURL: url)
        XCTAssertEqual(reopened.projects.first?.id, ids.0)
        XCTAssertEqual(reopened.projects.first?.pinned, true)
        let note = try XCTUnwrap(reopened.notes.first)
        XCTAssertEqual(note.id, ids.1)
        XCTAssertEqual(note.body, "Первое  \n\n\nВторое")
        XCTAssertEqual(reopened.sources(for: note).count, 2)
        XCTAssertEqual(reopened.sources(for: note).first?.transcript, "Полная расшифровка")
        let source = try XCTUnwrap(reopened.sources(for: note).last)
        try reopened.distribute(source, project: reopened.projects[0], existing: note)
        XCTAssertEqual(note.body, "Первое  \n\n\nВторое")
    }
    func testReadOnlySaveFailureRollsBackPendingChanges() throws {
        let directory = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString)
        defer { try? FileManager.default.removeItem(at: directory) }
        let url = directory.appendingPathComponent("brain.store")
        try autoreleasepool {
            let store = try LocalStore(storeURL: url)
            _ = try project(store)
        }
        let store = try LocalStore(storeURL: url, allowsSave: false)
        let existing = try XCTUnwrap(store.projects.first)
        let original = existing.title
        existing.title = "Несохранённое изменение"
        XCTAssertThrowsError(try store.save())
        XCTAssertEqual(existing.title, original)
        XCTAssertFalse(store.context.hasChanges)
    }
    func testInvalidDeletionPathDoesNotDeleteMetadata() throws {
        let store = try LocalStore(inMemory: true)
        let capture = Capture(originalPath: "Audio")
        capture.phase = .failed; try store.insert(capture)
        XCTAssertThrowsError(try store.deleteCapture(capture))
        XCTAssertEqual(store.captures.count, 1)
    }
}
