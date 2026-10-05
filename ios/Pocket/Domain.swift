import Foundation
import CoreFoundation

struct PocketError: LocalizedError {
    let message: String
    init(_ message: String) { self.message = message }
    var errorDescription: String? { message }
}
func require(_ condition: Bool, _ message: String) throws {
    if !condition { throw PocketError(message) }
}
let energyNames = ["Planta", "Fuego", "Agua", "Rayo", "Psíquico", "Lucha", "Oscuridad", "Metal"]
let avatarIDs = ["trainer_red", "trainer_teal", "trainer_orange", "trainer_violet", "trainer_green", "trainer_blue"]
func canonicalID(_ text: String) throws -> String {
    let value = text.trimmingCharacters(in: .whitespacesAndNewlines).uppercased()
    try require(value.range(of: "^[A-Z0-9]+(?:-[A-Z0-9]+)*-[0-9]+$", options: .regularExpression) != nil, "ID no válido.")
    guard let split = value.lastIndex(of: "-"), let n = Int(value[value.index(after: split)...]), n > 0, n <= Int32.max else { throw PocketError("Número de carta no válido.") }
    return String(value[..<split]) + "-" + String(format: "%03d", n)
}
func normalizedRarity(_ value: String) throws -> String {
    let symbols = ["♦", "♦♦", "♦♦♦", "♦♦♦♦", "★", "★★", "★★★", "♛", "✷", "✷✷"]
    let labels = ["1 Diamante", "2 Diamantes", "3 Diamantes", "4 Diamantes", "1 Estrella", "2 Estrellas", "3 Estrellas (Inmersiva)", "Corona", "1 Brillo", "2 Brillos"]
    let names = ["ONE_DIAMOND", "TWO_DIAMONDS", "THREE_DIAMONDS", "FOUR_DIAMONDS", "ONE_STAR", "TWO_STARS", "THREE_STARS", "CROWN", "SHINY_ONE", "SHINY_TWO"]
    let v = value.trimmingCharacters(in: .whitespacesAndNewlines)
    guard let index = symbols.indices.first(where: { symbols[$0] == v || labels[$0].lowercased() == v.lowercased() || names[$0] == v }) else { throw PocketError("Rareza no reconocida.") }
    return symbols[index]
}
func strictInteger(_ value: Any?) throws {
    guard let n = value as? NSNumber, CFGetTypeID(n) != CFBooleanGetTypeID(), !["f", "d"].contains(String(cString: n.objCType)) else { throw PocketError("Se requiere una cantidad entera JSON.") }
}
struct Card: Codable, Identifiable, Hashable {
    let id: String
    let name: String
    let rarity: String
    let packs: [String]
    let category: String
    let element: String
    let stage: String
    let evolvesFrom: String
    var set: String { String(id[..<id.lastIndex(of: "-")!]) }
    var rulesName: String { name.replacingOccurrences(of: "’", with: "'").lowercased() }
    var energy: String? {
        ["grass":"Planta", "fire":"Fuego", "water":"Agua", "lightning":"Rayo", "psychic":"Psíquico", "fighting":"Lucha", "darkness":"Oscuridad", "metal":"Metal"][element]
    }
}
struct Catalog: Decodable { let cards: [Card] }
struct Inventory: Codable, Equatable, Identifiable {
    var id: String
    var name: String
    var rarity: String
    var pack: String
    var quantity: Int
    var wishlist: Bool
    var acquiredAt: Int64
}
struct Reference: Codable, Equatable { var id: String; var count: Int }
struct DeckContent: Codable, Equatable {
    var format = 2
    var energies: [String] = []
    var cards: [Reference] = []
    var total: Int { cards.reduce(0) { $0 + $1.count } }
    static func parse(_ text: String) throws -> Self {
        var value: Self
        if text.trimmingCharacters(in: .whitespacesAndNewlines).hasPrefix("{") {
            let data = Data(text.utf8)
            guard let root = try JSONSerialization.jsonObject(with: data) as? [String: Any], let rows = root["cards"] as? [[String: Any]] else { throw PocketError("Mazo no válido.") }
            for row in rows { try strictInteger(row["count"]) }
            value = try JSONDecoder().decode(Self.self, from: data)
            try require(value.format == 2, "Formato de mazo no compatible.")
        } else {
            value = Self()
            for row in text.split(separator: ";") where !row.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
                let parts = row.split(separator: ":", omittingEmptySubsequences: false)
                try require(parts.count == 2, "Carta de mazo no válida.")
                guard let count = Int(parts[1]) else { throw PocketError("Cantidad de mazo no válida.") }
                value.cards.append(Reference(id: String(parts[0]), count: count))
            }
        }
        value.cards = try value.cards.map { Reference(id: try canonicalID($0.id), count: $0.count) }
        try value.validateStructure()
        return value
    }
    func validateStructure() throws {
        try require(cards.allSatisfy { (1...2).contains($0.count) } && total <= 20, "Cantidades de mazo no válidas.")
        try require(Set(cards.map(\.id)).count == cards.count, "Carta duplicada en el mazo.")
        try require(energies.count <= 3 && Set(energies).count == energies.count && energies.allSatisfy { energyNames.contains($0) }, "Energías no válidas.")
    }
    func serialized() throws -> String {
        try validateStructure()
        return String(decoding: try JSONEncoder().encode(self), as: UTF8.self)
    }
    func warnings(catalog: [String: Card]) -> [String] {
        var result: [String] = []
        if total != 20 { result.append("Borrador: \(total)/20 cartas.") }
        if energies.isEmpty { result.append("Selecciona de una a tres energías.") }
        var names: [String: Int] = [:]
        let present = Set(cards.compactMap { catalog[$0.id]?.rulesName })
        var basic = false
        for ref in cards {
            guard let c = catalog[ref.id] else { result.append("Carta desconocida: \(ref.id)"); continue }
            names[c.rulesName, default: 0] += ref.count
            if c.category == "pokemon" {
                if c.stage == "basic" { basic = true }
                if !["basic", "1", "2"].contains(c.stage) { result.append("Etapa sin verificar: \(c.name)") }
                if c.stage != "basic" && (c.evolvesFrom.isEmpty || !present.contains(c.evolvesFrom.replacingOccurrences(of: "’", with: "'").lowercased())) {
                    result.append("Falta preevolución de \(c.name).")
                }
            }
        }
        if !basic { result.append("Falta un Pokémon básico verificado.") }
        if names.values.contains(where: { $0 > 2 }) { result.append("Máximo dos copias por nombre, incluidas variantes.") }
        return result
    }
}
struct SavedDeck: Codable, Equatable, Identifiable {
    var name: String
    var archetype: String
    var strategy: String
    var cards: String
    var total: Int
    var createdAt: Int64
    var id: String { "\(createdAt)|\(name)|\(cards)" }
    var signature: String { "\(name.utf8.count):\(name)\(cards.utf8.count):\(cards)\(strategy)" }
}
struct Preferences: Codable, Equatable { var theme = "system"; var avatar: String? = "trainer_red" }
struct Backup: Codable, Equatable {
    var format = "demeberant-tcg-pocket-backup"
    var version = 1
    var createdAt = milliseconds()
    var inventory: [Inventory] = []
    var decks: [SavedDeck] = []
    var preferences = Preferences()
    static func decode(_ data: Data) throws -> Self {
        try require(data.count <= 8_000_000, "Respaldo demasiado grande.")
        guard let root = try JSONSerialization.jsonObject(with: data) as? [String: Any], let rows = root["inventory"] as? [[String: Any]], let decks = root["decks"] as? [[String: Any]] else { throw PocketError("Estructura de respaldo no válida.") }
        try strictInteger(root["version"])
        for row in rows { try strictInteger(row["quantity"]) }
        for deck in decks { try strictInteger(deck["total"]) }
        var value = try JSONDecoder().decode(Self.self, from: data)
        try require(value.format == "demeberant-tcg-pocket-backup" && value.version == 1, "Formato de respaldo no compatible.")
        value.inventory = try value.inventory.map { row in
            var row = row
            row.id = try canonicalID(row.id)
            row.rarity = try normalizedRarity(row.rarity)
            try require(row.quantity >= 0 && row.quantity <= Int32.max && !row.name.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty, "Inventario no válido.")
            return row
        }
        try require(Set(value.inventory.map(\.id)).count == value.inventory.count, "Cartas duplicadas.")
        for deck in value.decks {
            let content = try DeckContent.parse(deck.cards)
            try require(deck.total == content.total && !deck.name.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty, "Mazo no válido.")
        }
        try require(["dark", "blue", "light", "system"].contains(value.preferences.theme), "Tema no válido.")
        if let avatar = value.preferences.avatar, !avatarIDs.contains(avatar) { value.preferences.avatar = avatarIDs[0] }
        return value
    }
    func data() throws -> Data { let encoder = JSONEncoder(); encoder.outputFormatting = [.prettyPrinted, .sortedKeys]; return try encoder.encode(self) }
}
func milliseconds() -> Int64 { Int64(Date().timeIntervalSince1970 * 1000) }
struct Draft: Codable, Equatable { var name = "Mi mazo"; var notes = ""; var content = DeckContent() }
struct AIProfile: Codable, Identifiable, Equatable {
    var id = UUID().uuidString
    var name: String
    var provider: String
    var model: String
    var endpoint: String
    var secretID = UUID().uuidString
}
struct LocalState: Codable, Equatable {
    var schema = 1
    var backup = Backup()
    var draft = Draft()
    var profiles: [AIProfile] = []
    var selectedProfile: String? = nil
    var meta: MetaSnapshot? = nil
    var autoMeta = false
    var lastAutoMeta: Int64? = nil
    mutating func merge(_ imported: Backup) {
        var byID = Dictionary(uniqueKeysWithValues: backup.inventory.map { ($0.id, $0) })
        for row in imported.inventory { byID[row.id] = row }
        backup.inventory = byID.values.sorted { $0.id < $1.id }
        var signatures = Set(backup.decks.map(\.signature))
        for deck in imported.decks where signatures.insert(deck.signature).inserted { backup.decks.append(deck) }
        backup.preferences.theme = imported.preferences.theme == "blue" ? "light" : imported.preferences.theme
        if let avatar = imported.preferences.avatar { backup.preferences.avatar = avatar }
    }
}
struct QRIdentity: Decodable { let id: String; let entity: Int; let kind: String; let name: String }
struct QRPayload {
    var trainers: [Int]
    var pokemon: [Int]
    var energies: [Int]
    func encoded() throws -> String {
        try require(trainers.count + pokemon.count == 20 && !pokemon.isEmpty, "El QR requiere 20 cartas y Pokémon.")
        try require((1...3).contains(energies.count) && Set(energies).count == energies.count && energies.allSatisfy { (1...8).contains($0) }, "Energías de QR no válidas.")
        var bytes = [UInt8(trainers.count)]
        func write(_ n: Int) { bytes += [UInt8((n >> 16) & 255), UInt8((n >> 8) & 255), UInt8(n & 255)] }
        for n in trainers { try require((1...999999).contains(n), "Entidad no válida."); write(n + 10_000_000) }
        bytes.append(UInt8(pokemon.count))
        for n in pokemon { try require((1...999999).contains(n), "Entidad no válida."); write(n) }
        bytes.append(UInt8(energies.count)); bytes += energies.map { UInt8($0) }
        return Data(bytes).base64EncodedString()
    }
    static func resolve(_ deck: DeckContent, identities: [String: QRIdentity]) throws -> Self {
        try deck.validateStructure()
        try require(deck.total == 20 && !deck.energies.isEmpty, "El QR requiere 20 cartas y energías.")
        var trainers: [Int] = [], pokemon: [Int] = [], names: [String:Int] = [:]
        for ref in deck.cards {
            guard let row = identities[ref.id] else { throw PocketError("Carta sin entidad del juego.") }
            let key = row.name.replacingOccurrences(of: "’", with: "'").lowercased()
            names[key, default: 0] += ref.count
            try require(names[key]! <= 2, "Máximo dos copias por nombre.")
            if row.kind == "trainer" { trainers += Array(repeating: row.entity, count: ref.count) }
            else { pokemon += Array(repeating: row.entity, count: ref.count) }
        }
        return Self(trainers: trainers, pokemon: pokemon, energies: deck.energies.map { energyNames.firstIndex(of: $0)! + 1 })
    }
}
