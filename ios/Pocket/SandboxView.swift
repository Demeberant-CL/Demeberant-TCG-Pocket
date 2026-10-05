import SwiftUI

struct SandboxView: View {
    @EnvironmentObject var store: Store
    @State var board: Board?
    @State var undo: [Board] = []
    @State var confirm = false
    var body: some View {
        List {
            Section { Text("Tablero manual de práctica. No es el motor oficial y no ejecuta automáticamente ataques, habilidades o victoria.").font(.caption); Button("Iniciar con el borrador") { confirm = true } }
            if let board {
                Section("Turno \(board.turn) · Mazo: \(board.pile.count)") {
                    Button("Robar carta") { apply { try $0.draw() } }
                    Button("Siguiente turno") { apply { $0.next() } }
                    Button("Deshacer") { if let old = undo.popLast() { self.board = old } }.disabled(undo.isEmpty)
                    Button("Lanzar moneda") { store.error = Bool.random() ? "Moneda: cara" : "Moneda: cruz" }
                }
                zone("Activo",cards:[board.active].compactMap{$0})
                zone("Banca",cards:board.bench)
                zone("Mano",cards:board.hand)
                zone("Descarte",cards:board.discard)
            }
        }.navigationTitle("Simulador local")
        .confirmationDialog("Reiniciar tablero",isPresented:$confirm) {
            Button("Iniciar") { do { board = try Board.start(store.state.draft.content,catalog:store.byID); undo = [] } catch { store.error = error.localizedDescription } }
        }
    }
    func zone(_ name: String, cards: [BoardCard]) -> some View {
        Section(name) { ForEach(cards) { card in
            VStack(alignment:.leading) {
                Text(card.name).font(.headline)
                Text("Energías: \(card.energies) · Daño: \(card.damage)").font(.caption)
                Menu("Mover") { ForEach(["Mano","Activo","Banca","Descarte"],id:\.self) { zone in Button(zone) { apply { try $0.move(card.id,to:zone) } } } }
                if ["Activo","Banca"].contains(name) {
                    Button("Asignar energía") { apply { try $0.energy(card.id) } }
                    Button("Añadir 10 de daño") { apply { try $0.damage(card.id,amount:card.damage+10) } }
                    Button("Quitar 10 de daño") { apply { try $0.damage(card.id,amount:max(0,card.damage-10)) } }
                }
                if name == "Banca" { Button("Intercambiar con Activo") { apply { try $0.swap(card.id) } } }
            }
        } }
    }
    func apply(_ mutation: (inout Board) throws -> Void) {
        guard let old = board else { return }
        var next = old
        do { try mutation(&next); undo.append(old); if undo.count > 50 { undo.removeFirst() }; board = next }
        catch { store.error = error.localizedDescription }
    }
}
