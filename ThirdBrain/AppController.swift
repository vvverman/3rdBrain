import AVFoundation
import Foundation
import Observation
import SwiftUI

@MainActor @Observable final class AppController {
    let store: LocalStore
    let recorder = AudioRecorder()
    let player = SourcePlayer()
    let speech = AppleSpeechService()
    let intelligence = LocalIntelligence()
    let compactor = AudioCompactor()
    var activeCaptureID: UUID?
    var routingCapture: Capture?
    var errorMessage: String?
    var onboarding = false
    var isStarting = false
    var processingID: UUID?
    var speechStatus = "Проверка…"
    var modelStatus = "Проверка…"
    @ObservationIgnored var worker: Task<Void, Never>?
    @ObservationIgnored var queue: [(UUID, Bool)] = []
    @ObservationIgnored var didLaunch = false
    @ObservationIgnored var wasBackground = false
    var activeCapture: Capture? { store.captures.first { $0.id == activeCaptureID } }
    var inbox: [Capture] { store.captures.filter { $0.noteID == nil && $0.id != activeCaptureID } }
    var testing: Bool { ProcessInfo.processInfo.environment["XCTestConfigurationFilePath"] != nil }

    init() throws {
        store = try LocalStore()
        recorder.onCheckpoint = { [weak self] time, paused in
            guard let self, let capture = self.activeCapture else { return }
            capture.duration = time; capture.phase = paused ? .paused : .recording
            self.attempt { try self.store.save() }
        }
        recorder.onUnexpectedStop = { [weak self] message in self?.finishRecording(warning: message) }
    }
    @discardableResult func attempt(_ action: () throws -> Void) -> Bool {
        do { try action(); return true } catch { errorMessage = error.localizedDescription; return false }
    }
    func launch() async {
        guard !didLaunch, !testing else { return }
        didLaunch = true
        attempt {
            for capture in store.captures where capture.phase.isWorking || [.recording, .paused].contains(capture.phase) {
                capture.phase = .failed
                capture.message = "Работа была прервана. Источник сохранён; можно повторить обработку."
                if let file = try? AVAudioFile(forReading: LocalFiles.url(capture.originalPath)) {
                    capture.duration = Double(file.length) / file.processingFormat.sampleRate
                }
            }
            try store.save()
        }
        if UserDefaults.standard.bool(forKey: "recordingConsent") { await startRecording() }
        else { onboarding = true }
        await refreshCapabilities()
    }
    func consentAndStart() async {
        UserDefaults.standard.set(true, forKey: "recordingConsent")
        onboarding = false
        await startRecording()
    }
    func enteredBackground() {
        wasBackground = true
        if let capture = activeCapture { capture.duration = recorder.elapsed; attempt { try store.save() } }
        // Не маскируем вычисления фоновой «тихой» аудиосессией.
        worker?.cancel()
    }
    func enteredForeground() async {
        guard wasBackground, didLaunch, !testing else { return }
        wasBackground = false
        if UserDefaults.standard.bool(forKey: "recordingConsent"), !recorder.hasSession { await startRecording() }
    }
    func startRecording() async {
        guard !recorder.hasSession, !isStarting, !testing else { return }
        guard UserDefaults.standard.bool(forKey: "recordingConsent") else { onboarding = true; return }
        isStarting = true; defer { isStarting = false }
        guard await AudioRecorder.requestPermission() else {
            errorMessage = "Разрешите микрофон в системных настройках 3rdBrain. Просмотр заметок доступен и без микрофона."
            return
        }
        player.stop()
        do {
            let id = UUID(); let path = try LocalFiles.createRecording(id: id)
            let capture = Capture(id: id, originalPath: path)
            capture.title = "Запись " + Date.now.formatted(date: .abbreviated, time: .shortened)
            try store.insert(capture); activeCaptureID = id
            do { try recorder.start(at: LocalFiles.url(path)) }
            catch { capture.phase = .failed; capture.message = error.localizedDescription; activeCaptureID = nil; try store.save(); throw error }
        } catch { errorMessage = error.localizedDescription }
    }
    func pauseOrResume() {
        if recorder.isRecording { recorder.pause() }
        else { player.stop(); attempt { try recorder.resume() } }
    }
    func finishRecording(warning: String = "") {
        guard let capture = activeCapture else { return }
        capture.duration = recorder.finish(); activeCaptureID = nil
        capture.phase = .queued; capture.message = warning
        guard attempt({ try store.save() }) else { return }
        routingCapture = capture
        enqueue(capture)
    }
    func play(_ capture: Capture, compact: Bool, originalTime: Double = 0) {
        recorder.pause()
        let path = compact ? (capture.compactPath ?? capture.originalPath) : capture.originalPath
        let time = compact && capture.compactPath != nil ? AudioTimeline.compactTime(for: originalTime, spans: capture.spans) : originalTime
        attempt { try player.play(path: path, from: time) }
    }
    func refreshCapabilities() async {
        speechStatus = await speech.diagnostic(localeID: "ru-RU")
        modelStatus = await intelligence.unavailableReason(localeID: "ru-RU") ?? "Локальная LLM доступна для русского языка."
    }
}
