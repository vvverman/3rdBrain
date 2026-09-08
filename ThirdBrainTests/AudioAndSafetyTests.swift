import AVFoundation
import XCTest
@testable import ThirdBrain

@MainActor final class AudioAndSafetyTests: XCTestCase {
    private func writeAudio(_ url: URL, silent: Bool = false) throws {
        let format = try XCTUnwrap(AVAudioFormat(standardFormatWithSampleRate: 16000, channels: 1))
        let file = try AVAudioFile(forWriting: url, settings: format.settings)
        let buffer = try XCTUnwrap(AVAudioPCMBuffer(pcmFormat: format, frameCapacity: 64000))
        buffer.frameLength = 64000
        let channel = try XCTUnwrap(buffer.floatChannelData)[0]
        for index in 0..<64000 {
            let time = Double(index) / 16000
            channel[index] = !silent && (time < 0.8 || time > 3.2) ? Float(0.3 * sin(2 * .pi * 440 * time)) : 0
        }
        try file.write(from: buffer)
    }
    func testRealAudioExportShortensSilenceAndKeepsOriginal() async throws {
        let directory = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString)
        try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
        defer { try? FileManager.default.removeItem(at: directory) }
        let source = directory.appendingPathComponent("original.caf"), destination = directory.appendingPathComponent("compact.m4a")
        try writeAudio(source)
        let original = try Data(contentsOf: source)
        let result = try await AudioCompactor().compact(source: source, destination: destination)
        XCTAssertEqual(try Data(contentsOf: source), original)
        XCTAssertLessThan(result.duration, 3)
        XCTAssertGreaterThan(result.duration, 1.5)
        let exported = try AVAudioFile(forReading: destination)
        XCTAssertEqual(Double(exported.length) / exported.processingFormat.sampleRate, result.duration, accuracy: 0.15)
        XCTAssertEqual(result.spans.count, 2)
    }
    func testSilentFileIsNotDiscarded() async throws {
        let directory = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString)
        try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
        defer { try? FileManager.default.removeItem(at: directory) }
        let source = directory.appendingPathComponent("silent.caf"), destination = directory.appendingPathComponent("compact.m4a")
        try writeAudio(source, silent: true)
        let result = try await AudioCompactor().compact(source: source, destination: destination)
        XCTAssertEqual(result.duration, 4, accuracy: 0.01)
        XCTAssertTrue(FileManager.default.fileExists(atPath: source.path))
    }
    func testCancelledExportKeepsExistingDestinationAndOriginal() async throws {
        let directory = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString)
        try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
        defer { try? FileManager.default.removeItem(at: directory) }
        let source = directory.appendingPathComponent("original.caf"), destination = directory.appendingPathComponent("compact.m4a")
        try writeAudio(source)
        let original = try Data(contentsOf: source), previous = Data("Старая версия".utf8)
        try previous.write(to: destination)
        let task = Task { try await AudioCompactor().compact(source: source, destination: destination) }
        task.cancel()
        do { _ = try await task.value; XCTFail("Отменённый экспорт не должен завершаться успехом") }
        catch is CancellationError {}
        XCTAssertEqual(try Data(contentsOf: destination), previous)
        XCTAssertEqual(try Data(contentsOf: source), original)
        XCTAssertEqual(try FileManager.default.contentsOfDirectory(atPath: directory.path).count, 2)
    }
    func testCompactorRefusesToOverwriteOriginal() async throws {
        let source = FileManager.default.temporaryDirectory.appendingPathComponent("\(UUID()).caf")
        defer { try? FileManager.default.removeItem(at: source) }
        try writeAudio(source)
        let before = try Data(contentsOf: source)
        do { _ = try await AudioCompactor().compact(source: source, destination: source); XCTFail("Оригинал защищён") }
        catch is BrainError {}
        XCTAssertEqual(try Data(contentsOf: source), before)
    }
    func testFilePathsCannotEscapeStorageOrDeleteItsRoot() throws {
        for path in ["", "..", "../outside", "/outside", "Audio/../../outside"] {
            XCTAssertThrowsError(try LocalFiles.url(path))
        }
        for path in ["Audio", "Audio/test/original.caf", "other/file.caf", "Audio/\(UUID())/compact.m4a"] {
            XCTAssertThrowsError(try LocalFiles.deleteRecording(containing: path))
        }
        XCTAssertTrue(FileManager.default.fileExists(atPath: try LocalFiles.root().path))
    }
    func testDeletingOneRecordingKeepsOtherRecordings() throws {
        let first = try LocalFiles.createRecording(id: UUID()), second = try LocalFiles.createRecording(id: UUID())
        defer { try? LocalFiles.deleteRecording(containing: first); try? LocalFiles.deleteRecording(containing: second) }
        try Data([1, 2, 3]).write(to: LocalFiles.url(first))
        try Data([4, 5, 6]).write(to: LocalFiles.url(second))
        try LocalFiles.deleteRecording(containing: first)
        XCTAssertEqual(try Data(contentsOf: LocalFiles.url(second)), Data([4, 5, 6]))
        XCTAssertFalse(FileManager.default.fileExists(atPath: try LocalFiles.url(first).path))
    }
    func testSymlinkCannotRedirectDeletionToAnotherRecording() throws {
        let real = try LocalFiles.createRecording(id: UUID())
        let fakeID = UUID().uuidString
        let root = try LocalFiles.root().appendingPathComponent("Audio")
        let link = root.appendingPathComponent(fakeID)
        defer { try? FileManager.default.removeItem(at: link); try? LocalFiles.deleteRecording(containing: real) }
        try FileManager.default.createSymbolicLink(at: link, withDestinationURL: try LocalFiles.url(real).deletingLastPathComponent())
        XCTAssertThrowsError(try LocalFiles.deleteRecording(containing: "Audio/\(fakeID)/original.caf"))
        XCTAssertTrue(FileManager.default.fileExists(atPath: try LocalFiles.url(real).deletingLastPathComponent().path))
    }
    func testCancelledLLMDoesNotReturnFakeSuccess() async throws {
        let model = LocalIntelligence()
        let task = Task { try await model.clean("Текст", localeID: "ru-RU") }
        task.cancel()
        do { _ = try await task.value; XCTFail("Ожидалась отмена") }
        catch is CancellationError {}
    }
    func testPinnedOnlyRankingDoesNotNeedModel() async throws {
        let result = try await LocalIntelligence().rank(text: "Текст", projects: [ProjectCandidate(id: UUID(), title: "Проект", pinned: true)], localeID: "ru-RU")
        XCTAssertTrue(result.isEmpty)
    }
    func testLateRecorderErrorWithoutSessionIsIgnored() async throws {
        let url = FileManager.default.temporaryDirectory.appendingPathComponent("\(UUID()).caf")
        defer { try? FileManager.default.removeItem(at: url) }
        let old = try AVAudioRecorder(url: url, settings: [AVFormatIDKey: kAudioFormatLinearPCM, AVSampleRateKey: 16000, AVNumberOfChannelsKey: 1, AVLinearPCMBitDepthKey: 16])
        let recorder = AudioRecorder()
        var called = false
        recorder.onUnexpectedStop = { _ in called = true }
        recorder.audioRecorderEncodeErrorDidOccur(old, error: BrainError("Позднее событие"))
        for _ in 0..<5 { await Task.yield() }
        XCTAssertFalse(called)
        XCTAssertFalse(recorder.hasSession)
    }
    func testMissingPlaybackFileDoesNotStartPlayer() throws {
        let player = SourcePlayer()
        XCTAssertThrowsError(try player.play(path: "Audio/\(UUID())/original.caf"))
        XCTAssertNil(player.path); XCTAssertFalse(player.isPlaying)
        player.seek(to: .nan)
        XCTAssertEqual(player.position, 0)
    }
}
