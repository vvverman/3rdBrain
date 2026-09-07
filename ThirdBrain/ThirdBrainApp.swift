import SwiftUI

@main @MainActor struct ThirdBrainApp: App {
    @State private var controller: AppController?
    private let launchError: String?
    init() {
        do { _controller = State(initialValue: try AppController()); launchError = nil }
        catch { _controller = State(initialValue: nil); launchError = error.localizedDescription }
    }
    var body: some Scene {
        WindowGroup {
            if let controller {
                RootView(app: controller)
            } else {
                ContentUnavailableView {
                    Label("Не удалось открыть хранилище", systemImage: "externaldrive.badge.exclamationmark")
                } description: {
                    Text((launchError ?? "Неизвестная ошибка") + "\nДанные не удалены. Не переустанавливайте приложение до сохранения копии данных.")
                }
            }
        }
    }
}
