package com.example.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import com.example.ui.components.DexPanel
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.local.SavedDeckEntity
import com.example.data.util.DeckCodec
import com.example.ui.viewmodel.TcgViewModel

@Composable
fun DeckLibraryScreen(viewModel: TcgViewModel, modifier: Modifier = Modifier, onEdit: () -> Unit) {
  val openSavedDeck = com.example.ui.components.rememberSavedDeckOpener(viewModel, onEdit)
  val draft by viewModel.generatedDeck.collectAsStateWithLifecycle()
  val decks by viewModel.savedDecks.collectAsStateWithLifecycle()
  val inventory by viewModel.inventoryList.collectAsStateWithLifecycle()
  val owned = remember(inventory) { inventory.associate { it.card.id to it.ownedCount } }
  val cards = remember(inventory) { inventory.associate { it.card.id to it.card } }
  var search by rememberSaveable { mutableStateOf("") }
  val visibleDecks = remember(decks, search) { decks.filter { it.name.contains(search.trim(), ignoreCase = true) } }
  var pendingDelete by remember { mutableStateOf<SavedDeckEntity?>(null) }
  LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
    item {
      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Mis mazos (${decks.size})", style = MaterialTheme.typography.titleLarge)
        Button(onClick = onEdit, modifier = Modifier.fillMaxWidth()) {
          Text(if (draft == null || draft?.cards?.isEmpty() == true) "+ Nuevo mazo" else "Continuar borrador")
        }
        OutlinedTextField(search, { search = it }, modifier = Modifier.fillMaxWidth(),
          label = { Text("Buscar mazo") }, singleLine = true)
      }
      if (decks.isEmpty()) Text("Guarda tu primera baraja desde el editor. Puedes empezar con tus cartas o consultar tu IA.")
    }
    if (decks.isNotEmpty() && visibleDecks.isEmpty()) item { Text("No hay mazos que coincidan con la búsqueda.") }
    items(visibleDecks, key = { it.id }) { saved ->
      val refs = remember(saved.cardListSerialized) { runCatching { DeckCodec.references(saved.cardListSerialized) }.getOrNull() }
      val energies = remember(saved.cardListSerialized) { runCatching { DeckCodec.energies(saved.cardListSerialized) }.getOrDefault(emptyList()) }
      val total = refs?.sumOf { it.second } ?: saved.totalCards
      val available = refs?.sumOf { (id, count) -> minOf(count, owned[id] ?: 0) }
      var showMenu by remember(saved.id) { mutableStateOf(false) }
      DexPanel(Modifier.fillMaxWidth()) {
        val cover = remember(saved.cardListSerialized) { runCatching { DeckCodec.cover(saved.cardListSerialized) }.getOrNull() }
        Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
          DeckCover(cover, saved.name, Modifier.width(76.dp).height(106.dp))
          Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(saved.name, style = MaterialTheme.typography.titleMedium)
            Surface(shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.secondaryContainer) {
              Text(if (refs == null) "$total/20 · Lista por revisar"
                else if (total == 20) "20/20 · Lista completa" else "$total/20 · Borrador",
                Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSecondaryContainer)
            }
            Text("Energías: " + energies.joinToString().ifBlank { "Por revisar en el editor" }, style = MaterialTheme.typography.bodySmall)
            if (available != null) Text(if (available < total) "Faltan ${total-available} copias · $available/$total disponibles" else "Todas las copias disponibles · $available/$total",
              style = MaterialTheme.typography.bodySmall,
              color = if (available < total) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
            if (refs == null) Text("No se pudo interpretar la lista guardada. El mazo se conserva.", style = MaterialTheme.typography.bodySmall)
            Row(verticalAlignment = Alignment.CenterVertically) {
              OutlinedButton(enabled = refs != null && refs.all { cards.containsKey(it.first) },
                onClick = { openSavedDeck(saved) }) { Text("Abrir") }
              Spacer(Modifier.weight(1f))
              Box {
                IconButton(onClick = { showMenu = true }) { Icon(Icons.Filled.MoreVert, "Opciones de ${saved.name}") }
                DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                  DropdownMenuItem(text = { Text("Eliminar mazo") }, onClick = { showMenu = false; pendingDelete = saved })
                }
              }
            }
            if (refs != null && refs.any { !cards.containsKey(it.first) }) Text("Hay cartas fuera del catálogo actual.", style = MaterialTheme.typography.bodySmall)
          }
        }
      }
    }
  }
  pendingDelete?.let { saved -> AlertDialog(onDismissRequest = { pendingDelete = null },
    title = { Text("Eliminar mazo") }, text = { Text("¿Eliminar «${saved.name}»? Las cartas de tu colección se conservan.", modifier = Modifier.verticalScroll(rememberScrollState())) },
    confirmButton = { TextButton(onClick = { viewModel.deleteSavedDeck(saved.id); pendingDelete = null }) { Text("Eliminar") } },
    dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("Cancelar") } }) }
}
