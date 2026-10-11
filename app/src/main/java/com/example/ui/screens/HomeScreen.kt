package com.example.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.preferences.VisualStyle
import com.example.data.util.DeckCodec
import com.example.data.util.TradeAdvisor
import com.example.ui.components.AvatarPickerDialog
import com.example.ui.components.ProfileAvatar
import com.example.ui.viewmodel.AdvancedViewModel
import com.example.ui.viewmodel.TcgViewModel

@Composable
fun HomeScreen(main: TcgViewModel, advanced: AdvancedViewModel, modifier: Modifier = Modifier,
  onDecks: () -> Unit, onEditor: () -> Unit, onCreate: () -> Unit, onMeta: () -> Unit,
  onTrade: () -> Unit, onCollection: (String?) -> Unit) {
  val inventory by main.inventoryList.collectAsStateWithLifecycle()
  val decks by main.savedDecks.collectAsStateWithLifecycle()
  val draft by main.generatedDeck.collectAsStateWithLifecycle()
  val prefs by main.userPreferences.collectAsStateWithLifecycle()
  val snapshot by advanced.metaSnapshot.collectAsStateWithLifecycle()
  val message by main.csvStatusMessage.collectAsStateWithLifecycle()
  val openSavedDeck = com.example.ui.components.rememberSavedDeckOpener(main, onEditor)
  var chooseAvatar by remember { mutableStateOf(false) }
  var chooseDesign by remember { mutableStateOf(false) }
  val advice = remember(inventory, decks, prefs.trade) { TradeAdvisor.analyze(inventory, decks, prefs.trade) }
  val owned = remember(inventory) { inventory.associate { it.card.id to it.ownedCount } }
  val distinct = remember(inventory) { inventory.count { it.ownedCount > 0 } }
  val copies = remember(inventory) { inventory.sumOf { it.ownedCount } }
  val progress = if (inventory.isEmpty()) 0 else distinct * 100 / inventory.size
  val expansions = remember(inventory, prefs.trade.expansion) {
    inventory.filter { !it.card.id.startsWith("PROMO") }.groupBy { it.card.id.substringBeforeLast('-') }
      .entries.sortedWith(compareByDescending<Map.Entry<String, List<com.example.data.repository.CardWithInventory>>> { it.key == prefs.trade.expansion }
        .thenByDescending { group -> group.value.count { it.ownedCount > 0 }.toFloat() / group.value.size })
  }
  val deckAvailability = remember(decks, owned) { decks.mapNotNull { saved ->
    runCatching { DeckCodec.references(saved.cardListSerialized) }.getOrNull()?.let { refs ->
      saved to refs.sumOf { (id, n) -> (n - (owned[id] ?: 0)).coerceAtLeast(0) }
    }
  }.sortedBy { it.second } }
  val target = decks.firstOrNull { it.cardListSerialized == prefs.trade.targetDeck }
  val album = prefs.visualStyle == VisualStyle.ALBUM
  LazyColumn(modifier.fillMaxSize().testTag("home_list"), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(if (album) 16.dp else 12.dp)) {
    item {
      Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        ProfileAvatar(prefs.avatarId, Modifier.size(36.dp).clip(CircleShape).clickable { chooseAvatar = true }, "Cambiar avatar")
        Column(Modifier.weight(1f)) {
          Text("Pocket Atlas", style = MaterialTheme.typography.titleLarge)
          Text("$distinct cartas · $copies copias · $progress%", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Box {
          TextButton(onClick = { chooseDesign = true }) { Text("Diseño") }
          DropdownMenu(chooseDesign, onDismissRequest = { chooseDesign = false }) {
            VisualStyle.entries.forEach { style -> DropdownMenuItem(text = { Text(style.label) }, onClick = { main.setVisualStyle(style); chooseDesign = false }) }
          }
        }
      }
    }
    item {
      Text(if (album) "Mi álbum" else "Tu próximo paso", style = MaterialTheme.typography.headlineMedium)
      Text(if (album) "Explora expansiones y elige qué completar." else "Decide qué conseguir con las cartas que ya tienes.", style = MaterialTheme.typography.bodyMedium)
    }
    if (album) {
      item { Text("Expansiones", style = MaterialTheme.typography.titleMedium) }
      items(expansions.take(6), key = { "exp_${it.key}" }) { group ->
        val count = group.value.count { it.ownedCount > 0 }
        OutlinedCard(onClick = { onCollection(group.key) }, modifier = Modifier.fillMaxWidth()) {
          Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            group.value.firstOrNull { it.ownedCount == 0 }?.let { DeckThumbnail(it.card.id, "Faltante de ${group.key}") }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
              Text(group.key, style = MaterialTheme.typography.titleLarge)
              Text("$count/${group.value.size} cartas · ${group.value.size - count} faltantes")
              LinearProgressIndicator(progress = { count.toFloat() / group.value.size }, modifier = Modifier.fillMaxWidth())
              Text("Ver faltantes y colección ›", style = MaterialTheme.typography.labelMedium)
            }
          }
        }
      }
      item { TextButton(onClick = { onCollection(null) }) { Text("Ver todas las expansiones") } }
    }
    item {
      Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
          Text("Tu objetivo", style = MaterialTheme.typography.labelLarge)
          Text(target?.name ?: prefs.trade.targetName.takeIf { it.isNotBlank() } ?: (if (prefs.trade.targetDeck.isNotBlank()) "Mazo objetivo guardado" else null) ?: prefs.trade.expansion.takeIf { it.isNotBlank() }?.let { "Completar $it" } ?: "Elige un mazo o una expansión", style = MaterialTheme.typography.titleLarge)
          Text(if (prefs.trade.targetDeck.isNotBlank()) "${advice.deckMissing} copias faltantes · tus mazos están protegidos"
            else "${advice.offers.size} cartas con copias disponibles para ofrecer", style = MaterialTheme.typography.bodySmall)
          TextButton(onClick = onTrade) { Text("Elegir objetivo e intercambios") }
        }
      }
    }
    item {
      Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text("Intercambios para ti", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
        TextButton(onClick = onTrade) { Text("Ver opciones") }
      }
      if (advice.choices.isEmpty()) Text("Protege tus cartas y revisa qué conseguir. Las propuestas aparecerán cuando tengas sobrantes compatibles.", style = MaterialTheme.typography.bodySmall)
    }
    items(advice.choices.take(if (prefs.visualStyle == VisualStyle.DECISIONS) 2 else 1), key = { "trade_${it.request.card.id}" }) {
      TradeChoiceCard(it, compact = prefs.visualStyle != VisualStyle.DECISIONS, onOpen = onTrade)
    }
    item {
      Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = onCreate, modifier = Modifier.weight(1f)) { Text("Crear mazo") }
        OutlinedButton(onClick = { onCollection(null) }, modifier = Modifier.weight(1f)) { Text("Colección") }
      }
    }
    if (deckAvailability.isNotEmpty()) {
      item { Text("Mazos y cartas disponibles", style = MaterialTheme.typography.titleMedium) }
      items(deckAvailability.take(2), key = { "ready_${it.first.id}" }) { (saved, missing) ->
        OutlinedCard(onClick = { openSavedDeck(saved) }, modifier = Modifier.fillMaxWidth()) {
          Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(saved.name, style = MaterialTheme.typography.titleMedium)
            Text(if (missing == 0) "Tienes todas las cartas de esta lista" else "Faltan $missing copias para completar esta lista", style = MaterialTheme.typography.bodySmall)
            Text("${saved.totalCards}/20 cartas · revisar mazo ›", style = MaterialTheme.typography.labelMedium)
          }
        }
      }
    }
    draft?.takeIf { it.cards.isNotEmpty() }?.let { deck -> item {
      OutlinedButton(onClick = onEditor, modifier = Modifier.fillMaxWidth()) { Text("Continuar ${deck.name} · ${deck.totalCardCount}/20") }
    } }
    message?.let { text -> item {
      Text(text, style = MaterialTheme.typography.bodySmall)
      TextButton(onClick = main::clearCsvStatusMessage) { Text("Cerrar") }
    } }
    item {
      Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text("Mazos recientes", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
        TextButton(onClick = onDecks) { Text("Ver todos") }
      }
      if (decks.isEmpty()) Text("Crea tu primer mazo con las cartas de tu colección.")
    }
    if (prefs.visualStyle == VisualStyle.DECISIONS || prefs.visualStyle == VisualStyle.COMPACT) {
      items(decks.take(3), key = { "recent_${it.id}" }) { saved ->
        TextButton(onClick = { openSavedDeck(saved) }, modifier = Modifier.fillMaxWidth()) { Text("${saved.name} · ${saved.totalCards}/20 ›") }
      }
    } else item {
      LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        items(decks.take(3), key = { it.id }) { saved ->
          OutlinedCard(onClick = { openSavedDeck(saved) }, modifier = Modifier.width(240.dp)) {
            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
              runCatching { DeckCodec.references(saved.cardListSerialized) }.getOrNull()?.firstOrNull()?.let { DeckThumbnail(it.first, saved.name) }
              Column(Modifier.weight(1f)) { Text(saved.name, style = MaterialTheme.typography.titleSmall); Text("${saved.totalCards}/20 cartas", style = MaterialTheme.typography.bodySmall) }
            }
          }
        }
      }
    }
    item {
      HorizontalDivider()
      TextButton(onClick = onMeta, modifier = Modifier.fillMaxWidth()) { Text("Ver meta y actualizar") }
      Text(snapshot?.let { "Muestra de ${it.tournaments} torneos · ${it.players} listas · actualizada ${it.updated.take(10)}" }
        ?: "Resultados públicos para comparar mazos", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
  }
  if (chooseAvatar) AvatarPickerDialog(prefs.avatarId, onSave = { main.setProfileAvatar(it); chooseAvatar = false }, onClose = { chooseAvatar = false })
}
