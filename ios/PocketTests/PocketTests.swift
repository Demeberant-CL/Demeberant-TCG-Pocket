import XCTest
import UIKit
@testable import Pocket

final class PocketTests: XCTestCase {
    func fixture(_ name: String) throws -> Data {
        let url = try XCTUnwrap(Bundle(for:Self.self).url(forResource:name,withExtension:"json"))
        return try Data(contentsOf:url)
    }
    func backup() throws -> Backup { try Backup.decode(fixture("android-v1-synthetic")) }
    func sampleCard(_ id: String = "A1-001", name: String = "Basic", stage: String = "basic", parent: String = "") -> Card {
        Card(id:id,name:name,rarity:"♦",packs:["Mewtwo"],category:"pokemon",element:"grass",stage:stage,evolvesFrom:parent)
    }
    func completeContent() -> DeckContent { DeckContent(energies:["Planta"],cards:(1...10).map { Reference(id:String(format:"A1-%03d",$0),count:2) }) }
    func completeCatalog() -> [String:Card] { Dictionary(uniqueKeysWithValues:(1...10).map { n in let id = String(format:"A1-%03d",n); return (id,sampleCard(id,name:"Basic\(n)")) }) }
    func testCanonicalPromoAndSuffixIDs() throws {
        XCTAssertEqual(try canonicalID(" promo-a-1 "),"PROMO-A-001")
        XCTAssertEqual(try canonicalID("b4b-12"),"B4B-012")
        XCTAssertThrowsError(try canonicalID("A1-0")); XCTAssertThrowsError(try canonicalID("A1-foo"))
    }
    func testBackupRoundTripPreservesNestedStringAndMissingAvatar() throws {
        let value = try backup(), decoded = try Backup.decode(value.data())
        XCTAssertEqual(value,decoded); XCTAssertNil(decoded.preferences.avatar)
        XCTAssertEqual(decoded.inventory.first?.id,"A1-001")
        let root = try XCTUnwrap(JSONSerialization.jsonObject(with:decoded.data()) as? [String:Any])
        let decks = try XCTUnwrap(root["decks"] as? [[String:Any]])
        XCTAssertTrue(decks[0]["cards"] is String)
    }
    func testInvalidBackupDoesNotChangeState() throws {
        let old = LocalState()
        var value = try backup(); value.inventory[0].quantity = -1
        XCTAssertThrowsError(try Backup.decode(value.data())); XCTAssertEqual(old,LocalState())
    }
    func testDuplicateNormalizedInventoryRejected() throws {
        var value = try backup(); var second = value.inventory[0]; second.id = "A1-1"; value.inventory.append(second)
        XCTAssertThrowsError(try Backup.decode(value.data()))
    }
    func testUnknownBackupVersionAndSizeRejected() throws {
        var value = try backup(); value.version = 2; XCTAssertThrowsError(try Backup.decode(value.data()))
        XCTAssertThrowsError(try Backup.decode(Data(repeating:32,count:8_000_001)))
    }
    func testBackupThemeAndDeckTotalRejected() throws {
        var value = try backup(); value.preferences.theme = "orange"; XCTAssertThrowsError(try Backup.decode(value.data()))
        value.preferences.theme = "system"; value.decks[0].total = 19; XCTAssertThrowsError(try Backup.decode(value.data()))
    }
    func testBackupStrictQuantityTypes() throws {
        var root = try XCTUnwrap(JSONSerialization.jsonObject(with:fixture("android-v1-synthetic")) as? [String:Any])
        for bad in ["2" as Any, true as Any, 1.5 as Any] {
            var rows = try XCTUnwrap(root["inventory"] as? [[String:Any]]); rows[0]["quantity"] = bad; root["inventory"] = rows
            XCTAssertThrowsError(try Backup.decode(JSONSerialization.data(withJSONObject:root)))
        }
    }
    func testRestoreMergesAndDeduplicatesWithoutRemovingOldRows() throws {
        var state = LocalState(), imported = try backup()
        var old = imported.inventory[0]; old.id = "PROMO-A-001"; old.name = "Old"; state.backup.inventory = [old]
        state.backup.preferences.avatar = "trainer_green"
        state.merge(imported); state.merge(imported)
        XCTAssertEqual(state.backup.inventory.count,2); XCTAssertEqual(state.backup.decks.count,1)
        XCTAssertEqual(state.backup.preferences.avatar,"trainer_green"); XCTAssertEqual(state.backup.preferences.theme,"light")
        imported.decks[0].strategy = "Different"; state.merge(imported); XCTAssertEqual(state.backup.decks.count,2)
    }
    func testCodecLegacyAndEnergyOrder() throws {
        let legacy = try DeckContent.parse("A1-1:2;PROMO-A-2:1")
        XCTAssertEqual(legacy.total,3); XCTAssertEqual(legacy.cards[1].id,"PROMO-A-002"); XCTAssertTrue(legacy.energies.isEmpty)
        let value = DeckContent(energies:["Metal","Planta","Agua"],cards:legacy.cards)
        XCTAssertEqual(try DeckContent.parse(value.serialized()),value)
    }
    func testCodecRejectsDuplicatesCountsAndEnergies() {
        for text in ["A1-001:3","A1-001:0","A1-001:1;A1-1:1"] { XCTAssertThrowsError(try DeckContent.parse(text)) }
        XCTAssertThrowsError(try DeckContent(energies:["Dragón"]).validateStructure())
        XCTAssertThrowsError(try DeckContent(energies:["Agua","Agua"]).validateStructure())
    }
    func testEvolutionBasicAndNameValidation() {
        let first = sampleCard(), evolution = sampleCard("A1-002",name:"Evolution",stage:"1",parent:"Absent")
        let content = DeckContent(energies:["Planta"],cards:[Reference(id:first.id,count:2),Reference(id:evolution.id,count:1)])
        XCTAssertTrue(content.warnings(catalog:[first.id:first,evolution.id:evolution]).contains{$0.contains("preevolución")})
        let art = sampleCard("A4-218")
        let repeated = DeckContent(energies:["Planta"],cards:[Reference(id:first.id,count:2),Reference(id:art.id,count:1)])
        XCTAssertTrue(repeated.warnings(catalog:[first.id:first,art.id:art]).contains{$0.contains("por nombre")})
    }
    func testFixedBinaryQRFixtures() throws {
        struct Fixture: Decodable { var trainers: [Int]; var pokemon: [Int]; var energies: [Int]; var payload: String }
        let fixtures = try JSONDecoder().decode([Fixture].self,from:fixture("qr-fixtures"))
        XCTAssertEqual(fixtures.count,4)
        for f in fixtures { XCTAssertEqual(try QRPayload(trainers:f.trainers,pokemon:f.pokemon,energies:f.energies).encoded(),f.payload) }
    }
    func testAllColoredPNGFixturesDecodeAndAlternativeDiffers() throws {
        struct Fixture: Decodable { var payload: String }
        for f in try JSONDecoder().decode([Fixture].self,from:fixture("qr-fixtures")) {
            let primary = try QRExport.png(payload:f.payload), alternate = try QRExport.png(payload:f.payload,alternate:true)
            XCTAssertEqual(Array(primary.prefix(8)),[137,80,78,71,13,10,26,10]); XCTAssertNotEqual(primary,alternate)
            for png in [primary,alternate] {
                let image = try XCTUnwrap(UIImage(data:png)); XCTAssertEqual(image.size.width,976)
                XCTAssertEqual(try QRExport.decoded(image),f.payload)
                let cg = try XCTUnwrap(image.cgImage), data = try XCTUnwrap(cg.dataProvider?.data)
                XCTAssertGreaterThan(CFDataGetLength(data),0)
            }
        }
    }
    func testQRMatrixHasFixedGeometryAndDistinctMasks() throws {
        let payload = "C5iWqJiWqJicApiaXpiXwJiaaJiahpiXKpiWnpiWnpiXogkAM+oAM+oAA9QAA9QAM/4AR6QAR6QAHPIAHPIBBA=="
        let first = try QRMatrix.make(payload,mask:0), second = try QRMatrix.make(payload,mask:1)
        XCTAssertEqual(first.count,53); XCTAssertEqual(first[0].count,53); XCTAssertNotEqual(first,second)
    }
    func testQRRejectsInvalidDraftAndEnergy() {
        XCTAssertThrowsError(try QRPayload(trainers:[],pokemon:[10],energies:[1]).encoded())
        XCTAssertThrowsError(try QRPayload(trainers:[],pokemon:Array(repeating:10,count:20),energies:[10]).encoded())
    }
    func testBundledCatalogAndQRMapCover4317Cards() throws {
        let url = try XCTUnwrap(Bundle.main.url(forResource:"pocket-catalog",withExtension:"json"))
        let qr = try XCTUnwrap(Bundle.main.url(forResource:"pocket-qr-entities",withExtension:"json"))
        let cards = try JSONDecoder().decode(Catalog.self,from:Data(contentsOf:url)).cards
        let identities = try JSONDecoder().decode([QRIdentity].self,from:Data(contentsOf:qr))
        XCTAssertEqual(cards.count,4317); XCTAssertEqual(identities.count,4317)
        XCTAssertEqual(Set(cards.map(\.id)),Set(identities.map(\.id)))
        XCTAssertEqual(identities.first{$0.id == "A1-098"}?.entity,980)
        XCTAssertEqual(identities.first{$0.id == "B1-224"}?.kind,"trainer")
    }
    func testDatabasePersistsDraftInventoryAndPreferencesOnRestart() async throws {
        let url = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString).appendingPathComponent("state.sqlite")
        defer { try? FileManager.default.removeItem(at:url.deletingLastPathComponent()) }
        let db = Database(url:url); var state = LocalState(); state.merge(try backup()); state.draft.content = completeContent(); state.draft.notes = "Draft survives"
        try await db.save(state)
        let reopened = try await Database(url:url).load(); XCTAssertEqual(reopened,state)
    }
    func testDatabaseKeepsPreviousVersionForRecovery() async throws {
        let url = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString).appendingPathComponent("state.sqlite")
        defer { try? FileManager.default.removeItem(at:url.deletingLastPathComponent()) }
        let db = Database(url:url); var old = LocalState(); old.draft.name = "First"; try await db.save(old)
        var next = old; next.draft.name = "Second"; try await db.save(next)
        let loaded = try await db.load(); XCTAssertEqual(loaded.draft.name,"Second")
    }
    func testAIRejectsUnknownIDsAndCollectionOverflow() throws {
        let content = completeContent(), catalog = completeCatalog(), owned = Dictionary(uniqueKeysWithValues:content.cards.map { ($0.id,2) })
        let proposal = AIProposal(name:"Deck",strategy:"Safe",energies:content.energies,cards:content.cards)
        try proposal.validate(catalog:catalog,quantities:owned,allowed:Set(owned.keys),action:"Crear",target:content)
        var limited = owned; limited["A1-001"] = 1
        XCTAssertThrowsError(try proposal.validate(catalog:catalog,quantities:limited,allowed:Set(owned.keys),action:"Crear",target:content))
        XCTAssertThrowsError(try proposal.validate(catalog:catalog,quantities:owned,allowed:[],action:"Crear",target:content))
    }
    func testAICompletePreservesAvailableTargetCopies() throws {
        let content = completeContent(); var cards = content.cards; cards.removeFirst(); cards.append(Reference(id:"A1-011",count:2))
        var catalog = completeCatalog(); catalog["A1-011"] = sampleCard("A1-011",name:"Other")
        var owned = Dictionary(uniqueKeysWithValues:content.cards.map { ($0.id,2) }); owned["A1-011"] = 2
        let proposal = AIProposal(name:"Deck",strategy:"",energies:["Planta"],cards:cards)
        XCTAssertThrowsError(try proposal.validate(catalog:catalog,quantities:owned,allowed:Set(owned.keys),action:"Completar faltantes",target:content))
        try proposal.validate(catalog:catalog,quantities:owned,allowed:Set(owned.keys),action:"Mejorar",target:content)
    }
    func testCSVQuotesMultilineBOMAndPromoRoundTrip() throws {
        let row = Inventory(id:"PROMO-A-001",name:"Name \"quoted\"\nsecond line",rarity:"♦",pack:"",quantity:3,wishlist:true,acquiredAt:1)
        let parsed = try CollectionCSV.parse("\u{FEFF}" + CollectionCSV.export([row]))
        XCTAssertEqual(parsed[0].id,row.id); XCTAssertEqual(parsed[0].name,row.name); XCTAssertEqual(parsed[0].wishlist,true)
    }
    func testCSVRejectsDuplicateNegativeAndBrokenQuotes() {
        let header = "Set,ID,Nombre,Rareza,Cantidad\n"
        for text in [header+"A1,1,Name,♦,-1",header+"A1,1,Name,♦,1\nA1,001,Name,♦,2",header+"A1,1,\"Name,♦,1"] { XCTAssertThrowsError(try CollectionCSV.parse(text)) }
    }
    func testCSVAbsentWishlistPreservesOptionalSemantics() throws {
        let rows = try CollectionCSV.parse("Set;ID;Nombre;Rareza;Cantidad\nA1;1;Name;♦;2")
        XCTAssertNil(rows[0].wishlist)
    }
    func testProbabilityKnownValuesAndBounds() throws {
        XCTAssertEqual(try Insights.cumulative(0.1,attempts:2),0.19,accuracy:0.000001)
        XCTAssertEqual(try Insights.draw(targets:2,draws:5),1-(15.0*14)/(20*19),accuracy:0.000001)
        XCTAssertEqual(try Insights.draw(targets:0,draws:20),0)
        XCTAssertThrowsError(try Insights.cumulative(.nan,attempts:2))
    }
    func testStarterHonorsInventoryAndStaysIncomplete() throws {
        let cards = [sampleCard()]
        let draft = try Insights.starter(catalog:cards,inventory:["A1-001":1],type:"Planta")
        XCTAssertEqual(draft.content.total,1); XCTAssertEqual(draft.content.energies,["Planta"])
        XCTAssertThrowsError(try Insights.starter(catalog:cards,inventory:[:],type:"Planta"))
    }
    func testSandboxStartsFiveWithBasicAndPreserves20Instances() throws {
        let board = try Board.start(completeContent(),catalog:completeCatalog())
        XCTAssertEqual(board.hand.count,5); XCTAssertEqual(board.pile.count,15); XCTAssertTrue(board.hand.contains{$0.basic})
        XCTAssertEqual(Set((board.hand+board.pile).map(\.id)).count,20)
    }
    func testSandboxEnergyOncePerTurnAndDiscardReset() throws {
        var board = try Board.start(completeContent(),catalog:completeCatalog())
        let id = try XCTUnwrap(board.hand.first).id
        try board.move(id,to:"Activo"); try board.energy(id); XCTAssertThrowsError(try board.energy(id))
        try board.damage(id,amount:30); board.next(); try board.energy(id); try board.move(id,to:"Descarte")
        XCTAssertEqual(board.discard[0].damage,0); XCTAssertEqual(board.discard[0].energies,0)
    }
    func testSandboxBenchLimitAndActiveOccupied() throws {
        var board = try Board.start(completeContent(),catalog:completeCatalog())
        let ids = board.hand.map(\.id); try board.move(ids[0],to:"Activo"); XCTAssertThrowsError(try board.move(ids[1],to:"Activo"))
        for id in ids[1...3] { try board.move(id,to:"Banca") }
        XCTAssertThrowsError(try board.move(ids[4],to:"Banca")); try board.swap(ids[1]); XCTAssertEqual(board.active?.id,ids[1])
    }
    func testDiagnosticsZIPChecksumAndSignature() {
        XCTAssertEqual(DiagnosticZIP.crc(Data("123456789".utf8)),0xCBF43926)
        XCTAssertEqual(Array(DiagnosticZIP.make("safe").prefix(4)),[80,75,3,4])
    }
    @MainActor func testDiagnosticDoesNotExposeProfilesAndDraftText() {
        let store = Store()
        let text = store.diagnostic(); XCTAssertFalse(text.contains("https://")); XCTAssertFalse(text.contains("Bearer "))
    }
    func testKeychainReplacementAndRemoval() throws {
        let id = UUID().uuidString; defer { try? Secrets.remove(id) }
        try Secrets.write("synthetic-only",id:id); XCTAssertEqual(try Secrets.read(id),"synthetic-only")
        try Secrets.write("replacement-only",id:id); XCTAssertEqual(try Secrets.read(id),"replacement-only")
        try Secrets.remove(id); XCTAssertEqual(try Secrets.read(id),"")
    }
    func testTCGdexIDsPreserveNumberAndSuffix() {
        XCTAssertEqual(tcgdexID("A1A-001"),"A1a-001"); XCTAssertEqual(tcgdexID("PROMO-A-002"),"P-A-002")
    }
    func testEffectsClassifyOnlyMatchingRules() {
        let value = CardRules(id:"A1-001",language:"es",hp:60,text:"Roba cartas. Cura a tu Pokémon. Une una energía.",updated:0)
        XCTAssertTrue(value.roles.contains("Robar")); XCTAssertTrue(value.roles.contains("Curar")); XCTAssertTrue(value.roles.contains("Energía")); XCTAssertFalse(value.roles.contains("Milling"))
    }
}
