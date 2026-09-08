import SwiftUI

private enum SpeechDownloadNotice {
    static let message = "Для Whisper будут загружены многоязычная модель small и токенизатор с Hugging Face — несколько сотен мегабайт. Рекомендуется Wi-Fi. После установки распознавание работает на iPhone без интернета. Аудио и тексты не отправляются. Не закрывайте приложение во время установки."
}

struct SpeechModelDownloadButton: View {
    let action: () -> Void
    @State private var confirming = false
    var body: some View {
        Button("Загрузить модель и повторить", systemImage: "arrow.down.circle") { confirming = true }
            .confirmationDialog("Разрешить загрузку модели?", isPresented: $confirming, titleVisibility: .visible) {
                Button("Загрузить и повторить", action: action)
                Button("Отмена", role: .cancel) {}
            } message: { Text(SpeechDownloadNotice.message) }
    }
}

struct SpeechModelControls: View {
    @Bindable var app: AppController
    @AppStorage("speechBackend") private var backend = SpeechBackend.automatic.rawValue
    @Environment(\.scenePhase) private var scenePhase
    @State private var downloadTask: Task<Void, Never>?
    @State private var removing = false
    @State private var progress = 0.0
    @State private var confirmingDownload = false
    @State private var confirmingRemoval = false
    private var busy: Bool { downloadTask != nil || removing || app.processingID != nil }

    var body: some View {
        Section("Распознавание речи") {
            Picker("Движок", selection: $backend) {
                ForEach(SpeechBackend.allCases, id: \.rawValue) { Text($0.label).tag($0.rawValue) }
            }.disabled(busy)
            Text("Автоматически: уже установленная локальная модель Apple; если её нет или она не смогла распознать запись — Whisper. Выбор Whisper принудительно использует его для новых расшифровок.")
                .font(.footnote).foregroundStyle(.secondary)
            if downloadTask != nil {
                ProgressView(value: progress)
                Text(progress < 0.95 ? "Загрузка Whisper: \(Int(progress * 100))%" : "Подготовка модели для этого iPhone…")
                    .font(.footnote)
                Button("Отменить установку", role: .cancel) { downloadTask?.cancel() }
            } else {
                Button("Загрузить Whisper", systemImage: "arrow.down.circle") { confirmingDownload = true }.disabled(busy)
            }
            Button("Удалить файлы Whisper", systemImage: "trash", role: .destructive) { confirmingRemoval = true }.disabled(busy)
            Text("Whisper преобразует речь в русский текст, но не заменяет LLM для оформления заметок и ранжирования проектов. Уже сохранённые транскрипты при смене движка не переписываются.")
                .font(.footnote).foregroundStyle(.secondary)
        }
        .confirmationDialog("Загрузить Whisper small?", isPresented: $confirmingDownload, titleVisibility: .visible) {
            Button("Загрузить") { install() }
            Button("Отмена", role: .cancel) {}
        } message: { Text(SpeechDownloadNotice.message) }
        .confirmationDialog("Удалить модель Whisper?", isPresented: $confirmingRemoval, titleVisibility: .visible) {
            Button("Удалить модель", role: .destructive) { remove() }
            Button("Отмена", role: .cancel) {}
        } message: { Text("Удалятся только файлы модели и токенизатора. Ваши записи, заметки и аудио останутся. Для следующего использования Whisper понадобится снова загрузить модель.") }
        .onChange(of: backend) { _, _ in Task { await app.refreshCapabilities() } }
        .onChange(of: scenePhase) { _, phase in if phase == .background { downloadTask?.cancel() } }
        .onDisappear { downloadTask?.cancel() }
    }

    private func install() {
        guard !busy else { return }
        progress = 0
        downloadTask = Task { @MainActor in
            defer { downloadTask = nil }
            do {
                try await app.speech.installWhisper { fraction in
                    Task { @MainActor in progress = max(progress, min(1, fraction)) }
                }
            } catch is CancellationError { /* Незавершённые файлы можно загрузить повторно. */ }
            catch { if !Task.isCancelled { app.errorMessage = "Не удалось установить Whisper: " + error.localizedDescription } }
            await app.refreshCapabilities()
        }
    }

    private func remove() {
        guard !busy else { return }
        removing = true
        Task { @MainActor in
            defer { removing = false }
            do { try await app.speech.removeWhisper() }
            catch { app.errorMessage = error.localizedDescription }
            await app.refreshCapabilities()
        }
    }
}
