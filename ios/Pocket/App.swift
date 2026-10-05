import SwiftUI
import UIKit

@main struct PocketApp: App {
    @StateObject private var store = Store()
    var body: some Scene {
        WindowGroup {
            RootView().environmentObject(store).tint(.orange)
                .preferredColorScheme(store.state.backup.preferences.theme == "dark" ? .dark : ["light","blue"].contains(store.state.backup.preferences.theme) ? .light : nil)
                .task { await store.start() }
        }
    }
}
struct RootView: View {
    @EnvironmentObject var store: Store
    var body: some View {
        Group {
            if store.ready {
                TabView {
                    NavigationStack { HomeView() }.tabItem { Label("Inicio",systemImage:"house") }
                    NavigationStack { CollectionView() }.tabItem { Label("Colección",systemImage:"square.grid.2x2") }
                    NavigationStack { DeckLibraryView() }.tabItem { Label("Mazos",systemImage:"rectangle.stack") }
                    NavigationStack { AIView() }.tabItem { Label("IA",systemImage:"sparkles") }
                    NavigationStack { MoreView() }.tabItem { Label("Más",systemImage:"ellipsis.circle") }
                }
            } else { VStack(spacing:16) { Text("Pocket").font(.largeTitle.bold()); ProgressView("Cargando datos locales…"); Button("Reintentar") { Task { await store.start() } } } }
        }
        .alert("No se completó la operación",isPresented:Binding(get:{ store.error != nil },set:{ if !$0 { store.error = nil } })) {
            Button("Aceptar",role:.cancel) { store.error = nil }
        } message: { Text(store.error ?? "") }
    }
}
struct AvatarView: View {
    let id: String
    var body: some View {
        Group {
            if let url = Bundle.main.url(forResource:"trainer_avatars",withExtension:"webp"), let image = UIImage(contentsOfFile:url.path), let cg = image.cgImage,
                let crop = cg.cropping(to:CGRect(x:((avatarIDs.firstIndex(of:id) ?? 0)%3)*512,y:((avatarIDs.firstIndex(of:id) ?? 0)/3)*512,width:512,height:512)) {
                Image(uiImage:UIImage(cgImage:crop)).resizable().scaledToFill()
            } else { Image(systemName:"person.crop.circle.fill").resizable().scaledToFit() }
        }.frame(width:64,height:64).clipShape(Circle()).accessibilityLabel("Avatar del entrenador")
    }
}
struct HomeView: View {
    @EnvironmentObject var store: Store
    @Environment(\.dynamicTypeSize) var textSize
    @State var editor = false
    var owned: Int { store.state.backup.inventory.filter { $0.quantity > 0 }.count }
    var body: some View {
        List {
            Section {
                if textSize.isAccessibilitySize {
                    VStack(alignment:.leading,spacing:12) { AvatarView(id:store.state.backup.preferences.avatar ?? avatarIDs[0]); collectionSummary }
                } else { HStack { AvatarView(id:store.state.backup.preferences.avatar ?? avatarIDs[0]); collectionSummary } }
                ProgressView(value:Double(owned),total:Double(max(1,store.catalog.count)))
                LabeledContent("Copias",value:"\(store.state.backup.inventory.reduce(0) { $0 + $1.quantity })")
            }
            Section("Borrador") { Button("\(store.state.draft.name) · \(store.state.draft.content.total)/20") { editor = true } }
            Section("Mazos recientes") {
                ForEach(Array(store.state.backup.decks.suffix(3).reversed())) { deck in Text("\(deck.name) · \(deck.total)/20") }
                if store.state.backup.decks.isEmpty { Text("Aún no tienes mazos guardados.").foregroundStyle(.secondary) }
            }
            Section("Meta comunitario") {
                if let meta = store.state.meta { Text(Date(timeIntervalSince1970:Double(meta.updated)/1000),style:.date); Text("\(meta.players) listas verificadas") }
                else { Text("Sin muestra descargada") }
            }
        }.navigationTitle("Pocket").sheet(isPresented:$editor) { NavigationStack { EditorView() } }
    }
    var collectionSummary: some View { VStack(alignment:.leading) { Text("Tu colección").font(.title2.bold()); Text("\(owned) de \(store.catalog.count) cartas") } }
}
struct SharedFile: Identifiable { let id = UUID(); let url: URL }
struct ActivitySheet: UIViewControllerRepresentable {
    let url: URL
    func makeUIViewController(context:Context) -> UIActivityViewController { UIActivityViewController(activityItems:[url],applicationActivities:nil) }
    func updateUIViewController(_ controller:UIActivityViewController,context:Context) {}
}
func temporaryFile(_ data: Data, name: String) throws -> SharedFile {
    let directory = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString)
    try FileManager.default.createDirectory(at:directory,withIntermediateDirectories:true)
    let url = directory.appendingPathComponent(name)
    try data.write(to:url,options:.atomic)
    return SharedFile(url:url)
}
