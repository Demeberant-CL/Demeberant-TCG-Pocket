import XCTest
import UIKit

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
        waitForOrientation(app,landscape:true)
        XCTAssertTrue(app.tabBars.buttons["Más"].exists)
        let shot = XCTAttachment(screenshot:app.screenshot()); shot.name = "collection-landscape"; shot.lifetime = .keepAlways; add(shot)
        XCUIDevice.shared.orientation = .portrait
        waitForOrientation(app,landscape:false)
    }
    func testTutorialsAndVersionRemainAccessible() {
        let app = XCUIApplication(); app.launch(); XCTAssertTrue(app.tabBars.buttons["Más"].waitForExistence(timeout:20)); app.tabBars.buttons["Más"].tap()
        app.swipeUp(); XCTAssertTrue(app.staticTexts["Pocket iOS · 0.1.0 (1)"].waitForExistence(timeout:5))
        app.staticTexts["Tutoriales"].tap(); XCTAssertTrue(app.staticTexts["Migrar desde Android"].exists)
    }
    func testTextSizesThemesAndAccessibilityDescriptions() throws {
        for (label,category) in [("100","UICTContentSizeCategoryL"),("130","UICTContentSizeCategoryXXXL"),("200","UICTContentSizeCategoryAccessibilityL")] {
            for theme in ["Light","Dark"] {
                let app = XCUIApplication()
                app.launchArguments = ["-UIPreferredContentSizeCategoryName",category]
                app.launch()
                XCTAssertTrue(app.tabBars.buttons["Colección"].waitForExistence(timeout:20))
                app.tabBars.buttons["Más"].tap()
                let settings = app.staticTexts["Ajustes y respaldos"]
                for _ in 0..<6 { if settings.isHittable { break }; app.swipeUp() }
                XCTAssertTrue(settings.isHittable); settings.tap()
                app.buttons["themePicker"].tap(); app.buttons[theme == "Dark" ? "Oscuro" : "Claro"].tap()
                app.tabBars.buttons["Inicio"].tap()
                assertTheme(app,dark:theme == "Dark")
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
    func testDraftTextSurvivesKeyboardRotationAndRestart() {
        let app = XCUIApplication(); app.launch()
        XCTAssertTrue(app.tabBars.buttons["Mazos"].waitForExistence(timeout:20)); app.tabBars.buttons["Mazos"].tap()
        app.buttons.matching(NSPredicate(format:"label BEGINSWITH %@","Continuar borrador")).firstMatch.tap()
        let name = app.textFields["Nombre del mazo"]
        XCTAssertTrue(name.waitForExistence(timeout:5)); name.tap(); name.typeText(" prueba UI")
        let value = name.value as? String
        XCUIDevice.shared.orientation = .landscapeLeft
        waitForOrientation(app,landscape:true)
        XCTAssertEqual(name.value as? String,value)
        XCUIDevice.shared.orientation = .portrait
        waitForOrientation(app,landscape:false)
        app.buttons["Cerrar"].tap(); app.buttons["Conservar borrador y cerrar"].tap()
        app.terminate(); app.launch()
        XCTAssertTrue(app.tabBars.buttons["Mazos"].waitForExistence(timeout:20)); app.tabBars.buttons["Mazos"].tap()
        app.buttons.matching(NSPredicate(format:"label BEGINSWITH %@","Continuar borrador")).firstMatch.tap()
        XCTAssertEqual(app.textFields["Nombre del mazo"].value as? String,value)
        app.buttons["Cerrar"].tap(); app.buttons["Conservar borrador y cerrar"].tap()
    }
    func waitForOrientation(_ app: XCUIApplication, landscape: Bool) {
        let expected = XCTNSPredicateExpectation(predicate:NSPredicate { object,_ in
            guard let window = object as? XCUIElement else { return false }
            return landscape ? window.frame.width > window.frame.height : window.frame.height > window.frame.width
        },object:app.windows.firstMatch)
        XCTAssertEqual(XCTWaiter.wait(for:[expected],timeout:10),.completed)
        Thread.sleep(forTimeInterval:1)
    }
    func assertTheme(_ app: XCUIApplication, dark: Bool) {
        guard let image = app.screenshot().image.cgImage else { XCTFail("Missing screenshot"); return }
        var pixel = [UInt8](repeating:0,count:4)
        let ok = pixel.withUnsafeMutableBytes { bytes -> Bool in
            guard let context = CGContext(data:bytes.baseAddress,width:1,height:1,bitsPerComponent:8,bytesPerRow:4,space:CGColorSpaceCreateDeviceRGB(),bitmapInfo:CGImageAlphaInfo.premultipliedLast.rawValue) else { return false }
            context.translateBy(x:-10,y:-CGFloat(image.height/2))
            context.draw(image,in:CGRect(x:0,y:0,width:image.width,height:image.height))
            return true
        }
        XCTAssertTrue(ok)
        let brightness = (Int(pixel[0])+Int(pixel[1])+Int(pixel[2]))/3
        if dark { XCTAssertLessThan(brightness,100) } else { XCTAssertGreaterThan(brightness,180) }
    }
}
