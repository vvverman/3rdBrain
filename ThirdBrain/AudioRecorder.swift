import AVFoundation
import Foundation
import Observation

@MainActor @Observable final class AudioRecorder: NSObject, AVAudioRecorderDelegate {
    private(set) var isRecording = false
    private(set) var isPaused = false
    private(set) var elapsed = 0.0
    private(set) var level = 0.0
    private(set) var interruptionMessage = ""
    var hasSession: Bool { recorder != nil }
    @ObservationIgnored private var recorder: AVAudioRecorder?
    @ObservationIgnored private var meterTask: Task<Void, Never>?
    @ObservationIgnored private var observers: [NSObjectProtocol] = []
    @ObservationIgnored var onCheckpoint: ((Double, Bool) -> Void)?
    @ObservationIgnored var onUnexpectedStop: ((String) -> Void)?
    @ObservationIgnored private var lastCheckpoint = -1
    override init() {
        super.init()
        let center = NotificationCenter.default
        observers.append(center.addObserver(forName: AVAudioSession.interruptionNotification, object: nil, queue: .main) { [weak self] notification in
            let value = notification.userInfo?[AVAudioSessionInterruptionTypeKey] as? UInt
            guard value == AVAudioSession.InterruptionType.began.rawValue else { return }
            Task { @MainActor in self?.interrupt("Запись приостановлена системой. Продолжите вручную.") }
        })
        observers.append(center.addObserver(forName: AVAudioSession.routeChangeNotification, object: nil, queue: .main) { [weak self] notification in
            let value = notification.userInfo?[AVAudioSessionRouteChangeReasonKey] as? UInt
            guard value == AVAudioSession.RouteChangeReason.oldDeviceUnavailable.rawValue else { return }
            Task { @MainActor in self?.interrupt("Аудиоустройство отключено. Проверьте микрофон и продолжите запись.") }
        })
        observers.append(center.addObserver(forName: AVAudioSession.mediaServicesWereResetNotification, object: nil, queue: .main) { [weak self] _ in
            Task { @MainActor in
                guard let self, self.hasSession else { return }
                self.onUnexpectedStop?("Аудиосистема перезапущена. Уже записанный источник сохранён.")
            }
        })
    }
    deinit {
        meterTask?.cancel()
        for observer in observers { NotificationCenter.default.removeObserver(observer) }
    }
    static func requestPermission() async -> Bool { await AVAudioApplication.requestRecordPermission() }
    private func activateSession() throws {
        let session = AVAudioSession.sharedInstance()
        try session.setCategory(.playAndRecord, mode: .default, options: [.defaultToSpeaker])
        try session.setActive(true)
    }
    func start(at url: URL) throws {
        guard recorder == nil else { return }
        try activateSession()
        // CAF/PCM позволяет восстанавливать источник после незапланированного прерывания.
        let settings: [String: Any] = [AVFormatIDKey: kAudioFormatLinearPCM,
            AVSampleRateKey: 16000.0, AVNumberOfChannelsKey: 1,
            AVLinearPCMBitDepthKey: 16, AVLinearPCMIsFloatKey: false,
            AVLinearPCMIsBigEndianKey: false]
        let newRecorder = try AVAudioRecorder(url: url, settings: settings)
        newRecorder.delegate = self; newRecorder.isMeteringEnabled = true
        guard newRecorder.prepareToRecord(), newRecorder.record() else { throw BrainError("Не удалось включить микрофон. Проверьте свободное место и доступ к записи.") }
        recorder = newRecorder; isRecording = true; isPaused = false
        elapsed = 0; level = 0; lastCheckpoint = -1; interruptionMessage = ""
        meterTask = Task { [weak self] in
            while !Task.isCancelled {
                self?.tick()
                do { try await Task.sleep(for: .milliseconds(200)) } catch { break }
            }
        }
    }
    func pause() {
        guard let recorder, isRecording else { return }
        elapsed = recorder.currentTime; recorder.pause()
        isRecording = false; isPaused = true; level = 0
        onCheckpoint?(elapsed, true)
    }
    func resume() throws {
        guard let recorder, isPaused else { return }
        try activateSession()
        guard recorder.record() else { throw BrainError("Не удалось продолжить запись.") }
        isPaused = false; isRecording = true; interruptionMessage = ""
        onCheckpoint?(elapsed, false)
    }
    @discardableResult func finish() -> Double {
        let active = recorder
        elapsed = active?.currentTime ?? elapsed
        recorder = nil; active?.stop(); meterTask?.cancel(); meterTask = nil
        isRecording = false; isPaused = false; level = 0
        try? AVAudioSession.sharedInstance().setActive(false, options: .notifyOthersOnDeactivation)
        return elapsed
    }
    private func interrupt(_ message: String) {
        guard hasSession else { return }
        pause(); interruptionMessage = message
    }
    private func tick() {
        guard let recorder else { return }
        if isRecording {
            elapsed = recorder.currentTime; recorder.updateMeters()
            level = min(1, max(0, (Double(recorder.averagePower(forChannel: 0)) + 55) / 55))
            let checkpoint = Int(elapsed) / 5
            if checkpoint != lastCheckpoint { lastCheckpoint = checkpoint; onCheckpoint?(elapsed, false) }
        }
    }
    nonisolated func audioRecorderDidFinishRecording(_ recorder: AVAudioRecorder, successfully flag: Bool) {
        Task { @MainActor [weak self] in
            guard let self, self.hasSession else { return }
            self.onUnexpectedStop?(flag ? "Запись остановлена системой." : "Запись прервана. Проверьте аудиоисточник.")
        }
    }
    nonisolated func audioRecorderEncodeErrorDidOccur(_ recorder: AVAudioRecorder, error: (any Error)?) {
        let message = error?.localizedDescription ?? "Ошибка записи аудио."
        Task { @MainActor [weak self] in self?.onUnexpectedStop?(message) }
    }
}
