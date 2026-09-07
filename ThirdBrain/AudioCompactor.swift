import AVFoundation
import Foundation

struct CompactAudio: Sendable { let spans: [AudioSpan]; let duration: Double }

actor AudioCompactor {
    func compact(source: URL, destination: URL) async throws -> CompactAudio {
        let file = try AVAudioFile(forReading: source, commonFormat: .pcmFormatFloat32, interleaved: false)
        let rate = file.processingFormat.sampleRate
        let duration = Double(file.length) / rate
        guard duration > 0 else { throw BrainError("В аудиофайле нет записанного звука.") }
        let frames = AVAudioFrameCount(max(1, Int(rate * 0.1)))
        guard let buffer = AVAudioPCMBuffer(pcmFormat: file.processingFormat, frameCapacity: frames) else {
            throw BrainError("Не удалось выделить буфер аудио.")
        }
        var levels: [Double] = []
        while file.framePosition < file.length {
            try Task.checkCancellation()
            try file.read(into: buffer, frameCount: frames)
            guard buffer.frameLength > 0, let channels = buffer.floatChannelData else { break }
            let count = Int(buffer.frameLength)
            var maximum = 0.0
            for channel in 0..<Int(buffer.format.channelCount) {
                var squareSum = 0.0
                for index in 0..<count { let sample = Double(channels[channel][index]); squareSum += sample * sample }
                maximum = max(maximum, sqrt(squareSum / Double(count)))
            }
            levels.append(20 * log10(max(maximum, 0.000001)))
        }
        let spans = SilencePlanner.spans(levels: levels, frameDuration: Double(frames) / rate, duration: duration)
        let asset = AVURLAsset(url: source)
        guard let sourceTrack = try await asset.loadTracks(withMediaType: .audio).first else { throw BrainError("Аудиодорожка не найдена.") }
        let composition = AVMutableComposition()
        guard let targetTrack = composition.addMutableTrack(withMediaType: .audio, preferredTrackID: kCMPersistentTrackID_Invalid) else {
            throw BrainError("Не удалось создать компактную аудиодорожку.")
        }
        for span in spans {
            try Task.checkCancellation()
            let range = CMTimeRange(start: CMTime(seconds: span.originalStart, preferredTimescale: 16000),
                                    duration: CMTime(seconds: span.duration, preferredTimescale: 16000))
            try targetTrack.insertTimeRange(range, of: sourceTrack, at: CMTime(seconds: span.compactStart, preferredTimescale: 16000))
        }
        guard let export = AVAssetExportSession(asset: composition, presetName: AVAssetExportPresetAppleM4A) else {
            throw BrainError("Компактный экспорт недоступен.")
        }
        let temporary = destination.deletingLastPathComponent().appendingPathComponent("compact-\(UUID().uuidString).m4a")
        do {
            try await export.export(to: temporary, as: .m4a)
            if FileManager.default.fileExists(atPath: destination.path) { try FileManager.default.removeItem(at: destination) }
            try FileManager.default.moveItem(at: temporary, to: destination)
        } catch { try? FileManager.default.removeItem(at: temporary); throw error }
        return CompactAudio(spans: spans, duration: spans.reduce(0) { $0 + $1.duration })
    }
}
