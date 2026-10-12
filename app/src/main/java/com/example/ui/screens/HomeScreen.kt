package com.example.ui.screens

import androidx.compose.foundation.clickable
import com.example.ui.components.PocketCardImage
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
  onTrade: () -> Unit, onCollection: (String?) -> Unit, onTools: () -> Unit) {
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
  val style = prefs.visualStyle
  val album = style == VisualStyle.ALBUM
  val showcase = inventory.filter { it.ownedCount > 0 }.take(6)
  LazyColumn(modifier.fillMaxSize().testTag("home_list"), contentPadding = PaddingValues(if (style == VisualStyle.COMPACT) 12.dp else 16.dp), verticalArrangement = Arrangement.spacedBy(if (style == VisualStyle.COMPACT) 6.dp else 16.dp)) {
    item {
      Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        ProfileAvatar(prefs.avatarId, Modifier.size(36.dp).clip(CircleShape).clickable { chooseAvatar = true }, "Cambiar avatar")
        Column(Modifier.weight(1f)) {
          Text("Pocket Atlas", style = MaterialTheme.typography.titleLarge)
          Text("$distinct cartas · $copies copias · $progress%", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Box {
          TextButton(onClick = { chooseDesign = true }) { Text("Diseño") }

        }
      }
    }
    item {
      when (style) {
        VisualStyle.GALLERY -> {
          Text("Tu colección, en primer plano", style = MaterialTheme.typography.displayMedium)
          Text("Una vitrina con las cartas que ya son tuyas.", style = MaterialTheme.typography.bodyMedium)
        }
        VisualStyle.EDITORIAL -> {
          HorizontalDivider(thickness = 3.dp, color = MaterialTheme.colorScheme.primary)
          Text("ATLAS / TU PORTADA", Modifier.padding(top = 12.dp), style = MaterialTheme.typography.labelLarge)
          Text("Coleccionar.\nConstruir.\nIntercambiar.", style = MaterialTheme.typography.displayLarge)
        }
        VisualStyle.COMPACT -> {
          Text("Panel rápido", style = MaterialTheme.typography.titleLarge)
          Text("$distinct/${inventory.size} cartas · ${decks.size} mazos", style = MaterialTheme.typography.bodySmall)
        }
        VisualStyle.DECISIONS -> {
          Text("¿Qué hacemos hoy?", style = MaterialTheme.typography.displayMedium)
          Text("Tus objetivos y las acciones para avanzar.", style = MaterialTheme.typography.bodyMedium)
        }
        VisualStyle.ALBUM -> {
          Text("Mi álbum", style = MaterialTheme.typography.displayLarge)
          Text("$progress% del catálogo en tu colección", style = MaterialTheme.typography.bodyLarge)
          LinearProgressIndicator(progress = { progress / 100f }, modifier = Modifier.fillMaxWidth().padding(top = 12.dp))
        }
      }
    }
    if (style == VisualStyle.GALLERY) item {
      LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        items(showcase, key = { it.card.id }) { card ->
          Column(Modifier.width(142.dp).clickable { onCollection(card.card.id.substringBeforeLast('-')) }) {
            PocketCardImage(card.card.id, card.card.name, modifier = Modifier.fillMaxWidth().aspectRatio(0.716f))
            Text(card.card.name, style = MaterialTheme.typography.titleSmall)
            Text("${card.ownedCount} copias", style = MaterialTheme.typography.bodySmall)
          }
        }
      }
      if (showcase.isEmpty()) OutlinedButton(onClick = { onCollection(null) }) { Text("Explorar cartas") }
      Button(onClick = { onCollection(null) }, modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) { Text("Abrir mi colección") }
    }
    if (style == VisualStyle.EDITORIAL) item {
      Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
          Text("01 / COLECCIÓN", style = MaterialTheme.typography.labelLarge)
          Text("$distinct", style = MaterialTheme.typography.displayLarge)
          Text("cartas diferentes de ${inventory.size}")
          TextButton(onClick = { onCollection(null) }) { Text("Explorar →") }
          HorizontalDivider()
          Text("02 / ESTRATEGIA", style = MaterialTheme.typography.labelLarge)
          TextButton(onClick = onCreate) { Text("Construir un mazo →") }
        }
        showcase.firstOrNull()?.let { card ->
          PocketCardImage(card.card.id, card.card.name, modifier = Modifier.weight(1f).aspectRatio(0.716f))
        }
      }
    }
    if (style == VisualStyle.DECISIONS) item {
      Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        HomeActionTile("Intercambiar", "${advice.offers.size} cartas para ofrecer", Modifier.weight(1f), onTrade)
        HomeActionTile("Construir", "${decks.size} mazos guardados", Modifier.weight(1f), onCreate)
      }
      Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        HomeActionTile("Completar", "${inventory.size - distinct} cartas faltantes", Modifier.weight(1f)) { onCollection(null) }
        HomeActionTile("Jugar", "Práctica, sobres y cálculo", Modifier.weight(1f), onTools)
      }
    }
    if (style == VisualStyle.COMPACT) item {
      Column {
        HomeQuickRow("Colección", "$distinct cartas · $progress%", { onCollection(null) })
        HomeQuickRow("Mazos", "${decks.size} guardados", onDecks)
        HomeQuickRow("Intercambios", "${advice.offers.size} para ofrecer", onTrade)
        HomeQuickRow("Herramientas del juego", "Práctica, sobres, probabilidades", onTools)
      }
    }
    if (album) {
      item { Text("Expansiones", style = MaterialTheme.typography.titleMedium) }
      items(expansions.take(6).chunked(2), key = { "exp_${it.first().key}" }) { pair ->
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
          pair.forEach { group ->
            val count = group.value.count { it.ownedCount > 0 }
            Card(onClick = { onCollection(group.key) }, modifier = Modifier.weight(1f)) {
              Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(group.key, style = MaterialTheme.typography.headlineLarge)
                val cover = group.value.firstOrNull { it.ownedCount == 0 } ?: group.value.first()
                PocketCardImage(cover.card.id, cover.card.name, modifier = Modifier.fillMaxWidth().aspectRatio(0.9f))
                Text("$count/${group.value.size}", style = MaterialTheme.typography.titleMedium)
                LinearProgressIndicator(progress = { count.toFloat() / group.value.size }, modifier = Modifier.fillMaxWidth())
                Text("${group.value.size - count} faltantes", style = MaterialTheme.typography.bodySmall)
              }
            }
          }
          if (pair.size == 1) Spacer(Modifier.weight(1f))
        }
      }
      item { TextButton(onClick = { onCollection(null) }) { Text("Ver todas las expansiones") } }
    }
    item {
      Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = if (style == VisualStyle.EDITORIAL || style == VisualStyle.COMPACT) MaterialTheme.colorScheme.surfaceContainer else MaterialTheme.colorScheme.primaryContainer)) {
        Column(Modifier.padding(if (style == VisualStyle.COMPACT) 10.dp else 16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
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
      TextButton(onClick = onTools, modifier = Modifier.fillMaxWidth()) { Text("Abrir herramientas del juego") }
      HorizontalDivider()
      TextButton(onClick = onMeta, modifier = Modifier.fillMaxWidth()) { Text("Ver meta y actualizar") }
      Text(snapshot?.let { "Muestra de ${it.tournaments} torneos · ${it.players} listas · actualizada ${it.updated.take(10)}" }
        ?: "Resultados públicos para comparar mazos", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
  }
  if (chooseDesign) AlertDialog(onDismissRequest = { chooseDesign = false },
    title = { Text("Elige tu pantalla de inicio") },
    text = {
      LazyColumn(Modifier.testTag("design_picker"), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        items(VisualStyle.entries.toList()) { option ->
          OutlinedCard(onClick = { main.setVisualStyle(option); chooseDesign = false }, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
              Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(selected = style == option, onClick = null)
                Text(option.label, style = MaterialTheme.typography.titleSmall)
              }
              DesignMiniature(option)
              Text(option.description, style = MaterialTheme.typography.bodySmall)
            }
          }
        }
      }
    }, confirmButton = { TextButton(onClick = { chooseDesign = false }) { Text("Cerrar") } })
  if (chooseAvatar) AvatarPickerDialog(prefs.avatarId, onSave = { main.setProfileAvatar(it); chooseAvatar = false }, onClose = { chooseAvatar = false })
}

