import SwiftUI
import UIKit

struct CardImage: View {
    let id: String
    var high = false
    @State var image: UIImage?
    @State var failed = false
    @State var attempt = 0
    var body: some View {
        Group {
            if let image { Image(uiImage:image).resizable().scaledToFit() }
            else if failed { VStack { Image(systemName:"photo"); Text("Sin imagen").font(.caption); Button("Reintentar") { attempt += 1 } } }
            else { ProgressView() }
        }.accessibilityLabel("Imagen de \(id)")
        .task(id:"\(id)-\(high)-\(attempt)") {
            do { failed = false; image = UIImage(data:try await CardImages.shared.imageData(id,high:high,retry:attempt > 0)) }
            catch is CancellationError { }
            catch { failed = true }
        }
    }
}
struct CollectionView: View {
    @EnvironmentObject var store: Store
    @State var search = ""
    @State var status = "Todas"
    @State var expansion = "Todas"
    @State var rarity = "Todas"
    @State var layout = "Cuadrícula"
    @State var large = false
    @State var filters = false
    var matches: [Card] {
        let inventory = Dictionary(uniqueKeysWithValues:store.state.backup.inventory.map { ($0.id,$0) })
        return store.catalog.filter { c in
            let row = inventory[c.id], quantity = row?.quantity ?? 0
            let statusMatch = status == "Todas" || status == "Tengo" && quantity > 0 || status == "Faltan" && quantity == 0 || status == "Repetidas" && quantity > 1 || status == "Deseos" && row?.wishlist == true
            return statusMatch && (expansion == "Todas" || expansion == c.set) && (rarity == "Todas" || rarity == c.rarity) && (search.isEmpty || c.name.localizedCaseInsensitiveContains(search) || c.id.localizedCaseInsensitiveContains(search))
        }
    }
    var body: some View {
        VStack(spacing:8) {
            if filters { filterPanel }
            Text("\(matches.count) resultados").font(.caption).foregroundStyle(.secondary)
            if layout == "Lista" {
                List(matches) { card in NavigationLink { CardDetailView(card:card) } label: { CardRow(card:card) } }
            } else {
                ScrollView { LazyVGrid(columns:[GridItem(.adaptive(minimum:large ? 230 : 140),spacing:12)],spacing:16) {
                    ForEach(matches) { card in NavigationLink { CardDetailView(card:card) } label: {
                        VStack(alignment:.leading,spacing:6) {
                            CardImage(id:card.id).frame(height:large ? 300 : 160)
                            Text(card.name).font(.headline).foregroundStyle(.primary)
                            Text("\(card.id) · \(card.rarity)").font(.caption).foregroundStyle(.secondary)
                            Text("Tengo \(store.quantity(card.id))").font(.caption.bold()).foregroundStyle(.orange)
                        }.padding(10).background(Color(uiColor:.secondarySystemGroupedBackground),in:RoundedRectangle(cornerRadius:14))
                    } }
                }.padding() }
            }
        }.background(Color(uiColor:.systemGroupedBackground)).navigationTitle("Colección")
        .searchable(text:$search,prompt:"Nombre o código")
        .toolbar { Button { withAnimation { filters.toggle() } } label: { Label("Filtros",systemImage:"line.3.horizontal.decrease.circle") } }
    }
    var filterPanel: some View {
        ScrollView {
            VStack(spacing:8) {
                Picker("Estado",selection:$status) { ForEach(["Todas","Tengo","Faltan","Deseos","Repetidas"],id:\.self) { Text($0) } }.pickerStyle(.menu).accessibilityIdentifier("collectionStatus")
                Picker("Expansión",selection:$expansion) { Text("Todas").tag("Todas"); ForEach(Array(Set(store.catalog.map(\.set))).sorted(),id:\.self) { Text($0) } }.pickerStyle(.menu)
                Picker("Rareza",selection:$rarity) { Text("Todas").tag("Todas"); ForEach(Array(Set(store.catalog.map(\.rarity))).sorted(),id:\.self) { Text($0) } }.pickerStyle(.menu)
                Divider()
                Picker("Vista",selection:$layout) { Text("Cuadrícula"); Text("Lista") }.pickerStyle(.menu)
                if layout == "Cuadrícula" { Toggle("Cartas grandes",isOn:$large) }
            }.padding(12)
        }.frame(maxHeight:240).background(Color(uiColor:.secondarySystemGroupedBackground),in:RoundedRectangle(cornerRadius:14))
        .overlay(RoundedRectangle(cornerRadius:14).stroke(.orange.opacity(0.4))).padding(.horizontal)
    }
}
struct CardRow: View {
    @EnvironmentObject var store: Store
    let card: Card
    var body: some View {
        HStack { CardImage(id:card.id).frame(width:48,height:68); VStack(alignment:.leading) { Text(card.name).font(.headline); Text("\(card.id) · \(card.rarity)").font(.caption).foregroundStyle(.secondary) }; Spacer(); Text("×\(store.quantity(card.id))") }
    }
}
struct CardDetailView: View {
    @EnvironmentObject var store: Store
    let card: Card
    @State var quantity = ""
    @State var rules: CardRules?
    @State var message = ""
    @State var retry = 0
    var body: some View {
        Form {
            Section { CardImage(id:card.id,high:true).frame(maxWidth:.infinity,minHeight:260,maxHeight:430) }
            Section("Colección") {
                Text("\(card.id) · \(card.rarity)")
                TextField("Cantidad",text:$quantity).keyboardType(.numberPad).accessibilityIdentifier("quantity")
                Button("Guardar cantidad") { Task {
                    guard let n = Int(quantity), (0...99999).contains(n) else { store.error = "Escribe una cantidad de 0 a 99999."; return }
                    var row = store.inventory(card); row.quantity = n; await store.update(row)
                } }.disabled(store.writing)
                Toggle("Lista de deseos",isOn:Binding(get:{store.inventory(card).wishlist},set:{ value in Task { var row = store.inventory(card); row.wishlist = value; await store.update(row) } }))
                Button("Añadir al borrador") { Task { await store.addToDraft(card,delta:1) } }.disabled(store.writing)
            }
            Section("Reglas") {
                if let rules { Text(rules.text.isEmpty ? "Sin texto disponible." : rules.text); Text(rules.source).font(.caption); if let hp = rules.hp { Text("PS: \(hp)") } }
                else { Text(message.isEmpty ? "Consultando detalles…" : message) }
                Button("Actualizar detalles") { retry += 1 }
            }
        }.navigationTitle(card.name).onAppear { if quantity.isEmpty { quantity = "\(store.quantity(card.id))" } }
        .task(id:retry) {
            do { rules = try await RulesCache.shared.details(card.id,retry:retry > 0) }
            catch is CancellationError { }
            catch { message = "Detalles no disponibles. Tu colección se conserva." }
        }
    }
}
