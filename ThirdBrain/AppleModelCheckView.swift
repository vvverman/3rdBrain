import SwiftUI

struct AppleModelCheckView: View {
    @Bindable var app: AppController
    @Environment(\.scenePhase) private var scenePhase
    @State private var task: Task<Void, Never>?
    @State private var result = ""
    private let sample = "Так, по интерфейсу: ширину боковой панели пока не меняем. Надо проверить кнопку записи. Новые папки сегодня не создаём."

    var body: some View {
        Section("Текстовый ИИ Apple") {
            Text(app.modelStatus)
            DisclosureGroup("Пример для проверки") { Text(sample).textSelection(.enabled) }
            if task != nil {
                ProgressView("Проверка оформления на русском…")
                Button("Отменить проверку", role: .cancel) { task?.cancel() }
            } else {
                Button("Проверить оформление на примере", systemImage: "text.badge.checkmark") { check() }
                    .disabled(app.processingID != nil)
            }
            if !result.isEmpty {
                Text(result).textSelection(.enabled)
                ShareLink(item: "3rdBrain — проверка Foundation Models\n\nИсходный текст:\n" + sample + "\n\n" + result) {
                    Label("Поделиться результатом проверки", systemImage: "square.and.arrow.up")
                }
            }
            Text("Проверяется тот же обработчик, что оформляет ваши заметки. Пример не сохраняется в проектах. Сравните смысл и отрицания сами: полученный ответ ещё не гарантирует качество. Эта проверка не использует микрофон.")
                .font(.footnote).foregroundStyle(.secondary)
        }
        .onChange(of: scenePhase) { _, phase in if phase == .background { task?.cancel() } }
        .onDisappear { task?.cancel() }
    }

    private func check() {
        guard task == nil, app.processingID == nil else { return }
        result = ""
        task = Task { @MainActor in
            defer { task = nil }
            do {
                let edited = try await app.intelligence.clean(sample, localeID: "ru-RU")
                try Task.checkCancellation()
                result = "Ответ локальной модели Apple:\n\n" + edited.title + "\n\n" + edited.body
            } catch is CancellationError { result = "Проверка отменена." }
            catch { result = "Оформление не выполнено:\n" + error.localizedDescription }
            await app.refreshCapabilities()
        }
    }
}
