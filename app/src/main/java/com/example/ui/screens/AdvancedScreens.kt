package com.example.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import com.example.ui.components.AdaptiveActionRow
import com.example.ui.components.DexPanel
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.AddCircleOutline
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Settings
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
  val connection by model.connection.collectAsStateWithLifecycle()
  val profiles by model.profiles.collectAsStateWithLifecycle()
  val models by model.availableModels.collectAsStateWithLifecycle()
  var profileId by remember { mutableStateOf(connection.id) }
  var profileName by remember { mutableStateOf(connection.label) }
  val ready by model.connectionReady.collectAsStateWithLifecycle()
  var provider by remember { mutableStateOf(connection.provider) }
  var apiKey by remember { mutableStateOf(connection.apiKey) }
  var modelName by remember { mutableStateOf(connection.model) }
  var url by remember { mutableStateOf(connection.endpoint) }
  var configure by remember { mutableStateOf(false) }
  var goal by rememberSaveable { mutableStateOf(model.goal) }
  var candidateType by rememberSaveable { mutableStateOf(model.candidateType) }
  var actionName by rememberSaveable { mutableStateOf(if (deck?.archetype == "Limitless") AiDeckAction.COMPLETE.name else AiDeckAction.CREATE.name) }
  val action = AiDeckAction.valueOf(actionName)
  var showCandidateFilter by rememberSaveable { mutableStateOf(false) }
  var confirmSend by remember { mutableStateOf(false) }
  var confirmDelete by remember { mutableStateOf(false) }
  var confirmOpen by remember { mutableStateOf(false) }
  val context = androidx.compose.ui.platform.LocalContext.current
  LaunchedEffect(connection) {
    profileId = connection.id; profileName = connection.label
    provider = connection.provider; apiKey = connection.apiKey
    modelName = connection.model; url = connection.endpoint
  }
  Column(modifier.fillMaxSize()) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp)) {
      Text("Asistente IA", style = MaterialTheme.typography.titleLarge)
      DexPanel(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
          if (!ready) LinearProgressIndicator(Modifier.fillMaxWidth())
          Text(if (connection.apiKey.isBlank()) "Configura tu conexión IA" else connection.label,
            style = MaterialTheme.typography.titleSmall)
          if (connection.apiKey.isNotBlank()) Text("${connection.provider.label} · ${connection.model}",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
          OutlinedButton(enabled = ready && !busy, onClick = { configure = true }) {
            Icon(Icons.Filled.Settings, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("Conexiones")
          }
        }
      }
    }
    LazyColumn(Modifier.weight(1f).fillMaxWidth(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
    item {
      Text("¿Qué quieres hacer?", style = MaterialTheme.typography.titleMedium)
      AiDeckAction.entries.forEach { choice ->
        OutlinedCard(onClick = { actionName = choice.name; model.proposal.value = null }, enabled = !busy,
          modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
          border = BorderStroke(1.dp, if (action == choice) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
          colors = CardDefaults.outlinedCardColors(containerColor = if (action == choice)
            MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface)) {
          Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(when (choice) {
              AiDeckAction.CREATE -> Icons.Filled.AutoAwesome
              AiDeckAction.COMPLETE -> Icons.Filled.AddCircleOutline
              AiDeckAction.IMPROVE -> Icons.Filled.Tune
            }, contentDescription = null)
            Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
              Text(choice.label, style = MaterialTheme.typography.titleSmall)
              Text(choice.description, style = MaterialTheme.typography.bodySmall)
            }
            RadioButton(selected = action == choice, onClick = null)
          }
        }
      }
      if (action != AiDeckAction.CREATE) Text(deck?.let { "Mazo abierto: ${it.name} · ${it.totalCardCount}/20" }
        ?: "Abre primero un mazo desde Mazos → Editar.")
      if (!action.canUse(deck?.totalCardCount)) Text(if (action == AiDeckAction.COMPLETE)
        "Necesitas un objetivo de 20 cartas. Completar faltantes sustituye las que no tienes; no rellena un borrador corto."
        else "Abre un mazo con al menos una carta para mejorarlo.", color = MaterialTheme.colorScheme.error)
      OutlinedTextField(goal, { if (it.length <= 2000) { goal = it; model.goal = it } }, enabled = !busy,
        label = { Text("Objetivo opcional") }, placeholder = { Text("Ej.: ataques rápidos de tipo Agua") }, modifier = Modifier.fillMaxWidth())
      TextButton(onClick = { showCandidateFilter = !showCandidateFilter }) { Text("Filtrar cartas: ${candidateType.ifBlank { "Todas" }}") }
      if (showCandidateFilter) LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        items(listOf("") + com.example.data.util.DeckCodec.energyNames + listOf("Dragón", "Incoloro")) { type ->
          FilterChip(selected = candidateType == type, enabled = !busy, onClick = {
            candidateType = type; model.candidateType = type; model.proposal.value = null
          }, label = { Text(type.ifBlank { "Todas" }) })
        }
      }
      Button(enabled = ready && !busy && connection.apiKey.isNotBlank() && action.canUse(deck?.totalCardCount),
        onClick = { confirmSend = true }, modifier = Modifier.fillMaxWidth()) { Text("${action.label} con IA") }

      AdvancedStatus(model)
    }
    proposal?.let { result ->
      item {
        Text(result.deck.name, style = MaterialTheme.typography.titleLarge)
        Text("20 cartas · Energías: ${result.deck.energyTypes.joinToString()}")
        Text(result.deck.strategy)
        Button(enabled = !busy, onClick = { confirmOpen = true }, modifier = Modifier.fillMaxWidth()) { Text("Usar este mazo") }
        Text("Se abrirá como borrador para que lo revises y guardes.")
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
  }
  if (configure) Dialog(onDismissRequest = { configure = false },
    properties = DialogProperties(usePlatformDefaultWidth = false)) {
    Card(Modifier.fillMaxWidth().padding(16.dp).heightIn(max = 620.dp)) {
      LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Conexiones y modelos", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
            TextButton(onClick = { configure = false }) { Text("Cerrar") }
          }
          LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            items(profiles.entries, key = { it.id }) { saved ->
              FilterChip(selected = saved.id == connection.id, enabled = !busy,
                onClick = { model.selectConnection(saved.id) }, label = { Text(saved.label) })
            }
          }
          OutlinedButton(enabled = !busy, onClick = {
            profileId = java.util.UUID.randomUUID().toString(); profileName = "Nueva conexión"
            apiKey = ""; model.availableModels.value = emptyList()
          }) { Text("Añadir proveedor") }
          OutlinedTextField(profileName, { profileName = it.take(60) }, label = { Text("Nombre de conexión") }, modifier = Modifier.fillMaxWidth())
          com.example.data.ai.AiProvider.entries.forEach { value ->
            FilterChip(selected = provider == value, enabled = !busy, onClick = {
              if (provider != value) {
                provider = value; modelName = value.defaultModel; apiKey = ""; url = ""
                profileId = java.util.UUID.randomUUID().toString(); profileName = value.label; model.availableModels.value = emptyList()
              }
            }, label = { Text(value.label) })
          }
          OutlinedTextField(apiKey, { if (it.length <= 4096) apiKey = it.trim() }, label = { Text("Tu clave API") },
            visualTransformation = PasswordVisualTransformation(), singleLine = true, enabled = !busy, modifier = Modifier.fillMaxWidth())
          OutlinedTextField(modelName, { if (it.length <= 120) modelName = it.trim() }, label = { Text("Modelo") }, singleLine = true, enabled = !busy, modifier = Modifier.fillMaxWidth())
          if (provider == com.example.data.ai.AiProvider.COMPATIBLE) {
            OutlinedTextField(url, { if (it.length <= 2000) url = it.trim() }, label = { Text("URL HTTPS completa de chat/completions") }, singleLine = true, enabled = !busy, modifier = Modifier.fillMaxWidth())
            Text("Solo APIs compatibles con Chat Completions. No todos los proveedores usan este formato; recibirán tus cartas y tu clave.")
          }
          Text(if (provider == com.example.data.ai.AiProvider.GEMINI)
            "Gemini tiene cuotas gratuitas según modelo y cuenta. Si activaste facturación, puede cobrar. En el nivel gratuito Google puede usar el contenido para mejorar sus productos."
            else "Esta API puede tener coste. ChatGPT Plus no incluye crédito API. Revisa los límites en tu cuenta del proveedor.")
          Text("La clave se cifra en este teléfono y no se incluye en diagnósticos ni respaldos. No uses una clave compartida para todos los usuarios.")
          if (provider != com.example.data.ai.AiProvider.COMPATIBLE) TextButton(onClick = {
            val link = if (provider == com.example.data.ai.AiProvider.GEMINI) "https://aistudio.google.com/apikey" else "https://platform.openai.com/api-keys"
            try { context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(link))) }
            catch (_: Exception) { model.message.value = "Abre la página de claves del proveedor en tu navegador." }
          }) { Text("Obtener mi clave API") }
          Button(enabled = !busy && apiKey.isNotBlank() && modelName.isNotBlank() &&
            (provider != com.example.data.ai.AiProvider.COMPATIBLE || url.isNotBlank()), onClick = {
              model.saveConnection(com.example.data.ai.AiConnection(provider, modelName, apiKey, url, profileId, profileName.ifBlank { provider.label }))
          }) { Text("Guardar conexión") }
          OutlinedButton(enabled = !busy && apiKey.isNotBlank(), onClick = {
            model.discoverModels(com.example.data.ai.AiConnection(provider, modelName, apiKey, url, profileId, profileName))
          }) { Text("Buscar modelos compatibles") }
          Text("La lista filtra compatibilidad de texto, no garantiza cuota o acceso. Si tu API no declara capacidades, usa el identificador manual indicado por el proveedor.", style = MaterialTheme.typography.bodySmall)
          LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            items(models) { name -> FilterChip(selected = name == modelName, onClick = { modelName = name }, label = { Text(name) }) }
          }
          if (profileId == connection.id && profiles.entries.any { it.id == profileId })
            TextButton(enabled = !busy, onClick = { confirmDelete = true }) { Text("Eliminar esta conexión") }
          AdvancedStatus(model)
          HelpButton("ia")

        }
      }
    }
  }
  if (confirmDelete) AlertDialog(onDismissRequest = { confirmDelete = false }, title = { Text("Eliminar ${connection.label}") },
    text = { Text("Elimina solo esta conexión y su clave guardada. Las demás conexiones y tus mazos se conservan.") },
    confirmButton = { TextButton(onClick = { model.removeConnection(); confirmDelete = false }) { Text("Eliminar") } },
    dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancelar") } })
  if (confirmSend) AlertDialog(onDismissRequest = { confirmSend = false }, title = { Text("${action.label} · ${connection.provider.label}") },
    text = { Text("${action.description}\n\nModelo: ${connection.model}. Se enviarán cartas disponibles, cantidades, efectos conocidos y tu objetivo. Si corresponde, también la lista del mazo abierto. No se enviarán nombre de perfil, Friend ID ni diagnósticos. Puede consumir cuota o crédito según tu cuenta. No se cambiará a otro proveedor si falla.") },
    confirmButton = { TextButton(onClick = { model.askConnected(action, deck); confirmSend = false }) { Text("Consultar") } },
    dismissButton = { TextButton(onClick = { confirmSend = false }) { Text("Cancelar") } })
  if (confirmOpen) AlertDialog(onDismissRequest = { confirmOpen = false }, title = { Text("Usar este mazo") },
    text = { Text("Reemplaza el borrador abierto; conserva mazos guardados y colección.") },
    confirmButton = { TextButton(onClick = { proposal?.let { main.openAiProposal(it.deck, onOpenDeck) { reason -> model.message.value = reason } }; confirmOpen = false }) { Text("Abrir en Mazos") } },
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
        AdaptiveActionRow { actionModifier ->
          OutlinedButton(modifier = actionModifier, enabled = board.drawPile.isNotEmpty(), onClick = { model.updateBoard(SandboxEngine::draw) }) { Text("Robar") }
          OutlinedButton(modifier = actionModifier, onClick = { model.updateBoard(SandboxEngine::nextTurn) }) { Text("Siguiente turno") }
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
      AdaptiveActionRow { fieldModifier ->
        OutlinedTextField(minHp, { if (it.length <= 3 && it.all(Char::isDigit)) minHp = it }, label = { Text("PS mínimo") }, modifier = fieldModifier)
        OutlinedTextField(maxHp, { if (it.length <= 3 && it.all(Char::isDigit)) maxHp = it }, label = { Text("PS máximo") }, modifier = fieldModifier)
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