@Composable
private fun HomeActionTile(title: String, detail: String, modifier: Modifier, onClick: () -> Unit) {
  FilledTonalButton(onClick = onClick, modifier = modifier, shape = MaterialTheme.shapes.large, contentPadding = PaddingValues(16.dp)) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
      Text(title, style = MaterialTheme.typography.titleLarge)
      Text(detail, style = MaterialTheme.typography.bodySmall)
    }
  }
}

@Composable
private fun HomeQuickRow(title: String, detail: String, onClick: () -> Unit) {
  Surface(onClick = onClick) {
    Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
      Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
      Text(detail, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
      Text(" ›")
    }
  }
  HorizontalDivider()
}

@Composable
internal fun DesignMiniature(style: VisualStyle) {
  @Composable fun Block(modifier: Modifier) {
    Surface(modifier, color = MaterialTheme.colorScheme.primaryContainer, shape = MaterialTheme.shapes.small) {}
  }
  when (style) {
    VisualStyle.GALLERY -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { repeat(3) { Block(Modifier.weight(1f).height(72.dp)) } }
    VisualStyle.EDITORIAL -> Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
      Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("ATLAS", style = MaterialTheme.typography.headlineLarge)
        HorizontalDivider(thickness = 3.dp)
        Text("Colección / Estrategia", style = MaterialTheme.typography.labelSmall)
      }
      Block(Modifier.width(50.dp).height(72.dp))
    }
    VisualStyle.COMPACT -> Column(verticalArrangement = Arrangement.spacedBy(4.dp)) { repeat(4) { Block(Modifier.fillMaxWidth().height(12.dp)) } }
    VisualStyle.DECISIONS -> Column(verticalArrangement = Arrangement.spacedBy(6.dp)) { repeat(2) {
      Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { repeat(2) { Block(Modifier.weight(1f).height(28.dp)) } }
    } }
    VisualStyle.ALBUM -> Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) { repeat(2) {
      Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Block(Modifier.fillMaxWidth().height(60.dp)); LinearProgressIndicator(progress = { 0.6f }, modifier = Modifier.fillMaxWidth())
      }
    } }
  }
}
