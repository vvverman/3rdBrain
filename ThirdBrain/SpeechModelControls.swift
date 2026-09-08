import SwiftUI

private enum SpeechDownloadNotice {
    static let message = "Будут загружены системные языковые ресурсы Apple для локального распознавания русской речи. Требуется интернет; рекомендуется Wi-Fi. Аудиозаписи не отправляются на сервер распознавания. Это не загрузка текстовой модели Apple Intelligence."
}

struct SpeechModelDownloadButton: View {
    let action: () -> Void
    @State private var confirming = false
    var body: some View {
        Button("Загрузить модель и повторить", systemImage: "arrow.down.circle") { confirming = true }
            .confirmationDialog("Подготовить модель речи Apple?", isPresented: $confirming, titleVisibility: .visible) {
                Button("Загрузить и повторить", action: action)
                Button("Отмена", role: .cancel) {}
            } message: { Text(SpeechDownloadNotice.message) }
    }
}

struct SpeechModelControls: View {
    @Bindable var app: AppController
    @Environment(\.scenePhase) private var scenePhase
    @State private var downloadTask: Task<Void, Never>?
    @State private var confirming = false
    @State private var message = ""

    var body: some View {
        Section("Распознавание речи Apple") {
            Text(app.speechStatus)
            Text("Используем установленный SpeechTranscriber или DictationTranscriber. Если один модуль не распознал запись, пробуем другой доступный модуль Apple. На сторонние модели и облако не переключаемся.")
                .font(.footnote).foregroundStyle(.secondary)
            if downloadTask != nil {
                ProgressView("Подготовка системной модели…")
                Button("Отменить подготовку", role: .cancel) { downloadTask?.cancel() }
            } else {
                Button("Подготовить русское распознавание", systemImage: "arrow.down.circle") { confirming = true }
                    .disabled(app.processingID != nil)
            }
            if !message.isEmpty { Text(message).font(.footnote).textSelection(.enabled) }
        }
        .confirmationDialog("Подготовить модель речи Apple?", isPresented: $confirming, titleVisibility: .visible) {
            Button("Подготовить") { install() }
            Button("Отмена", role: .cancel) {}
        } message: { Text(SpeechDownloadNotice.message) }
        .onChange(of: scenePhase) { _, phase in if phase == .background { downloadTask?.cancel() } }
        .onDisappear { downloadTask?.cancel() }
    }

    private func install() {
        guard downloadTask == nil, app.processingID == nil else { return }
        message = ""
        downloadTask = Task { @MainActor in
            defer { downloadTask = nil }
            do {
                try await app.speech.install(localeID: "ru-RU")
                try Task.checkCancellation()
                message = "Системные ресурсы готовы. Теперь запишите короткую фразу и проверьте транскрипт."
            } catch is CancellationError { message = "Подготовка отменена. Её можно повторить; записи не удалены." }
            catch { message = error.localizedDescription }
            await app.refreshCapabilities()
        }
    }
}
