package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.util.DeckCodec
import com.example.domain.*
import com.example.ui.components.DexPanel
import com.example.ui.viewmodel.*

@Composable
fun DeckChatScreen(main: TcgViewModel, model: AdvancedViewModel, onBack: () -> Unit,
  onConnections: () -> Unit, modifier: Modifier = Modifier, creating: Boolean = false, initialAction: String = "chat") {
  val deck by main.generatedDeck.collectAsStateWithLifecycle()
  val messages by model.deckChatMessages.collectAsStateWithLifecycle()
  val suggestion by model.deckChatSuggestion.collectAsStateWithLifecycle()
  val status by model.deckChatStatus.collectAsStateWithLifecycle()
  val busy by model.busy.collectAsStateWithLifecycle()
  val retryAvailable by model.deckChatRetryAvailable.collectAsStateWithLifecycle()
  val connection by model.connection.collectAsStateWithLifecycle()
  val ready by model.connectionReady.collectAsStateWithLifecycle()
  val current = deck ?: return
  var input by rememberSaveable(creating, initialAction) { mutableStateOf(when(initialAction) {
    "complete" -> "Completa este mazo conservando mis cartas disponibles."
    "improve" -> "Mejora la consistencia de este mazo y explica los cambios."
    else -> ""
  }) }
  LaunchedEffect(messages.lastOrNull()) { if (messages.lastOrNull()?.user == false) input = "" }
  var energy by rememberSaveable { mutableStateOf(current.energyTypes.firstOrNull() ?: "") }
  var style by rememberSaveable { mutableStateOf("Equilibrado") }
  var reviewing by rememberSaveable { mutableStateOf(false) }
  val listState = rememberLazyListState()
  LaunchedEffect(current) { model.startDeckChat(current); reviewing = false }
  LaunchedEffect(Unit) { com.example.data.util.AppDiagnostics.screen("DECK_CHAT") }
  LaunchedEffect(messages.size) { if (messages.isNotEmpty()) listState.animateScrollToItem(2 + messages.lastIndex) }
  BackHandler { if (reviewing) reviewing = false else onBack() }
  if (reviewing && suggestion != null) {
    DeckProposalReview(main, model, suggestion!!, { reviewing = false }, onBack, modifier)
    return
  }
  Column(modifier.fillMaxSize().imePadding()) {
    TextButton(onClick = onBack) { Text(if (creating) "← Crear mazo" else "← Editor del mazo") }
    LazyColumn(Modifier.weight(1f).fillMaxWidth().testTag("deck_chat_list"), state = listState,
      contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
      item { Text(if (creating) "Crear con IA" else "Chat del mazo", style = MaterialTheme.typography.headlineSmall) }
      item { DexPanel(Modifier.fillMaxWidth()) { Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(current.name, style = MaterialTheme.typography.titleMedium)
        Text("${current.totalCardCount}/20 · " + current.energyTypes.joinToString().ifBlank { "Energías por elegir" }, style = MaterialTheme.typography.bodySmall)
        Text(if (connection.apiKey.isBlank()) "Configura tu conexión para conversar" else "${connection.label} · ${connection.model}", style = MaterialTheme.typography.bodySmall)
        QuickAiSwitch(model)
        OutlinedButton(enabled = ready && !busy, onClick = onConnections, shape = MaterialTheme.shapes.medium) { Text("Conexiones") }
      } } }
      if (creating && messages.isEmpty()) item {
        Text("Describe el mazo que quieres", style = MaterialTheme.typography.titleMedium)
        DeckChoice("Energía", energy, listOf("" to "Todas") + DeckCodec.energyNames.map { it to it }) { energy = it }
        DeckChoice("Estilo", style, listOf("Equilibrado", "Agresivo", "Control").map { it to it }) { style = it }
      }
      items(messages) { row ->
        Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor =
          if (row.user) MaterialTheme.colorScheme.surfaceContainerHigh else MaterialTheme.colorScheme.surface)) {
          Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(if (row.user) "Tú" else "Asistente", color = if (row.user) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary,
              style = MaterialTheme.typography.labelLarge)
            Text(row.text)
          }
        }
      }
      if (messages.isEmpty() && !creating) item { Text("Pregunta por estrategias o pide cambios. La IA recibe el mazo abierto, las energías y tu colección disponible.") }
      suggestion?.let { result -> item {
        DexPanel(Modifier.fillMaxWidth()) { Column(Modifier.padding(12.dp)) {
          Text("Propuesta lista para revisar", style = MaterialTheme.typography.titleMedium)
          Text("${result.proposal.deck.totalCardCount}/20 · ${result.proposal.deck.energyTypes.joinToString()}")
          Button(onClick = { reviewing = true }, enabled = !busy, modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium) { Text("Ver cambios") }
          Text("Tu borrador todavía conserva la lista anterior.", style = MaterialTheme.typography.bodySmall)
        } }
      } }
      status?.let { item { Text(it, style = MaterialTheme.typography.bodySmall) } }
      if (retryAvailable && !busy) item { OutlinedButton(onClick = { model.retryDeckChat(current) }) { Text("Reintentar consulta") } }
      if (busy) item {
        LinearProgressIndicator(Modifier.fillMaxWidth())
        TextButton(onClick = { model.cancel(); model.deckChatStatus.value = "Consulta cancelada. Puedes volver a enviar tu mensaje." }) { Text("Cancelar consulta") }
      }
    }
    Surface {
      Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        OutlinedTextField(input, { input = it.take(2000) }, enabled = !busy, modifier = Modifier.fillMaxWidth().testTag("deck_chat_input"),
          label = { Text(if (creating && messages.isEmpty()) "Idea para el mazo" else "Mensaje para la IA") }, maxLines = 4)
        Button(enabled = ready && !busy && input.isNotBlank() && connection.apiKey.isNotBlank(), onClick = {
          val text = input
          model.sendDeckChat(current, if (creating) "$text\nEstilo: $style" else text,
            if (creating) "create" else initialAction, if (creating) energy else "")
          // Keep the instructions available after errors and cancellation.
        }, modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium) {
          Text(if (creating && messages.isEmpty()) "Generar propuesta" else "Enviar")
        }
      }
    }
  }
}

