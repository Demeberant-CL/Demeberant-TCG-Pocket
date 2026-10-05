import XCTest

final class PocketUITests: XCTestCase {
    override func setUpWithError() throws { continueAfterFailure = false }
    func testFiveDestinationsAndOfflineEmptyStart() {
        let app = XCUIApplication(); app.launch()
        XCTAssertTrue(app.tabBars.buttons["Colección"].waitForExistence(timeout:20))
        for destination in ["Inicio","Colección","Mazos","IA","Más"] { XCTAssertTrue(app.tabBars.buttons[destination].exists) }
        app.tabBars.buttons["IA"].tap(); app.swipeUp(); XCTAssertTrue(app.buttons["Consultar IA"].waitForExistence(timeout:5)); XCTAssertFalse(app.buttons["Consultar IA"].isEnabled)
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
    func testTextSizesThemesAndAccessibilityDescriptions() throws {
        for (label,category) in [("100","UICTContentSizeCategoryL"),("130","UICTContentSizeCategoryXXXL"),("200","UICTContentSizeCategoryAccessibilityXXXL")] {
            for theme in ["Light","Dark"] {
                let app = XCUIApplication()
                app.launchArguments = ["-UIPreferredContentSizeCategoryName",category,"-AppleInterfaceStyle",theme]
                app.launch()
                XCTAssertTrue(app.tabBars.buttons["Colección"].waitForExistence(timeout:20))
                let home = XCTAttachment(screenshot:app.screenshot()); home.name = "home-\(theme)-text-\(label)"; home.lifetime = .keepAlways; add(home)
                app.tabBars.buttons["Colección"].tap(); app.buttons["Filtros"].tap()
                XCTAssertTrue(app.buttons["collectionStatus"].exists)
                let filters = XCTAttachment(screenshot:app.screenshot()); filters.name = "filters-\(theme)-text-\(label)"; filters.lifetime = .keepAlways; add(filters)
                app.tabBars.buttons["Mazos"].tap()
                XCTAssertTrue(app.buttons.matching(NSPredicate(format:"label BEGINSWITH %@","Continuar borrador")).firstMatch.exists)
                try app.performAccessibilityAudit(for: [.sufficientElementDescription])
                app.terminate()
            }
        }
    }
}
