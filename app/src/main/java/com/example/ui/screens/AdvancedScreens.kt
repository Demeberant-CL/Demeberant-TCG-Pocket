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
  val externalPrompt by model.externalPrompt.collectAsStateWithLifecycle()
  var goal by rememberSaveable { mutableStateOf(model.goal) }
  var responseText by remember { mutableStateOf("") }
  var showResponseEditor by remember { mutableStateOf(false) }
  var replace by rememberSaveable { mutableStateOf(false) }
  var step by rememberSaveable { mutableIntStateOf(1) }
  var showCopy by remember { mutableStateOf(false) }
  var advanced by rememberSaveable { mutableStateOf(false) }
  var useServer by rememberSaveable { mutableStateOf(false) }
  var confirmSend by remember { mutableStateOf(false) }
  var confirmOpen by remember { mutableStateOf(false) }
  var clipboardMessage by remember { mutableStateOf<String?>(null) }
  var endpoint by remember { mutableStateOf(model.endpoint) }
  var token by remember { mutableStateOf(model.token) }
  var meta by remember { mutableStateOf(model.meta) }
  var candidateType by rememberSaveable { mutableStateOf(model.candidateType) }
  val context = androidx.compose.ui.platform.LocalContext.current
  LaunchedEffect(externalPrompt) { if (externalPrompt != null) step = 2 }
  LaunchedEffect(proposal) { if (proposal != null) step = 4 }
  LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
    item {
      Text("Crea un mazo con tu IA", style = MaterialTheme.typography.titleLarge)
      HelpButton("ia")
      Text("Te guiamos en cuatro pasos. No necesitas comprender el formato de la respuesta.")
      Text("Paso $step de 4", style = MaterialTheme.typography.titleMedium)
      Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(selected = !replace, onClick = { if (!busy) { replace = false; model.clearExternal(); responseText = ""; step = 1 } }, label = { Text("Crear mazo") })
        FilterChip(selected = replace, onClick = { if (!busy) { replace = true; model.clearExternal(); responseText = ""; step = 1 } }, label = { Text("Mejorar mazo abierto") })
      }
      if (replace) Text(deck?.let { "Mazo objetivo: ${it.name} · ${it.totalCardCount}/20" } ?: "Abre primero un mazo en Mazos.")
      if (step == 1 || useServer) {
        Text("1. ¿Qué mazo quieres?")
        OutlinedTextField(goal, { if (it.length <= 2000) { goal = it; model.goal = it } }, label = { Text("Ejemplo: mazo de Agua fácil de jugar") }, modifier = Modifier.fillMaxWidth())
        TextButton(onClick = { advanced = !advanced }) { Text(if (advanced) "Ocultar opciones avanzadas" else "Opciones avanzadas (opcional)") }
        if (advanced) {
          Text("Limitar cartas por tipo")
          LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            items(listOf("") + com.example.data.util.DeckCodec.energyNames + listOf("Dragón", "Incoloro")) { type ->
              FilterChip(selected = candidateType == type, onClick = { candidateType = type; model.candidateType = type }, label = { Text(type.ifBlank { "Todas" }) })
            }
          }
          OutlinedTextField(meta, { if (it.length <= 16000) { meta = it; model.meta = it } }, label = { Text("Contexto adicional y fuente (opcional)") }, modifier = Modifier.fillMaxWidth())
          Row { Text("Usar servidor propio", Modifier.weight(1f)); Switch(useServer, { useServer = it }) }
          if (useServer) {
            OutlinedTextField(endpoint, { endpoint = it; model.endpoint = it }, label = { Text("URL HTTPS /assist") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(token, { token = it; model.token = it }, label = { Text("Token del servidor") }, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
            Text("Opción técnica: servidor propio, no la contraseña de tu IA. Puede tener coste API.")
            TextButton(onClick = { token = ""; model.clearToken() }) { Text("Eliminar token") }
          }
        }
        Button(enabled = !busy && (!replace || deck?.totalCardCount == 20) && (!useServer || endpoint.isNotBlank() && token.isNotBlank()),
          onClick = { responseText = ""; clipboardMessage = null; if (useServer) confirmSend = true else model.prepareExternal(replace, deck) }, modifier = Modifier.fillMaxWidth()) {
          Text(if (useServer) "Consultar servidor" else "Preparar consulta")
        }
      }
      if (!useServer && externalPrompt != null && step != 4) {
        Text("2. Lleva la consulta a tu IA")
        Text("Revisa y copia la consulta. Luego abre tu IA, pégala y copia toda su respuesta.")
        Button(enabled = !busy, onClick = { showCopy = true }, modifier = Modifier.fillMaxWidth()) { Text("Revisar y copiar consulta") }
        OutlinedButton(onClick = {
          try { context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("https://chatgpt.com"))) }
          catch (_: Exception) { clipboardMessage = "Abre tu aplicación IA habitual y pega la consulta." }
        }) { Text("Abrir ChatGPT") }
        Text("También puedes abrir cualquier otra IA. La app no envía la consulta por ti.")
        Text("3. Trae la respuesta")
        Button(enabled = !busy, onClick = {
          val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
          val value = clipboard.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.text?.toString()
          if (value.isNullOrBlank()) clipboardMessage = "Copia primero la respuesta completa de tu IA."
          else if (value.length > 100000) clipboardMessage = "La respuesta es demasiado larga. Pide solo el mazo solicitado."
          else { responseText = value; step = 3; clipboardMessage = "Respuesta pegada. Pulsa Revisar mazo." }
        }, modifier = Modifier.fillMaxWidth()) { Text("Pegar respuesta") }
        if (responseText.isNotBlank()) Text("Respuesta lista · ${responseText.length} caracteres. Pulsa Revisar mazo para ver las cartas.")
        TextButton(enabled = !busy, onClick = { showResponseEditor = !showResponseEditor; step = 3 }) {
          Text(if (showResponseEditor) "Ocultar texto de respuesta" else "Pegar o editar manualmente (opcional)")
        }
        if (showResponseEditor) OutlinedTextField(responseText, { if (it.length <= 100000) responseText = it },
          label = { Text("Respuesta de tu IA") }, modifier = Modifier.fillMaxWidth().heightIn(min = 100.dp, max = 180.dp))
        Button(enabled = !busy && responseText.isNotBlank(), onClick = { model.importExternal(responseText) }, modifier = Modifier.fillMaxWidth()) { Text("Revisar mazo") }
        Text("Si no se acepta, vuelve a tu IA con el motivo del aviso. La consulta ya pide el formato necesario.")
        TextButton(enabled = !busy, onClick = { responseText = ""; model.clearExternal(); step = 1; clipboardMessage = null }) { Text("Empezar otra consulta") }
      }
      clipboardMessage?.let { Text(it) }
      AdvancedStatus(model)
    }
    proposal?.let { result ->
      item {
        Text("4. Revisa tu mazo", style = MaterialTheme.typography.titleLarge)
        Text(result.deck.name, style = MaterialTheme.typography.titleMedium)
        Text("20 cartas · Energías incluidas: ${result.deck.energyTypes.joinToString()}")
        Text(result.deck.strategy)
        Button(enabled = !busy, onClick = { confirmOpen = true }, modifier = Modifier.fillMaxWidth()) { Text("Usar este mazo") }
        Text("Se abre como borrador. Después pulsa Guardar mazo; no cambia tus cantidades.")
        TextButton(enabled = !busy, onClick = { model.clearExternal(); responseText = ""; step = 1 }) { Text("Crear otra propuesta") }
      }
      items(result.deck.cards, key = { it.card.id }) { entry ->
        Card(Modifier.fillMaxWidth()) {
          Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            DeckThumbnail(entry.card.id, entry.card.name)
            Column { Text("${entry.count} × ${entry.card.name}"); Text(entry.card.id, style = MaterialTheme.typography.bodySmall) }
          }
        }
      }
      items(result.replacements) { Text("${it.count} × ${it.removedId} → ${it.addedId}: ${it.reason}") }
    }
  }
  if (showCopy) AlertDialog(onDismissRequest = { showCopy = false }, title = { Text("Revisa los datos que vas a copiar") },
    text = { Column { Text("Incluye cartas, cantidades, objetivo y contexto. No incluye credenciales. Tú eliges quién recibe estos datos.")
      androidx.compose.foundation.text.selection.SelectionContainer {
        LazyColumn(Modifier.heightIn(max = 300.dp)) { item { Text(externalPrompt ?: "") } }
      }
    } }, confirmButton = { TextButton(onClick = {
      externalPrompt?.let { prompt ->
        (context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager)
          .setPrimaryClip(android.content.ClipData.newPlainText("Consulta de mazo", prompt))
        clipboardMessage = "Consulta copiada. Pégala en tu IA y vuelve con su respuesta."
      }; showCopy = false
    }) { Text("Copiar consulta") } }, dismissButton = { TextButton(onClick = { showCopy = false }) { Text("Cancelar") } })
  if (confirmSend) AlertDialog(onDismissRequest = { confirmSend = false }, title = { Text("Enviar contexto al servidor") },
    text = { Text("Envía cartas, cantidades, efectos, objetivo y contexto a tu servidor y OpenAI. Puede tener coste API. No guarda un mazo automáticamente.") },
    confirmButton = { TextButton(onClick = { model.askAssistant(replace, deck, "es"); confirmSend = false }) { Text("Enviar") } },
    dismissButton = { TextButton(onClick = { confirmSend = false }) { Text("Cancelar") } })
  if (confirmOpen) AlertDialog(onDismissRequest = { confirmOpen = false }, title = { Text("Usar este mazo") },
    text = { Text("Reemplaza el borrador abierto; conserva mazos guardados y colección.") },
    confirmButton = { TextButton(onClick = { proposal?.let { main.openAiProposal(it.deck); onOpenDeck() }; confirmOpen = false }) { Text("Abrir en Mazos") } },
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
      HelpButton("sandbox")
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
      HelpButton("analisis")
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
