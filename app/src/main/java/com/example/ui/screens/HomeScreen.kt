package com.example.ui.screens

import com.example.ui.components.PocketCardImage
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.util.DeckCodec
import com.example.ui.viewmodel.AdvancedViewModel
import com.example.ui.viewmodel.TcgViewModel

@Composable
fun HomeScreen(
  main: TcgViewModel,
  advanced: AdvancedViewModel,
  modifier: Modifier = Modifier,
  onDecks: () -> Unit,
  onEditor: () -> Unit,
  onCreate: () -> Unit,
  onMeta: () -> Unit,
  onCollection: (String?) -> Unit,
  onTools: () -> Unit,
  onZoneSync: () -> Unit = { onCollection(null) },
  onAiSettings: () -> Unit = {},
  onSettings: () -> Unit = {},
) {
  val inventory by main.inventoryList.collectAsStateWithLifecycle()
  val decks by main.savedDecks.collectAsStateWithLifecycle()
  val draft by main.generatedDeck.collectAsStateWithLifecycle()
  val message by main.csvStatusMessage.collectAsStateWithLifecycle()
  val chatGptAccount by advanced.chatGptPlanAccount.collectAsStateWithLifecycle()
  val chatGptConnected = chatGptAccount?.canUsePlan == true
  val owned = remember(inventory) { inventory.associate { it.card.id to it.ownedCount } }
  val distinct = remember(inventory) { inventory.count { it.ownedCount > 0 } }
  val totalCards = remember(inventory) { inventory.sumOf { it.ownedCount } }
  val progress = if (inventory.isEmpty()) 0 else distinct * 100 / inventory.size
  val showcase = remember(inventory) {
    inventory.filter { it.ownedCount > 0 }
      .sortedWith(compareByDescending<com.example.data.repository.CardWithInventory> { it.isWishlist }
        .thenByDescending { it.card.isEx }
        .thenBy { it.card.id })
      .take(3)
  }
  val deckAvailability = remember(decks, owned) {
    decks.mapNotNull { saved ->
      runCatching { DeckCodec.references(saved.cardListSerialized) }.getOrNull()?.let { refs ->
        saved to refs.sumOf { (id, count) -> (count - (owned[id] ?: 0)).coerceAtLeast(0) }
      }
    }.sortedBy { it.second }
  }
  val openSavedDeck = com.example.ui.components.rememberSavedDeckOpener(main, onEditor)
  var reading by remember { mutableStateOf<com.example.data.repository.CardWithInventory?>(null) }
  reading?.let { entry ->
    CardDetailsDialog(entry, "es", { reading = null }, { main.setQuantity(entry.card.id, it) }, { main.toggleWishlist(entry.card.id) })
  }

  LazyColumn(
    modifier.fillMaxSize().testTag("home_list"),
    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    item {
      Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
          Text("TCG Lab", style = MaterialTheme.typography.headlineMedium)
          Text("Pokémon TCG Pocket", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        TextButton(onClick = onSettings) { Text("Ajustes") }
      }
    }

    item {
      Card(Modifier.fillMaxWidth()) {
        Row(
          Modifier.fillMaxWidth().padding(14.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
          Column(Modifier.weight(1f)) {
            Text("Pokémon Zone", style = MaterialTheme.typography.titleMedium)
            Text("Importa o actualiza la colección", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
          }
          Button(onClick = onZoneSync) { Text("Sincronizar") }
        }
      }
    }

    item {
      OutlinedCard(onClick = { onCollection(null) }, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
          Row(verticalAlignment = Alignment.Bottom) {
            Text("Colección", Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
            Text("$progress%", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
          }
          Text("$distinct de ${inventory.size} cartas distintas · $totalCards copias", style = MaterialTheme.typography.bodyMedium)
          LinearProgressIndicator(progress = { progress / 100f }, modifier = Modifier.fillMaxWidth())
          Text("Abrir colección  ›", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        }
      }
    }

    item {
      Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text("Cartas destacadas", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
        TextButton(onClick = { onCollection(null) }) { Text("Ver todas") }
      }
    }
    if (showcase.isNotEmpty()) item {
      LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        items(showcase, key = { it.card.id }) { entry ->
          OutlinedCard(onClick = { reading = entry }, modifier = Modifier.width(128.dp)) {
            Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
              PocketCardImage(entry.card.id, entry.card.name, modifier = Modifier.fillMaxWidth().aspectRatio(0.716f))
              Text(entry.card.name, style = MaterialTheme.typography.titleSmall, maxLines = 1)
              Text("${entry.ownedCount} copias", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
          }
        }
      }
    } else item {
      OutlinedCard(onClick = { onCollection(null) }, modifier = Modifier.fillMaxWidth()) {
        Text("La colección está vacía. Importa datos o registra cartas para comenzar.", Modifier.padding(14.dp))
      }
    }

    item {
      Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text("Misiones", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
        TextButton(onClick = onZoneSync) { Text("Sincronizar") }
      }
      OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
          Text("Progreso de misiones", style = MaterialTheme.typography.titleSmall)
          Text("Se mostrará cuando Pokémon Zone lo incluya en los datos disponibles.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
      }
    }

    item {
      Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text("Mazos", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
        TextButton(onClick = onDecks) { Text("Ver todos") }
      }
      if (decks.isEmpty()) {
        OutlinedCard(onClick = onCreate, modifier = Modifier.fillMaxWidth()) {
          Column(Modifier.padding(14.dp)) {
            Text("Todavía no hay mazos guardados.", style = MaterialTheme.typography.titleSmall)
            Text("Crear un mazo con las cartas disponibles  ›", color = MaterialTheme.colorScheme.primary)
          }
        }
      }
    }
    items(decks.take(2), key = { "tcg_lab_deck_${it.id}" }) { saved ->
      val missing = deckAvailability.firstOrNull { it.first.id == saved.id }?.second
      OutlinedCard(onClick = { openSavedDeck(saved) }, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
          Column(Modifier.weight(1f)) {
            Text(saved.name, style = MaterialTheme.typography.titleSmall)
            Text("${saved.totalCards}/20 cartas · " + (missing?.let { if (it == 0) "completo en colección" else "faltan $it copias" } ?: "revisar colección"),
              style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
          }
          Text("›", color = MaterialTheme.colorScheme.primary)
        }
      }
    }

    item {
      OutlinedButton(onClick = onTools, modifier = Modifier.fillMaxWidth()) {
        Text("Herramientas del juego")
      }
    }

    item {
      Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
      ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
          Text("ChatGPT", style = MaterialTheme.typography.titleLarge)
          Text(
            if (chatGptConnected) "Analiza cartas disponibles y propone mazos de 20 cartas."
            else "Conecta ChatGPT para analizar la colección y crear mazos.",
            style = MaterialTheme.typography.bodyMedium,
          )
          Button(
            onClick = {
              if (chatGptConnected) {
                advanced.setChatGptPlanPreferred(true)
                onCreate()
              } else onAiSettings()
            },
            modifier = Modifier.fillMaxWidth(),
          ) {
            Text(if (chatGptConnected) "Crear mazo con ChatGPT" else "Conectar ChatGPT")
          }
        }
      }
    }

    draft?.takeIf { it.cards.isNotEmpty() }?.let { deck ->
      item {
        OutlinedButton(onClick = onEditor, modifier = Modifier.fillMaxWidth()) {
          Text("Continuar ${deck.name} · ${deck.totalCardCount}/20")
        }
      }
    }

    message?.let { status ->
      item {
        Text(status, style = MaterialTheme.typography.bodySmall)
        TextButton(onClick = main::clearCsvStatusMessage) { Text("Cerrar") }
      }
    }
  }
}
