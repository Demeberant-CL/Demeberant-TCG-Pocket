package com.example.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.draw.clip
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.local.SavedDeckEntity
import com.example.data.util.DeckCodec
import com.example.ui.viewmodel.TcgViewModel

@Composable
fun DeckLibraryScreen(viewModel: TcgViewModel, modifier: Modifier = Modifier, onEdit: () -> Unit, onCreate: () -> Unit = onEdit) {
  val openSavedDeck = com.example.ui.components.rememberSavedDeckOpener(viewModel, onEdit)
  val draft by viewModel.generatedDeck.collectAsStateWithLifecycle()
  val decks by viewModel.savedDecks.collectAsStateWithLifecycle()
  val inventory by viewModel.inventoryList.collectAsStateWithLifecycle()
  val owned = remember(inventory) { inventory.associate { it.card.id to it.ownedCount } }
  val cards = remember(inventory) { inventory.associate { it.card.id to it.card } }
  var search by rememberSaveable { mutableStateOf("") }
  val visibleDecks = remember(decks, search) { decks.filter { it.name.contains(search.trim(), ignoreCase = true) } }
  var pendingDelete by remember { mutableStateOf<SavedDeckEntity?>(null) }
  LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
    item {
      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Pocket Atlas", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        Text("Mazos", style = MaterialTheme.typography.displayLarge)
        Button(onClick = onCreate, modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium) { Text("+ Crear mazo") }
        if (draft?.cards?.isNotEmpty() == true) OutlinedButton(onClick = onEdit, modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium) { Text("Continuar borrador · ${draft?.totalCardCount}/20") }
        OutlinedTextField(search, { search = it }, modifier = Modifier.fillMaxWidth(),
          label = { Text("Buscar mazo") }, singleLine = true)
      }
      if (decks.isEmpty()) Text("Tu próximo mazo empieza aquí. Crea uno con tus cartas, manualmente o con IA.")
    }
    if (decks.isNotEmpty() && visibleDecks.isEmpty()) item { Text("No hay mazos que coincidan con la búsqueda.") }
    items(visibleDecks, key = { it.id }) { saved ->
      val refs = remember(saved.cardListSerialized) { runCatching { DeckCodec.references(saved.cardListSerialized) }.getOrNull() }
      val energies = remember(saved.cardListSerialized) { runCatching { DeckCodec.energies(saved.cardListSerialized) }.getOrDefault(emptyList()) }
      val total = refs?.sumOf { it.second } ?: saved.totalCards
      val available = refs?.sumOf { (id, count) -> minOf(count, owned[id] ?: 0) }
      var showMenu by remember(saved.id) { mutableStateOf(false) }
      val resolved = remember(refs, cards) { refs?.mapNotNull { (id, count) -> cards[id]?.let { com.example.data.repository.DeckCardEntry(it, count) } } }
      val valid = resolved != null && resolved.size == refs?.size && total == 20 && energies.isNotEmpty() &&
        com.example.data.repository.DeckBuilderEngine.validate(resolved).isEmpty()
      val covers = remember(resolved) { resolved.orEmpty().sortedWith(compareByDescending<com.example.data.repository.DeckCardEntry> { it.card.isEx }
        .thenBy { it.card.type == "Entrenador" }).take(2) }
      OutlinedCard(onClick = { openSavedDeck(saved) },
        enabled = refs != null && refs.all { cards.containsKey(it.first) },
        shape = MaterialTheme.shapes.large,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth().testTag("saved_deck_${saved.id}")) {
        BoxWithConstraints(Modifier.fillMaxWidth().padding(16.dp)) {
          val stacked = LocalDensity.current.fontScale >= 1.3f || maxWidth < 300.dp
          Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
              Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(saved.name, style = MaterialTheme.typography.headlineMedium, maxLines = 2,
                  overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                Text("$total/20 cartas", style = MaterialTheme.typography.bodyMedium)
                if (energies.isNotEmpty()) com.example.ui.components.EnergyBadges(energies)
                Surface(shape = MaterialTheme.shapes.small, color = if (valid) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest) {
                  Text(if (valid) "Lista completa" else "Por completar", Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface)
                }
              }
              if (!stacked) Box(Modifier.padding(top = 36.dp)) { DeckCoverPair(covers) }
            }
            if (stacked) DeckCoverPair(covers)
            if (available != null) Text(if (available < total) "Faltan ${total - available} copias · $available/$total disponibles" else "Todas las copias disponibles",
              style = MaterialTheme.typography.bodySmall,
              color = if (available < total) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
            if (refs == null) Text("Lista por revisar. El mazo se conserva.", style = MaterialTheme.typography.bodySmall)
            if (refs != null && refs.any { !cards.containsKey(it.first) }) Text("Hay cartas fuera del catálogo actual.", style = MaterialTheme.typography.bodySmall)
          }
          Box(Modifier.align(Alignment.TopEnd)) {
            IconButton(onClick = { showMenu = true }) { Icon(Icons.Filled.MoreVert, "Opciones de ${saved.name}") }
            DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
              DropdownMenuItem(text = { Text("Eliminar mazo") }, onClick = { showMenu = false; pendingDelete = saved })
            }
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

@Composable
private fun DeckCoverPair(covers: List<com.example.data.repository.DeckCardEntry>) {
  Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
    covers.forEach { entry ->
      com.example.ui.components.PocketCardImage(entry.card.id, entry.card.name,
        modifier = Modifier.width(76.dp).height(108.dp).clip(MaterialTheme.shapes.small))
    }
  }
}
