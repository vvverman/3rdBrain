import Foundation
import WhisperKit

// Адаптер публичного протокола WhisperKit. BPE выполняет TokenizerWrapper SDK;
// здесь нет собственного токенизатора, загрузчика или сетевого fallback.
final class LocalWhisperTokenizer: WhisperTokenizer {
    private let tokenizer: TokenizerWrapper
    let specialTokens: SpecialTokens
    let allLanguageTokens: Set<Int>

    init(_ tokenizer: TokenizerWrapper) throws {
        func token(_ text: String) throws -> Int {
            guard let id = tokenizer.convertTokenToId(text) else {
                throw BrainError("Повреждён токенизатор Whisper: отсутствует \(text). Удалите модель и загрузите снова.")
            }
            return id
        }
        let end = try token("<|endoftext|>")
        specialTokens = try SpecialTokens(
            endToken: end, englishToken: token("<|en|>"), noSpeechToken: token("<|nospeech|>"),
            noTimestampsToken: token("<|notimestamps|>"), specialTokenBegin: end,
            startOfPreviousToken: token("<|startofprev|>"), startOfTranscriptToken: token("<|startoftranscript|>"),
            timeTokenBegin: token("<|0.00|>"), transcribeToken: token("<|transcribe|>"),
            translateToken: token("<|translate|>"), whitespaceToken: 220
        )
        guard try token("<|ru|>") > end, end == 50257 else {
            throw BrainError("Токенизатор не соответствует многоязычному Whisper small.")
        }
        self.tokenizer = tokenizer
        allLanguageTokens = Set(Constants.languageCodes.compactMap { tokenizer.convertTokenToId("<|\($0)|>") }.filter { $0 > end })
    }

    func encode(text: String) -> [Int] { tokenizer.encode(text: text) }
    func decode(tokens: [Int]) -> String { tokenizer.decode(tokens: tokens) }
    func convertTokenToId(_ token: String) -> Int? { tokenizer.convertTokenToId(token) }
    func convertIdToToken(_ id: Int) -> String? { tokenizer.convertIdToToken(id) }

    // Русский профиль разделяет слова по пробелам. Сначала собираем целые
    // Unicode-фрагменты: кириллический символ может занимать несколько BPE-токенов.
    // В текущем профиле используются таймкоды сегментов, не отдельных слов.
    func splitToWordTokens(tokenIds: [Int]) -> (words: [String], wordTokens: [[Int]]) {
        var words: [String] = []
        var groups: [[Int]] = []
        var pending: [Int] = []
        func append(_ ids: [Int]) {
            guard !ids.isEmpty else { return }
            let text = decode(tokens: ids)
            let trimmed = text.trimmingCharacters(in: .whitespacesAndNewlines)
            let punctuation = !trimmed.isEmpty && trimmed.unicodeScalars.allSatisfy { CharacterSet.punctuationCharacters.contains($0) }
            let special = ids[0] >= specialTokens.specialTokenBegin
            let previousSpecial = groups.last?.first.map { $0 >= specialTokens.specialTokenBegin } ?? false
            if words.isEmpty || special || previousSpecial || text.first?.isWhitespace == true || punctuation {
                words.append(text); groups.append(ids)
            } else {
                words[words.count - 1] += text; groups[groups.count - 1].append(contentsOf: ids)
            }
        }
        for id in tokenIds {
            if id >= specialTokens.specialTokenBegin {
                append(pending); pending = []; append([id])
            } else {
                pending.append(id)
                if !decode(tokens: pending).contains("\u{fffd}") { append(pending); pending = [] }
            }
        }
        append(pending)
        return (words, groups)
    }
}
