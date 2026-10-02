package com.example.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.domain.*
import com.example.ui.viewmodel.*

@Composable
private fun AdvancedStatus(model: AdvancedViewModel) {
  val busy by model.busy.collectAsStateWithLifecycle()
  val message by model.message.collectAsStateWithLifecycle()
  if (busy) {
    LinearProgressIndicator(Modifier.fillMaxWidth())
    TextButton(onClick = model::cancel) { Text("Cancelar operación") }
  }
  message?.let { Text(it) }
}

@Composable
fun AIAssistantScreen(main: TcgViewModel, model: AdvancedViewModel, onOpenDeck: () -> Unit, modifier: Modifier = Modifier) {
  val deck by main.generatedDeck.collectAsStateWithLifecycle()
  val proposal by model.proposal.collectAsStateWithLifecycle()
  val busy by model.busy.collectAsStateWithLifecycle()
  var endpoint by remember { mutableStateOf(model.endpoint) }
  var token by remember { mutableStateOf(model.token) }
  var meta by rememberSaveable { mutableStateOf(model.meta) }
  var goal by rememberSaveable { mutableStateOf(model.goal) }
  var candidateType by rememberSaveable { mutableStateOf(model.candidateType) }
  var confirmMode by remember { mutableStateOf<Boolean?>(null) }
  var confirmOpen by remember { mutableStateOf(false) }
  LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
    item {
      Text("Asistente IA", style = MaterialTheme.typography.titleLarge)
      Text("Propuestas de mazos y sustituciones con tus cartas. Se validan IDs, cantidades, copias por nombre y evoluciones. No garantiza el mejor mazo ni un winrate.")
      OutlinedTextField(endpoint, { endpoint = it; model.endpoint = it }, label = { Text("Servidor IA · URL HTTPS /assist") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
      OutlinedTextField(token, { token = it; model.token = it }, label = { Text("Token de acceso al servidor") },
        visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth(), singleLine = true)
      Text("La clave de OpenAI permanece en tu servidor. Este token solo se guarda durante la sesión y no entra en respaldos o logs.")
      TextButton(onClick = { token = ""; model.clearToken() }) { Text("Eliminar token de la sesión") }
      OutlinedTextField(goal, { if (it.length <= 2000) { goal = it; model.goal = it } }, label = { Text("Objetivo y estrategia") }, modifier = Modifier.fillMaxWidth())
      OutlinedTextField(meta, { if (it.length <= 16_000) { meta = it; model.meta = it } }, label = { Text("Contexto meta · fuente y fecha (opcional)") },
        modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp))
      Text("Acotar cartas enviadas · incluye Entrenadores y las cartas del mazo objetivo")
      LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        items(listOf("" to "Todas", "Planta" to "Planta", "Fuego" to "Fuego", "Agua" to "Agua",
          "Rayo" to "Rayo", "Psíquico" to "Psíquico", "Lucha" to "Lucha", "Oscuridad" to "Oscuridad",
          "Metal" to "Metal", "Dragón" to "Dragón", "Incoloro" to "Incoloro")) { (type, label) ->
          FilterChip(selected = candidateType == type, onClick = { candidateType = type; model.candidateType = type }, label = { Text(label) })
        }
      }
      Text("El contexto meta es aportado por ti. No se obtiene mediante scraping ni se verifica como actual. Los efectos disponibles dependen de las cartas indexadas en Efectos.")
      Button(enabled = !busy && endpoint.isNotBlank() && token.isNotBlank(), onClick = { confirmMode = false }) { Text("Proponer mazo de 20 cartas") }
      OutlinedButton(enabled = !busy && endpoint.isNotBlank() && token.isNotBlank() && deck?.totalCardCount == 20,
        onClick = { confirmMode = true }) { Text("Sustituir faltantes del mazo abierto") }
      AdvancedStatus(model)
    }
    proposal?.let { result ->
      item {
        Card(Modifier.fillMaxWidth()) {
          Column(Modifier.padding(16.dp)) {
            Text(result.deck.name, style = MaterialTheme.typography.titleMedium)
            Text(result.deck.strategy)
            Text("Energías: ${result.deck.energyTypes.joinToString()}")
            result.deck.cards.forEach { Text("${it.count}× ${it.card.name} · ${it.card.id}") }
            result.replacements.forEach { Text("${it.count}× ${it.removedId} → ${it.addedId}: ${it.reason}") }
            Button(onClick = { confirmOpen = true }) { Text("Abrir propuesta en el editor") }
          }
        }
      }
    }
  }
  confirmMode?.let { replace ->
    AlertDialog(onDismissRequest = { confirmMode = null }, title = { Text("Enviar contexto a la IA") },
      text = { Text("Se enviarán IDs, cantidades disponibles, tipos, efectos indexados, objetivo y contexto meta a tu servidor y a OpenAI. Puede generar un coste en tu cuenta API. No modifica la colección ni guarda automáticamente un mazo.") },
      confirmButton = { TextButton(onClick = { model.askAssistant(replace, deck, "es"); confirmMode = null }) { Text("Enviar y consultar") } },
      dismissButton = { TextButton(onClick = { confirmMode = null }) { Text("Cancelar") } })
  }
  if (confirmOpen) AlertDialog(onDismissRequest = { confirmOpen = false }, title = { Text("Abrir propuesta") },
    text = { Text("Reemplaza el borrador actual del editor. Los mazos guardados se conservan.") },
    confirmButton = { TextButton(onClick = { proposal?.let { main.openAiProposal(it.deck); onOpenDeck() }; confirmOpen = false }) { Text("Abrir") } },
    dismissButton = { TextButton(onClick = { confirmOpen = false }) { Text("Cancelar") } })
}

