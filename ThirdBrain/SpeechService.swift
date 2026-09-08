import Foundation
import Speech

actor SpeechService {
    private let apple = AppleSpeechService()
    private let whisper = WhisperSpeechService()

    private var backend: SpeechBackend {
        SpeechBackend(rawValue: UserDefaults.standard.string(forKey: "speechBackend") ?? "") ?? .automatic
    }

    func transcribe(url: URL, localeID: String, allowDownload: Bool) async throws -> SpeechOutput {
        let selected = backend
        let ready = selected == .automatic ? await appleReady(localeID: localeID) : false
        let apple = self.apple, whisper = self.whisper
        return try await SpeechRouting.run(backend: selected, appleReady: ready) {
            // Используем только уже установленную локальную модель Apple.
            let result = try await apple.transcribe(url: url, localeID: localeID, allowDownload: false)
            guard !result.text.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else {
                throw BrainError("Apple не вернула распознанный текст.")
            }
            return result
        } whisper: {
            try await whisper.transcribe(url: url, localeID: localeID, allowDownload: allowDownload)
        }
    }

    func installWhisper(progress: @escaping @Sendable (Double) -> Void) async throws {
        try await whisper.install(progress: progress)
    }

    func removeWhisper() async throws { try await whisper.remove() }

    private func appleReady(localeID: String) async -> Bool {
        if SpeechTranscriber.isAvailable,
           SpeechRouting.supports(await SpeechTranscriber.supportedLocales, localeID: localeID),
           SpeechRouting.supports(await SpeechTranscriber.installedLocales, localeID: localeID) {
            return true
        }
        let supported = SpeechRouting.supports(await DictationTranscriber.supportedLocales, localeID: localeID)
        let installed = SpeechRouting.supports(await DictationTranscriber.installedLocales, localeID: localeID)
        return supported && installed
    }

    func diagnostic(localeID: String) async -> String {
        let transcriberSupported = SpeechTranscriber.isAvailable
            ? SpeechRouting.supports(await SpeechTranscriber.supportedLocales, localeID: localeID) : false
        let transcriberInstalled = SpeechRouting.supports(await SpeechTranscriber.installedLocales, localeID: localeID)
        let dictationSupported = SpeechRouting.supports(await DictationTranscriber.supportedLocales, localeID: localeID)
        let dictationInstalled = SpeechRouting.supports(await DictationTranscriber.installedLocales, localeID: localeID)
        func status(_ supported: Bool, _ installed: Bool) -> String {
            supported ? (installed ? "русский поддерживается, модель установлена" : "русский поддерживается, модель не установлена")
                : "русский недоступен на этом устройстве"
        }
        let selected = backend == .whisper ? "Выбран Whisper." : "Автовыбор: установленная модель Apple; при недоступности или ошибке — Whisper."
        let whisperStatus = await whisper.diagnostic()
        return [selected, "SpeechTranscriber: " + status(transcriberSupported, transcriberInstalled),
                "DictationTranscriber: " + status(dictationSupported, dictationInstalled), whisperStatus].joined(separator: "\n\n")
    }
}
