import Foundation
import SQLite3
import Security
import Combine

// One versioned document in SQLite: draft, inventory and preferences commit together.
// FULL synchronous WAL and SQLite transactions avoid partial multi-domain restores.
actor Database {
    let url: URL
    init(url: URL) { self.url = url }
    private func open() throws -> OpaquePointer {
        try FileManager.default.createDirectory(at: url.deletingLastPathComponent(), withIntermediateDirectories: true)
        var db: OpaquePointer?
        guard sqlite3_open(url.path, &db) == SQLITE_OK, let db else { if let db { sqlite3_close(db) }; throw PocketError("No se pudo abrir el almacenamiento.") }
        do {
            try execute(db, "PRAGMA journal_mode=WAL; PRAGMA synchronous=FULL; PRAGMA busy_timeout=5000; CREATE TABLE IF NOT EXISTS state (id INTEGER PRIMARY KEY CHECK(id=1), current BLOB NOT NULL, previous BLOB);")
            return db
        } catch { sqlite3_close(db); throw error }
    }
    private func execute(_ db: OpaquePointer, _ sql: String) throws {
        try require(sqlite3_exec(db, sql, nil, nil, nil) == SQLITE_OK, "La escritura local falló. Se conservaron los datos anteriores.")
    }
    func load() throws -> LocalState {
        let db = try open(); defer { sqlite3_close(db) }
        var stmt: OpaquePointer?
        try require(sqlite3_prepare_v2(db, "SELECT current, previous FROM state WHERE id=1", -1, &stmt, nil) == SQLITE_OK, "No se pudo leer el almacenamiento.")
        defer { sqlite3_finalize(stmt) }
        let status = sqlite3_step(stmt)
        if status == SQLITE_DONE { return LocalState() }
        try require(status == SQLITE_ROW, "No se pudo leer el almacenamiento.")
        for column in [Int32(0), Int32(1)] {
            if let bytes = sqlite3_column_blob(stmt, column) {
                let data = Data(bytes: bytes, count: Int(sqlite3_column_bytes(stmt, column)))
                if let state = try? Self.validatedState(data) { return state }
            }
        }
        throw PocketError("Datos locales dañados. No se inicializó una colección vacía sobre ellos.")
    }
    private static func validatedState(_ data: Data) throws -> LocalState {
        let state = try JSONDecoder().decode(LocalState.self, from: data)
        try require(state.schema == 1, "Esquema local no compatible.")
        _ = try Backup.decode(state.backup.data())
        try state.draft.content.validateStructure()
        return state
    }
    func save(_ state: LocalState) throws {
        let data = try JSONEncoder().encode(state)
        let db = try open(); defer { sqlite3_close(db) }
        try execute(db, "BEGIN IMMEDIATE")
        do {
            var stmt: OpaquePointer?
            try require(sqlite3_prepare_v2(db, "INSERT INTO state(id,current,previous) VALUES(1,?,NULL) ON CONFLICT(id) DO UPDATE SET previous=current, current=excluded.current", -1, &stmt, nil) == SQLITE_OK, "No se pudo preparar la escritura.")
            defer { sqlite3_finalize(stmt) }
            let transient = unsafeBitCast(-1, to: sqlite3_destructor_type.self)
            let bound = data.withUnsafeBytes { sqlite3_bind_blob(stmt, 1, $0.baseAddress, Int32(data.count), transient) }
            try require(bound == SQLITE_OK && sqlite3_step(stmt) == SQLITE_DONE, "No se pudo guardar. Los datos anteriores se conservan.")
            try execute(db, "COMMIT")
        } catch { try? execute(db, "ROLLBACK"); throw error }
    }
}
enum Secrets {
    private static let service = "Pocket.iOS.AI"
    static func read(_ id: String) throws -> String {
        var result: CFTypeRef?
        let status = SecItemCopyMatching([kSecClass: kSecClassGenericPassword, kSecAttrService: service,
            kSecAttrAccount: id, kSecReturnData: true, kSecMatchLimit: kSecMatchLimitOne] as CFDictionary, &result)
        if status == errSecItemNotFound { return "" }
        guard status == errSecSuccess, let data = result as? Data, let text = String(data: data, encoding: .utf8) else { throw PocketError("No se pudo leer Keychain.") }
        return text
    }
    static func write(_ key: String, id: String) throws {
        let query = [kSecClass: kSecClassGenericPassword, kSecAttrService: service, kSecAttrAccount: id] as [CFString: Any]
        let attrs = [kSecValueData: Data(key.utf8), kSecAttrAccessible: kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly] as [CFString: Any]
        var status = SecItemUpdate(query as CFDictionary, attrs as CFDictionary)
        if status == errSecItemNotFound { var insert = query; attrs.forEach { insert[$0.key] = $0.value }; status = SecItemAdd(insert as CFDictionary, nil) }
        try require(status == errSecSuccess, "No se pudo guardar Keychain. El perfil anterior se conserva.")
    }
    static func remove(_ id: String) throws {
        let status = SecItemDelete([kSecClass: kSecClassGenericPassword, kSecAttrService: service, kSecAttrAccount: id] as CFDictionary)
        try require(status == errSecSuccess || status == errSecItemNotFound, "No se pudo eliminar la clave de Keychain.")
    }
}
@MainActor final class Store: ObservableObject {
    @Published private(set) var state = LocalState()
    @Published private(set) var catalog: [Card] = []
    @Published private(set) var ready = false
    @Published private(set) var writing = false
    @Published var error: String?
    private(set) var byID: [String: Card] = [:]
    private(set) var identities: [String: QRIdentity] = [:]
    let database: Database
    private var writeWaiters: [CheckedContinuation<Void, Never>] = []
    init(url: URL? = nil) {
        let root = FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask)[0]
        database = Database(url: url ?? root.appendingPathComponent("Pocket/state.sqlite"))
    }
    func start() async {
        guard !ready else { return }
        do {
            let values = try await Task.detached { () -> ([Card], [QRIdentity]) in
                guard let catalogURL = Bundle.main.url(forResource: "pocket-catalog", withExtension: "json"),
                    let qrURL = Bundle.main.url(forResource: "pocket-qr-entities", withExtension: "json") else { throw PocketError("Faltan recursos locales.") }
                return (try JSONDecoder().decode(Catalog.self, from: Data(contentsOf: catalogURL)).cards,
                    try JSONDecoder().decode([QRIdentity].self, from: Data(contentsOf: qrURL)))
            }.value
            catalog = values.0; byID = Dictionary(uniqueKeysWithValues: catalog.map { ($0.id, $0) })
            identities = Dictionary(uniqueKeysWithValues: values.1.map { ($0.id, $0) })
            state = try await database.load()
            for row in state.backup.inventory where byID[row.id] == nil {
                let card = Card(id: row.id, name: row.name, rarity: row.rarity, packs: [row.pack], category: "unknown", element: "", stage: "unknown", evolvesFrom: "")
                catalog.append(card); byID[card.id] = card
            }
            ready = true
        } catch { self.error = "No se pudieron cargar los datos. \(error.localizedDescription)" }
    }
    func commit(_ mutate: (inout LocalState) throws -> Void) async -> Bool {
        guard ready else { return false }
        while writing { await withCheckedContinuation { writeWaiters.append($0) } }
        if Task.isCancelled { return false }
        writing = true
        defer {
            writing = false
            let pending = writeWaiters; writeWaiters.removeAll()
            pending.forEach { $0.resume() }
        }
        do {
            var next = state
            try mutate(&next)
            try await database.save(next)
            state = next
            return true
        } catch { self.error = error.localizedDescription; return false }
    }
    func inventory(_ card: Card) -> Inventory {
        state.backup.inventory.first { $0.id == card.id } ?? Inventory(id: card.id, name: card.name, rarity: card.rarity, pack: card.packs.first ?? "", quantity: 0, wishlist: false, acquiredAt: milliseconds())
    }
    func quantity(_ id: String) -> Int { state.backup.inventory.first { $0.id == id }?.quantity ?? 0 }
    func update(_ row: Inventory) async {
        _ = await commit { state in
            try require((0...99999).contains(row.quantity), "Cantidad no válida.")
            state.backup.inventory.removeAll { $0.id == row.id }; state.backup.inventory.append(row)
        }
    }
    func importBackup(_ backup: Backup) async { _ = await commit { $0.merge(backup) }; if ready { ready = false; await start() } }
    func saveDraft() async {
        _ = await commit { state in
            let draft = state.draft
            try require(!draft.name.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty, "Escribe un nombre.")
            let serialized = try draft.content.serialized()
            state.backup.decks.append(SavedDeck(name: draft.name, archetype: "Manual", strategy: draft.notes, cards: serialized, total: draft.content.total, createdAt: milliseconds()))
        }
    }
    func addToDraft(_ card: Card, delta: Int) async {
        _ = await commit { state in
            var rows = state.draft.content.cards
            let current = rows.first { $0.id == card.id }?.count ?? 0
            if delta > 0 {
                let sameName = rows.reduce(0) { $0 + (self.byID[$1.id]?.rulesName == card.rulesName ? $1.count : 0) }
                try require(state.draft.content.total < 20 && sameName < 2, "Máximo 20 cartas y dos copias por nombre.")
            }
            rows.removeAll { $0.id == card.id }
            if current + delta > 0 { rows.append(Reference(id: card.id, count: current + delta)) }
            state.draft.content.cards = rows
        }
    }
    func diagnostic() -> String {
        "Pocket iOS 0.1.0 (1)\nCatálogo: \(catalog.count)\nRegistros de inventario: \(state.backup.inventory.count)\nMazos: \(state.backup.decks.count)\nBorrador: \(state.draft.content.total)/20\nPerfiles: \(state.profiles.count)\nMeta en caché: \(state.meta != nil)\nNo incluye claves, URLs personalizadas, nombres privados, prompts ni cuerpos HTTP.\n"
    }
}
