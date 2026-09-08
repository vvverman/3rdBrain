import XCTest
@testable import ThirdBrain

@MainActor final class ProcessingTests: XCTestCase {
    private func fixture() throws -> (AppController, Capture) {
        let store = try LocalStore(inMemory: true)
        let app = try AppController(store: store)
        let capture = Capture(originalPath: "Audio/\(UUID())/original.caf")
        capture.phase = .ready; capture.transcript = "Расшифровка"
        capture.preparedText = "Мой текст"; capture.draftEdited = true
        capture.compactPath = "Audio/\(UUID())/compact.m4a"
        try store.insert(capture)
        return (app, capture)
    }
    func testCompletedStagesAreNotRepeatedAndEditedTextSurvives() async throws {
        let (app, capture) = try fixture()
        app.enqueue(capture, warning: "Системное прерывание")
        await app.worker?.value
        XCTAssertEqual(capture.phase, .ready)
        XCTAssertEqual(capture.preparedText, "Мой текст")
        XCTAssertEqual(capture.transcript, "Расшифровка")
        XCTAssertTrue(capture.message.contains("Системное прерывание"))
        XCTAssertNil(app.processingID)
    }
    func testDuplicateEnqueueDoesNotCreateSecondJob() async throws {
        let (app, capture) = try fixture()
        app.enqueue(capture); app.enqueue(capture)
        XCTAssertEqual(app.queue.count, 1)
        await app.worker?.value
        XCTAssertTrue(app.queue.isEmpty)
    }
    func testCancelledQueueKeepsSourceAndAllowsRetry() async throws {
        let (app, capture) = try fixture()
        let path = capture.originalPath
        app.enqueue(capture); app.worker?.cancel()
        await app.worker?.value
        XCTAssertEqual(capture.phase, .failed)
        XCTAssertEqual(capture.originalPath, path)
        XCTAssertEqual(capture.preparedText, "Мой текст")
        app.enqueue(capture)
        await app.worker?.value
        XCTAssertEqual(capture.phase, .ready)
    }
    func testActiveCaptureCannotBeEnqueued() throws {
        let (app, capture) = try fixture()
        app.activeCaptureID = capture.id
        app.enqueue(capture)
        XCTAssertTrue(app.queue.isEmpty)
        XCTAssertNil(app.worker)
    }
    func testSavedSourceCannotBeReprocessed() throws {
        let (app, capture) = try fixture()
        capture.noteID = UUID(); try app.store.save()
        app.enqueue(capture)
        XCTAssertTrue(app.queue.isEmpty)
    }
}
