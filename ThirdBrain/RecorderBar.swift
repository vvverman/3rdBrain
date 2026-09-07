import SwiftUI

struct RecorderBar: View {
    @Bindable var app: AppController
    @Environment(\.dynamicTypeSize) private var typeSize
    var body: some View {
        HStack(spacing: 12) {
            Image(systemName: app.recorder.isRecording ? "waveform" : "mic")
                .foregroundStyle(app.recorder.isRecording ? Color.red : Color.secondary)
                .font(.title3).accessibilityHidden(true)
            VStack(alignment: .leading, spacing: 2) {
                Text(app.isStarting ? "Запуск…" : app.recorder.isRecording ? "Запись" : app.recorder.isPaused ? "Пауза" : "Микрофон выключен")
                    .font(.caption).foregroundStyle(.secondary)
                if app.recorder.hasSession { Text(clock(app.recorder.elapsed)).font(.body.monospacedDigit()) }
            }
            Spacer(minLength: 0)
            if app.recorder.hasSession {
                if !typeSize.isAccessibilitySize {
                    ProgressView(value: app.recorder.level).frame(width: 30).tint(.red).accessibilityHidden(true)
                }
                Button { app.pauseOrResume() } label: {
                    Image(systemName: app.recorder.isRecording ? "pause.fill" : "play.fill").frame(minWidth: 44, minHeight: 44)
                }.accessibilityLabel(app.recorder.isRecording ? "Приостановить запись" : "Продолжить запись")
                Button { app.finishRecording() } label: {
                    Image(systemName: "stop.fill").frame(minWidth: 44, minHeight: 44)
                }.tint(.red).accessibilityLabel("Завершить и сохранить запись")
            } else {
                Button("Записать", systemImage: "mic.fill") { Task { await app.startRecording() } }
                    .disabled(app.isStarting).frame(minHeight: 44)
            }
        }
        .buttonStyle(.borderless).padding(.horizontal, 6).padding(.vertical, 4)
        .sensoryFeedback(.start, trigger: app.recorder.isRecording)
        .accessibilityElement(children: .contain)
    }
}
