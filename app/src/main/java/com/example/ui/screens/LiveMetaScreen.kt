package com.example.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.viewmodel.*
import com.example.data.repository.*
import com.example.data.meta.TournamentRepository

@Composable
fun LiveMetaScreen(main: TcgViewModel, model: AdvancedViewModel, onAi: () -> Unit, modifier: Modifier = Modifier) {
  val snapshot by model.metaSnapshot.collectAsStateWithLifecycle()
  val busy by model.busy.collectAsStateWithLifecycle()
  val message by model.message.collectAsStateWithLifecycle()
  val inventory by main.inventoryList.collectAsStateWithLifecycle()
  val cards = inventory.associateBy { it.card.id }
  var selected by remember { mutableStateOf<com.example.data.meta.MetaDeck?>(null) }
  LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
    item {
      Text("Meta de torneos", style = MaterialTheme.typography.headlineSmall)
      Button(onClick = model::refreshMeta, enabled = !busy) { Text("Actualizar meta") }
      if (busy) { LinearProgressIndicator(Modifier.fillMaxWidth()); TextButton(onClick = model::cancel) { Text("Cancelar") } }
      message?.let { Text(it) }
      snapshot?.let { Text("Limitless · ${it.updated}\n${it.tournaments} torneos · ${it.players} listas válidas · últimos 30 días (hasta 12 torneos)") }
        ?: Text("Pulsa Actualizar meta para descargar resultados públicos.")
      Text("Muestra parcial de torneos; no representa todas las partidas del juego. Los porcentajes se calculan, no los genera la IA.", style = MaterialTheme.typography.bodySmall)
    }
    items(snapshot?.decks ?: emptyList()) { d ->
      val unknown = d.cards.keys.filter { it !in cards }
      val missing = d.cards.entries.sumOf { (id, count) -> (count - (cards[id]?.ownedCount ?: 0)).coerceAtLeast(0) }
      Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(d.name, style = MaterialTheme.typography.titleMedium)
        val total = snapshot?.players ?: 1
        Text("${d.count} listas · ${"%.1f".format(d.count * 100.0 / total)}% de la muestra")
        val decisive = d.wins + d.losses
        Text("${d.wins} victorias · ${d.losses} derrotas · ${d.ties} empates" +
          if (decisive > 0) " · ${"%.1f".format(d.wins * 100.0 / decisive)}% sin empates" else "")
        Text("Lista de ejemplo · Energías: ${d.energies.joinToString()} · $missing copias faltantes")
        if (unknown.isNotEmpty()) Text("${unknown.size} IDs fuera del catálogo: actualiza el catálogo antes de adaptar este mazo.")
        OutlinedButton(onClick = { selected = d }, enabled = unknown.isEmpty()) { Text("Adaptar a mi colección") }
      } }
    }
  }
  selected?.let { d -> AlertDialog(onDismissRequest = { selected = null }, title = { Text("Adaptar ${d.name}") },
    text = { Text("Abrirá la lista de ejemplo como borrador y el asistente. No guarda ni consulta IA automáticamente. Puede reemplazar el borrador abierto.") },
    confirmButton = { TextButton(onClick = {
      val entries = d.cards.map { (id, count) -> DeckCardEntry(cards.getValue(id).card, count) }
      main.openTournamentDeck(GeneratedDeck(d.name, "Limitless", "Lista de ejemplo: https://play.limitlesstcg.com/tournament/${d.tournamentId}", entries, 20, energyTypes = d.energies))
      model.meta = org.json.JSONObject().put("source", "Limitless").put("updated", snapshot!!.updated)
        .put("tournaments", snapshot!!.tournaments).put("sampleLists", snapshot!!.players)
        .put("archetype", d.name).put("wins", d.wins).put("losses", d.losses).put("ties", d.ties).toString()
      model.goal = "Adapta el mazo de torneo conservando todas mis copias disponibles."
      selected = null; onAi()
    }) { Text("Abrir asistente") } }, dismissButton = { TextButton(onClick = { selected = null }) { Text("Cancelar") } }) }
}
