#if DEBUG
import Foundation

extension AppController {
    // Только UI-тесты: отдельная база в памяти, без микрофона, сети и пользовательских данных.
    func seedInterfaceTests() throws {
        try store.addProject(title: "Работа", details: "Рабочие решения", instruction: "Задачи и решения по работе")
        try store.addProject(title: "Личное", details: "Личные идеи", instruction: "Личные записи")
        guard let project = store.projects.first(where: { $0.title == "Работа" }) else { return }
        try store.setPinned(project, true)
        try store.addNote(project: project, title: "Существующая", body: "Старый текст")
        let capture = Capture(originalPath: "Audio/\(UUID().uuidString)/original.caf")
        capture.title = "Тестовая запись"; capture.preparedText = "Новая мысль"
        capture.transcript = "Новая мысль"; capture.phase = .ready
        try store.insert(capture)
        speechStatus = "Тест интерфейса: распознавание не вызывается."
        modelStatus = "Тест интерфейса: модель не вызывается."
    }
}
#endif