@Composable
fun SandboxScreen(main: TcgViewModel, model: AdvancedViewModel, modifier: Modifier = Modifier) {
  val deck by main.generatedDeck.collectAsStateWithLifecycle()
  val state by model.board.collectAsStateWithLifecycle()
  val busy by model.busy.collectAsStateWithLifecycle()
  var restart by remember { mutableStateOf(false) }
  LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
    item {
      Text("Tapete de práctica", style = MaterialTheme.typography.titleLarge)
      Text("Abre un mazo de 20 cartas en Mazos y úsalo aquí. Tablero manual de un jugador; no ejecuta ataques, evoluciones ni reglas automáticamente.")
      Text("La mano inicial garantiza un básico por intercambio de una carta si hace falta. Es una aproximación, no el algoritmo interno del juego.")
      Button(enabled = !busy && deck?.totalCardCount == 20, onClick = { if (state == null) model.startSandbox(deck, "es") else restart = true }) {
        Text(if (state == null) "Iniciar con el mazo abierto" else "Nueva práctica")
      }
      AdvancedStatus(model)
    }
    state?.let { board ->
      item {
        Text("Turno ${board.turn} · Mazo ${board.drawPile.size} · Mano ${board.hand.size} · Descartes ${board.discard.size}")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          OutlinedButton(enabled = board.drawPile.isNotEmpty(), onClick = { model.updateBoard(SandboxEngine::draw) }) { Text("Robar") }
          OutlinedButton(onClick = { model.updateBoard(SandboxEngine::nextTurn) }) { Text("Siguiente turno") }
        }
        TextButton(onClick = model::undoMove) { Text("Deshacer") }
        Text("Activo", style = MaterialTheme.typography.titleMedium)
        if (board.active == null) Text("Vacío · mueve un Pokémon desde la mano.")
      }
      board.active?.let { card -> item(key = "active-${card.instanceId}") { BoardCardControls(card, BoardZone.ACTIVE, model) } }
      item { Text("Banca · ${board.bench.size}/3", style = MaterialTheme.typography.titleMedium) }
      items(board.bench, key = { "bench-${it.instanceId}" }) { card -> BoardCardControls(card, BoardZone.BENCH, model) }
      item { Text("Mano", style = MaterialTheme.typography.titleMedium) }
      items(board.hand, key = { "hand-${it.instanceId}" }) { card -> BoardCardControls(card, BoardZone.HAND, model) }
      item { Text("Descartes", style = MaterialTheme.typography.titleMedium) }
      items(board.discard, key = { "discard-${it.instanceId}" }) { card -> BoardCardControls(card, BoardZone.DISCARD, model) }
    }
  }
  if (restart) AlertDialog(onDismissRequest = { restart = false }, title = { Text("Reiniciar práctica") },
    text = { Text("Se reinician solo el tapete, la mano y los descartes. No modifica la colección ni los mazos guardados.") },
    confirmButton = { TextButton(onClick = { model.startSandbox(deck, "es"); restart = false }) { Text("Reiniciar") } },
    dismissButton = { TextButton(onClick = { restart = false }) { Text("Cancelar") } })
}

