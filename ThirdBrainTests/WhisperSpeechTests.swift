import AVFoundation
import Foundation
import WhisperKit
import XCTest
@testable import ThirdBrain

final class WhisperSpeechTests: XCTestCase {
    func testRussianTranscriptionNotEnglishTranslation() {
        let options = WhisperSpeechService.decodingOptions()
        XCTAssertEqual(options.language, "ru")
        XCTAssertEqual(options.task, .transcribe)
        XCTAssertFalse(options.detectLanguage)
        XCTAssertFalse(options.withoutTimestamps)
        XCTAssertTrue(options.skipSpecialTokens)
        XCTAssertEqual(options.concurrentWorkerCount, 1)
        XCTAssertFalse(WhisperSpeechService.modelName.contains(".en"))
    }

    func testAllChunksAndTailKeepOriginalTimestamps() {
        let results = [
            TranscriptionResult(text: "Начало.", segments: [.init(start: 0, end: 2, text: "Начало.")], language: "ru", timings: .init()),
            TranscriptionResult(text: "Конец длинной записи.", segments: [.init(start: 3600, end: 3605, text: "Конец длинной записи.")], language: "ru", timings: .init())
        ]
        let output = WhisperSpeechService.output(results, duration: 3610)
        XCTAssertEqual(output.text, "Начало. Конец длинной записи.")
        XCTAssertEqual(output.pieces.count, 2)
        XCTAssertEqual(output.pieces.last?.start, 3600)
        XCTAssertEqual(output.pieces.last?.end, 3605)
        XCTAssertTrue(output.engine.contains("Whisper"))
    }

    func testInvalidTimestampsDoNotLoseTranscriptText() {
        let segments: [TranscriptionSegment] = [
            .init(start: .nan, end: 1, text: "Слово с неверным временем."),
            .init(start: -1, end: 12, text: "Допустимый фрагмент."),
            .init(start: 2, end: 1, text: "Неверный интервал.")
        ]
        let text = "Полный текст не зависит от качества таймкодов."
        let output = WhisperSpeechService.output([.init(text: text, segments: segments, language: "ru", timings: .init())], duration: 10)
        XCTAssertEqual(output.text, text)
        XCTAssertEqual(output.pieces.count, 1)
        XCTAssertEqual(output.pieces.first?.start, 0)
        XCTAssertEqual(output.pieces.first?.end, 10)
    }

    func testIncompleteDownloadIsNotInstalledAndDeletionKeepsAudio() async throws {
        let root = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString)
        defer { try? FileManager.default.removeItem(at: root) }
        let models = root.appendingPathComponent("Models")
        try FileManager.default.createDirectory(at: models, withIntermediateDirectories: true)
        let audio = root.appendingPathComponent("original.caf")
        try Data([1, 2, 3]).write(to: audio)
        try Data([4, 5]).write(to: models.appendingPathComponent("partial-download"))
        let service = WhisperSpeechService(base: models)
        let installed = await service.isInstalled()
        XCTAssertFalse(installed)
        try await service.remove()
        XCTAssertFalse(FileManager.default.fileExists(atPath: models.path))
        XCTAssertEqual(try Data(contentsOf: audio), Data([1, 2, 3]))
    }

    func testNoDownloadWithoutConsentAndAudioStaysIntact() async throws {
        let root = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString)
        defer { try? FileManager.default.removeItem(at: root) }
        try FileManager.default.createDirectory(at: root, withIntermediateDirectories: true)
        let audio = root.appendingPathComponent("original.caf")
        let format = try XCTUnwrap(AVAudioFormat(standardFormatWithSampleRate: 16000, channels: 1))
        let buffer = try XCTUnwrap(AVAudioPCMBuffer(pcmFormat: format, frameCapacity: 1600))
        buffer.frameLength = 1600
        for index in 0..<1600 { buffer.floatChannelData![0][index] = 0 }
        do {
            let file = try AVAudioFile(forWriting: audio, settings: format.settings)
            try file.write(from: buffer)
        }
        let original = try Data(contentsOf: audio)
        let models = root.appendingPathComponent("Models")
        let service = WhisperSpeechService(base: models)
        do {
            _ = try await service.transcribe(url: audio, localeID: "ru-RU", allowDownload: false)
            XCTFail("Без согласия загрузка не должна начаться")
        } catch {
            XCTAssertTrue(error.localizedDescription.contains("нужна модель Whisper"))
        }
        XCTAssertFalse(FileManager.default.fileExists(atPath: models.path))
        XCTAssertEqual(try Data(contentsOf: audio), original)
    }
}
