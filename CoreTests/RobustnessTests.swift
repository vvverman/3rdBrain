import Foundation
import Testing
@testable import BrainCore

@Test func unsafeTimesDoNotCrashClock() {
    for value in [Double.nan, .infinity, -.infinity, .greatestFiniteMagnitude, -1, Double(Int.max)] {
        #expect(AudioClock.format(value) == "00:00")
    }
    #expect(AudioClock.format(59.9) == "00:59")
    #expect(AudioClock.format(60) == "01:00")
    #expect(AudioClock.format(3661) == "1:01:01")
    #expect(AudioClock.format(360000) == "100:00:00")
}

@Test func whitespaceTitleGetsUsefulDefault() {
    #expect(NoteText.title(from: " \n\t ") == "Новая заметка")
    #expect(NoteText.title(from: " \n Первая мысль\nвторая") == "Первая мысль")
}

@Test func nonfiniteTimelineInputIsSafe() {
    let spans = [AudioSpan(originalStart: 3, duration: 2, compactStart: 0)]
    for value in [Double.nan, .infinity, -.infinity, -5] {
        #expect(AudioTimeline.compactTime(for: value, spans: spans) == 0)
        #expect(AudioTimeline.originalTime(for: value, spans: spans) == 3)
        #expect(AudioTimeline.compactTime(for: value, spans: []) == 0)
    }
}

@Test func invalidSilenceParametersPreserveAudio() {
    let full = [AudioSpan(originalStart: 0, duration: 8, compactStart: 0)]
    for value in [Double.nan, .infinity, -.infinity, -1, 0] {
        #expect(SilencePlanner.spans(levels: [-10], frameDuration: value, duration: 8) == full)
    }
    #expect(SilencePlanner.spans(levels: [-10], frameDuration: 0.1, duration: .infinity).isEmpty)
    #expect(SilencePlanner.spans(levels: [-10], frameDuration: 0.1, duration: 8, padding: -1) == full)
    #expect(SilencePlanner.spans(levels: [.nan, -.infinity], frameDuration: 0.1, duration: 8) == full)
}

@Test func variedUnicodeChunksAreLossless() {
    let text = String(repeating: "Русский текст, отрицания: не 52, а 56. 👩🏽‍💻 café e\u{301}\n你好\t", count: 80)
    let largestCharacter = text.map { String($0).utf8.count }.max() ?? 0
    for budget in [1, 3, 17, 128, 2000, 2400] {
        let chunks = TextChunks.split(text, maxBytes: budget)
        #expect(chunks.joined() == text)
        #expect(chunks.allSatisfy { !$0.isEmpty && $0.utf8.count <= max(budget, largestCharacter) })
    }
}

@Test func generatedSilencePlansStayInsideSource() {
    // Детерминированные входы: одинаковый тест во всех окружениях.
    for seed in 0..<200 {
        let count = 1 + seed % 91
        let levels = (0..<count).map { index in Double((index * 37 + seed * 11) % 90 - 90) }
        let duration = Double(count) * 0.1 - 0.025
        let spans = SilencePlanner.spans(levels: levels, frameDuration: 0.1, duration: duration)
        var originalEnd = 0.0, compactEnd = 0.0
        for span in spans {
            #expect(span.duration > 0)
            #expect(span.originalStart >= originalEnd - 1e-8)
            #expect(span.originalStart + span.duration <= duration + 1e-8)
            #expect(abs(span.compactStart - compactEnd) < 1e-8)
            let middle = span.originalStart + span.duration / 2
            let mapped = AudioTimeline.compactTime(for: middle, spans: spans)
            #expect(abs(AudioTimeline.originalTime(for: mapped, spans: spans) - middle) < 1e-8)
            originalEnd = span.originalStart + span.duration
            compactEnd = span.compactStart + span.duration
        }
        #expect(compactEnd <= duration + 1e-8)
    }
}

@Test func tiedProjectScoresAreStableAndNeverHideProjects() {
    let ids = (1...40).map { UUID(uuidString: String(format: "00000000-0000-0000-0000-%012d", $0))! }
    let projects = ids.enumerated().map { index, id in
        ProjectCandidate(id: id, title: "Одинаковое", pinned: index % 3 == 0, pinOrder: index % 2)
    }
    let first = ProjectOrder.sorted(projects, scores: Dictionary(uniqueKeysWithValues: ids.map { ($0, 4) }))
    let second = ProjectOrder.sorted(projects.reversed(), scores: Dictionary(uniqueKeysWithValues: ids.map { ($0, 4) }))
    #expect(first == second)
    #expect(Set(first.map(\.id)) == Set(ids))
    #expect(first.prefix(projects.filter(\.pinned).count).allSatisfy { $0.pinned })
}
