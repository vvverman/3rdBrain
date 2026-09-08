import XCTest

@MainActor final class NavigationTests: XCTestCase {
    private var app: XCUIApplication!
    override func setUpWithError() throws {
        continueAfterFailure = false
        app = XCUIApplication()
        app.launchArguments = ["--ui-testing", "-AppleLanguages", "(ru)", "-AppleLocale", "ru_RU"]
    }
    override func tearDownWithError() throws {
        let screenshot = XCTAttachment(screenshot: app.screenshot())
        screenshot.name = name; screenshot.lifetime = .keepAlways
        add(screenshot)
        app.terminate(); app = nil
    }
    private func openDistribution() {
        app.launch()
        XCTAssertTrue(app.staticTexts["Тестовая запись"].waitForExistence(timeout: 10))
        app.staticTexts["Тестовая запись"].firstMatch.tap()
        app.buttons["Выбрать проект"].tap()
        XCTAssertTrue(app.navigationBars["Выберите проект"].waitForExistence(timeout: 5))
    }
    private func openWork() {
        app.tabBars.buttons["Проекты"].tap()
        app.staticTexts["Работа"].firstMatch.tap()
    }
    func testProjectCreationAndPinnedOrder() {
        app.launch()
        app.tabBars.buttons["Проекты"].tap()
        XCTAssertLessThan(app.staticTexts["Работа"].firstMatch.frame.minY, app.staticTexts["Личное"].firstMatch.frame.minY)
        app.buttons["Создать проект"].tap()
        let name = app.textFields["Название"]
        XCTAssertTrue(name.waitForExistence(timeout: 5))
        name.tap(); name.typeText("Новый проект")
        app.navigationBars.buttons["Сохранить"].tap()
        XCTAssertTrue(app.navigationBars["Проекты"].waitForExistence(timeout: 5))
        XCTAssertTrue(app.staticTexts["Новый проект"].waitForExistence(timeout: 5))
    }
    func testAppendPreservesOldTextAndSource() {
        openDistribution()
        XCTAssertTrue(app.staticTexts["Личное"].exists)
        app.staticTexts["Работа"].firstMatch.tap()
        app.staticTexts["Существующая"].firstMatch.tap()
        XCTAssertTrue(app.navigationBars["Тестовая запись"].waitForExistence(timeout: 5))
        openWork()
        app.staticTexts["Существующая"].firstMatch.tap()
        XCTAssertTrue(app.staticTexts["Старый текст\n\nНовая мысль"].waitForExistence(timeout: 5))
        XCTAssertTrue(app.staticTexts["Тестовая запись"].exists)
    }
    func testCreateNewNoteDoesNotChangeExisting() {
        openDistribution()
        app.staticTexts["Работа"].firstMatch.tap()
        app.buttons["Новая заметка"].tap()
        XCTAssertTrue(app.navigationBars["Тестовая запись"].waitForExistence(timeout: 5))
        openWork()
        XCTAssertTrue(app.staticTexts["Тестовая запись"].waitForExistence(timeout: 5))
        app.staticTexts["Существующая"].firstMatch.tap()
        XCTAssertTrue(app.staticTexts["Старый текст"].exists)
        XCTAssertFalse(app.staticTexts["Старый текст\n\nНовая мысль"].exists)
    }
    func testDeferringDistributionKeepsDraft() {
        openDistribution()
        app.buttons["Позже"].tap()
        XCTAssertTrue(app.buttons["Выбрать проект"].waitForExistence(timeout: 5))
        XCTAssertTrue(app.staticTexts["Новая мысль"].exists)
    }
    func testLargeTextKeepsRecorderControlReachable() {
        app.launchArguments.append("--ui-large-text")
        app.launch()
        XCTAssertTrue(app.buttons["Записать"].waitForExistence(timeout: 10))
        XCTAssertTrue(app.buttons["Записать"].isHittable)
        app.tabBars.buttons["Проекты"].tap()
        XCTAssertTrue(app.buttons["Записать"].isHittable)
    }
}
