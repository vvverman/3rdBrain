import Foundation

public enum AppleSpeechEngine: String, Sendable {
    case speechTranscriber = "SpeechTranscriber"
    case dictationTranscriber = "DictationTranscriber"
}

public struct AppleSpeechCandidate: Equatable, Sendable {
    public let engine: AppleSpeechEngine
    public let localeID: String
    public let isInstalled: Bool
}

public enum AppleSpeechSelection {
    public static func match(_ locales: [Locale], localeID: String) -> Locale? {
        let requested = Locale(identifier: localeID)
        if let exact = locales.first(where: { $0.identifier(.bcp47) == requested.identifier(.bcp47) }) {
            return exact
        }
        guard let language = requested.language.languageCode else { return nil }
        return locales.first { $0.language.languageCode == language }
    }

    public static func candidates(
        localeID: String,
        speechAvailable: Bool,
        speechSupported: [Locale],
        speechInstalled: [Locale],
        dictationSupported: [Locale],
        dictationInstalled: [Locale]
    ) -> [AppleSpeechCandidate] {
        var result: [AppleSpeechCandidate] = []
        func append(_ engine: AppleSpeechEngine, supported: [Locale], installed: [Locale]) {
            guard let locale = match(supported, localeID: localeID) else { return }
            let installedExactly = installed.contains { $0.identifier(.bcp47) == locale.identifier(.bcp47) }
            result.append(AppleSpeechCandidate(engine: engine, localeID: locale.identifier(.bcp47), isInstalled: installedExactly))
        }
        if speechAvailable { append(.speechTranscriber, supported: speechSupported, installed: speechInstalled) }
        append(.dictationTranscriber, supported: dictationSupported, installed: dictationInstalled)
        // Сначала уже установленные ресурсы; при равных условиях — новый SpeechTranscriber.
        return result.filter(\.isInstalled) + result.filter { !$0.isInstalled }
    }
}
