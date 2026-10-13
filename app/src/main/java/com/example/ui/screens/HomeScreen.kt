package com.example.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.util.DeckCodec
import com.example.ui.components.PocketCardImage
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

  val compact = LocalConfiguration.current.screenHeightDp < 760
  val pad = if (compact) 12.dp else 18.dp
  val gap = if (compact) 7.dp else 10.dp
  val imageHeight = if (compact) 76.dp else 102.dp
  val cardHeight = if (compact) 132.dp else 160.dp

  Column(
    modifier.fillMaxSize().padding(horizontal = pad, vertical = if (compact) 6.dp else 10.dp)
      .testTag("home_fixed"),
    verticalArrangement = Arrangement.SpaceBetween,
  ) {
    Row(Modifier.fillMaxWidth().height(if (compact) 44.dp else 52.dp), verticalAlignment = Alignment.CenterVertically) {
      Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
        Text("TCG Lab", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, maxLines = 1)
        Text("Pokémon TCG Pocket", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
      }
      IconButton(onClick = onSettings) {
        Icon(Icons.Filled.Settings, contentDescription = "Ajustes", tint = MaterialTheme.colorScheme.onSurfaceVariant)
      }
    }

    Card(
      onClick = { onCollection(null) },
      modifier = Modifier.fillMaxWidth().height(if (compact) 92.dp else 108.dp),
      shape = MaterialTheme.shapes.extraLarge,
      colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
      Row(Modifier.fillMaxSize().padding(horizontal = if (compact) 14.dp else 18.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(7.dp)) {
          Text("Colección", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
          Text("${distinct} de ${inventory.size} cartas · ${totalCards} copias",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1, overflow = TextOverflow.Ellipsis)
          LinearProgressIndicator(progress = { progress / 100f }, modifier = Modifier.fillMaxWidth().height(6.dp),
            trackColor = MaterialTheme.colorScheme.surface)
        }
        Column(horizontalAlignment = Alignment.End) {
          Text("${progress}%", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
          Text("completada", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
      }
    }

    OutlinedCard(onClick = onZoneSync, modifier = Modifier.fillMaxWidth().height(if (compact) 46.dp else 52.dp),
      shape = MaterialTheme.shapes.large) {
      Row(Modifier.fillMaxSize().padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
          Text("Pokémon Zone", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
          Text("Sincroniza colección, perfil y misiones", style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text("Sincronizar  ›", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
      }
    }

    Column(verticalArrangement = Arrangement.spacedBy(if (compact) 5.dp else 7.dp)) {
      Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text("Cartas destacadas", Modifier.weight(1f), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        TextButton(onClick = { onCollection(null) }, contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)) { Text("Ver colección") }
      }
      Row(Modifier.fillMaxWidth().height(cardHeight), horizontalArrangement = Arrangement.spacedBy(gap)) {
        if (showcase.isEmpty()) {
          OutlinedCard(onClick = { onCollection(null) }, modifier = Modifier.fillMaxSize(), shape = MaterialTheme.shapes.large) {
            Box(Modifier.fillMaxSize().padding(14.dp), contentAlignment = Alignment.CenterStart) {
              Text("Sin cartas todavía. Sincroniza Pokémon Zone para empezar.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
          }
        } else {
          showcase.forEach { entry ->
            OutlinedCard(onClick = { reading = entry }, modifier = Modifier.weight(1f).fillMaxHeight(),
              shape = MaterialTheme.shapes.large,
              colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
              Column(Modifier.fillMaxSize().padding(if (compact) 5.dp else 7.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                PocketCardImage(entry.card.id, entry.card.name, modifier = Modifier.fillMaxWidth().height(imageHeight))
                Text(entry.card.name, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold,
                  maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${entry.ownedCount} copias", style = MaterialTheme.typography.labelSmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
              }
            }
          }
        }
      }
    }

    Row(Modifier.fillMaxWidth().height(if (compact) 68.dp else 76.dp), horizontalArrangement = Arrangement.spacedBy(gap)) {
      OutlinedCard(onClick = onZoneSync, modifier = Modifier.weight(1f).fillMaxHeight(), shape = MaterialTheme.shapes.large) {
        Column(Modifier.fillMaxSize().padding(horizontal = 11.dp, vertical = 8.dp), verticalArrangement = Arrangement.SpaceBetween) {
          Text("Misiones", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
          Text("Leer perfil y progreso", style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
      }
      OutlinedCard(onClick = onDecks, modifier = Modifier.weight(1f).fillMaxHeight(), shape = MaterialTheme.shapes.large) {
        Column(Modifier.fillMaxSize().padding(horizontal = 11.dp, vertical = 8.dp), verticalArrangement = Arrangement.SpaceBetween) {
          Text("Mazos · ${decks.size}", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 1)
          val saved = decks.firstOrNull()
          val missing = saved?.let { deck -> deckAvailability.firstOrNull { it.first.id == deck.id }?.second }
          Text(
            when {
              draft?.cards?.isNotEmpty() == true -> "Borrador · ${draft?.name.orEmpty()}"
              saved == null -> "Diseña uno con ChatGPT"
              else -> "${saved.name} · ${saved.totalCards}/20" + (missing?.let { if (it == 0) " · listo" else " · faltan $it" } ?: "")
            },
            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1, overflow = TextOverflow.Ellipsis,
          )
        }
      }
    }

    Card(modifier = Modifier.fillMaxWidth().height(if (compact) 82.dp else 96.dp), shape = MaterialTheme.shapes.extraLarge,
      colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
      Row(Modifier.fillMaxSize().padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
          Text("Diseña tu próximo mazo", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
          Text(
            if (chatGptConnected) "ChatGPT analiza tus cartas y propone una estrategia."
            else "Conecta ChatGPT para crear un mazo desde tu colección.",
            style = MaterialTheme.typography.labelSmall, maxLines = 2, overflow = TextOverflow.Ellipsis,
          )
        }
        Spacer(Modifier.width(8.dp))
        Button(onClick = {
          if (chatGptConnected) {
            advanced.setChatGptPlanPreferred(true)
            onCreate()
          } else onAiSettings()
        }, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)) {
          Text(if (chatGptConnected) "Crear" else "Conectar", maxLines = 1)
        }
      }
    }

    Row(Modifier.fillMaxWidth().height(if (compact) 38.dp else 42.dp), verticalAlignment = Alignment.CenterVertically) {
      OutlinedButton(onClick = onTools, modifier = Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 10.dp, vertical = 5.dp)) {
        Text("Herramientas", maxLines = 1)
      }
      Spacer(Modifier.width(8.dp))
      if (draft?.cards?.isNotEmpty() == true) {
        TextButton(onClick = onEditor, contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp)) { Text("Continuar borrador", maxLines = 1) }
      } else if (message != null) {
        TextButton(onClick = main::clearCsvStatusMessage, contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp)) {
          Text(message!!, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
      } else {
        TextButton(onClick = onMeta, contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp)) { Text("Meta", maxLines = 1) }
      }
    }
  }
}
