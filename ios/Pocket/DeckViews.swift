import SwiftUI

struct DeckLibraryView: View {
    @EnvironmentObject var store: Store
    @State var editor = false
    @State var opening: SavedDeck?
    @State var deleting: SavedDeck?
    var body: some View {
        List {
            Section { Button("Continuar borrador · \(store.state.draft.content.total)/20") { editor = true } }
            ForEach(store.state.backup.decks.reversed()) { deck in
                Button { opening = deck } label: {
                    VStack(alignment:.leading,spacing:8) {
                        Text(deck.name).font(.headline).foregroundStyle(.primary)
                        Text("\(deck.total)/20 · \((try? DeckContent.parse(deck.cards).energies.joined(separator:" · ")) ?? "")").font(.caption)
                        if let content = try? DeckContent.parse(deck.cards) {
                            Text(content.cards.allSatisfy { store.quantity($0.id) >= $0.count } ? "Disponible en tu colección" : "Faltan copias").font(.caption).foregroundStyle(.secondary)
                        }
                    }
                }.swipeActions { Button("Eliminar",role:.destructive) { deleting = deck } }
            }
        }.navigationTitle("Mis mazos")
        .sheet(isPresented:$editor) { NavigationStack { EditorView() } }
        .confirmationDialog("Reemplazar el borrador actual",isPresented:Binding(get:{opening != nil},set:{if !$0 {opening = nil}})) {
            Button("Abrir mazo") { if let deck = opening { Task {
                do { let content = try DeckContent.parse(deck.cards); if await store.commit({ $0.draft = Draft(name:deck.name,notes:deck.strategy,content:content) }) { editor = true } }
                catch { store.error = "No se pudo abrir el mazo." }
            } }; opening = nil }
        } message: { Text("Tu colección no se descuenta. El borrador actual se reemplazará.") }
        .confirmationDialog("Eliminar mazo",isPresented:Binding(get:{deleting != nil},set:{if !$0 {deleting = nil}})) {
            Button("Eliminar",role:.destructive) { if let deck = deleting { Task { _ = await store.commit { $0.backup.decks.removeAll { $0.id == deck.id } } } }; deleting = nil }
        }
    }
}
struct EditorView: View {
    @EnvironmentObject var store: Store
    @Environment(\.dismiss) var dismiss
    @State var section = "Cartas"
    @State var search = ""
    @State var name = ""
    @State var notes = ""
    @State var close = false
    @State var discard = false
    @State var share: SharedFile?
    @State var saved = false
    @State var qrBusy = false
    var body: some View {
        Form {
            Section {
                TextField("Nombre del mazo",text:$name)
                Text("\(store.state.draft.content.total)/20 cartas").font(.headline)
                Picker("Sección",selection:$section) { ForEach(["Cartas","Energías","Notas"],id:\.self) { Text($0) } }.pickerStyle(.menu)
            }
            if section == "Cartas" { cardsSection }
            if section == "Energías" {
                Section("Entre una y tres energías") { ForEach(energyNames,id:\.self) { energy in
                    Toggle(energy,isOn:Binding(get:{store.state.draft.content.energies.contains(energy)},set:{ selected in Task { _ = await store.commit { state in
                        if selected { try require(state.draft.content.energies.count < 3,"Máximo tres energías."); state.draft.content.energies.append(energy) }
                        else { state.draft.content.energies.removeAll { $0 == energy } }
                    } } }))
                } }
            }
            if section == "Notas" { Section("Estrategia") { TextEditor(text:$notes).frame(minHeight:160) } }
            Section("Validación") {
                ForEach(store.state.draft.content.warnings(catalog:store.byID),id:\.self) { Text($0).foregroundStyle(.orange) }
                Text("Guardar permite mazos incompletos; QR requiere 20 cartas.").font(.caption)
                Button("Guardar en mis mazos") { Task { await flush(); await store.saveDraft(); saved = store.error == nil } }.accessibilityIdentifier("saveDeck")
                if saved { Text("Mazo guardado.") }
                Button(qrBusy ? "Comprobando QR…" : "Compartir QR turquesa") { exportQR(false) }.disabled(qrBusy || store.state.draft.content.total != 20)
                Button("QR alternativo") { exportQR(true) }.disabled(qrBusy || store.state.draft.content.total != 20)
                Button("Nuevo borrador",role:.destructive) { discard = true }
            }
        }.navigationTitle("Editor").disabled(store.writing)
        .toolbar { ToolbarItem(placement:.cancellationAction) { Button("Cerrar") { close = true } } }
        .interactiveDismissDisabled()
        .onAppear { name = store.state.draft.name; notes = store.state.draft.notes }
        .task(id:name + "\u{0}" + notes) { do { try await Task.sleep(nanoseconds:300_000_000); if !Task.isCancelled { await flush() } } catch {} }
        .confirmationDialog("Cerrar editor",isPresented:$close) {
            Button("Conservar borrador y cerrar") { Task { await flush(); dismiss() } }
            Button("Seguir editando",role:.cancel) {}
        } message: { Text("El borrador se conserva localmente para continuar después.") }
        .confirmationDialog("Descartar borrador",isPresented:$discard) {
            Button("Descartar",role:.destructive) { Task { if await store.commit({$0.draft = Draft()}) { name = "Mi mazo"; notes = "" } } }
        }.sheet(item:$share) { ActivitySheet(url:$0.url) }
    }
    var cardsSection: some View {
        Group {
            Section("En el mazo") { ForEach(store.state.draft.content.cards,id:\.id) { ref in
                HStack {
                    VStack(alignment:.leading) { Text(store.byID[ref.id]?.name ?? ref.id); Text("\(ref.id) · Tengo \(store.quantity(ref.id))").font(.caption).foregroundStyle(.secondary) }
                    Spacer()
                    Button { if let c = store.byID[ref.id] { Task { await store.addToDraft(c,delta:-1) } } } label: { Image(systemName:"minus.circle") }.buttonStyle(.borderless).accessibilityLabel("Quitar \(ref.id)")
                    Text("\(ref.count)")
                    Button { if let c = store.byID[ref.id] { Task { await store.addToDraft(c,delta:1) } } } label: { Image(systemName:"plus.circle") }.buttonStyle(.borderless).accessibilityLabel("Añadir \(ref.id)")
                }
            } }
            Section("Añadir cartas") {
                TextField("Buscar nombre o código",text:$search)
                ForEach(Array(store.catalog.filter { search.isEmpty ? store.quantity($0.id) > 0 : $0.name.localizedCaseInsensitiveContains(search) || $0.id.localizedCaseInsensitiveContains(search) }.prefix(50))) { card in
                    Button("\(card.name) · \(card.id)") { Task { await store.addToDraft(card,delta:1) } }
                }
                Text("Se muestran hasta 50 resultados; busca para reducir.").font(.caption)
            }
        }
    }
    func flush() async { _ = await store.commit { $0.draft.name = name; $0.draft.notes = notes } }
    func exportQR(_ alternate: Bool) {
        qrBusy = true
        let content = store.state.draft.content, identities = store.identities
        Task {
            defer { qrBusy = false }
            do {
                let payload = try QRPayload.resolve(content,identities:identities).encoded()
                let png = try await Task.detached { try QRExport.png(payload:payload,alternate:alternate) }.value
                share = try temporaryFile(png,name:alternate ? "Pocket-QR-alternativo.png" : "Pocket-QR.png")
            } catch { store.error = error.localizedDescription }
        }
    }
}
