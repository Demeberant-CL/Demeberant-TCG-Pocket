import XCTest

final class PocketUITests: XCTestCase {
    override func setUpWithError() throws { continueAfterFailure = false }
    func testFiveDestinationsAndOfflineEmptyStart() {
        let app = XCUIApplication(); app.launch()
        XCTAssertTrue(app.tabBars.buttons["Colección"].waitForExistence(timeout:20))
        for destination in ["Inicio","Colección","Mazos","IA","Más"] { XCTAssertTrue(app.tabBars.buttons[destination].exists) }
        app.tabBars.buttons["IA"].tap(); XCTAssertTrue(app.buttons["Consultar IA"].exists); XCTAssertFalse(app.buttons["Consultar IA"].isEnabled)
    }
    func testCollectionFiltersAndRotationKeepNativeNavigation() {
        let app = XCUIApplication(); app.launch(); XCTAssertTrue(app.tabBars.buttons["Colección"].waitForExistence(timeout:20)); app.tabBars.buttons["Colección"].tap()
        app.buttons["Filtros"].tap(); XCTAssertTrue(app.buttons["collectionStatus"].exists)
        XCUIDevice.shared.orientation = .landscapeLeft
        XCTAssertTrue(app.tabBars.buttons["Más"].exists)
        let shot = XCTAttachment(screenshot:app.screenshot()); shot.name = "collection-landscape"; shot.lifetime = .keepAlways; add(shot)
        XCUIDevice.shared.orientation = .portrait
    }
    func testTutorialsAndVersionRemainAccessible() {
        let app = XCUIApplication(); app.launch(); XCTAssertTrue(app.tabBars.buttons["Más"].waitForExistence(timeout:20)); app.tabBars.buttons["Más"].tap()
        app.swipeUp(); XCTAssertTrue(app.staticTexts["Pocket iOS · 0.1.0 (1)"].waitForExistence(timeout:5))
        app.staticTexts["Tutoriales"].tap(); XCTAssertTrue(app.staticTexts["Migrar desde Android"].exists)
    }
}
