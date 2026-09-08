import Foundation
import Testing
@testable import BrainCore

private enum TestFailure: Error { case native; case whisper }
private actor Calls {
    var values: [String] = []
    func add(_ value: String) { values.append(value) }
}

@Test func nativeReadyAvoidsWhisper() async throws {
    let calls = Calls()
    let result = try await SpeechRouting.run(backend: .automatic, appleReady: true) {
        await calls.add("apple"); return "русский текст"
    } whisper: { await calls.add("whisper"); return "резерв" }
    #expect(result == "русский текст")
    #expect(await calls.values == ["apple"])
}

@Test func missingNativeUsesWhisper() async throws {
    let calls = Calls()
    let result = try await SpeechRouting.run(backend: .automatic, appleReady: false) {
        await calls.add("apple"); return "не должен вызываться"
    } whisper: { await calls.add("whisper"); return "Whisper" }
    #expect(result == "Whisper")
    #expect(await calls.values == ["whisper"])
}

@Test func forcedWhisperSkipsAvailableNative() async throws {
    let calls = Calls()
    let result = try await SpeechRouting.run(backend: .whisper, appleReady: true) {
        await calls.add("apple"); return "Apple"
    } whisper: { await calls.add("whisper"); return "Whisper" }
    #expect(result == "Whisper")
    #expect(await calls.values == ["whisper"])
}

@Test func nativeFailureFallsBackOnce() async throws {
    let calls = Calls()
    let result: String = try await SpeechRouting.run(backend: .automatic, appleReady: true) {
        await calls.add("apple"); throw TestFailure.native
    } whisper: { await calls.add("whisper"); return "Весь текст, включая конец." }
    #expect(result == "Весь текст, включая конец.")
    #expect(await calls.values == ["apple", "whisper"])
}

@Test func nativeCancellationNeverStartsWhisper() async {
    let calls = Calls()
    do {
        let _: String = try await SpeechRouting.run(backend: .automatic, appleReady: true) {
            await calls.add("apple"); throw CancellationError()
        } whisper: { await calls.add("whisper"); return "не должен вызываться" }
        Issue.record("Ожидалась отмена")
    } catch is CancellationError {} catch { Issue.record("Получена не отмена: \(error)") }
    #expect(await calls.values == ["apple"])
}

@Test func alreadyCancelledTaskStartsNoEngine() async {
    let calls = Calls()
    let task = Task {
        withUnsafeCurrentTask { $0?.cancel() }
        let _: String = try await SpeechRouting.run(backend: .automatic, appleReady: true) {
            await calls.add("apple"); return "Apple"
        } whisper: { await calls.add("whisper"); return "Whisper" }
    }
    do { try await task.value; Issue.record("Ожидалась отмена") }
    catch is CancellationError {} catch { Issue.record("Получена не отмена: \(error)") }
    #expect(await calls.values.isEmpty)
}

@Test func cancelledNativeResultIsNotAccepted() async {
    let calls = Calls()
    let task = Task {
        let _: String = try await SpeechRouting.run(backend: .automatic, appleReady: true) {
            await calls.add("apple")
            withUnsafeCurrentTask { $0?.cancel() }
            return "Частичный результат после отмены"
        } whisper: { await calls.add("whisper"); return "Whisper" }
    }
    do { try await task.value; Issue.record("Ожидалась отмена") }
    catch is CancellationError {} catch { Issue.record("Получена не отмена: \(error)") }
    #expect(await calls.values == ["apple"])
}

@Test func whisperFailureRemainsAnError() async {
    do {
        let _: String = try await SpeechRouting.run(backend: .automatic, appleReady: true) {
            throw TestFailure.native
        } whisper: { throw TestFailure.whisper }
        Issue.record("Нельзя выдавать ошибку за готовый текст")
    } catch TestFailure.whisper {} catch { Issue.record("Потеряна ошибка Whisper: \(error)") }
}

@Test func russianLocaleIdentifiersMatch() {
    #expect(SpeechRouting.languageCode(for: "ru-RU") == "ru")
    #expect(SpeechRouting.languageCode(for: "ru_RU") == "ru")
    #expect(SpeechRouting.supports([Locale(identifier: "ru_RU")], localeID: "ru-RU"))
    #expect(!SpeechRouting.supports([Locale(identifier: "en-US")], localeID: "ru-RU"))
    #expect(!SpeechRouting.supports([], localeID: "ru-RU"))
}
