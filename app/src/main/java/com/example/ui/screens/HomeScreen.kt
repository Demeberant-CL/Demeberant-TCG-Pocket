package com.example.ui.screens

import com.example.ui.components.PocketCardImage
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
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
  var reading by remember { mutableStateOf<com.example.data.repository.CardWithInventory?>(null) }
  reading?.let { entry ->
    CardDetailsDialog(entry, "es", { reading = null }, { main.setQuantity(entry.card.id, it) }, { main.toggleWishlist(entry.card.id) })
  }

  val compact = LocalConfiguration.current.screenHeightDp < 720
  val gap = if (compact) 5.dp else 8.dp
  val sidePadding = if (compact) 10.dp else 16.dp
  val imageHeight = if (compact) 68.dp else 94.dp

  Column(
    modifier.fillMaxSize().padding(horizontal = sidePadding, vertical = if (compact) 5.dp else 10.dp)
      .testTag("home_list"),
    verticalArrangement = Arrangement.spacedBy(gap),
  ) {
    Row(
      Modifier.fillMaxWidth().height(if (compact) 42.dp else 50.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Column(Modifier.weight(1f)) {
        Text("TCG Lab", style = MaterialTheme.typography.titleLarge, maxLines = 1)
        Text("Pokémon TCG Pocket", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
      }
      TextButton(onClick = onSettings, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)) { Text("Ajustes") }
    }

    Row(Modifier.fillMaxWidth().height(if (compact) 76.dp else 88.dp), horizontalArrangement = Arrangement.spacedBy(gap)) {
      OutlinedCard(onClick = { onCollection(null) }, modifier = Modifier.weight(1.25f).fillMaxHeight()) {
        Column(Modifier.fillMaxSize().padding(horizontal = 10.dp, vertical = 7.dp), verticalArrangement = Arrangement.SpaceBetween) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Colección", Modifier.weight(1f), style = MaterialTheme.typography.titleSmall, maxLines = 1)
            Text("$progress%", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
          }
          Text("$distinct/${inventory.size} distintas · $totalCards copias", style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
          LinearProgressIndicator(progress = { progress / 100f }, modifier = Modifier.fillMaxWidth())
        }
      }
      OutlinedCard(onClick = onZoneSync, modifier = Modifier.weight(1f).fillMaxHeight()) {
        Column(Modifier.fillMaxSize().padding(horizontal = 10.dp, vertical = 7.dp), verticalArrangement = Arrangement.SpaceBetween) {
          Text("Pokémon Zone", style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
          Text("Actualizar colección y misiones", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
          Text("Sincronizar  ›", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        }
      }
    }

    Row(Modifier.fillMaxWidth().height(28.dp), verticalAlignment = Alignment.CenterVertically) {
      Text("Cartas destacadas", Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
      TextButton(onClick = { onCollection(null) }, contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)) { Text("Ver todas") }
    }

    Row(Modifier.fillMaxWidth().height(imageHeight + if (compact) 50.dp else 58.dp), horizontalArrangement = Arrangement.spacedBy(gap)) {
      if (showcase.isEmpty()) {
        OutlinedCard(onClick = { onCollection(null) }, modifier = Modifier.fillMaxSize()) {
          Box(Modifier.fillMaxSize().padding(10.dp), contentAlignment = Alignment.CenterStart) {
            Text("Aún no hay cartas. Importa o registra la colección para verlas aquí.", style = MaterialTheme.typography.bodySmall)
          }
        }
      } else {
        showcase.forEach { entry ->
          OutlinedCard(onClick = { reading = entry }, modifier = Modifier.weight(1f).fillMaxHeight()) {
            Column(Modifier.fillMaxSize().padding(5.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
              PocketCardImage(entry.card.id, entry.card.name, modifier = Modifier.fillMaxWidth().height(imageHeight))
              Text(entry.card.name, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
              Text("${entry.ownedCount} copias", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
          }
        }
      }
    }

    Row(Modifier.fillMaxWidth().height(if (compact) 75.dp else 86.dp), horizontalArrangement = Arrangement.spacedBy(gap)) {
      OutlinedCard(onClick = onZoneSync, modifier = Modifier.weight(1f).fillMaxHeight()) {
        Column(Modifier.fillMaxSize().padding(9.dp), verticalArrangement = Arrangement.SpaceBetween) {
          Text("Misiones", style = MaterialTheme.typography.titleSmall)
          Text("Sincroniza Pokémon Zone para actualizar el progreso.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
      }
      OutlinedCard(onClick = onDecks, modifier = Modifier.weight(1f).fillMaxHeight()) {
        Column(Modifier.fillMaxSize().padding(9.dp), verticalArrangement = Arrangement.SpaceBetween) {
          Text("Mazos · ${decks.size}", style = MaterialTheme.typography.titleSmall, maxLines = 1)
          val saved = decks.firstOrNull()
          val missing = saved?.let { deck -> deckAvailability.firstOrNull { it.first.id == deck.id }?.second }
          Text(
            when {
              draft?.cards?.isNotEmpty() == true -> "Borrador: ${draft?.name.orEmpty()}"
              saved == null -> "Aún no hay mazos guardados"
              else -> "${saved.name} · ${saved.totalCards}/20" + (missing?.let { if (it == 0) " · listo" else " · faltan $it" } ?: "")
            },
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
          )
        }
      }
    }

    Card(Modifier.fillMaxWidth().height(if (compact) 76.dp else 88.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
      Row(Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
          Text("ChatGPT", style = MaterialTheme.typography.titleSmall)
          Text(
            if (chatGptConnected) "Analiza la colección y crea un mazo de 20 cartas."
            else "Conecta ChatGPT para crear mazos con tu colección.",
            style = MaterialTheme.typography.labelSmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
          )
        }
        Spacer(Modifier.width(8.dp))
        Button(
          onClick = {
            if (chatGptConnected) {
              advanced.setChatGptPlanPreferred(true)
              onCreate()
            } else onAiSettings()
          },
          contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
        ) { Text(if (chatGptConnected) "Crear mazo" else "Conectar", maxLines = 1) }
      }
    }

    Row(Modifier.fillMaxWidth().weight(1f, fill = true), verticalAlignment = Alignment.CenterVertically) {
      OutlinedButton(onClick = onTools, modifier = Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)) {
        Text("Herramientas del juego", maxLines = 1, overflow = TextOverflow.Ellipsis)
      }
      if (draft?.cards?.isNotEmpty() == true) {
        TextButton(onClick = onEditor, contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp)) { Text("Continuar mazo", maxLines = 1) }
      } else if (message != null) {
        TextButton(onClick = main::clearCsvStatusMessage, contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp)) {
          Text(message!!, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
      } else {
        TextButton(onClick = onCreate, contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp)) { Text("Nuevo mazo", maxLines = 1) }
      }
    }
  }
}
