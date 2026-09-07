import AVFoundation
import Foundation
import Speech

struct SpeechOutput: Sendable {
    let text: String
    let pieces: [TranscriptPiece]
    let engine: String
}

actor AppleSpeechService {
    func transcribe(url: URL, localeID: String, allowDownload: Bool) async throws -> SpeechOutput {
        let requested = Locale(identifier: localeID)
        if SpeechTranscriber.isAvailable,
           let locale = Self.match(await SpeechTranscriber.supportedLocales, requested) {
            let module = SpeechTranscriber(locale: locale, transcriptionOptions: [], reportingOptions: [], attributeOptions: [.audioTimeRange])
            let installed = Self.match(await SpeechTranscriber.installedLocales, locale) != nil
            try await prepare(module, installed: installed, allowDownload: allowDownload)
            let analyzer = SpeechAnalyzer(modules: [module])
            let reader = Task {
                var text = ""; var pieces: [TranscriptPiece] = []
                for try await result in module.results {
                    text += String(result.text.characters)
                    pieces += Self.pieces(result.text)
                }
                return SpeechOutput(text: text.trimmingCharacters(in: .whitespacesAndNewlines), pieces: pieces, engine: "SpeechTranscriber")
            }
            do { try await analyze(url, with: analyzer); return try await reader.value }
            catch { await analyzer.cancelAndFinishNow(); reader.cancel(); throw error }
        }
        if let locale = Self.match(await DictationTranscriber.supportedLocales, requested) {
            let module = DictationTranscriber(locale: locale, contentHints: [], transcriptionOptions: [], reportingOptions: [], attributeOptions: [.audioTimeRange])
            let installed = Self.match(await DictationTranscriber.installedLocales, locale) != nil
            try await prepare(module, installed: installed, allowDownload: allowDownload)
            let analyzer = SpeechAnalyzer(modules: [module])
            let reader = Task {
                var text = ""; var pieces: [TranscriptPiece] = []
                for try await result in module.results {
                    text += String(result.text.characters)
                    pieces += Self.pieces(result.text)
                }
                return SpeechOutput(text: text.trimmingCharacters(in: .whitespacesAndNewlines), pieces: pieces, engine: "DictationTranscriber")
            }
            do { try await analyze(url, with: analyzer); return try await reader.value }
            catch { await analyzer.cancelAndFinishNow(); reader.cancel(); throw error }
        }
        throw BrainError("Системное локальное распознавание этого языка недоступно на устройстве. Аудио сохранено. Облачное распознавание не используется.")
    }
    private func prepare(_ module: any SpeechModule, installed: Bool, allowDownload: Bool) async throws {
        guard !installed else { return }
        guard allowDownload else {
            throw BrainError("Нужна первоначальная загрузка системной языковой модели. Нажмите «Загрузить модель и повторить». После загрузки распознавание выполняется на iPhone.")
        }
        if let request = try await AssetInventory.assetInstallationRequest(supporting: [module]) {
            try await request.downloadAndInstall()
        }
    }
    private func analyze(_ url: URL, with analyzer: SpeechAnalyzer) async throws {
        let file = try AVAudioFile(forReading: url)
        try await withTaskCancellationHandler {
            if let last = try await analyzer.analyzeSequence(from: file) {
                try await analyzer.finalizeAndFinish(through: last)
            } else { await analyzer.cancelAndFinishNow() }
        } onCancel: { Task { await analyzer.cancelAndFinishNow() } }
    }
    private static func match(_ locales: [Locale], _ requested: Locale) -> Locale? {
        locales.first { $0.identifier(.bcp47) == requested.identifier(.bcp47) }
            ?? locales.first { $0.language.languageCode == requested.language.languageCode }
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
        let locale = Locale(identifier: localeID)
        if SpeechTranscriber.isAvailable, Self.match(await SpeechTranscriber.supportedLocales, locale) != nil {
            return "SpeechTranscriber: язык поддерживается. Наличие ресурсов проверяется перед обработкой."
        }
        if Self.match(await DictationTranscriber.supportedLocales, locale) != nil {
            return "DictationTranscriber: язык поддерживается. Наличие ресурсов проверяется перед обработкой."
        }
        return "Системное локальное распознавание этого языка недоступно."
    }
}
