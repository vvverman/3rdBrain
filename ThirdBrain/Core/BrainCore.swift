import Foundation

public struct TranscriptPiece: Codable, Equatable, Sendable {
    public var start: Double
    public var end: Double
    public var text: String
    public init(start: Double, end: Double, text: String) {
        self.start = start; self.end = end; self.text = text
    }
}

public struct AudioSpan: Codable, Equatable, Sendable {
    public var originalStart: Double
    public var duration: Double
    public var compactStart: Double
    public init(originalStart: Double, duration: Double, compactStart: Double) {
        self.originalStart = originalStart; self.duration = duration; self.compactStart = compactStart
    }
}

public enum AudioTimeline {
    // В удалённой паузе переходим к ближайшей сохранённой границе.
    public static func compactTime(for originalTime: Double, spans: [AudioSpan]) -> Double {
        let originalTime = originalTime.isFinite ? max(0, originalTime) : 0
        guard let last = spans.last else { return originalTime }
        for span in spans {
            if originalTime < span.originalStart { return span.compactStart }
            if originalTime <= span.originalStart + span.duration {
                return span.compactStart + max(0, originalTime - span.originalStart)
            }
        }
        return last.compactStart + last.duration
    }
    public static func originalTime(for compactTime: Double, spans: [AudioSpan]) -> Double {
        let compactTime = compactTime.isFinite ? max(0, compactTime) : 0
        guard let last = spans.last else { return compactTime }
        for span in spans where compactTime < span.compactStart + span.duration {
            return span.originalStart + max(0, compactTime - span.compactStart)
        }
        return last.originalStart + last.duration
    }
}

public enum SilencePlanner {
    // Консервативное сокращение тихих пауз. Это не распознавание речи.
    // Если сигнал не найден, сохраняем всё; оригинал всегда остаётся неизменным.
    public static func spans(levels: [Double], frameDuration: Double, duration: Double,
                             threshold: Double = -48, padding: Double = 0.25,
                             minimumGap: Double = 0.9) -> [AudioSpan] {
        guard duration.isFinite, duration > 0 else { return [] }
        let full = [AudioSpan(originalStart: 0, duration: duration, compactStart: 0)]
        guard frameDuration.isFinite, frameDuration > 0, padding.isFinite, padding >= 0,
              minimumGap.isFinite, minimumGap >= 0, threshold.isFinite, !levels.isEmpty else { return full }
        var ranges: [(Double, Double)] = []
        for (index, level) in levels.enumerated() where level.isFinite && level > threshold {
            let start = max(0, Double(index) * frameDuration - padding)
            let end = min(duration, Double(index + 1) * frameDuration + padding)
            guard end > start else { continue }
            if let previous = ranges.last, start - previous.1 < minimumGap {
                ranges[ranges.count - 1].1 = max(previous.1, end)
            } else { ranges.append((start, end)) }
        }
        guard !ranges.isEmpty else { return full }
        var cursor = 0.0
        return ranges.map { start, end in
            let span = AudioSpan(originalStart: start, duration: end - start, compactStart: cursor)
            cursor += span.duration
            return span
        }
    }
}

public enum TextChunks {
    // Деление по UTF-8 бюджету без потери символов или хвоста записи.
    public static func split(_ text: String, maxBytes: Int = 2400) -> [String] {
        precondition(maxBytes > 0)
        guard !text.isEmpty else { return [] }
        var chunks: [String] = []
        var start = text.startIndex
        while start < text.endIndex {
            var end = start
            var boundary: String.Index?
            var bytes = 0
            while end < text.endIndex {
                let next = text.index(after: end)
                let count = text[end..<next].utf8.count
                if bytes + count > maxBytes && end > start { break }
                bytes += count
                if text[end].isWhitespace { boundary = next }
                end = next
            }
            if end < text.endIndex, let boundary, boundary > start { end = boundary }
            chunks.append(String(text[start..<end])); start = end
        }
        return chunks
    }
}

public struct ProjectCandidate: Sendable, Equatable, Identifiable {
    public let id: UUID
    public let title: String
    public let details: String
    public let instruction: String
    public let pinned: Bool
    public let pinOrder: Int
    public init(id: UUID, title: String, details: String = "", instruction: String = "", pinned: Bool = false, pinOrder: Int = 0) {
        self.id = id; self.title = title; self.details = details; self.instruction = instruction
        self.pinned = pinned; self.pinOrder = pinOrder
    }
}

public enum ProjectOrder {
    public static func sorted(_ projects: [ProjectCandidate], scores: [UUID: Int] = [:]) -> [ProjectCandidate] {
        projects.sorted { left, right in
            if left.pinned != right.pinned { return left.pinned }
            if left.pinned && left.pinOrder != right.pinOrder { return left.pinOrder < right.pinOrder }
            if !left.pinned && scores[left.id, default: 0] != scores[right.id, default: 0] {
                return scores[left.id, default: 0] > scores[right.id, default: 0]
            }
            let comparison = left.title.localizedStandardCompare(right.title)
            return comparison == .orderedSame ? left.id.uuidString < right.id.uuidString : comparison == .orderedAscending
        }
    }
}

public enum NoteText {
    public static func appending(_ addition: String, to existing: String) -> String {
        guard !addition.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else { return existing }
        return existing.isEmpty ? addition : existing + "\n\n" + addition
    }
    public static func title(from text: String) -> String {
        let text = text.trimmingCharacters(in: .whitespacesAndNewlines)
        let line = text.split(whereSeparator: \.isNewline).first.map(String.init) ?? "Новая заметка"
        return String(line.prefix(70))
    }
}

public enum AudioClock {
    public static func format(_ value: Double) -> String {
        guard value.isFinite, value >= 0, value < Double(Int.max) else { return "00:00" }
        let seconds = Int(value)
        // Интерполяция не обрезает 64-битный Int до 32-битного формата C.
        func two(_ value: Int) -> String { value < 10 ? "0\(value)" : String(value) }
        return seconds >= 3600 ? "\(seconds / 3600):\(two((seconds / 60) % 60)):\(two(seconds % 60))"
            : "\(two(seconds / 60)):\(two(seconds % 60))"
    }
}
