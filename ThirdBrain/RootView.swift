import SwiftUI

struct RootView: View {
    @Bindable var app: AppController
    @Environment(\.scenePhase) private var scenePhase
    var body: some View {
        TabView {
            Tab("Входящие", systemImage: "tray") { NavigationStack { InboxView(app: app) } }
            Tab("Проекты", systemImage: "folder") { NavigationStack { ProjectsView(app: app) } }
            Tab("Настройки", systemImage: "gearshape") { NavigationStack { SettingsView(app: app) } }
        }
        .tabViewBottomAccessory { RecorderBar(app: app).padding(.horizontal, 8) }
        .task { await app.launch() }
        .onChange(of: scenePhase) { _, phase in
            if phase == .background { app.enteredBackground() }
            if phase == .active { Task { await app.enteredForeground() } }
        }
        .sheet(isPresented: $app.onboarding) { OnboardingView(app: app) }
        .sheet(item: $app.routingCapture) { capture in
            DistributionFlow(app: app, capture: capture)
                .safeAreaInset(edge: .bottom) {
                    RecorderBar(app: app).padding(12).glassEffect(in: .capsule).padding()
                }
        }
        .alert("Не удалось выполнить действие", isPresented: Binding(get: { app.errorMessage != nil }, set: { if !$0 { app.errorMessage = nil } })) {
            Button("Понятно", role: .cancel) { app.errorMessage = nil }
        } message: { Text(app.errorMessage ?? "") }
    }
}

struct InboxView: View {
    @Bindable var app: AppController
    @State private var search = ""
    private var records: [Capture] {
        app.inbox.filter { search.isEmpty || $0.title.localizedCaseInsensitiveContains(search) || $0.textToSave.localizedCaseInsensitiveContains(search) }
    }
    var body: some View {
        List {
            if !app.recorder.interruptionMessage.isEmpty {
                Label(app.recorder.interruptionMessage, systemImage: "pause.circle").font(.callout)
            }
            ForEach(records) { capture in
                NavigationLink { CaptureView(app: app, capture: capture) } label: { CaptureRow(capture: capture) }
            }
        }
        .navigationTitle("Входящие")
        .searchable(text: $search, prompt: "Поиск по записям")
        .overlay {
            if records.isEmpty {
                ContentUnavailableView(search.isEmpty ? "Здесь появятся ваши мысли" : "Ничего не найдено", systemImage: search.isEmpty ? "waveform" : "magnifyingglass", description: Text(search.isEmpty ? "Говорите, затем завершите запись на нижней панели. Разложить её по проектам можно позже." : "Попробуйте изменить запрос."))
                    .allowsHitTesting(false)
            }
        }
    }
}

struct CaptureRow: View {
    let capture: Capture
    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            Text(capture.title).font(.headline).lineLimit(2)
            if !capture.textToSave.isEmpty { Text(capture.textToSave).font(.subheadline).foregroundStyle(.secondary).lineLimit(2) }
            HStack {
                Text(capture.createdAt, format: .dateTime.day().month().hour().minute())
                Spacer()
                if capture.phase.isWorking { ProgressView().controlSize(.mini) }
                Text(capture.phase.isWorking || capture.phase == .failed ? capture.phase.label : clock(capture.duration))
            }.font(.caption).foregroundStyle(.secondary)
        }.padding(.vertical, 4)
    }
}

struct OnboardingView: View {
    @Bindable var app: AppController
    var body: some View {
        VStack(alignment: .leading, spacing: 24) {
            Image(systemName: "waveform.circle.fill").font(.system(size: 64)).foregroundStyle(.tint)
            Text("Откройте.\nСкажите. Сохраните.").font(.largeTitle.bold())
            Text("После вашего согласия 3rdBrain начинает запись при каждом открытии. Микрофон всегда виден на нижней панели: там можно поставить запись на паузу или завершить её.")
            Text("Во время записи можно читать заметки. Аудио и тексты хранятся на iPhone. Для первой загрузки системных моделей может понадобиться интернет.").foregroundStyle(.secondary)
            Spacer()
            Button("Разрешить микрофон и начать", systemImage: "mic.fill") { Task { await app.consentAndStart() } }
                .buttonStyle(.glassProminent).controlSize(.large).frame(maxWidth: .infinity)
            Button("Пока только смотреть заметки") { app.onboarding = false }.frame(maxWidth: .infinity)
        }.padding(28).interactiveDismissDisabled()
    }
}

func clock(_ value: Double) -> String {
    let seconds = value.isFinite ? max(0, Int(value)) : 0
    return seconds >= 3600 ? String(format: "%d:%02d:%02d", seconds / 3600, (seconds / 60) % 60, seconds % 60)
        : String(format: "%02d:%02d", seconds / 60, seconds % 60)
}
