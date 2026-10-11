package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.repository.ParsedCsvCard
import com.example.data.util.*
import com.example.ui.viewmodel.TcgViewModel
import kotlinx.coroutines.*

@Composable
fun TradeAdvisorScreen(main: TcgViewModel, modifier: Modifier = Modifier) {
  val inventory by main.inventoryList.collectAsStateWithLifecycle()
  val decks by main.savedDecks.collectAsStateWithLifecycle()
  val prefs by main.userPreferences.collectAsStateWithLifecycle()
  val settings = prefs.trade
  var tab by rememberSaveable { mutableIntStateOf(0) }
  var query by rememberSaveable { mutableStateOf("") }
  var peer by remember { mutableStateOf<List<ParsedCsvCard>?>(null) }
  var error by remember { mutableStateOf<String?>(null) }
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
    if (uri != null) scope.launch {
      try {
        val parsed = withContext(Dispatchers.IO) {
          context.contentResolver.openInputStream(uri)?.use { CollectionCsv.parse(it.readBytesBounded(8_000_000).toString(Charsets.UTF_8)) }
            ?: error("No se pudo abrir el CSV")
        }
        peer = parsed; error = null
      } catch (e: CancellationException) { throw e }
      catch (e: Exception) { error = e.localizedMessage ?: "No se pudo comparar el archivo" }
    }
  }
  val advice = remember(inventory, decks, settings, peer) { TradeAdvisor.analyze(inventory, decks, settings, peer) }
  LazyColumn(modifier.fillMaxSize().testTag("trade_advisor_list"), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
    item {
      Text("Asesor de intercambios", style = MaterialTheme.typography.headlineMedium)
      Text("Elige qué conseguir y qué copias puedes ofrecer sin desarmar tus mazos.", style = MaterialTheme.typography.bodyMedium)
    }
    item {
      TradeGoalPicker("Mazo objetivo", decks.firstOrNull { it.cardListSerialized == settings.targetDeck }?.name ?: "Ninguno",
        listOf("Ninguno" to "") + decks.map { it.name to it.cardListSerialized }) { main.setTradeSettings(settings.copy(targetDeck = it)) }
      TradeGoalPicker("Expansión objetivo", settings.expansion.ifBlank { "Todas" },
        listOf("Todas" to "") + inventory.map { it.card.id.substringBeforeLast('-') }.distinct().sorted().map { it to it }) {
        main.setTradeSettings(settings.copy(expansion = it))
      }
      Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
        Text("Conservar dos copias por carta", Modifier.weight(1f))
        Switch(checked = settings.reserve == 2, onCheckedChange = { main.setTradeSettings(settings.copy(reserve = if (it) 2 else 1)) })
      }
      Text("Tus mazos conservan sus copias exactas aunque reduzcas la reserva. No se suman reservas entre mazos que comparten cartas.", style = MaterialTheme.typography.bodySmall)
      if (settings.targetDeck.isNotBlank()) Text("${advice.deckMissing} copias faltantes para el mazo objetivo")
    }
    item {
      Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf("Recomendados", "Para ofrecer", "Qué conseguir", "Protección").forEachIndexed { n, label ->
          FilterChip(selected = tab == n, onClick = { tab = n; query = "" }, label = { Text(label) })
        }
      }
    }
    item {
      OutlinedButton(onClick = { picker.launch("*/*") }) { Text("Comparar colección de un amigo") }
      peer?.let {
        Text("${it.size} registros comparados. Un cero explícito indica un faltante; una carta omitida no se supone faltante.", style = MaterialTheme.typography.bodySmall)
        TextButton(onClick = { peer = null }) { Text("Quitar comparación") }
      }
      error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
      Text("Comprueba elegibilidad, rareza, idioma, marco, efecto decorativo y coste en el juego. Las propuestas no se ejecutan ni descuentan cartas. Sin CSV ajeno son opciones para buscar, no ofertas disponibles.", style = MaterialTheme.typography.bodySmall)
      if (advice.invalidDecks > 0) Text("Hay ${advice.invalidDecks} mazos que no se pudieron leer. Revisa sus listas antes de ofrecer cartas.", color = MaterialTheme.colorScheme.error)
    }
    if (tab == 0) {
      if (advice.choices.isEmpty()) item { Text("No hay parejas con la misma rareza y copias disponibles. Revisa tus objetivos, reserva o comparación.") }
      items(advice.choices, key = { it.request.card.id }) { choice -> TradeChoiceCard(choice) }
      if (advice.choices.isNotEmpty()) item { Text("Cada opción se evalúa por separado; varias pueden usar la misma copia sobrante. Se muestran hasta 30 objetivos.", style = MaterialTheme.typography.bodySmall) }
    } else {
      item { OutlinedTextField(query, { query = it }, label = { Text("Buscar nombre o código") }, modifier = Modifier.fillMaxWidth(), singleLine = true) }
      val rows = (when(tab) { 1 -> advice.offers; 2 -> advice.wanted; else -> inventory.filter { it.ownedCount > 0 } })
        .filter { query.isBlank() || it.card.name.contains(query, true) || it.card.id.contains(query, true) }
        .sortedByDescending { tab == 3 && it.card.id in settings.blocked }
      item { Text("${rows.size} cartas") }
      items(rows, key = { it.card.id }) { item ->
        OutlinedCard(Modifier.fillMaxWidth()) {
          Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            DeckThumbnail(item.card.id, item.card.name)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
              Text(item.card.name, style = MaterialTheme.typography.titleSmall)
              Text("${item.card.id} · ${item.card.rarity.symbol} · tienes ${item.ownedCount}", style = MaterialTheme.typography.bodySmall)
              if (tab == 1) Text("Puedes ofrecer ${item.ownedCount - maxOf(settings.reserve, advice.protected[item.card.id] ?: 0)} copias", style = MaterialTheme.typography.bodySmall)
              if (tab == 2 && item.isWishlist) Text("En tus Deseos")
              if (tab == 3 || tab == 1) {
                val blocked = item.card.id in settings.blocked
                if ((advice.protected[item.card.id] ?: 0) > 0) Text("Reservadas para mazos: ${advice.protected[item.card.id]}", style = MaterialTheme.typography.bodySmall)
                TextButton(onClick = { main.setTradeSettings(settings.copy(blocked = if (blocked) settings.blocked - item.card.id else settings.blocked + item.card.id)) }) {
                  Text(if (blocked) "Desbloquear" else "Bloquear para intercambio")
                }
              }
            }
          }
        }
      }
    }
  }
}