@Composable
private fun DeckProposalReview(main: TcgViewModel, model: AdvancedViewModel, suggestion: DeckChatSuggestion,
  onChat: () -> Unit, onApplied: () -> Unit, modifier: Modifier) {
  val inventory by main.inventoryList.collectAsStateWithLifecycle()
  val current by main.generatedDeck.collectAsStateWithLifecycle()
  val owned = remember(inventory) { inventory.associate { it.card.id to it.ownedCount } }
  val proposed = suggestion.proposal.deck
  val (removed, added) = remember(suggestion) { DeckChat.changes(suggestion.base, proposed) }
  var error by remember { mutableStateOf<String?>(null) }
  var applying by remember { mutableStateOf(false) }
  val valid = current == suggestion.base && runCatching { DeckChat.requireApplicable(suggestion, current, owned) }.isSuccess
  Column(modifier.fillMaxSize()) {
    TextButton(onClick = onChat) { Text("← Chat del mazo") }
    LazyColumn(Modifier.weight(1f).fillMaxWidth(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
      item { Text("Revisar cambios", style = MaterialTheme.typography.headlineSmall)
        Text(proposed.name, style = MaterialTheme.typography.titleMedium)
        Text("Antes: ${suggestion.base.totalCardCount}/20 → Después: ${proposed.totalCardCount}/20") }
      item { Text("Quitar", style = MaterialTheme.typography.titleMedium)
        if (removed.isEmpty()) Text("No se quitan cartas.", style = MaterialTheme.typography.bodySmall) }
      items(removed, key = { "remove-${it.id}" }) { change ->
        val card = suggestion.base.cards.first { it.card.id == change.id }.card
        DeckChangeRow(card.id, card.name, change.count, "${owned[card.id] ?: 0} en colección")
      }
      item { Text("Añadir", style = MaterialTheme.typography.titleMedium)
        if (added.isEmpty()) Text("No se añaden cartas.", style = MaterialTheme.typography.bodySmall) }
      items(added, key = { "add-${it.id}" }) { change ->
        val entry = proposed.cards.first { it.card.id == change.id }
        DeckChangeRow(entry.card.id, entry.card.name, change.count, "Tienes ${owned[entry.card.id] ?: 0} · Se usarán ${entry.count}")
      }
      item { DexPanel(Modifier.fillMaxWidth()) { Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Motivo y estrategia", style = MaterialTheme.typography.titleMedium)
        Text(proposed.strategy)
        Text("Energías: ${suggestion.base.energyTypes.joinToString().ifBlank { "Sin elegir" }} → ${proposed.energyTypes.joinToString()}")
        Text("20 cartas · máximo dos copias por nombre · colección comprobada", style = MaterialTheme.typography.bodySmall)
        if (!valid) Text("El mazo o las copias disponibles cambió. Consulta de nuevo.", color = MaterialTheme.colorScheme.error)
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
      } } }
    }
    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
      Button(enabled = valid && !applying, onClick = {
        applying = true
        main.applyDeckChatSuggestion(suggestion, onApplied = { model.discardDeckChatSuggestion(); applying = false; onApplied() },
          onRejected = { error = it; applying = false })
      }, modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium) { Text("Aplicar cambios") }
      OutlinedButton(onClick = onChat, enabled = !applying, modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium) { Text("Seguir conversando") }
    }
  }
}

@Composable
private fun DeckChangeRow(id: String, name: String, count: Int, detail: String) {
  DexPanel(Modifier.fillMaxWidth()) { Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(12.dp)) {
    DeckThumbnail(id, name)
    Column(Modifier.weight(1f)) { Text("$count × $name", style = MaterialTheme.typography.titleSmall); Text(detail, style = MaterialTheme.typography.bodySmall) }
  } }
}
