import SwiftUI
import UniformTypeIdentifiers

struct MoreView: View {
    var body: some View {
        List {
            Section("Herramientas") {
                NavigationLink("Meta de torneos") { MetaView() }
                NavigationLink("Sobres recomendados") { PacksView() }
                NavigationLink("Canjes y comparación CSV") { TradesView() }
                NavigationLink("Simulador local") { SandboxView() }
                NavigationLink("Calculadora de probabilidades") { CalculatorView() }
                NavigationLink("Filtros de efectos") { EffectsView() }
                NavigationLink("Borrador local por tipo") { StarterView() }
            }
            Section {
                NavigationLink("Ajustes y respaldos") { SettingsView() }
                NavigationLink("Tutoriales") { HelpView() }
                NavigationLink("Diagnóstico") { DiagnosticsView() }
            }
            Section { Text("Pocket iOS · 0.1.0 (1)").font(.caption).foregroundStyle(.secondary) }
        }.navigationTitle("Más")
    }
}
struct BackupPreview: Identifiable { let id = UUID(); let backup: Backup }
struct SettingsView: View {
    @EnvironmentObject var store: Store
    @State var importer = false
    @State var csvImporter = false
    @State var preview: BackupPreview?
    @State var share: SharedFile?
    var body: some View {
        Form {
            Section("Apariencia") {
                Picker("Tema",selection:Binding(get:{store.state.backup.preferences.theme},set:{ theme in Task { _ = await store.commit { $0.backup.preferences.theme = theme } } })) {
                    Text("Sistema").tag("system"); Text("Claro").tag("light"); Text("Oscuro").tag("dark")
                }
                Picker("Avatar",selection:Binding(get:{store.state.backup.preferences.avatar ?? avatarIDs[0]},set:{ id in Task { _ = await store.commit { $0.backup.preferences.avatar = id } } })) {
                    ForEach(Array(avatarIDs.enumerated()),id:\.element) { index,id in HStack { AvatarView(id:id); Text(["Rojo","Turquesa","Naranja","Violeta","Verde","Azul"][index]) }.tag(id) }
                }
            }
            Section("Respaldo completo Android ↔ iOS") {
                Button("Guardar respaldo JSON") { do { var backup = store.state.backup; backup.createdAt = milliseconds(); share = try temporaryFile(backup.data(),name:"Pocket-respaldo-v1.json") } catch { store.error = "No se pudo exportar el respaldo." } }
                Button("Importar respaldo JSON") { importer = true }
                Text("Se validará el archivo y verás un resumen antes de importar. Las cartas y mazos no incluidos se conservan. Las claves no se trasladan.").font(.caption)
            }
            Section("CSV de colección") {
                Button("Exportar CSV") {
                    let rows = store.catalog.map { store.inventory($0) }
                    do { share = try temporaryFile(Data(CollectionCSV.export(rows).utf8),name:"Pocket-coleccion.csv") } catch { store.error = "No se pudo exportar CSV." }
                }
                Button("Importar CSV") { csvImporter = true }
            }
            Section { Text("Pocket iOS · 0.1.0 (1)") }
        }.navigationTitle("Ajustes").disabled(store.writing)
        .fileImporter(isPresented:$importer,allowedContentTypes:[.json]) { result in importFile(result,csv:false) }
        .fileImporter(isPresented:$csvImporter,allowedContentTypes:[.commaSeparatedText,.plainText]) { result in importFile(result,csv:true) }
        .sheet(item:$share) { ActivitySheet(url:$0.url) }
        .sheet(item:$preview) { item in NavigationStack {
            Form {
                Section("Archivo validado") {
                    Text("\(item.backup.inventory.count) registros de inventario")
                    Text("\(item.backup.inventory.reduce(0){$0+$1.quantity}) copias")
                    Text("\(item.backup.decks.count) mazos")
                    Text("Tema: \(item.backup.preferences.theme)")
                }
                Section { Button("Importar y conservar lo no incluido") { Task { await store.importBackup(item.backup); preview = nil } }.disabled(store.writing) }
            }.navigationTitle("Revisar importación").toolbar { Button("Cancelar") { preview = nil } }
        } }
    }
    func importFile(_ result: Result<URL,Error>, csv: Bool) {
        Task {
            do {
                let url = try result.get(), access = url.startAccessingSecurityScopedResource()
                defer { if access { url.stopAccessingSecurityScopedResource() } }
                let data = try await Task.detached {
                    let size = try url.resourceValues(forKeys:[.fileSizeKey]).fileSize ?? 0
                    try require(size <= 8_000_000,"Archivo demasiado grande.")
                    return try Data(contentsOf:url)
                }.value
                if csv {
                    guard let text = String(data:data,encoding:.utf8) else { throw PocketError("CSV no es UTF-8.") }
                    let rows = try await Task.detached { try CollectionCSV.parse(text) }.value
                    var backup = Backup(); backup.preferences = store.state.backup.preferences; backup.preferences.avatar = nil
                    backup.inventory = rows.map { row in
                        let old = store.state.backup.inventory.first { $0.id == row.id }
                        return Inventory(id:row.id,name:row.name,rarity:row.rarity,pack:store.byID[row.id]?.packs.first ?? "",quantity:row.quantity,wishlist:row.wishlist ?? old?.wishlist ?? false,acquiredAt:old?.acquiredAt ?? milliseconds())
                    }
                    preview = BackupPreview(backup:try Backup.decode(backup.data()))
                } else { preview = BackupPreview(backup:try await Task.detached { try Backup.decode(data) }.value) }
            } catch { store.error = "Archivo rechazado: \(error.localizedDescription)" }
        }
    }
}
struct PacksView: View {
    @EnvironmentObject var store: Store
    var body: some View {
        let inventory = Dictionary(uniqueKeysWithValues:store.state.backup.inventory.map { ($0.id,$0) })
        let targets = Set(store.state.draft.content.cards.filter { store.quantity($0.id) < $0.count }.map(\.id))
        List {
            Section { Text("Orden por cobertura: faltantes + deseos × 3 + objetivos del borrador × 5. No predice tasas ni resultados de apertura.").font(.caption) }
            ForEach(Insights.coverage(catalog:store.catalog,inventory:inventory,targets:targets).filter{$0.score > 0}) { pack in
                Section("\(pack.set) · \(pack.pack)") {
                    Text("\(pack.missing) faltantes · \(pack.wishes) deseos · \(pack.score) puntos")
                    ForEach(pack.targets,id:\.self) { Text($0) }
                }
            }
        }.navigationTitle("Sobres")
    }
}
struct TradesView: View {
    @EnvironmentObject var store: Store
    @State var importing = false
    @State var peer: [CSVRow] = []
    @State var message = ""
    var body: some View {
        List {
            Section { Button("Comparar CSV de otra colección") { importing = true }; Text("Comparación local. Revisa las condiciones de canje del juego; no modifica ninguna colección.").font(.caption); Text(message) }
            Section("Puedo ofrecer") { ForEach(peer.filter { $0.quantity == 0 && store.quantity($0.id) > 1 },id:\.id) { Text("\($0.id) · \($0.name) · \(store.quantity($0.id)-1) copias sobrantes") } }
            Section("Puedo recibir") { ForEach(peer.filter { $0.quantity > 1 && store.quantity($0.id) == 0 },id:\.id) { Text("\($0.id) · \($0.name) · \($0.quantity-1) copias sobrantes") } }
        }.navigationTitle("Canjes")
        .fileImporter(isPresented:$importing,allowedContentTypes:[.commaSeparatedText,.plainText]) { result in Task {
            do { let url = try result.get(), access = url.startAccessingSecurityScopedResource(); defer { if access { url.stopAccessingSecurityScopedResource() } }
                peer = try await Task.detached { let size = try url.resourceValues(forKeys:[.fileSizeKey]).fileSize ?? 0; try require(size <= 8_000_000,"CSV grande."); return try CollectionCSV.parse(String(contentsOf:url,encoding:.utf8)) }.value
                message = "\(peer.count) cartas comparadas."
            } catch { message = "CSV rechazado. Se conserva la comparación anterior." }
        } }
    }
}
struct CalculatorView: View {
    @State var rate = ""
    @State var attempts = 10
    @State var targets = 2
    @State var draws = 5
    var body: some View {
        Form {
            Section("Conseguir una carta") {
                Text("Introduce la probabilidad total por sobre que muestra el juego, no la tasa de una ranura.")
                TextField("Probabilidad por sobre (%)",text:$rate).keyboardType(.decimalPad)
                Stepper("\(attempts) sobres",value:$attempts,in:1...500)
                if let p = Double(rate.replacingOccurrences(of:",",with:".")), let chance = try? Insights.cumulative(p/100,attempts:attempts) {
                    Text("Al menos una: \(chance*100,specifier:"%.2f") %")
                    if p > 0 { Text("Promedio: \(100/p,specifier:"%.1f") sobres") }
                }
                Text("Aperturas independientes y tasa constante. El promedio no garantiza el resultado.").font(.caption)
            }
            Section("Robar una carta objetivo") {
                Stepper("\(targets) copias objetivo",value:$targets,in:0...20)
                Stepper("\(draws) cartas robadas",value:$draws,in:0...20)
                Text("Al menos una: \((try? Insights.draw(targets:targets,draws:draws)) ?? 0,specifier:"%.4f")")
                Text("Mazo de 20 cartas sin reemplazo. No incluye garantía de básico, búsquedas ni habilidades.").font(.caption)
            }
        }.navigationTitle("Probabilidades")
    }
}
struct StarterView: View {
    @EnvironmentObject var store: Store
    @State var type = "Agua"
    @State var confirm = false
    @State var editor = false
    var body: some View {
        Form {
            Picker("Energía",selection:$type) { ForEach(energyNames,id:\.self) { Text($0) } }
            Text("Construye un borrador con tus cartas sin descontarlas. Puede quedar incompleto.")
            Button("Crear borrador local") { confirm = true }
        }.navigationTitle("Borrador por tipo")
        .confirmationDialog("Reemplazar borrador actual",isPresented:$confirm) {
            Button("Crear") { Task { do {
                let draft = try Insights.starter(catalog:store.catalog,inventory:Dictionary(uniqueKeysWithValues:store.state.backup.inventory.map{($0.id,$0.quantity)}),type:type)
                if await store.commit({$0.draft = draft}) { editor = true }
            } catch { store.error = error.localizedDescription } } }
        }.sheet(isPresented:$editor) { NavigationStack { EditorView() } }
    }
}
struct EffectsView: View {
    @EnvironmentObject var store: Store
    @State var rows: [CardRules] = []
    @State var search = ""
    @State var role = "Todos"
    @State var language = "es"
    @State var minHP = ""
    @State var maxHP = ""
    @State var element = ""
    @State var message = ""
    @State var job: Task<Void,Never>?
    var body: some View {
        List {
            Section {
                Text("Filtros heurísticos sobre textos TCGdex guardados; no son dictámenes oficiales.").font(.caption)
                Picker("Efecto",selection:$role) { ForEach(["Todos","Robar","Curar","Energía","Milling","Mover"],id:\.self) { Text($0) } }
                Picker("Idioma",selection:$language) { Text("Español").tag("es"); Text("Inglés").tag("en"); Text("Japonés").tag("ja") }
                TextField("PS mínimo",text:$minHP).keyboardType(.numberPad)
                TextField("PS máximo",text:$maxHP).keyboardType(.numberPad)
                TextField("Tipo exacto TCGdex (ej. Grass)",text:$element)
                Button("Indexar hasta 25 cartas de mi colección") { index() }.disabled(job != nil)
                if job != nil { Button("Cancelar") { job?.cancel() } }
                Text(message)
            }
            ForEach(rows.filter { rules in
                (rules.requestedLanguage ?? rules.language) == language && (role == "Todos" || rules.roles.contains(role)) &&
                (search.isEmpty || rules.text.localizedCaseInsensitiveContains(search)) && (element.isEmpty || rules.element == element) &&
                (Int(minHP) == nil || (rules.hp ?? -1) >= Int(minHP)!) && (Int(maxHP) == nil || (rules.hp ?? Int.max) <= Int(maxHP)!)
            },id:\.cacheKey) { rules in
                if let card = store.byID[rules.id] { NavigationLink { CardDetailView(card:card) } label: { VStack(alignment:.leading) { Text(card.name); Text(rules.text).lineLimit(3).font(.caption); Text(rules.source).font(.caption) } } }
            }
        }.navigationTitle("Efectos").searchable(text:$search)
        .task { rows = await RulesCache.shared.all() }.onDisappear { job?.cancel() }
    }
    func index() {
        guard job == nil else { return }
        let ids = Array(store.state.backup.inventory.filter{$0.quantity > 0}.map(\.id).prefix(25))
        job = Task {
            defer { job = nil }
            for (n,id) in ids.enumerated() {
                if Task.isCancelled { break }
                _ = try? await RulesCache.shared.details(id,language:language)
                message = "Indexando \(n+1)/\(ids.count)"
            }
            rows = await RulesCache.shared.all(); message = "\(rows.count) textos guardados."
        }
    }
}
struct DiagnosticsView: View {
    @EnvironmentObject var store: Store
    @State var share: SharedFile?
    var body: some View {
        Form {
            Text(store.diagnostic()).font(.system(.body,design:.monospaced)).textSelection(.enabled)
            Button("Compartir TXT") { export(false) }
            Button("Compartir ZIP") { export(true) }
        }.navigationTitle("Diagnóstico").sheet(item:$share) { ActivitySheet(url:$0.url) }
    }
    func export(_ zip: Bool) {
        do { let text = store.diagnostic(); share = try temporaryFile(zip ? DiagnosticZIP.make(text) : Data(text.utf8),name:zip ? "Pocket-diagnostico.zip" : "Pocket-diagnostico.txt") }
        catch { store.error = "No se pudo exportar el diagnóstico." }
    }
}
struct HelpView: View {
    var body: some View {
        List {
            NavigationLink("Colección y filtros") { HelpArticle(title:"Colección",text:"Busca nombres o códigos. Filtros reúne estado, expansión, rareza y vista. Guarda cantidades entre 0 y 99999. Deseos puede incluir cartas que ya posees. Imágenes ausentes no eliminan cartas.") }
            NavigationLink("Mazos y energías") { HelpArticle(title:"Mazos",text:"Añade cartas al borrador desde Colección o el editor. Máximo dos copias por nombre, también entre artes. Guarda borradores incompletos. Revisa básicos, preevoluciones y entre una y tres energías. Guardar no consume tu colección.") }
            NavigationLink("QR y compartir") { HelpArticle(title:"QR",text:"Necesitas 20 cartas y energías admitidas. El PNG usa payload binario del juego, blanco y degradado turquesa-azul. Se decodifica antes de compartir. La aceptación por todas las versiones del juego requiere pruebas reales.") }
            NavigationLink("IA y privacidad") { HelpArticle(title:"IA",text:"Configura tu proveedor, modelo y clave. Cada envío requiere confirmación y puede consumir cuota. Completar conserva copias disponibles; Mejorar permite reemplazos. No hay reintentos ni cambio de proveedor automáticos. Revisa cada propuesta antes de abrirla.") }
            NavigationLink("Migrar desde Android") { HelpArticle(title:"Respaldos",text:"Exporta el JSON completo desde Android y ábrelo en Ajustes de iOS. Verifica el resumen antes de importar. Lo no incluido se conserva. Las claves cifradas con Android Keystore se configuran otra vez en iOS. No uses el CSV histórico como colección actual.") }
            NavigationLink("Simulador y herramientas") { HelpArticle(title:"Práctica",text:"El simulador es un tablero manual local. No reproduce el motor oficial ni todos los efectos. Sobres ordena cobertura, no probabilidades oficiales. Canjes compara CSV localmente; verifica las restricciones actuales del juego.") }
        }.navigationTitle("Tutoriales")
    }
}
struct HelpArticle: View {
    let title: String; let text: String
    var body: some View { ScrollView { Text(text).frame(maxWidth:.infinity,alignment:.leading).padding() }.navigationTitle(title) }
}
