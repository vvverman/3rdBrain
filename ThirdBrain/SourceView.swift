import SwiftUI

struct SourceView: View {
    @Bindable var app: AppController
    let capture: Capture
    @State private var compact = false
    private var path: String { compact ? (capture.compactPath ?? capture.originalPath) : capture.originalPath }
    private var duration: Double { compact && capture.compactPath != nil ? capture.compactDuration : capture.duration }
    private var isCurrent: Bool { app.player.path == path }
    var body: some View {
        Group {
            Section("Аудиоисточник") {
                if capture.compactPath != nil {
                    Picker("Версия", selection: $compact) {
                        Text("Оригинал").tag(false); Text("Без длинных пауз").tag(true)
                    }.pickerStyle(.segmented)
                }
                HStack {
                    Button {
                        app.recorder.pause()
                        if isCurrent && app.player.isPlaying { app.player.pause() }
                        else if isCurrent {
                            if app.player.position >= duration - 0.2 { app.player.seek(to: 0) }
                            app.attempt { try app.player.resume() }
                        } else { app.play(capture, compact: compact) }
                    } label: {
                        Image(systemName: isCurrent && app.player.isPlaying ? "pause.fill" : "play.fill")
                            .frame(minWidth: 44, minHeight: 44)
                    }.buttonStyle(.glass).accessibilityLabel(isCurrent && app.player.isPlaying ? "Пауза воспроизведения" : "Слушать источник")
                    Text(clock(isCurrent ? app.player.position : 0) + " / " + clock(duration)).monospacedDigit().font(.subheadline)
                    Spacer()
                    Menu {
                        Picker("Скорость", selection: Binding(get: { app.player.rate }, set: { app.player.rate = $0 })) {
                            Text("1×").tag(Float(1)); Text("1,25×").tag(Float(1.25))
                            Text("1,5×").tag(Float(1.5)); Text("2×").tag(Float(2))
                        }
                    } label: { Text(String(format: "%g×", app.player.rate)).monospacedDigit() }
                }
                if isCurrent, let message = app.player.errorMessage {
                    Label(message, systemImage: "exclamationmark.triangle").font(.footnote)
                }
                Slider(value: Binding(get: { isCurrent ? min(duration, max(0, app.player.position)) : 0 }, set: { app.player.seek(to: $0) }), in: 0...max(0.1, duration))
                    .disabled(!isCurrent).accessibilityLabel("Позиция воспроизведения")
                if let url = try? LocalFiles.url(path), FileManager.default.fileExists(atPath: url.path) {
                    ShareLink(item: url) { Label("Поделиться аудио", systemImage: "square.and.arrow.up") }
                }
                Text("Скорость меняется при воспроизведении, без изменения оригинала. Прослушивание ставит микрофон на паузу.").font(.caption).foregroundStyle(.secondary)
            }
            Section("Транскрипт и время") {
                DisclosureGroup("Полная транскрипция") {
                    Text(capture.transcript.isEmpty ? "Транскрипция ещё не получена." : capture.transcript).textSelection(.enabled)
                }
                if !capture.pieces.isEmpty {
                    DisclosureGroup("Фрагменты с таймкодами") {
                        ForEach(Array(capture.pieces.enumerated()), id: \.offset) { _, piece in
                            Button { app.play(capture, compact: compact, originalTime: piece.start) } label: {
                                HStack(alignment: .top) {
                                    Text(clock(piece.start)).font(.caption.monospacedDigit()).foregroundStyle(.secondary)
                                    Text(piece.text).foregroundStyle(.primary).multilineTextAlignment(.leading)
                                }.padding(.vertical, 3)
                            }
                        }
                    }
                }
                if !capture.speechEngine.isEmpty { Text("Распознано: " + capture.speechEngine).font(.caption).foregroundStyle(.secondary) }
            }
        }
        .onAppear { compact = capture.compactPath != nil }
        .onChange(of: compact) { _, _ in app.player.stop() }
    }
}
