import Foundation

extension AppController {
    func enqueue(_ capture: Capture, allowDownload: Bool = false) {
        guard capture.id != activeCaptureID, capture.noteID == nil,
              processingID != capture.id, !queue.contains(where: { $0.0 == capture.id }) else { return }
        capture.phase = .queued
        guard attempt({ try store.save() }) else { return }
        queue.append((capture.id, allowDownload))
        guard worker == nil else { return }
        worker = Task { [weak self] in
            guard let self else { return }
            while !self.queue.isEmpty && !Task.isCancelled {
                let (id, download) = self.queue.removeFirst()
                guard let record = self.store.captures.first(where: { $0.id == id }) else { continue }
                self.processingID = id
                await self.process(record, allowDownload: download)
            }
            if Task.isCancelled {
                for (id, _) in self.queue {
                    if let record = self.store.captures.first(where: { $0.id == id }) {
                        record.phase = .failed; record.message = "Обработка отложена. Источник сохранён; повторите при открытом приложении."
                    }
                }
                self.queue.removeAll(); self.attempt { try self.store.save() }
            }
            self.processingID = nil; self.worker = nil
        }
    }
    private func process(_ capture: Capture, allowDownload: Bool) async {
        var warnings: [String] = []
        capture.message = ""
        do {
            try Task.checkCancellation()
            let source = try LocalFiles.url(capture.originalPath)
            if capture.compactPath == nil {
                capture.phase = .compacting; try store.save()
                do {
                    let relative = "Audio/\(capture.id.uuidString)/compact.m4a"
                    let result = try await compactor.compact(source: source, destination: LocalFiles.url(relative))
                    capture.compactPath = relative; capture.compactDuration = result.duration
                    capture.timelineData = try JSONEncoder().encode(result.spans)
                    try store.save()
                } catch is CancellationError { throw CancellationError() }
                catch { warnings.append("Компактная версия не создана: " + error.localizedDescription) }
            }
            try Task.checkCancellation()
            if capture.transcript.isEmpty {
                capture.phase = .transcribing; try store.save()
                let result = try await speech.transcribe(url: source, localeID: capture.localeID, allowDownload: allowDownload)
                capture.transcript = result.text; capture.transcriptData = try JSONEncoder().encode(result.pieces)
                capture.speechEngine = result.engine
                if !capture.draftEdited {
                    capture.preparedText = result.text; capture.title = NoteText.title(from: result.text)
                }
                try store.save()
            }
            guard !capture.transcript.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else {
                throw BrainError("Речь не распознана. Можно прослушать источник, ввести текст вручную или повторить обработку.")
            }
            try Task.checkCancellation()
            if !capture.draftEdited && !capture.llmApplied {
                capture.phase = .polishing; try store.save()
                do {
                    let result = try await intelligence.clean(capture.transcript, localeID: capture.localeID)
                    capture.preparedText = result.body; capture.title = result.title; capture.llmApplied = true
                } catch is CancellationError { throw CancellationError() }
                catch { warnings.append(error.localizedDescription) }
            }
            capture.phase = .ready; capture.message = warnings.joined(separator: "\n\n")
            try store.save()
        } catch {
            capture.phase = .failed
            capture.message = error is CancellationError
                ? "Обработка приостановлена. Источник и готовые этапы сохранены; нажмите повторить."
                : (warnings + [error.localizedDescription]).joined(separator: "\n\n")
            attempt { try store.save() }
        }
    }
}
