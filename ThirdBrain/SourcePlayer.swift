import AVFoundation
import Foundation
import Observation

@MainActor @Observable final class SourcePlayer {
    private(set) var path: String?
    private(set) var isPlaying = false
    private(set) var position = 0.0
    var rate: Float = 1 { didSet { if isPlaying { player?.rate = rate } } }
    @ObservationIgnored private var player: AVPlayer?
    @ObservationIgnored private var timeObserver: Any?
    func play(path: String, from seconds: Double = 0) throws {
        stop()
        let session = AVAudioSession.sharedInstance()
        try session.setCategory(.playAndRecord, mode: .default, options: [.defaultToSpeaker])
        try session.setActive(true)
        let url = try LocalFiles.url(path)
        guard FileManager.default.fileExists(atPath: url.path) else { throw BrainError("Аудиофайл не найден.") }
        let item = AVPlayerItem(url: url); item.audioTimePitchAlgorithm = .spectral
        let player = AVPlayer(playerItem: item)
        self.player = player; self.path = path
        player.seek(to: CMTime(seconds: max(0, seconds), preferredTimescale: 600), toleranceBefore: .zero, toleranceAfter: .zero)
        player.playImmediately(atRate: rate); isPlaying = true
        timeObserver = player.addPeriodicTimeObserver(forInterval: CMTime(seconds: 0.2, preferredTimescale: 600), queue: .main) { [weak self] time in
            Task { @MainActor in
                guard let self else { return }
                self.position = time.seconds.isFinite ? time.seconds : 0
                self.isPlaying = (self.player?.rate ?? 0) != 0
            }
        }
    }
    func pause() { player?.pause(); isPlaying = false }
    func resume() { player?.playImmediately(atRate: rate); isPlaying = player != nil }
    func seek(to seconds: Double) {
        player?.seek(to: CMTime(seconds: max(0, seconds), preferredTimescale: 600), toleranceBefore: .zero, toleranceAfter: .zero)
    }
    func stop() {
        player?.pause()
        if let timeObserver { player?.removeTimeObserver(timeObserver) }
        timeObserver = nil; player = nil; path = nil; isPlaying = false; position = 0
    }
}
