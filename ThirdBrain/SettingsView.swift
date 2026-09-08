import SwiftUI

struct SettingsView: View {
    @Bindable var app: AppController
    @Environment(\.openURL) private var openURL
    private var diagnosticReport: String {
        "3rdBrain — Apple-стек\n" + ProcessInfo.processInfo.operatingSystemVersionString
            + "\nЯзык записи: ru-RU\n\nРаспознавание речи:\n" + app.speechStatus
            + "\n\nТекстовая модель:\n" + app.modelStatus
            + "\n\nЭто проверка доступности API, не оценка качества распознавания и текста."
    }
    var body: some View {
        Form {
            Section("На этом iPhone") {
                LabeledContent("Язык записи", value: "Русский")
                LabeledContent("Модели", value: "Только Apple")
                Button("Обновить диагностику", systemImage: "arrow.clockwise") { Task { await app.refreshCapabilities() } }
                ShareLink(item: diagnosticReport) { Label("Поделиться диагностикой", systemImage: "square.and.arrow.up") }
                Button("Открыть системные настройки", systemImage: "gearshape") {
                    if let url = URL(string: UIApplication.openSettingsURLString) { openURL(url) }
                }
            }
            SpeechModelControls(app: app)
            AppleModelCheckView(app: app)
            Section("Запись") {
                Text("При открытии приложения микрофон включается автоматически после вашего согласия. Запись можно остановить на нижней панели. Пауза сама не снимается.")
                Text("При блокировке экрана активная запись продолжается. Локальная обработка текста выполняется при открытом приложении; прерванные этапы можно повторить.")
            }
            Section("Хранение и приватность") {
                Label("Без сервера и синхронизации iCloud", systemImage: "iphone")
                Text("Проекты, тексты и аудио находятся в хранилище приложения. При удалении приложения локальные данные могут быть потеряны. Сохраните важные тексты и аудио через «Поделиться».")
                Text("Системные резервные копии iOS зависят от настроек телефона. Отсутствие синхронизации приложения не отключает резервное копирование системы.").font(.footnote).foregroundStyle(.secondary)
                Text("Системные языковые ресурсы загружаются Apple после вашего подтверждения. Аудио и заметки не отправляются внешней LLM или серверу распознавания. Подготовка текстовой модели выполняется через системные настройки Apple Intelligence.").font(.footnote).foregroundStyle(.secondary)
            }
            Section("О приложении") {
                LabeledContent("3rdBrain", value: "0.1.0")
                Text("SwiftUI · SwiftData · AVFoundation · SpeechAnalyzer · Foundation Models").font(.footnote).foregroundStyle(.secondary)
                Text("Оригинал хранится в CAF/PCM, компактная версия — в M4A. Оригинальное аудио не удаляется автоматически.").font(.footnote).foregroundStyle(.secondary)
            }
        }
        .navigationTitle("Настройки")
        .task { await app.refreshCapabilities() }
    }
}
