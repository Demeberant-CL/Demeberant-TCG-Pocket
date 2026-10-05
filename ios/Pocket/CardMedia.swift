import Foundation
import UIKit

func tcgdexID(_ id: String) -> String {
    let split = id.lastIndex(of: "-")!, set = String(id[..<split]), number = String(id[id.index(after: split)...])
    let adjusted: String
    if set == "PROMO-A" { adjusted = "P-A" }
    else if set == "PROMO-B" { adjusted = "P-B" }
    else { adjusted = set.replacingOccurrences(of: "([AB][0-9]+)([A-Z]+)$", with: "$1$2", options: .regularExpression)
        // Uppercase set prefix, lowercase optional suffix, matching Android assets.
    }
    var prefix = "", suffix = "", seenDigit = false, endedDigits = false
    for character in adjusted {
        if character.isNumber { seenDigit = true }
        else if seenDigit { endedDigits = true }
        if endedDigits && !adjusted.hasPrefix("P-") { suffix.append(contentsOf: String(character).lowercased()) } else { prefix.append(character) }
    }
    return prefix + suffix + "-" + number
}
actor CardImages {
    static let shared = CardImages()
    var missing: [URL:Date] = [:]
    var community: [String:URL] = [:]
    var initialized = false
    func candidates(_ id: String, high: Bool) -> [URL] {
        if !initialized {
            initialized = true
            if let url = Bundle.main.url(forResource: "pocket-image-index", withExtension: "json"), let data = try? Data(contentsOf: url),
                let root = try? JSONSerialization.jsonObject(with:data) as? [String:Any], let revision = root["revision"] as? String,
                let paths = root["paths"] as? [String] {
                for path in paths {
                    let parts = path.replacingOccurrences(of: ".webp",with: "").split(separator: "/")
                    if parts.count == 2, let key = try? canonicalID("\(parts[0])-\(parts[1])") {
                        community[key] = URL(string:"https://cdn.jsdelivr.net/gh/flibustier/pokemon-tcg-exchange@\(revision)/public/images/cards-by-set/\(path)")
                    }
                }
            }
        }
        let api = tcgdexID(id), split = api.lastIndex(of: "-")!, set = api[..<split], number = api[api.index(after:split)...]
        var result: [URL] = []
        for lang in ["es","en"] { for size in high ? ["high","low"] : ["low"] {
            if let url = URL(string:"https://assets.tcgdex.net/\(lang)/tcgp/\(set)/\(number)/\(size).webp") { result.append(url) }
        } }
        if let url = community[id] { result.append(url) }
        return result
    }
    func imageData(_ id: String, high: Bool = false, retry: Bool = false) async throws -> Data {
        let urls = candidates(id,high:high)
        if retry { urls.forEach { missing.removeValue(forKey:$0) } }
        missing = missing.filter { Date().timeIntervalSince($0.value) < 86400 }
        for url in urls where missing[url] == nil {
            let (data,code) = try await HTTP.shared.get(URLRequest(url:url),limit:4_000_000)
            if code == 404 { missing[url] = Date(); if missing.count > 4096 { missing.removeValue(forKey:missing.keys.first!) }; continue }
            try require((200...299).contains(code),"Imagen temporalmente no disponible.")
            try require(UIImage(data:data) != nil,"Formato de imagen no válido.")
            return data
        }
        throw PocketError("No hay imagen disponible. La carta se conserva.")
    }
}
struct CardRules: Codable, Identifiable {
    var id: String; var language: String; var hp: Int?; var text: String; var updated: Int64
    var source: String { "TCGdex · \(language)" }
    var roles: [String] {
        let value = text.folding(options:[.diacriticInsensitive,.caseInsensitive],locale:Locale(identifier:"en_US_POSIX"))
        var roles: [String] = []
        if value.range(of:"\\b(draw|roba|robar|robe)\\b",options:.regularExpression) != nil { roles.append("Robar") }
        if value.range(of:"\\b(heal|cura|curar|curate|soigne|heals)\\b",options:.regularExpression) != nil { roles.append("Curar") }
        if ["attach","une","unir","unida"].contains(where:value.contains) && ["energy","energia"].contains(where:value.contains) { roles.append("Energía") }
        if ["opponent","rival"].contains(where:value.contains) && ["deck","baraja","mazo"].contains(where:value.contains) && ["discard","descarta"].contains(where:value.contains) { roles.append("Milling") }
        if value.range(of:"\\b(switch|cambia|cambiar|retreat|retirada)\\b",options:.regularExpression) != nil { roles.append("Mover") }
        return roles
    }
}
actor RulesCache {
    static let shared = RulesCache()
    private let url = FileManager.default.urls(for:.cachesDirectory,in:.userDomainMask)[0].appendingPathComponent("pocket-rules.json")
    private var loaded = false
    private var entries: [String:CardRules] = [:]
    private func load() {
        if !loaded { entries = (try? JSONDecoder().decode([String:CardRules].self,from:Data(contentsOf:url))) ?? [:]; loaded = true }
    }
    func all() -> [CardRules] { load(); return Array(entries.values) }
    func details(_ id: String, retry: Bool = false) async throws -> CardRules {
        load()
        let cached = entries[id]
        if !retry, let cached, milliseconds()-cached.updated < 86400*1000 { return cached }
        do {
            for language in ["es","en"] {
                let (data,code) = try await HTTP.shared.get(URLRequest(url:URL(string:"https://api.tcgdex.net/v2/\(language)/cards/\(tcgdexID(id))")!),limit:256_000)
                if code == 404 && language == "es" { continue }
                try require((200...299).contains(code),"Detalles no disponibles.")
                guard let root = try JSONSerialization.jsonObject(with:data) as? [String:Any], (root["id"] as? String)?.lowercased() == tcgdexID(id).lowercased() else { throw PocketError("Identidad de carta incorrecta.") }
                var text = [root["effect"] as? String ?? ""]
                for field in ["attacks","abilities"] { for row in root[field] as? [[String:Any]] ?? [] {
                    text.append([row["name"],row["damage"],row["effect"]].compactMap { $0.map { String(describing:$0) } }.joined(separator:" · "))
                } }
                let entry = CardRules(id:id,language:language,hp:root["hp"] as? Int,text:text.filter { !$0.isEmpty }.joined(separator:"\n"),updated:milliseconds())
                entries[id] = entry
                if entries.count > 512, let oldest = entries.values.min(by: { $0.updated < $1.updated }) { entries.removeValue(forKey:oldest.id) }
                try JSONEncoder().encode(entries).write(to:url,options:.atomic)
                return entry
            }
            throw PocketError("Detalles no disponibles.")
        } catch is CancellationError { throw CancellationError() }
        catch { if let cached { return cached }; throw PocketError("No se pudieron consultar los detalles.") }
    }
}
