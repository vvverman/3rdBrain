import SwiftUI

struct SettingsView: View {
    @Bindable var app: AppController
    @Environment(\.openURL) private var openURL
    var body: some View {
        Form {
            Section("На этом iPhone") {
                LabeledContent("Язык записи", value: "Русский")
                Text(app.speechStatus)
                Text(app.modelStatus)
                Button("Проверить модели снова", systemImage: "arrow.clockwise") { Task { await app.refreshCapabilities() } }
                Button("Открыть системные настройки", systemImage: "gearshape") {
                    if let url = URL(string: UIApplication.openSettingsURLString) { openURL(url) }
                }
            }
            Section("Запись") {
                Text("При открытии приложения микрофон включается автоматически после вашего согласия. Запись можно остановить на нижней панели. Пауза сама не снимается.")
                Text("При блокировке экрана активная запись продолжается. Локальная обработка текста выполняется при открытом приложении; прерванные этапы можно повторить.")
            }
            Section("Хранение и приватность") {
                Label("Без сервера и синхронизации iCloud", systemImage: "iphone")
                Text("Проекты, тексты и аудио находятся в хранилище приложения. При удалении приложения локальные данные могут быть потеряны. Сохраните важные тексты и аудио через «Поделиться».")
                Text("Системные резервные копии iOS зависят от настроек телефона. Отсутствие синхронизации приложения не отключает резервное копирование системы.").font(.footnote).foregroundStyle(.secondary)
                Text("Первоначальная загрузка языковых ресурсов выполняется Apple. Аудио и заметки не отправляются внешней LLM или серверу распознавания.").font(.footnote).foregroundStyle(.secondary)
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
