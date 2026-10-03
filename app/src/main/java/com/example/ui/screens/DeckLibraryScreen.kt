package com.example.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.local.SavedDeckEntity
import com.example.data.util.DeckCodec
import com.example.ui.viewmodel.TcgViewModel

@Composable
fun DeckLibraryScreen(viewModel: TcgViewModel, modifier: Modifier = Modifier, onEdit: () -> Unit) {
  val decks by viewModel.savedDecks.collectAsStateWithLifecycle()
  val inventory by viewModel.inventoryList.collectAsStateWithLifecycle()
  val owned = remember(inventory) { inventory.associate { it.card.id to it.ownedCount } }
  val cards = remember(inventory) { inventory.associate { it.card.id to it.card } }
  var pendingDelete by remember { mutableStateOf<SavedDeckEntity?>(null) }
  LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
    item {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Text("Mis mazos (${decks.size})", Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
        Button(onClick = onEdit) { Text("Crear / editar") }
      }
      if (decks.isEmpty()) Text("Guarda tu primera baraja desde el editor. Puedes empezar con tus cartas o consultar tu IA.")
    }
    items(decks, key = { it.id }) { saved ->
      val refs = remember(saved.cardListSerialized) { runCatching { DeckCodec.references(saved.cardListSerialized) }.getOrNull() }
      val energies = remember(saved.cardListSerialized) { runCatching { DeckCodec.energies(saved.cardListSerialized) }.getOrDefault(emptyList()) }
      val total = refs?.sumOf { it.second } ?: saved.totalCards
      val available = refs?.sumOf { (id, count) -> minOf(count, owned[id] ?: 0) }
      ElevatedCard(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
          refs?.firstOrNull()?.let { (id, _) -> DeckThumbnail(id, cards[id]?.name ?: "Portada del mazo") }
          Column(Modifier.weight(1f)) {
            Text(saved.name, style = MaterialTheme.typography.titleMedium)
            Text("$total/20 cartas" + if (total < 20) " · Borrador" else "", style = MaterialTheme.typography.bodySmall)
            Text("Energías: " + energies.joinToString().ifBlank { "Por revisar en el editor" }, style = MaterialTheme.typography.bodySmall)
            if (available != null) Text("Tienes $available/$total copias" + if (available < total) " · Faltan ${total-available}" else "",
              style = MaterialTheme.typography.bodySmall,
              color = if (available < total) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
            if (refs == null) Text("No se pudo interpretar la lista guardada. El mazo se conserva.", style = MaterialTheme.typography.bodySmall)
            Row(verticalAlignment = Alignment.CenterVertically) {
              OutlinedButton(enabled = refs != null && refs.all { cards.containsKey(it.first) },
                onClick = { viewModel.loadSavedDeck(saved); onEdit() }) { Text("Editar") }
              Spacer(Modifier.weight(1f))
              IconButton(onClick = { pendingDelete = saved }) { Icon(Icons.Filled.Delete, "Eliminar ${saved.name}") }
            }
            if (refs != null && refs.any { !cards.containsKey(it.first) }) Text("Hay cartas fuera del catálogo actual.", style = MaterialTheme.typography.bodySmall)
          }
        }
      }
    }
  }
  pendingDelete?.let { saved -> AlertDialog(onDismissRequest = { pendingDelete = null },
    title = { Text("Eliminar mazo") }, text = { Text("¿Eliminar «${saved.name}»? Las cartas de tu colección se conservan.") },
    confirmButton = { TextButton(onClick = { viewModel.deleteSavedDeck(saved.id); pendingDelete = null }) { Text("Eliminar") } },
    dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("Cancelar") } }) }
}
