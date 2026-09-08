import Foundation

public enum SpeechBackend: String, CaseIterable, Sendable {
    case automatic
    case whisper
    public var label: String {
        switch self {
        case .automatic: return "Автоматически"
        case .whisper: return "Whisper"
        }
    }
}

public enum SpeechRouting {
    // Отмена — не отказ движка: после неё не запускаем второй распознаватель.
    public static func run<T: Sendable>(
        backend: SpeechBackend, appleReady: Bool,
        apple: @Sendable () async throws -> T,
        whisper: @Sendable () async throws -> T
    ) async throws -> T {
        try Task.checkCancellation()
        if backend == .automatic && appleReady {
            do {
                let result = try await apple()
                try Task.checkCancellation()
                return result
            }
            catch is CancellationError { throw CancellationError() }
            catch { try Task.checkCancellation() }
        }
        try Task.checkCancellation()
        let result = try await whisper()
        try Task.checkCancellation()
        return result
    }

    public static func languageCode(for localeID: String) -> String? {
        Locale(identifier: localeID).language.languageCode?.identifier
    }
    public static func supports(_ locales: [Locale], localeID: String) -> Bool {
        guard let code = languageCode(for: localeID), !code.isEmpty else { return false }
        return locales.contains { $0.language.languageCode?.identifier == code }
    }
}
