import Foundation

final class NoRedirect: NSObject, URLSessionTaskDelegate {
    func urlSession(_ session: URLSession, task: URLSessionTask, willPerformHTTPRedirection response: HTTPURLResponse,
                    newRequest request: URLRequest, completionHandler: @escaping (URLRequest?) -> Void) { completionHandler(nil) }
}
struct HTTPResult: Sendable { let data: Data; let status: Int }
final class HTTP {
    static let shared = HTTP()
    let session: URLSession
    init(configuration: URLSessionConfiguration? = nil) {
        let config = configuration ?? URLSessionConfiguration.ephemeral
        config.timeoutIntervalForRequest = 25; config.timeoutIntervalForResource = 100
        config.urlCache = URLCache(memoryCapacity: 16_000_000, diskCapacity: 64_000_000, diskPath: "pocket-images")
        session = URLSession(configuration: config, delegate: NoRedirect(), delegateQueue: nil)
    }
    func get(_ request: URLRequest, limit: Int) async throws -> (Data, Int) {
        try Task.checkCancellation()
        return try await withThrowingTaskGroup(of: HTTPResult.self) { group in
            group.addTask { let pair = try await self.perform(request, limit: limit); return HTTPResult(data: pair.0, status: pair.1) }
            group.addTask { try await Task.sleep(nanoseconds: UInt64(max(0.01, request.timeoutInterval)*1_000_000_000)); throw PocketError("La llamada agotó su tiempo.") }
            defer { group.cancelAll() }
            let result = try await group.next()!
            return (result.data, result.status)
        }
    }
    private func perform(_ request: URLRequest, limit: Int) async throws -> (Data, Int) {
        try Task.checkCancellation()
        let (bytes, response) = try await session.bytes(for: request)
        guard let response = response as? HTTPURLResponse else { throw PocketError("Respuesta HTTP no válida.") }
        try require(response.expectedContentLength <= Int64(limit), "Respuesta demasiado grande.")
        var data = Data(); data.reserveCapacity(min(limit, 32_000))
        for try await byte in bytes {
            try Task.checkCancellation()
            try require(data.count < limit, "Respuesta demasiado grande.")
            data.append(byte)
        }
        return (data, response.statusCode)
    }
    func json(_ url: String, timeout: TimeInterval = 25) async throws -> Any {
        guard let url = URL(string: url), url.scheme == "https" else { throw PocketError("URL no válida.") }
        let (data, code) = try await get(URLRequest(url: url, timeoutInterval: timeout), limit: 2_000_000)
        try require((200...299).contains(code), "La fuente no está disponible. Se conserva la caché.")
        return try JSONSerialization.jsonObject(with: data)
    }
}
enum ConnectedAI {
    static func accepts(provider: String, row: [String:Any], id: String) -> Bool {
        guard id.range(of: "^[a-zA-Z0-9._:/-]{1,120}$", options: .regularExpression) != nil,
            id.range(of: "image|audio|tts|embedding|embed-|realtime|live-|transcrib|moderation|vision-only|deep-research|codex|computer-use|search-preview", options: [.regularExpression,.caseInsensitive]) == nil else { return false }
        if provider == "Gemini" {
            return id.range(of: "^gemini-(2\\.5|3\\.[0-9]+)-(flash(-lite)?|pro)(-preview(-[0-9-]+)?|-[0-9]{3})?$", options: .regularExpression) != nil &&
                (row["supportedGenerationMethods"] as? [String] ?? []).contains("generateContent") && (row["outputTokenLimit"] as? Int ?? 4096) >= 4096
        }
        if provider == "OpenAI" { return id == "chat-latest" || id.range(of: "^gpt-(4o(-mini)?|4\\.1(-mini|-nano)?|5(\\.[0-9]+)?(-mini|-nano)?)(-[0-9]{4}-[0-9]{2}-[0-9]{2})?$", options:.regularExpression) != nil }
        let endpoints = row["supported_endpoints"] as? [String] ?? []
        let outputs = (row["architecture"] as? [String:Any])?["output_modalities"] as? [String] ?? []
        return endpoints.contains("chat/completions") || endpoints.contains("/v1/chat/completions") ? outputs.contains("text") : false
    }
    static func models(profile: AIProfile, key: String, client: HTTP = .shared) async throws -> [String] {
        try require(!key.isEmpty && key.count <= 4096, "Introduce una clave válida.")
        let gemini = profile.provider == "Gemini"
        let endpoint = gemini ? "https://generativelanguage.googleapis.com/v1beta/models" : profile.provider == "OpenAI" ? "https://api.openai.com/v1/models" : String(profile.endpoint.dropLast("/chat/completions".count)) + "/models"
        let c = URLComponents(string:endpoint)
        try require(c?.scheme == "https" && c?.host != nil && c?.user == nil && c?.password == nil && c?.query == nil && c?.fragment == nil && (profile.provider != "Compatible" || profile.endpoint.hasSuffix("/chat/completions")), "URL de modelos no válida.")
        var request = URLRequest(url:c!.url!,timeoutInterval:25)
        request.setValue(gemini ? key : "Bearer \(key)",forHTTPHeaderField:gemini ? "x-goog-api-key" : "Authorization")
        let (data,status) = try await client.get(request,limit:1_000_000)
        try require((200...299).contains(status), "La API no permite listar modelos. Puedes escribirlo manualmente.")
        let root = try JSONSerialization.jsonObject(with:data) as? [String:Any] ?? [:]
        let rows = root[gemini ? "models" : "data"] as? [[String:Any]] ?? []
        return Array(Set(rows.prefix(1000).compactMap { row -> String? in
            guard let raw = row[gemini ? "name" : "id"] as? String else { return nil }
            let id = raw.hasPrefix("models/") ? String(raw.dropFirst(7)) : raw
            return accepts(provider:profile.provider,row:row,id:id) ? id : nil
        })).sorted()
    }
    static func request(profile: AIProfile, key: String, prompt: String, client: HTTP = .shared) async throws -> String {
        try require(!key.isEmpty && key.utf8.count <= 4096 && key.unicodeScalars.allSatisfy { (33...126).contains(Int($0.value)) }, "Clave no válida.")
        try require(profile.model.range(of: "^[a-zA-Z0-9._:/-]{1,120}$", options: .regularExpression) != nil, "Modelo no válido.")
        try require(prompt.utf8.count <= 60_000, "Hay demasiadas cartas. Usa el filtro de tipo.")
        let gemini = profile.provider == "Gemini"
        if gemini { try require(!profile.model.contains(":") && !profile.model.contains("/"), "Usa el nombre de modelo sin prefijos.") }
        let endpoint = gemini ? "https://generativelanguage.googleapis.com/v1beta/models/\(profile.model):generateContent" : profile.provider == "OpenAI" ? "https://api.openai.com/v1/chat/completions" : profile.endpoint
        guard let components = URLComponents(string: endpoint), let url = components.url else { throw PocketError("URL no válida.") }
        try require(components.scheme == "https" && components.host != nil && components.user == nil && components.password == nil && components.query == nil && components.fragment == nil, "Usa una URL HTTPS sin credenciales ni parámetros.")
        try require(profile.provider != "Compatible" || endpoint.hasSuffix("/chat/completions"), "La URL debe terminar en /chat/completions.")
        let body: [String: Any] = gemini ? ["contents": [["role":"user", "parts":[["text":prompt]]]], "generationConfig":["responseMimeType":"application/json", "maxOutputTokens":4096]] : ["model":profile.model, "messages":[["role":"user", "content":prompt]], "max_completion_tokens":4096]
        var request = URLRequest(url: url, timeoutInterval: 90); request.httpMethod = "POST"
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        request.setValue(gemini ? key : "Bearer \(key)", forHTTPHeaderField: gemini ? "x-goog-api-key" : "Authorization")
        request.httpBody = try JSONSerialization.data(withJSONObject: body)
        let (data, code) = try await client.get(request, limit: 100_000)
        try require((200...299).contains(code), code == 429 ? "Cuota alcanzada. No se cambió de proveedor." : "La API rechazó la consulta. Revisa clave y modelo.")
        guard let root = try JSONSerialization.jsonObject(with: data) as? [String: Any] else { throw PocketError("Respuesta no válida.") }
        if gemini {
            guard let candidate = (root["candidates"] as? [[String:Any]])?.first, candidate["finishReason"] as? String == "STOP",
                let content = candidate["content"] as? [String:Any], let parts = content["parts"] as? [[String:Any]] else { throw PocketError("Respuesta incompleta o bloqueada.") }
            return parts.filter { ($0["thought"] as? Bool) != true }.compactMap { $0["text"] as? String }.joined()
        }
        guard let choice = (root["choices"] as? [[String:Any]])?.first, choice["finish_reason"] as? String == "stop",
            let message = choice["message"] as? [String:Any], let text = message["content"] as? String else { throw PocketError("Respuesta incompleta.") }
        return text
    }
}
struct AIReplacement: Codable, Equatable {
    var removedId: String; var addedId: String; var count: Int; var reason: String
}
struct AIProposal: Codable {
    let name: String
    let strategy: String
    let energies: [String]
    let cards: [Reference]
    var replacements: [AIReplacement]? = nil
    var draft: Draft { Draft(name: name, notes: strategy, content: DeckContent(energies: energies, cards: cards)) }
    static func decode(_ data: Data) throws -> Self {
        try require(data.count <= 100_000, "Respuesta IA demasiado grande.")
        guard let root = try JSONSerialization.jsonObject(with: data) as? [String:Any], let cards = root["cards"] as? [[String:Any]] else { throw PocketError("Propuesta IA no válida.") }
        for row in cards { try strictInteger(row["count"]) }
        for row in root["replacements"] as? [[String:Any]] ?? [] { try strictInteger(row["count"]) }
        return try JSONDecoder().decode(Self.self, from: data)
    }
    func validate(catalog: [String:Card], quantities: [String:Int], allowed: Set<String>, action: String, target: DeckContent) throws {
        let content = draft.content
        try content.validateStructure()
        try require(content.total == 20 && !name.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty && name.count <= 100 && strategy.count <= 8000, "Propuesta no válida.")
        try require(content.warnings(catalog: catalog).isEmpty, "Propuesta sin evolución, energías o básicos válidos.")
        for ref in cards {
            try require(ref.id == canonicalID(ref.id) && allowed.contains(ref.id) && ref.count <= quantities[ref.id, default: 0], "La propuesta supera la colección o usa IDs no permitidos.")
        }
        let replacements = replacements ?? []
        for replacement in replacements {
            try require((1...2).contains(replacement.count) && replacement.reason.count <= 1000 && !replacement.reason.trimmingCharacters(in:.whitespacesAndNewlines).isEmpty &&
                replacement.removedId == canonicalID(replacement.removedId) && replacement.addedId == canonicalID(replacement.addedId), "Reemplazo no válido.")
        }
        if action == "Completar faltantes" {
            for ref in target.cards {
                let keep = min(ref.count, quantities[ref.id, default: 0])
                try require((cards.first { $0.id == ref.id }?.count ?? 0) >= keep, "Completar debe conservar las copias disponibles.")
            }
            let old = Dictionary(uniqueKeysWithValues: target.cards.map { ($0.id, $0.count) })
            let next = Dictionary(uniqueKeysWithValues: cards.map { ($0.id, $0.count) })
            let removed = old.mapValues { $0 }.filter { next[$0.key, default:0] < $0.value }.mapValues { $0 }
            var expectedRemoved: [String:Int] = [:], expectedAdded: [String:Int] = [:]
            for (id,count) in removed { expectedRemoved[id] = count-next[id,default:0] }
            for (id,count) in next where count > old[id,default:0] { expectedAdded[id] = count-old[id,default:0] }
            var actualRemoved: [String:Int] = [:], actualAdded: [String:Int] = [:]
            for row in replacements { actualRemoved[row.removedId, default:0] += row.count; actualAdded[row.addedId, default:0] += row.count }
            try require(actualRemoved == expectedRemoved && actualAdded == expectedAdded, "La explicación de reemplazos no coincide con las cartas.")
        } else { try require(replacements.isEmpty, "Esta acción no debe declarar reemplazos.") }
    }
}
struct MetaDeck: Codable, Identifiable, Equatable {
    var name: String; var count: Int; var wins: Int; var losses: Int; var ties: Int
    var cards: [String:Int]; var energies: [String]; var tournamentId: String
    var id: String { name }
}
struct MetaSnapshot: Codable, Equatable { var updated: Int64; var tournaments: Int; var players: Int; var decks: [MetaDeck]; var skipped: Int }
enum MetaRefreshPolicy {
    static func shouldRefresh(updated: Int64?, lastAttempt: Int64, now: Int64) -> Bool {
        if lastAttempt > 0 && (0..<900_000).contains(now-lastAttempt) { return false }
        guard let updated else { return true }
        return now-updated >= 21_600_000 || updated > now
    }
}
enum Tournaments {
    static let source = "https://play.limitlesstcg.com/api/"
    static func refresh(client: HTTP = .shared, timeout: UInt64 = 90_000_000_000, progress: @escaping @Sendable (Int,Int) async -> Void) async throws -> MetaSnapshot {
        try await withThrowingTaskGroup(of: MetaSnapshot.self) { group in
            group.addTask { try await fetch(client:client,progress: progress) }
            group.addTask { try await Task.sleep(nanoseconds: timeout); throw PocketError("La actualización superó 90 segundos.") }
            defer { group.cancelAll() }
            return try await group.next()!
        }
    }
    private static func fetch(client: HTTP, progress: @escaping @Sendable (Int,Int) async -> Void) async throws -> MetaSnapshot {
        try Task.checkCancellation()
        guard let rows = try await client.json(source + "tournaments?game=POCKET&limit=12") as? [[String:Any]] else { throw PocketError("Lista de torneos no válida.") }
        var groups: [String:MetaDeck] = [:], included = 0, players = 0, skipped = 0
        let tournaments = Array(rows.prefix(12)), now = Date()
        let formatter = ISO8601DateFormatter()
        let fractional = ISO8601DateFormatter(); fractional.formatOptions = [.withInternetDateTime,.withFractionalSeconds]
        for (index,t) in tournaments.enumerated() {
            try Task.checkCancellation(); await progress(index+1,tournaments.count)
            guard t["game"] as? String == "POCKET", let dateText = t["date"] as? String,
                let date = formatter.date(from: dateText) ?? fractional.date(from: dateText), date <= now && now.timeIntervalSince(date) <= 30*86400,
                let id = t["id"] as? String, id.range(of:"^[a-zA-Z0-9]{1,60}$", options:.regularExpression) != nil else { skipped += 1; continue }
            guard let d = try await client.json(source + "tournaments/\(id)/details") as? [String:Any],
                d["game"] as? String == "POCKET", d["id"] as? String == id, d["isPublic"] as? Bool == true,
                d["decklists"] as? Bool == true, (d["specialRules"] as? [Any] ?? []).isEmpty,
                (d["bannedCards"] as? [Any] ?? []).isEmpty, ["", "STANDARD", "null"].contains(d["format"] as? String ?? "") else { skipped += 1; continue }
            guard let standings = try await client.json(source + "tournaments/\(id)/standings") as? [[String:Any]], !standings.isEmpty,
                standings.allSatisfy({ ($0["placing"] as? Int ?? 0) > 0 }) else { skipped += 1; continue }
            included += 1
            for row in standings {
                guard let list = row["decklist"] as? [String:Any] else { continue }
                var cards: [String:Int] = [:]
                for section in ["pokemon","trainer"] {
                    for c in list[section] as? [[String:Any]] ?? [] {
                        guard let set = c["set"] as? String, set.range(of:"^[A-Za-z0-9-]{1,12}$",options:.regularExpression) != nil,
                            let number = c["number"], let id = try? canonicalID("\(set)-\(number)"), let count = c["count"] as? Int else { continue }
                        cards[id, default: 0] += count
                    }
                }
                let rawEnergies = list["energy"] as? [String] ?? []
                let mapping = Dictionary(uniqueKeysWithValues: zip(["Grass","Fire","Water","Lightning","Psychic","Fighting","Darkness","Metal"],energyNames))
                let energies = rawEnergies.compactMap { mapping[$0] }
                guard cards.values.reduce(0,+) == 20, cards.values.allSatisfy({ (1...2).contains($0) }), (1...3).contains(energies.count), Set(energies).count == rawEnergies.count else { skipped += 1; continue }
                let name = String(((row["deck"] as? [String:Any])?["name"] as? String ?? "Sin clasificar").prefix(100))
                let record = row["record"] as? [String:Any] ?? [:]
                var deck = groups[name] ?? MetaDeck(name: name,count:0,wins:0,losses:0,ties:0,cards:cards,energies:energies,tournamentId:id)
                deck.count += 1; deck.wins += max(0,record["wins"] as? Int ?? 0); deck.losses += max(0,record["losses"] as? Int ?? 0); deck.ties += max(0,record["ties"] as? Int ?? 0)
                groups[name] = deck; players += 1
            }
        }
        try require(players > 0,"No hay resultados completos recientes. Se conserva la caché.")
        try Task.checkCancellation()
        return MetaSnapshot(updated: milliseconds(),tournaments:included,players:players,decks:groups.values.sorted { $0.count > $1.count },skipped:skipped)
    }
}