@Composable
fun TradeGoalPicker(label: String, current: String, options: List<Pair<String, String>>, onSelect: (String) -> Unit) {
  var expanded by remember { mutableStateOf(false) }
  Box {
    OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) { Text("$label: $current") }
    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
      options.forEach { (name, value) -> DropdownMenuItem(text = { Text(name) }, onClick = { expanded = false; onSelect(value) }) }
    }
  }
}

@Composable
fun TradeChoiceCard(choice: TradeChoice, compact: Boolean = false, onOpen: (() -> Unit)? = null) {
  val context = LocalContext.current
  var copied by remember(choice) { mutableStateOf(false) }
  OutlinedCard(Modifier.fillMaxWidth()) {
    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
      Text(if (choice.peerMatched) "Coincidencia con el CSV" else "Opción para buscar", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
      Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        listOf("Ofrece" to choice.offer, "Pide" to choice.request).forEach { (label, item) ->
          Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(label, style = MaterialTheme.typography.labelLarge)
            if (!compact) DeckThumbnail(item.card.id, "$label ${item.card.name}")
            Text(item.card.name, style = MaterialTheme.typography.titleSmall)
            Text("${item.card.id} · ${item.card.rarity.symbol}", style = MaterialTheme.typography.bodySmall)
          }
        }
      }
      Text("Te quedan ${choice.remaining} copias de ${choice.offer.card.name}", style = MaterialTheme.typography.bodySmall)
      Text(choice.reasons.take(if (compact) 1 else 4).joinToString(" · "), style = MaterialTheme.typography.bodySmall)
      if (onOpen != null) TextButton(onClick = onOpen) { Text("Revisar intercambio") }
      else TextButton(onClick = {
        (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("Propuesta de intercambio", "Ofrezco ${choice.offer.card.id} ${choice.offer.card.name} por ${choice.request.card.id} ${choice.request.card.name}. Confirmar elegibilidad y coste en el juego.")); copied = true
      }) { Text(if (copied) "Propuesta copiada" else "Copiar propuesta") }
    }
  }
}
