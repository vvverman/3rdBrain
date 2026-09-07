import Foundation
import Testing
@testable import BrainCore

@Test func unicodeChunksAreLossless() {
    let source = String(repeating: "Мысль: не менять 52 пикселя. 👨‍👩‍👧\n", count: 400) + "ПОСЛЕДНИЙ ФРАГМЕНТ"
    let chunks = TextChunks.split(source, maxBytes: 180)
    #expect(chunks.joined() == source)
    #expect(chunks.allSatisfy { $0.utf8.count <= 180 })
}
@Test func longWordIsNotLost() {
    let text = String(repeating: "ю", count: 10001)
    #expect(TextChunks.split(text).joined() == text)
}
@Test func emptyChunks() { #expect(TextChunks.split("").isEmpty) }
@Test func shortChunks() { #expect(TextChunks.split("абв") == ["абв"]) }
@Test func silenceAloneIsNeverDiscarded() {
    #expect(SilencePlanner.spans(levels: Array(repeating: -90, count: 100), frameDuration: 0.1, duration: 10)
        == [AudioSpan(originalStart: 0, duration: 10, compactStart: 0)])
}
@Test func longPauseIsShortened() {
    let levels = Array(repeating: -15.0, count: 10) + Array(repeating: -80.0, count: 50) + Array(repeating: -15.0, count: 10)
    let spans = SilencePlanner.spans(levels: levels, frameDuration: 0.1, duration: 7)
    #expect(spans.count == 2)
    #expect(spans.reduce(0) { $0 + $1.duration } < 3)
    #expect(spans.last?.originalStart == 5.75)
}
@Test func normalPausesRemain() {
    let levels = [-10.0, -10, -90, -90, -10, -10]
    #expect(SilencePlanner.spans(levels: levels, frameDuration: 0.1, duration: 0.6).count == 1)
}
@Test func emptyAudioHasNoSpans() {
    #expect(SilencePlanner.spans(levels: [], frameDuration: 0.1, duration: 0).isEmpty)
}
@Test func timelineMapping() {
    let spans = [AudioSpan(originalStart: 0, duration: 2, compactStart: 0), AudioSpan(originalStart: 10, duration: 3, compactStart: 2)]
    #expect(AudioTimeline.compactTime(for: 11, spans: spans) == 3)
    #expect(AudioTimeline.originalTime(for: 3, spans: spans) == 11)
    #expect(AudioTimeline.compactTime(for: 5, spans: spans) == 2)
    #expect(AudioTimeline.compactTime(for: 99, spans: spans) == 5)
}
@Test func pinsOverrideScores() {
    let pinned = ProjectCandidate(id: UUID(), title: "Закреплённый", pinned: true)
    let probable = ProjectCandidate(id: UUID(), title: "Подходящий")
    #expect(ProjectOrder.sorted([probable, pinned], scores: [probable.id: 4]).first?.id == pinned.id)
}
@Test func pinOrderIsManual() {
    let a = ProjectCandidate(id: UUID(), title: "А", pinned: true, pinOrder: 2)
    let b = ProjectCandidate(id: UUID(), title: "Б", pinned: true, pinOrder: 1)
    #expect(ProjectOrder.sorted([a, b]).map(\.id) == [b.id, a.id])
}
@Test func allProjectsRemainVisible() {
    let projects = (0..<20).map { ProjectCandidate(id: UUID(), title: "Проект \($0)") }
    let result = ProjectOrder.sorted(projects, scores: [projects[17].id: 4])
    #expect(result.count == 20)
    #expect(Set(result.map(\.id)) == Set(projects.map(\.id)))
    #expect(result.first?.id == projects[17].id)
}
@Test func appendPreservesOldTextExactly() {
    let old = "Старый текст.\n  Не менять пробелы!  "
    #expect(NoteText.appending("Новый", to: old) == old + "\n\nНовый")
}
@Test func emptyAppendDoesNotChangeNote() { #expect(NoteText.appending(" \n", to: "Текст") == "Текст") }
