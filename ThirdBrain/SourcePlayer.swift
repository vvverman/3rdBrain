import AVFoundation
import Foundation
import Observation

@MainActor @Observable final class SourcePlayer {
    private(set) var path: String?
    private(set) var isPlaying = false
    private(set) var position = 0.0
    private(set) var errorMessage: String?
    var rate: Float = 1 {
        didSet {
            if !rate.isFinite || rate < 1 || rate > 2 { rate = 1 }
            if isPlaying { player?.rate = rate }
        }
    }
    @ObservationIgnored private var player: AVPlayer?
    @ObservationIgnored private var timeObserver: Any?
    @ObservationIgnored private var playbackObserver: NSKeyValueObservation?
    @ObservationIgnored private var statusObserver: NSKeyValueObservation?

    private func activateSession() throws {
        let session = AVAudioSession.sharedInstance()
        try session.setCategory(.playAndRecord, mode: .default, options: [.defaultToSpeaker])
        try session.setActive(true)
    }

    func play(path: String, from seconds: Double = 0) throws {
        let url = try LocalFiles.url(path)
        guard FileManager.default.fileExists(atPath: url.path) else { throw BrainError("Аудиофайл не найден.") }
        stop()
        try activateSession()
        let item = AVPlayerItem(url: url)
        item.audioTimePitchAlgorithm = .spectral
        let player = AVPlayer(playerItem: item)
        self.player = player; self.path = path
        // Отложенные события предыдущего источника не должны менять новый проигрыватель.
        timeObserver = player.addPeriodicTimeObserver(forInterval: CMTime(seconds: 0.2, preferredTimescale: 600), queue: .main) { [weak self, weak player] time in
            Task { @MainActor in
                guard let self, let player, self.player === player else { return }
                self.position = time.seconds.isFinite ? max(0, time.seconds) : 0
            }
        }
        playbackObserver = player.observe(\.timeControlStatus, options: [.initial, .new]) { [weak self] observed, _ in
            Task { @MainActor in
                guard let self, self.player === observed else { return }
                self.isPlaying = observed.timeControlStatus == .playing
            }
        }
        statusObserver = item.observe(\.status, options: [.initial, .new]) { [weak self] observed, _ in
            Task { @MainActor in
                guard let self, self.player?.currentItem === observed, observed.status == .failed else { return }
                self.errorMessage = observed.error?.localizedDescription ?? "Не удалось прочитать аудиоисточник."
                self.pause()
            }
        }
        seek(to: seconds)
        player.playImmediately(atRate: rate)
    }
    func pause() { player?.pause(); isPlaying = false }
    func resume() throws {
        guard let player else { return }
        // Завершение записи или системное прерывание могли деактивировать общую аудиосессию.
        try activateSession()
        player.playImmediately(atRate: rate)
    }
    func seek(to seconds: Double) {
        guard let player, seconds.isFinite else { return }
        let duration = player.currentItem?.duration.seconds ?? .nan
        let target = duration.isFinite ? min(max(0, seconds), max(0, duration)) : max(0, seconds)
        position = target
        player.seek(to: CMTime(seconds: target, preferredTimescale: 600), toleranceBefore: .zero, toleranceAfter: .zero)
    }
    func stop() {
        playbackObserver?.invalidate(); playbackObserver = nil
        statusObserver?.invalidate(); statusObserver = nil
        player?.pause()
        if let timeObserver { player?.removeTimeObserver(timeObserver) }
        timeObserver = nil; player = nil; path = nil; isPlaying = false; position = 0; errorMessage = nil
    }
    deinit {
        playbackObserver?.invalidate(); statusObserver?.invalidate()
        if let timeObserver { player?.removeTimeObserver(timeObserver) }
        player?.pause()
    }
}
