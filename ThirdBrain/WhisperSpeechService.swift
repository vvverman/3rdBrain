import AVFoundation
import Foundation
import WhisperKit

actor WhisperSpeechService {
    // Именно многоязычная small, не small.en. Версия SDK закреплена в Xcode-проекте.
    static let modelName = "openai_whisper-small"
    private let baseOverride: URL?
    private var busy = false

    init(base: URL? = nil) { baseOverride = base }

    private func base() throws -> URL {
        if let baseOverride { return baseOverride }
        return try LocalFiles.root().appendingPathComponent("Models/Whisper", isDirectory: true)
    }

    private func folders() throws -> (base: URL, model: URL, tokenizer: URL, receipt: URL) {
        let base = try base()
        let hub = HubApiWrapper(downloadBase: base)
        return (base,
                hub.localRepoLocation(.init(id: "argmaxinc/whisperkit-coreml")).appendingPathComponent(Self.modelName),
                hub.localRepoLocation(.init(id: "openai/whisper-small")),
                base.appendingPathComponent("installed-small-v1"))
    }

    func isInstalled() -> Bool {
        guard let paths = try? folders(),
              let receipt = try? String(contentsOf: paths.receipt, encoding: .utf8), receipt == Self.modelName else { return false }
        let modelFiles = ["MelSpectrogram.mlmodelc", "AudioEncoder.mlmodelc", "TextDecoder.mlmodelc"]
        let tokenFiles = ["tokenizer.json", "tokenizer_config.json"]
        return modelFiles.allSatisfy { FileManager.default.fileExists(atPath: paths.model.appendingPathComponent($0).path) }
            && tokenFiles.allSatisfy { FileManager.default.fileExists(atPath: paths.tokenizer.appendingPathComponent($0).path) }
    }

    func diagnostic() -> String {
        "Whisper small: русский поддерживается. " + (isInstalled() ? "Модель установлена; распознавание локальное." : "Нужна первоначальная загрузка модели в настройках.")
    }

    func install(progress: @escaping @Sendable (Double) -> Void = { _ in }) async throws {
        guard !busy else { throw BrainError("Whisper занят обработкой или установкой. Повторите после завершения.") }
        busy = true; defer { busy = false }
        let pipe = try await load(allowDownload: true, progress: progress)
        await pipe.unloadModels()
        try Task.checkCancellation()
        progress(1)
    }

    func remove() throws {
        guard !busy else { throw BrainError("Нельзя удалить модель во время обработки или установки.") }
        let location = try base()
        if FileManager.default.fileExists(atPath: location.path) { try FileManager.default.removeItem(at: location) }
    }

    func transcribe(url: URL, localeID: String, allowDownload: Bool) async throws -> SpeechOutput {
        guard SpeechRouting.languageCode(for: localeID) == "ru" else {
            throw BrainError("Этот профиль Whisper настроен для русского языка.")
        }
        guard !busy else { throw BrainError("Whisper занят. Источник сохранён; повторите обработку после установки модели.") }
        busy = true; defer { busy = false }
        try Task.checkCancellation()
        // Проверяем источник до возможной загрузки модели.
        let file = try AVAudioFile(forReading: url)
        let duration = Double(file.length) / file.processingFormat.sampleRate
        guard duration.isFinite, duration > 0 else { throw BrainError("Аудиоисточник пуст или повреждён.") }
        let pipe = try await load(allowDownload: allowDownload)
        do {
            let results = try await pipe.transcribe(
                audioPath: url.path,
                audioInputOptions: AudioInputOptions(audioLoadingMode: .incremental),
                decodeOptions: Self.decodingOptions()
            )
            try Task.checkCancellation()
            let output = Self.output(results, duration: duration)
            await pipe.unloadModels()
            return output
        } catch {
            await pipe.unloadModels()
            throw error
        }
    }

    static func decodingOptions() -> DecodingOptions {
        DecodingOptions(task: .transcribe, language: "ru", usePrefillPrompt: true,
                        detectLanguage: false, skipSpecialTokens: true, withoutTimestamps: false,
                        wordTimestamps: false, suppressBlank: true, concurrentWorkerCount: 1, chunkingStrategy: .vad)
    }

    static func output(_ results: [TranscriptionResult], duration: Double) -> SpeechOutput {
        let text = results.map(\.text).joined(separator: " ").trimmingCharacters(in: .whitespacesAndNewlines)
        let pieces = results.flatMap(\.segments).compactMap { segment -> TranscriptPiece? in
            let start = Double(segment.start), end = Double(segment.end)
            let text = segment.text.trimmingCharacters(in: .whitespacesAndNewlines)
            guard start.isFinite, end.isFinite, end > start, start < duration, end > 0, !text.isEmpty else { return nil }
            return TranscriptPiece(start: max(0, start), end: min(duration, end), text: text)
        }
        return SpeechOutput(text: text, pieces: pieces, engine: "Whisper small · русский · локально")
    }

    // Только эта ветка имеет право обращаться к сети — после отдельного согласия.
    private func load(allowDownload: Bool, progress: @escaping @Sendable (Double) -> Void = { _ in }) async throws -> WhisperKit {
        try Task.checkCancellation()
        let paths = try folders()
        if !isInstalled() {
            guard allowDownload else {
                throw BrainError("Для локального распознавания нужна модель Whisper small. Откройте настройки → «Загрузить Whisper» или нажмите «Загрузить модель и повторить». Аудио сохранено и никуда не отправляется.")
            }
            try FileManager.default.createDirectory(at: paths.base, withIntermediateDirectories: true)
            var resourceValues = URLResourceValues(); resourceValues.isExcludedFromBackup = true
            var location = paths.base; try location.setResourceValues(resourceValues)
            _ = try await WhisperKit.download(variant: Self.modelName, downloadBase: paths.base, useBackgroundSession: false) {
                progress(min(0.9, max(0, $0.fractionCompleted) * 0.9))
            }
            try Task.checkCancellation()
            let hub = HubApiWrapper(downloadBase: paths.base, useBackgroundSession: false)
            _ = try await hub.snapshot(from: .init(id: "openai/whisper-small"), matching: [
                "config.json", "tokenizer.json", "tokenizer_config.json", "special_tokens_map.json", "added_tokens.json", "vocab.json", "merges.txt"
            ]) { progress(0.9 + min(1, max(0, $0.fractionCompleted)) * 0.05) }
            try Task.checkCancellation()
        }
        // Внедряем токенизатор, загруженный строго с диска: SDK иначе может сам
        // обратиться в Hub при повреждённом или отсутствующем кэше токенизатора.
        let tokenizer = try await AutoTokenizerWrapper.from(modelFolder: paths.tokenizer)
        let pipe = try await WhisperKit(WhisperKitConfig(modelFolder: paths.model.path,
            tokenizerFolder: paths.tokenizer, verbose: false, prewarm: false, load: false, download: false))
        pipe.tokenizer = try LocalWhisperTokenizer(tokenizer)
        pipe.textDecoder.isModelMultilingual = true
        do {
            try Task.checkCancellation()
            try await pipe.prewarmModels()
            try Task.checkCancellation()
            try await pipe.loadModels()
            guard pipe.textDecoder.logitsSize == 51865, pipe.audioEncoder.embedSize == 768 else {
                throw BrainError("Загружена не многоязычная модель Whisper small. Удалите модель в настройках и загрузите её снова.")
            }
            try Task.checkCancellation()
            // Частичная/отменённая загрузка не считается установленной моделью.
            try Self.modelName.write(to: paths.receipt, atomically: true, encoding: .utf8)
            return pipe
        } catch {
            await pipe.unloadModels()
            throw error
        }
    }
}