@Composable
private fun BoardCardControls(card: BoardCard, zone: BoardZone, model: AdvancedViewModel) {
  Card(Modifier.fillMaxWidth()) {
    Column(Modifier.padding(12.dp)) {
      Text("${card.name} · ${card.cardId}")
      if (zone in setOf(BoardZone.ACTIVE, BoardZone.BENCH)) {
        Text("Energías: ${card.energies} · Daño: ${card.damage}")
        Row {
          TextButton(onClick = { model.updateBoard { SandboxEngine.attachEnergy(it, card.instanceId) } }) { Text("+ Energía") }
          TextButton(onClick = { model.updateBoard { SandboxEngine.damage(it, card.instanceId, minOf(999, card.damage + 10)) } }) { Text("+10 daño") }
          TextButton(onClick = { model.updateBoard { SandboxEngine.damage(it, card.instanceId, maxOf(0, card.damage - 10)) } }) { Text("−10 daño") }
        }
      }
      LazyRow {
        items(BoardZone.entries.filter { it != zone }) { destination ->
          TextButton(onClick = { model.updateBoard { SandboxEngine.move(it, card.instanceId, destination) } }) {
            Text(when (destination) { BoardZone.HAND -> "A mano"; BoardZone.ACTIVE -> "A activo"; BoardZone.BENCH -> "A banca"; BoardZone.DISCARD -> "Descartar" })
          }
        }
        if (zone == BoardZone.BENCH) item {
          TextButton(onClick = { model.updateBoard { SandboxEngine.swapActive(it, card.instanceId) } }) { Text("Cambiar con activo") }
        }
      }
    }
  }
}

@Composable
fun EffectFiltersScreen(main: TcgViewModel, model: AdvancedViewModel, modifier: Modifier = Modifier) {
  val inventory by main.inventoryList.collectAsStateWithLifecycle()
  val matches by model.matches.collectAsStateWithLifecycle()
  val busy by model.busy.collectAsStateWithLifecycle()
  var query by rememberSaveable { mutableStateOf("") }
  var keyword by rememberSaveable { mutableStateOf("") }
  var role by rememberSaveable { mutableStateOf("") }
  var minHp by rememberSaveable { mutableStateOf("") }
  var maxHp by rememberSaveable { mutableStateOf("") }
  var element by rememberSaveable { mutableStateOf("") }
  LaunchedEffect("es", keyword, role, minHp, maxHp, element) {
    model.updateFilter(RulesFilter("es", minHp.toIntOrNull(), maxHp.toIntOrNull(), element, role, keyword))
  }
  val candidates = inventory.filter { query.isBlank() || it.card.name.contains(query, true) || it.card.id.contains(query, true) }
  val visible = matches.filter { query.isBlank() || it.cardId.contains(query, true) ||
    inventory.find { card -> card.card.id == it.cardId }?.card?.name?.contains(query, true) == true }
  LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
    item {
      Text("Filtros por efectos", style = MaterialTheme.typography.titleLarge)
      Text("Solo consulta reglas obtenidas de TCGdex y almacenadas en Room. Las etiquetas de rol se estiman por palabras clave; pueden tener falsos positivos.")
      OutlinedTextField(query, { query = it }, label = { Text("Nombre o código para indexar y buscar") }, modifier = Modifier.fillMaxWidth())
      OutlinedTextField(keyword, { keyword = it }, label = { Text("Texto del ataque o habilidad") }, modifier = Modifier.fillMaxWidth())
      Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(minHp, { if (it.length <= 3 && it.all(Char::isDigit)) minHp = it }, label = { Text("PS mínimo") }, modifier = Modifier.weight(1f))
        OutlinedTextField(maxHp, { if (it.length <= 3 && it.all(Char::isDigit)) maxHp = it }, label = { Text("PS máximo") }, modifier = Modifier.weight(1f))
      }
      OutlinedTextField(element, { element = it }, label = { Text("Tipo exacto de TCGdex (ej. Grass)") }, modifier = Modifier.fillMaxWidth())
      LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        item { FilterChip(selected = role.isBlank(), onClick = { role = "" }, label = { Text("Todos los roles") }) }
        items(CardRole.entries) { value -> FilterChip(selected = role == value.key,
          onClick = { role = if (role == value.key) "" else value.key }, label = { Text(value.label) }) }
      }
      OutlinedButton(enabled = !busy && candidates.isNotEmpty(), onClick = { model.indexCards(candidates.map { it.card.id }, "es") }) { Text("Indexar hasta 25 cartas de esta búsqueda") }
      TextButton(onClick = { query = ""; keyword = ""; role = ""; minHp = ""; maxHp = ""; element = "" }) { Text("Limpiar filtros") }
      Text("También se indexa una carta al abrir sus detalles en Colección. No se descarga todo el catálogo automáticamente.")
      AdvancedStatus(model)
      Text("${visible.size} coincidencias indexadas")
      if (visible.isEmpty()) Text("No hay resultados. Indexa cartas o limpia los filtros.")
    }
    items(visible, key = { it.cardId }) { rules ->
      val name = inventory.find { it.card.id == rules.cardId }?.card?.name ?: rules.cardId
      Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp)) {
          Text("$name · ${rules.cardId}")
          Text("PS: ${rules.hp?.toString() ?: "Sin datos"} · ${rules.element} · ${rules.source}")
          Text(rules.rulesText)
          Text(CardRole.entries.filter { rules.roles.contains("|${it.key}|") }.joinToString { it.label })
        }
      }
    }
  }
}
