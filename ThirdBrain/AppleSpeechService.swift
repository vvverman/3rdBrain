import AVFoundation
import Foundation
import Speech

struct SpeechOutput: Sendable {
    let text: String
    let pieces: [TranscriptPiece]
    let engine: String
}

actor AppleSpeechService {
    private var busy = false
    private let downloadMessage = "Нужна системная языковая модель Apple. Загрузите её в настройках приложения или нажмите «Загрузить модель и повторить». Аудио уже сохранено."

    func transcribe(url: URL, localeID: String, allowDownload: Bool) async throws -> SpeechOutput {
        guard !busy else { throw BrainError("Модель речи Apple сейчас занята. Дождитесь подготовки и повторите обработку; аудио сохранено.") }
        busy = true
        defer { busy = false }
        let choices = await candidates(localeID: localeID)
        guard !choices.isEmpty else { throw BrainError("Apple не предоставляет локальное распознавание языка записи на этом устройстве. Аудио сохранено; другие модели и облако не используются.") }
        let permitted = choices.filter { $0.isInstalled || allowDownload }
        guard !permitted.isEmpty else { throw BrainError(downloadMessage) }
        var failures: [String] = []
        for candidate in permitted {
            try Task.checkCancellation()
            do {
                let result = try await run(url: url, candidate: candidate, allowDownload: allowDownload)
                try Task.checkCancellation()
                guard !result.text.isEmpty else { throw BrainError("Речь не распознана.") }
                return result
            } catch is CancellationError { throw CancellationError() }
            catch {
                try Task.checkCancellation()
                failures.append(candidate.engine.rawValue + ": " + error.localizedDescription)
            }
        }
        if !allowDownload && choices.contains(where: { !$0.isInstalled }) { failures.append(downloadMessage) }
        throw BrainError("Не удалось распознать запись системными моделями Apple. Источник сохранён.\n\n" + failures.joined(separator: "\n\n"))
    }

    func install(localeID: String) async throws {
        guard !busy else { throw BrainError("Подготовка или распознавание уже выполняется.") }
        busy = true
        defer { busy = false }
        let choices = await candidates(localeID: localeID)
        guard !choices.isEmpty else { throw BrainError("Для этого языка и устройства нет доступной локальной модели речи Apple.") }
        if choices.contains(where: \.isInstalled) { return }
        var failures: [String] = []
        for candidate in choices {
            try Task.checkCancellation()
            do {
                let locale = Locale(identifier: candidate.localeID)
                switch candidate.engine {
                case .speechTranscriber:
                    let module = SpeechTranscriber(locale: locale, transcriptionOptions: [], reportingOptions: [], attributeOptions: [.audioTimeRange])
                    try await prepare(module, candidate: candidate, allowDownload: true)
                case .dictationTranscriber:
                    let module = DictationTranscriber(locale: locale, contentHints: [], transcriptionOptions: [], reportingOptions: [], attributeOptions: [.audioTimeRange])
                    try await prepare(module, candidate: candidate, allowDownload: true)
                }
                return
            } catch is CancellationError { throw CancellationError() }
            catch {
                try Task.checkCancellation()
                failures.append(candidate.engine.rawValue + ": " + error.localizedDescription)
            }
        }
        throw BrainError(failures.joined(separator: "\n\n"))
    }

    private func candidates(localeID: String) async -> [AppleSpeechCandidate] {
        let available = SpeechTranscriber.isAvailable
        let supported = available ? await SpeechTranscriber.supportedLocales : []
        let installed = available ? await SpeechTranscriber.installedLocales : []
        let dictationSupported = await DictationTranscriber.supportedLocales
        let dictationInstalled = await DictationTranscriber.installedLocales
        return AppleSpeechSelection.candidates(localeID: localeID, speechAvailable: available,
            speechSupported: supported, speechInstalled: installed,
            dictationSupported: dictationSupported, dictationInstalled: dictationInstalled)
    }

    private func prepare(_ module: any SpeechModule, candidate: AppleSpeechCandidate, allowDownload: Bool) async throws {
        try Task.checkCancellation()
        guard !candidate.isInstalled else { return }
        guard allowDownload else { throw BrainError(downloadMessage) }
        if let request = try await AssetInventory.assetInstallationRequest(supporting: [module]) {
            try await request.downloadAndInstall()
        }
        try Task.checkCancellation()
        let installed: [Locale]
        switch candidate.engine {
        case .speechTranscriber: installed = await SpeechTranscriber.installedLocales
        case .dictationTranscriber: installed = await DictationTranscriber.installedLocales
        }
        guard installed.contains(where: { $0.identifier(.bcp47) == candidate.localeID }) else {
            throw BrainError("Системная модель ещё не установлена. Проверьте интернет и повторите её подготовку.")
        }
    }

    private func run(url: URL, candidate: AppleSpeechCandidate, allowDownload: Bool) async throws -> SpeechOutput {
        let locale = Locale(identifier: candidate.localeID)
        switch candidate.engine {
        case .speechTranscriber:
            let module = SpeechTranscriber(locale: locale, transcriptionOptions: [], reportingOptions: [], attributeOptions: [.audioTimeRange])
            try await prepare(module, candidate: candidate, allowDownload: allowDownload)
            let analyzer = SpeechAnalyzer(modules: [module])
            let reader = Task {
                var text = ""; var pieces: [TranscriptPiece] = []
                for try await result in module.results {
                    try Task.checkCancellation()
                    text += String(result.text.characters)
                    pieces += Self.pieces(result.text)
                }
                return SpeechOutput(text: text.trimmingCharacters(in: .whitespacesAndNewlines), pieces: pieces, engine: candidate.engine.rawValue)
            }
            return try await finish(url: url, analyzer: analyzer, reader: reader)
        case .dictationTranscriber:
            let module = DictationTranscriber(locale: locale, contentHints: [], transcriptionOptions: [], reportingOptions: [], attributeOptions: [.audioTimeRange])
            try await prepare(module, candidate: candidate, allowDownload: allowDownload)
            let analyzer = SpeechAnalyzer(modules: [module])
            let reader = Task {
                var text = ""; var pieces: [TranscriptPiece] = []
                for try await result in module.results {
                    try Task.checkCancellation()
                    text += String(result.text.characters)
                    pieces += Self.pieces(result.text)
                }
                return SpeechOutput(text: text.trimmingCharacters(in: .whitespacesAndNewlines), pieces: pieces, engine: candidate.engine.rawValue)
            }
            return try await finish(url: url, analyzer: analyzer, reader: reader)
        }
    }

    private func finish(url: URL, analyzer: SpeechAnalyzer, reader: Task<SpeechOutput, Error>) async throws -> SpeechOutput {
        // Отмена охватывает не только чтение аудио, но и ожидание последних результатов.
        try await withTaskCancellationHandler {
            do {
                try Task.checkCancellation()
                let file = try AVAudioFile(forReading: url)
                if let last = try await analyzer.analyzeSequence(from: file) {
                    try await analyzer.finalizeAndFinish(through: last)
                } else { await analyzer.cancelAndFinishNow() }
                let output = try await reader.value
                try Task.checkCancellation()
                return output
            } catch {
                reader.cancel()
                await analyzer.cancelAndFinishNow()
                throw error
            }
        } onCancel: {
            reader.cancel()
            Task { await analyzer.cancelAndFinishNow() }
        }
    }

    private static func pieces(_ text: AttributedString) -> [TranscriptPiece] {
        text.runs.compactMap { run in
            guard let range = run.audioTimeRange else { return nil }
            let start = range.start.seconds, end = CMTimeRangeGetEnd(range).seconds
            guard start.isFinite, end.isFinite, end >= start else { return nil }
            return TranscriptPiece(start: start, end: end, text: String(text[run.range].characters))
        }
    }

    func diagnostic(localeID: String) async -> String {
        let choices = await candidates(localeID: localeID)
        return [AppleSpeechEngine.speechTranscriber, .dictationTranscriber].map { engine in
            guard let item = choices.first(where: { $0.engine == engine }) else {
                return engine.rawValue + ": локальный язык или модуль недоступен на этом устройстве."
            }
            return engine.rawValue + " (" + item.localeID + "): " + (item.isInstalled
                ? "язык поддерживается, ресурсы установлены."
                : "язык поддерживается, требуется загрузка ресурсов Apple.")
        }.joined(separator: "\n\n")
    }
}
