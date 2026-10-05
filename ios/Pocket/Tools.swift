import Foundation

struct CSVRow { var id: String; var name: String; var rarity: String; var quantity: Int; var wishlist: Bool? }
enum CollectionCSV {
    static func parse(_ text: String) throws -> [CSVRow] {
        try require(text.utf8.count <= 8_000_000,"CSV demasiado grande.")
        var text = text
        if text.hasPrefix("\u{FEFF}") { text.removeFirst() }
        let header = text.split(whereSeparator: { $0 == "\n" || $0 == "\r" }).first ?? ""
        let delimiter: Character = header.filter { $0 == ";" }.count > header.filter { $0 == "," }.count ? ";" : ","
        let chars = Array(text)
        var rows: [[String]] = [], row: [String] = [], field = "", quoted = false, closed = false, i = 0
        func finishField() { row.append(field); field = ""; closed = false }
        func finishRow() { finishField(); if row.contains(where:{ !$0.trimmingCharacters(in:.whitespacesAndNewlines).isEmpty }) { rows.append(row) }; row = [] }
        while i < chars.count {
            let c = chars[i]
            if quoted {
                if c == "\"" {
                    if i+1 < chars.count && chars[i+1] == "\"" { field.append(c); i += 1 }
                    else { quoted = false; closed = true }
                } else { field.append(c) }
            } else {
                switch c {
                case "\"": try require(field.isEmpty && !closed,"Comillas CSV no válidas."); quoted = true
                case delimiter: finishField()
                case "\n": finishRow()
                case "\r": if i+1 < chars.count && chars[i+1] == "\n" { i += 1 }; finishRow()
                default: try require(!closed || c.isWhitespace,"Contenido después de comillas."); if !closed { field.append(c) }
                }
            }
            i += 1
        }
        try require(!quoted,"Comillas sin cerrar.")
        if !field.isEmpty || !row.isEmpty || closed { finishRow() }
        try require(rows.count > 1,"CSV sin cartas.")
        func normalized(_ value: String) -> String { value.trimmingCharacters(in:.whitespacesAndNewlines).folding(options:[.diacriticInsensitive,.caseInsensitive],locale:Locale(identifier:"en_US_POSIX")) }
        let headers = rows[0].map(normalized)
        func col(_ aliases: [String]) -> Int? { headers.firstIndex { aliases.contains($0) } }
        guard let set = col(["set","expansion","coleccion"]), let id = col(["id","card_id","codigo","numero"]),
            let name = col(["nombre","name"]), let rarity = col(["rareza","rarity"]), let qty = col(["cantidad","quantity","count","copias"]) else { throw PocketError("Faltan columnas Set, ID, Nombre, Rareza y Cantidad.") }
        let wish = col(["deseos","wishlist","favorito","favorita"])
        var seen = Set<String>()
        return try rows.dropFirst().map { row in
            try require(row.count == headers.count,"Número de campos CSV incorrecto.")
            let canonical = try canonicalID(row[set].trimmingCharacters(in:.whitespaces) + "-" + row[id].trimmingCharacters(in:.whitespaces))
            try require(seen.insert(canonical).inserted,"ID CSV duplicado.")
            guard let quantity = Int(row[qty].trimmingCharacters(in:.whitespaces)), quantity >= 0 else { throw PocketError("Cantidad CSV no válida.") }
            try require(!row[name].trimmingCharacters(in:.whitespacesAndNewlines).isEmpty,"Nombre CSV vacío.")
            var wishlist: Bool? = nil
            if let wish { let value = normalized(row[wish]); try require(["si","true","1","no","false","0",""].contains(value),"Deseos CSV no válido."); wishlist = ["si","true","1"].contains(value) }
            return CSVRow(id:canonical,name:row[name].trimmingCharacters(in:.whitespacesAndNewlines),rarity:try normalizedRarity(row[rarity]),quantity:quantity,wishlist:wishlist)
        }
    }
    static func export(_ rows: [Inventory]) -> String {
        func quote(_ text: String) -> String { "\"" + text.replacingOccurrences(of:"\"",with:"\"\"") + "\"" }
        return "\"Set\",\"ID\",\"Nombre\",\"Rareza\",\"Cantidad\",\"Registrada\",\"Deseos\"\n" + rows.sorted { $0.id < $1.id }.map { row in
            let split = row.id.lastIndex(of:"-")!
            return [String(row.id[..<split]),String(Int(row.id[row.id.index(after:split)...])!),row.name,row.rarity,"\(row.quantity)",row.quantity > 0 ? "sí" : "no",row.wishlist ? "sí" : "no"].map(quote).joined(separator:",")
        }.joined(separator:"\n")
    }
}
struct PackCoverage: Identifiable {
    var set: String; var pack: String; var missing: Int; var wishes: Int; var targets: [String]
    var score: Int { missing + wishes*3 + targets.count*5 }
    var id: String { set + "|" + pack }
}
enum Insights {
    static func coverage(catalog: [Card], inventory: [String:Inventory], targets: Set<String>) -> [PackCoverage] {
        var groups: [String:PackCoverage] = [:]
        for card in catalog where !card.set.hasPrefix("PROMO-") {
            for pack in card.packs {
                let key = card.set + "|" + pack
                var group = groups[key] ?? PackCoverage(set:card.set,pack:pack,missing:0,wishes:0,targets:[])
                if (inventory[card.id]?.quantity ?? 0) == 0 {
                    group.missing += 1
                    if inventory[card.id]?.wishlist == true { group.wishes += 1 }
                    if targets.contains(card.id) { group.targets.append(card.id + " · " + card.name) }
                }
                groups[key] = group
            }
        }
        return groups.values.sorted { $0.score == $1.score ? $0.id < $1.id : $0.score > $1.score }
    }
    static func cumulative(_ rate: Double, attempts: Int) throws -> Double {
        try require(rate.isFinite && (0...1).contains(rate) && attempts >= 0,"Probabilidad no válida.")
        return 1-pow(1-rate,Double(attempts))
    }
    static func draw(deck: Int = 20, targets: Int, draws: Int) throws -> Double {
        try require((1...100).contains(deck) && (0...deck).contains(targets) && (0...deck).contains(draws),"Robo no válido.")
        if draws > deck-targets { return 1 }
        var none = 1.0
        for i in 0..<draws { none *= Double(deck-targets-i)/Double(deck-i) }
        return 1-none
    }
    static func starter(catalog: [Card], inventory: [String:Int], type: String) throws -> Draft {
        try require(energyNames.contains(type),"Energía no válida.")
        let pool = catalog.filter { inventory[$0.id,default:0] > 0 && ($0.energy == type || $0.element == "colorless" || $0.category == "trainer") }.sorted { $0.id < $1.id }
        var cards: [Reference] = []
        func add(_ card: Card) {
            if !card.evolvesFrom.isEmpty && !cards.contains(where: { ref in catalog.first { $0.id == ref.id }?.rulesName == card.evolvesFrom.lowercased() }) { return }
            if card.category != "trainer" && (!["basic","1","2"].contains(card.stage) || card.stage != "basic" && card.evolvesFrom.isEmpty) { return }
            let sameName = cards.reduce(0) { total, ref in total + (catalog.first { $0.id == ref.id }?.rulesName == card.rulesName ? ref.count : 0) }
            let old = cards.first { $0.id == card.id }?.count ?? 0
            let count = min(2-sameName,min(inventory[card.id,default:0]-old,20-cards.reduce(0) { $0+$1.count }))
            if count > 0 { if let index = cards.firstIndex(where:{$0.id == card.id}) { cards[index].count += count } else { cards.append(Reference(id:card.id,count:count)) } }
        }
        for card in pool.filter({$0.energy == type && $0.stage == "basic"}).prefix(3) { add(card) }
        for card in pool.filter({$0.category == "trainer"}) {
            if cards.reduce(0,{ total, ref in total + (catalog.first { $0.id == ref.id }?.category == "trainer" ? ref.count : 0) }) < 8 { add(card) }
        }
        for _ in 0..<3 { for card in pool where card.category != "trainer" { add(card) } }
        try require(cards.contains { ref in catalog.contains { $0.id == ref.id && $0.category == "pokemon" && $0.stage == "basic" } },"No tienes un Pokémon básico verificado de este tipo.")
        return Draft(name:"Mi mazo de \(type)",notes:"Borrador local con tus cartas. Revisa estrategia y costes; no es recomendación del meta.",content:DeckContent(energies:[type],cards:cards))
    }
}
struct BoardCard: Identifiable, Equatable {
    var id: Int; var cardID: String; var name: String; var basic: Bool; var pokemon: Bool
    var energies = 0; var damage = 0
}
struct Board: Equatable {
    var pile: [BoardCard]; var hand: [BoardCard]; var active: BoardCard? = nil
    var bench: [BoardCard] = []; var discard: [BoardCard] = []; var turn = 1; var attached = false
    static func start(_ content: DeckContent, catalog: [String:Card]) throws -> Self {
        try content.validateStructure()
        try require(content.total == 20,"El simulador requiere 20 cartas.")
        var names: [String:Int] = [:], instances: [BoardCard] = []
        for ref in content.cards {
            guard let card = catalog[ref.id] else { throw PocketError("Carta sin datos.") }
            names[card.rulesName,default:0] += ref.count
            for _ in 0..<ref.count { instances.append(BoardCard(id:instances.count,cardID:card.id,name:card.name,basic:card.category == "pokemon" && card.stage == "basic",pokemon:card.category == "pokemon")) }
        }
        try require(names.values.allSatisfy { $0 <= 2 } && instances.contains { $0.basic },"Se requiere un básico y máximo dos copias por nombre.")
        instances.shuffle()
        var hand = Array(instances.prefix(5)), pile = Array(instances.dropFirst(5))
        if !hand.contains(where:{$0.basic}), let index = pile.firstIndex(where:{$0.basic}) { let basic = pile.remove(at:index); pile.append(hand.removeLast()); hand.append(basic) }
        return Self(pile:pile,hand:hand)
    }
    mutating func draw() throws { try require(!pile.isEmpty,"No quedan cartas."); hand.append(pile.removeFirst()) }
    mutating func next() { if !pile.isEmpty { hand.append(pile.removeFirst()) }; turn += 1; attached = false }
    mutating func move(_ id: Int, to zone: String) throws {
        guard var card = (hand+bench+discard+[active].compactMap{$0}).first(where:{$0.id == id}) else { throw PocketError("Carta no disponible.") }
        try require(["Mano","Activo","Banca","Descarte"].contains(zone),"Zona no válida.")
        if zone == "Activo" { try require(active == nil || active?.id == id,"Activo ocupado.") }
        if zone == "Banca" { try require(bench.count < 3 || bench.contains { $0.id == id },"Máximo tres Pokémon en banca.") }
        if ["Activo","Banca"].contains(zone) { try require(card.pokemon,"Solo Pokémon en Activo o Banca.") }
        hand.removeAll{$0.id == id}; bench.removeAll{$0.id == id}; discard.removeAll{$0.id == id}; if active?.id == id { active = nil }
        if ["Mano","Descarte"].contains(zone) { card.energies = 0; card.damage = 0 }
        switch zone { case "Mano": hand.append(card); case "Activo": active = card; case "Banca": bench.append(card); default: discard.append(card) }
    }
    mutating func swap(_ id: Int) throws {
        guard let chosen = bench.first(where:{$0.id == id}) else { throw PocketError("Pokémon fuera de la banca.") }
        bench.removeAll{$0.id == id}; if let active { bench.append(active) }; active = chosen
    }
    mutating func energy(_ id: Int) throws {
        try require(!attached,"Ya asignaste energía este turno.")
        try require(active?.id == id || bench.contains{$0.id == id},"Solo Activo o Banca.")
        if active?.id == id { active?.energies += 1 } else if let i = bench.firstIndex(where:{$0.id == id}) { bench[i].energies += 1 }
        attached = true
    }
    mutating func damage(_ id: Int, amount: Int) throws {
        try require((0...999).contains(amount) && (active?.id == id || bench.contains{$0.id == id}),"Daño no válido.")
        if active?.id == id { active?.damage = amount } else if let i = bench.firstIndex(where:{$0.id == id}) { bench[i].damage = amount }
    }
}
enum DiagnosticZIP {
    static func crc(_ data: Data) -> UInt32 {
        var crc: UInt32 = 0xFFFFFFFF
        for byte in data { crc ^= UInt32(byte); for _ in 0..<8 { crc = (crc >> 1) ^ (crc & 1 == 0 ? 0 : 0xEDB88320) } }
        return crc ^ 0xFFFFFFFF
    }
    static func make(_ text: String) -> Data {
        let name = Data("diagnostico.txt".utf8), bytes = Data(text.utf8), checksum = crc(bytes)
        var output = Data()
        func u16(_ n: UInt16) { output.append(UInt8(n&255)); output.append(UInt8(n>>8)) }
        func u32(_ n: UInt32) { u16(UInt16(n&65535)); u16(UInt16(n>>16)) }
        u32(0x04034B50); u16(20); u16(0); u16(0); u16(0); u16(0); u32(checksum); u32(UInt32(bytes.count)); u32(UInt32(bytes.count)); u16(UInt16(name.count)); u16(0); output += name; output += bytes
        let offset = output.count
        u32(0x02014B50); u16(20); u16(20); u16(0); u16(0); u16(0); u16(0); u32(checksum); u32(UInt32(bytes.count)); u32(UInt32(bytes.count)); u16(UInt16(name.count)); u16(0); u16(0); u16(0); u16(0); u32(0); u32(0); output += name
        let size = output.count-offset
        u32(0x06054B50); u16(0); u16(0); u16(1); u16(1); u32(UInt32(size)); u32(UInt32(offset)); u16(0)
        return output
    }
}
