package com.example.ui.viewmodel

import kotlinx.coroutines.async

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.ai.*
import com.example.data.api.CardDetailsClient
import com.example.data.local.AppDatabase
import com.example.data.local.CardRulesEntity
import com.example.data.repository.*
import com.example.data.util.ErrorLogManager
import com.example.domain.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

data class RulesFilter(val language: String = "es", val minHp: Int? = null, val maxHp: Int? = null,
  val element: String = "", val role: String = "", val keyword: String = "")

@OptIn(ExperimentalCoroutinesApi::class)
class AdvancedViewModel(application: Application) : AndroidViewModel(application) {
  private val db = AppDatabase.getDatabase(application)
  private val repository = InventoryRepository.fromDatabase(db)
  private val assistant = AIAssistantRepository()
  private val connectedAssistant = ConnectedAiRepository()
  private val tournamentRepository = com.example.data.meta.TournamentRepository(application)
  val metaSnapshot = MutableStateFlow<com.example.data.meta.MetaSnapshot?>(null)
  private val metaPreferences = application.getSharedPreferences("pocket-meta-preferences", android.content.Context.MODE_PRIVATE)
  val autoMetaRefresh = MutableStateFlow(metaPreferences.getBoolean("auto-refresh", false))
  val metaReady = MutableStateFlow(false)
  fun setAutoMetaRefresh(enabled: Boolean) {
    metaPreferences.edit().putBoolean("auto-refresh", enabled).apply()
    autoMetaRefresh.value = enabled
  }
  fun refreshMetaIfNeeded() {
    if (!autoMetaRefresh.value || !metaReady.value || busy.value) return
    if (com.example.data.meta.MetaRefreshPolicy.shouldRefresh(metaSnapshot.value?.updated,
        metaPreferences.getLong("last-attempt", 0), System.currentTimeMillis())) refreshMeta()
  }
  fun refreshMeta() = task("META_FETCH") {
    metaPreferences.edit().putLong("last-attempt", System.currentTimeMillis()).apply()
    message.value = "Consultando torneos… Puedes cancelar y conservar la muestra guardada."
    try {
      metaSnapshot.value = tournamentRepository.refresh { current, total ->
        message.value = "Revisando torneo $current de $total… Puedes cancelar."
      }
    } catch (e: CancellationException) { throw e }
    catch (e: Exception) {
      if (e is com.example.data.meta.NoRecentTournamentResults) ErrorLogManager.event("META_EMPTY", "No complete recent tournament results")
      else ErrorLogManager.event("META_FETCH", "Tournament refresh failed", e)
      message.value = when (e) {
        is com.example.data.meta.NoRecentTournamentResults -> "Todavía no hay resultados completos recientes. Se conserva la muestra guardada."
        is com.example.data.meta.TournamentSourceUnavailable -> "El servicio de torneos no está disponible. Se conserva la muestra guardada; puedes reintentar más tarde."
        is java.io.IOException -> "No se pudo completar la descarga. Revisa la conexión y vuelve a intentarlo. Se conserva el meta guardado."
        else -> "No se obtuvieron resultados completos válidos. Se conserva el meta guardado."
      }
      return@task
    }
    message.value = "Resultados actualizados desde Limitless."
  }
  private val connectorStore = AiConnectionStore(application, fileName = "pocket-connector.enc")
  private val pocketConnector = PocketConnector()
  val connectorConfig = MutableStateFlow<AiConnection?>(null)
  val connectorProposals = MutableStateFlow<List<AiProposal>>(emptyList())
  fun loadConnector() = task("CONNECTOR_CONFIG") {
    connectorConfig.value = withContext(Dispatchers.IO) { connectorStore.load() }
  }
  fun saveConnector(url: String, key: String) = task("CONNECTOR_CONFIG") {
    PocketConnector.validate(url.trim(), key.trim())
    val config = AiConnection(AiProvider.COMPATIBLE, "connector", key.trim(), url.trim(), "connector", "Rotom AI TCG Pocket")
    withContext(Dispatchers.IO) { connectorStore.save(config) }
    connectorConfig.value = config
    connectorProposals.value = emptyList()
    message.value = "Conector guardado. Sincroniza la colección para compartirla con el chat."
  }
  fun syncConnector(draft: GeneratedDeck?) = task("CONNECTOR_SYNC") {
    val config = connectorConfig.value ?: error("Configura primero el servidor del conector.")
    val inventory = repository.inventoryFlow.first()
    require(inventory.isNotEmpty()) { "Espera a que se cargue la colección." }
    val rules = db.cardRulesDao().all("es").associateBy { it.cardId }
    val cards = org.json.JSONArray()
    inventory.forEach { row ->
      val rule = rules[row.card.id]
      val c = hydrate(row.card, rule)
      cards.put(org.json.JSONObject().put("id", c.id).put("name", c.name).put("rulesName", c.rulesName)
        .put("expansion", c.id.substringBeforeLast("-")).put("owned", row.ownedCount).put("wishlist", row.isWishlist)
        .put("type", c.type).put("category", c.category).put("stage", c.stage).put("evolvesFrom", c.evolvesFrom)
        .put("effects", rule?.rulesText.orEmpty().take(10000)).put("effectsKnown", !rule?.rulesText.isNullOrBlank()))
    }
    val decks = org.json.JSONArray()
    repository.savedDecksFlow.first().forEach { d -> decks.put(org.json.JSONObject().put("id", d.id.toString())
      .put("name", d.name).put("data", d.cardListSerialized)) }
    val snapshot = org.json.JSONObject().put("format", 1).put("cards", cards).put("decks", decks)
    draft?.let { snapshot.put("draft", org.json.JSONObject().put("id", "draft").put("name", it.name)
      .put("data", com.example.data.util.DeckCodec.encode(it.cards, it.energyTypes))) }
    pocketConnector.request(config.endpoint, config.apiKey, "/app/snapshot", "PUT", snapshot)
    connectorProposals.value = emptyList()
    message.value = "Colección y mazos sincronizados. El chat puede consultar esta copia."
  }
  fun receiveConnector() = task("CONNECTOR_RECEIVE") {
    connectorProposals.value = emptyList()
    val config = connectorConfig.value ?: error("Configura primero el servidor del conector.")
    val response = pocketConnector.request(config.endpoint, config.apiKey, "/app/proposals", "GET")
    val inventory = repository.inventoryFlow.first()
    val rules = db.cardRulesDao().all("es").associateBy { it.cardId }
    val candidates = inventory.map { AiCandidate(hydrate(it.card, rules[it.card.id]), it.ownedCount, rules[it.card.id]?.rulesText.orEmpty()) }
    val rows = response.getJSONArray("proposals")
    require(rows.length() <= 20)
    var rejected = 0
    val accepted = (0 until rows.length()).mapNotNull { i ->
      try {
        val row = rows.getJSONObject(i)
        // Revalidate every proposal against current local inventory, even after a resync.
        val proposal = AiValidator.parse(row.getJSONObject("deck").toString(), candidates)
        com.example.domain.DeckChat.parse(org.json.JSONObject().put("message", "Propuesta del conector").put("proposal", row.getJSONObject("deck")).toString(), candidates)
        proposal
      } catch (_: Exception) { rejected++; null }
    }
    connectorProposals.value = accepted
    message.value = "${accepted.size} propuestas válidas para revisar" + if (rejected > 0) ". $rejected rechazadas por cartas, cantidades o estructura." else "."
  }
  val connectorCommands = MutableStateFlow<List<org.json.JSONObject>>(emptyList())
  fun receiveConnectorCommands() = task("CONNECTOR_COMMANDS") {
    val config = connectorConfig.value ?: error("Configura primero el servidor del conector.")
    val response = pocketConnector.request(config.endpoint, config.apiKey, "/app/commands", "GET")
    val rows = response.getJSONArray("commands")
    require(rows.length() <= 20) { "Demasiados comandos pendientes." }
    connectorCommands.value = (0 until rows.length()).map { rows.getJSONObject(it) }
    message.value = "${rows.length()} instrucciones pendientes. Revisa antes de ejecutarlas."
  }
  fun sendConnectorDiagnostics() = task("CONNECTOR_DIAGNOSTICS") {
    val config = connectorConfig.value ?: error("Configura primero el servidor del conector.")
    val version = getApplication<android.app.Application>().packageManager.getPackageInfo(getApplication<android.app.Application>().packageName, 0).versionName ?: "unknown"
    // Deliberately exclude logs, tokens, device identifiers and exception traces.
    val payload = org.json.JSONObject().put("version", version).put("errors", org.json.JSONArray())
    pocketConnector.request(config.endpoint, config.apiKey, "/app/diagnostics", "PUT", payload)
    message.value = "Estado básico enviado. No se compartieron registros ni datos sensibles."
  }
  fun runSafeConnectorCommands() = task("CONNECTOR_COMMANDS") {
    val config = connectorConfig.value ?: error("Configura primero el servidor del conector.")
    val response = pocketConnector.request(config.endpoint, config.apiKey, "/app/commands", "GET")
    val rows = response.getJSONArray("commands")
    require(rows.length() <= 20) { "Demasiados comandos pendientes." }
    var done = 0
    for (i in 0 until rows.length()) {
      val cmd = rows.getJSONObject(i)
      val action = cmd.getString("action")
      if (action !in setOf("inspect_errors", "inspect_decks", "inspect_collection")) continue
      val report = when (action) {
        "inspect_errors" -> "Diagnóstico disponible desde la aplicación; no se ha autorizado enviar registros sensibles."
        "inspect_decks" -> "Mazos guardados: ${repository.savedDecksFlow.first().size}"
        else -> "Cartas en inventario: ${repository.inventoryFlow.first().size}"
      }
      pocketConnector.request(config.endpoint, config.apiKey, "/app/commands/result", "POST",
        org.json.JSONObject().put("id", cmd.getString("id")).put("status", "completed").put("message", report))
      done++
    }
    val remaining = pocketConnector.request(config.endpoint, config.apiKey, "/app/commands", "GET").getJSONArray("commands")
    connectorCommands.value = (0 until remaining.length()).map { remaining.getJSONObject(it) }
    message.value = "$done consultas de diagnóstico respondidas. Las acciones de modificación requieren aprobación en Android."
  }
  fun deleteConnectorData() = task("CONNECTOR_DELETE") {
    val config = connectorConfig.value ?: error("Configura primero el servidor del conector.")
    pocketConnector.request(config.endpoint, config.apiKey, "/app/data", "DELETE")
    connectorProposals.value = emptyList()
    connectorCommands.value = emptyList()
    message.value = "Copia de colección y propuestas eliminadas del servidor."
  }
  private val chatGptPlanRepository = ChatGptPlanRepository(application)
  val chatGptPlanAccount = MutableStateFlow<ChatGptPlanAccount?>(null)
  val chatGptPlanModels = MutableStateFlow<List<ChatGptModel>>(emptyList())
  private val chatGptPreferences = application.getSharedPreferences("rotom-chatgpt-settings", Application.MODE_PRIVATE)
  val selectedChatGptPlanModel = MutableStateFlow(chatGptPreferences.getString("selected-model", "").orEmpty())
  private suspend fun loadChatGptPlanModels(): List<ChatGptModel> {
    val found = chatGptPlanRepository.models()
    chatGptPlanModels.value = found
    if (selectedChatGptPlanModel.value !in found.map { it.slug }) {
      val first = found.firstOrNull()?.slug.orEmpty()
      selectedChatGptPlanModel.value = first
      chatGptPreferences.edit().putString("selected-model", first).apply()
    }
    return found
  }
  fun connectChatGptPlan(activity: android.app.Activity) = task("CHATGPT_SIGN_IN") {
    val account = chatGptPlanRepository.signIn(activity)
    chatGptPlanAccount.value = account
    chatGptPlanModels.value = emptyList()
    message.value = "Cuenta ChatGPT conectada. Cargando modelos disponibles…"
    try {
      val found = loadChatGptPlanModels()
      message.value = if (found.isEmpty()) "Cuenta conectada, pero no aparecen modelos disponibles para Rotom."
        else "ChatGPT conectado como ${account.email.ifBlank { "tu cuenta" }}. Modelo listo: ${found.first { it.slug == selectedChatGptPlanModel.value }.displayName}."
    } catch (e: CancellationException) { throw e }
    catch (_: Exception) {
      message.value = "ChatGPT quedó conectado. No se cargaron los modelos; vuelve a intentarlo desde el selector."
    }
  }
  fun discoverChatGptPlanModels() = task("CHATGPT_MODELS") {
    chatGptPlanModels.value = emptyList()
    val found = loadChatGptPlanModels()
    message.value = if (found.isEmpty()) "Tu cuenta no muestra modelos disponibles para esta app."
      else "${found.size} modelos disponibles en tu cuenta ChatGPT. Modelo seleccionado: ${found.first { it.slug == selectedChatGptPlanModel.value }.displayName}."
  }
  fun selectChatGptPlanModel(model: String) {
    if (chatGptPlanModels.value.any { it.slug == model }) {
      selectedChatGptPlanModel.value = model
      chatGptPreferences.edit().putString("selected-model", model).apply()
    }
  }
  fun disconnectChatGptPlan() = task("CHATGPT_SIGN_OUT") {
    val hadAccount = chatGptPlanAccount.value != null
    try {
      chatGptPlanRepository.signOut()
      message.value = "Cuenta ChatGPT desconectada. Los tokens se eliminaron de este teléfono."
    } finally {
      chatGptPlanAccount.value = null
      chatGptPlanModels.value = emptyList()
      selectedChatGptPlanModel.value = ""
      chatGptPreferences.edit().remove("selected-model").apply()
    }
    if (!hadAccount) message.value = "No hay una cuenta ChatGPT conectada."
  }
  fun askChatGptPlan(action: AiDeckAction, target: GeneratedDeck?, model: String) = task("CHATGPT_PLAN_REQUEST") {
    proposal.value = null
    try {
      require(chatGptPlanAccount.value?.canUsePlan == true) { "Conecta ChatGPT y autoriza el uso del plan." }
      require(action.canUse(target?.totalCardCount)) { "Abre un mazo adecuado para esta acción." }
      require(model == selectedChatGptPlanModel.value && model.isNotBlank()) { "Elige un modelo de ChatGPT." }
      val (candidates, requestedTarget) = assistantContext(action == AiDeckAction.COMPLETE, target, "es")
      val reference = if (action == AiDeckAction.IMPROVE) target!!.cards else null
      val prompt = ExternalAiExchange.prompt(meta, goal, candidates, requestedTarget, reference)
      val result = chatGptPlanRepository.request(model, prompt)
      val current = repository.inventoryFlow.first().associateBy { it.card.id }
      val available = candidates.map { it.copy(owned = current[it.card.id]?.ownedCount ?: 0) }
      proposal.value = withContext(Dispatchers.Default) {
        AiValidator.parse(ExternalAiExchange.response(result), available, requestedTarget)
      }
      message.value = "Mazo validado con tu colección actual. Revisa la propuesta antes de guardarla."
    } catch (e: CancellationException) { throw e }
    catch (e: Exception) {
      val safe = e.message?.takeIf { it.startsWith("ChatGPT") || it.startsWith("La consulta de ChatGPT") ||
        it.startsWith("La respuesta de ChatGPT") || it.startsWith("El plan ChatGPT") ||
        it.startsWith("Conecta tu cuenta") || it.startsWith("Elige un modelo") ||
        it.startsWith("Necesitas al menos") || it.startsWith("Cartas obligatorias fuera") ||
        it.startsWith("El borrador contiene") || it.startsWith("La propuesta") }
      message.value = safe ?: "No se pudo completar la consulta con ChatGPT. Tu colección y mazo se conservan."
      ErrorLogManager.event("CHATGPT_PLAN_REQUEST", "category=${com.example.data.ai.AiFailure.category(e)}", e)
    }
  }
  private val connectionStore = AiConnectionStore(application)
  val connection = MutableStateFlow(AiConnection())
  val profiles = MutableStateFlow(AiProfiles())
  val availableModels = MutableStateFlow<List<String>>(emptyList())
  val connectionReady = MutableStateFlow(false)
  fun saveConnection(value: AiConnection) = task("AI_CONFIG") {
    require(connectionReady.value) { "Espera a que se carguen las conexiones guardadas." }
    require(value.apiKey.isNotBlank() && value.apiKey.length <= 4096 && value.apiKey.all { it.code in 33..126 }) { "Introduce una clave API válida." }
    require(value.model.matches(Regex("[a-zA-Z0-9._:/-]{1,120}")) && value.endpoint.length <= 2000) { "Revisa modelo y URL." }
    withContext(Dispatchers.IO) { connectionStore.save(value) }
    profiles.value = withContext(Dispatchers.IO) { connectionStore.loadProfiles() }
    connection.value = value
    availableModels.value = emptyList()
    proposal.value = null
    message.value = "Conexión guardada en este dispositivo. Aún no se ha consultado la API."
  }
  fun removeConnection() = task("AI_CONFIG") {
    require(connectionReady.value) { "Espera a que se carguen las conexiones guardadas." }
    val next = withContext(Dispatchers.IO) { connectionStore.remove(connection.value.id) }
    profiles.value = next
    connection.value = next.active ?: AiConnection()
    availableModels.value = emptyList()
    proposal.value = null
    message.value = "Conexión eliminada."
  }
  fun selectConnection(id: String) = task("AI_CONFIG") {
    require(connectionReady.value) { "Espera a que se carguen las conexiones guardadas." }
    val next = withContext(Dispatchers.IO) { connectionStore.select(id) }
    profiles.value = next; connection.value = next.active!!; proposal.value = null; availableModels.value = emptyList()
    deckChatSuggestion.value = null
    if (pendingChat != null) deckChatStatus.value = "Conexión: ${next.active!!.label}. Puedes reintentar la consulta pendiente."
    message.value = "Conexión activa: ${next.active!!.label}."
  }
  fun discoverModels(value: AiConnection) = task("AI_MODELS") {
    availableModels.value = emptyList()
    try { availableModels.value = connectedAssistant.models(value) }
    catch (e: CancellationException) { throw e }
    catch (e: Exception) { ErrorLogManager.event("AI_MODELS", "category=${com.example.data.ai.AiFailure.category(e)}", e); message.value = "No se pudieron listar modelos. Revisa la clave y URL; puedes introducir el modelo manualmente."; return@task }
    message.value = "${availableModels.value.size} modelos filtrados para texto. Listar no comprueba cuota ni generación. Guarda el modelo elegido; una consulta puede consumir cuota."
  }
  fun askConnected(action: AiDeckAction, target: GeneratedDeck?) = task("AI_CONNECTED") {
    proposal.value = null
    try {
      require(action.canUse(target?.totalCardCount)) { "Abre un mazo adecuado para esta acción." }
      val (candidates, requestedTarget) = assistantContext(action == AiDeckAction.COMPLETE, target, "es")
      val reference = if (action == AiDeckAction.IMPROVE) target!!.cards else null
      val prompt = ExternalAiExchange.prompt(meta, goal, candidates, requestedTarget, reference)
      val result = connectedAssistant.request(connection.value, prompt)
      val current = repository.inventoryFlow.first().associateBy { it.card.id }
      val available = candidates.map { it.copy(owned = current[it.card.id]?.ownedCount ?: 0) }
      proposal.value = withContext(Dispatchers.Default) {
        AiValidator.parse(ExternalAiExchange.response(result), available, requestedTarget)
      }
      message.value = "Mazo validado con tu colección actual. Revisa la propuesta antes de guardarla."
    } catch (e: CancellationException) { throw e }
    catch (e: Exception) {
      // Provider bodies and network exception messages may contain private content.
      val safe = e.message?.takeIf { it.startsWith("Cuota") || it.startsWith("La API") ||
        it.startsWith("Modelo o") || it.startsWith("Hay demasiadas") || it.startsWith("Necesitas al menos") ||
        it.startsWith("La respuesta quedó") || it.startsWith("Cartas obligatorias fuera") || it.startsWith("El borrador contiene") || it.startsWith("Fuera de los criterios") || it.startsWith("Revisa los códigos") || it.startsWith("Una carta no puede") || it.startsWith("Las cartas obligatorias") || it.startsWith("La propuesta") || it.startsWith("Faltan cartas obligatorias") || it.startsWith("Introduce tu") || it.startsWith("Revisa el identificador") }
      message.value = safe ?: "No se pudo obtener un mazo válido. Revisa conexión, cartas disponibles y modelo. No se guardó ninguna propuesta."
      val category = com.example.data.ai.AiFailure.category(e)
      ErrorLogManager.event("AI_CONNECTED", "category=$category status=${(e as? com.example.data.ai.AiHttpFailure)?.status ?: 0}", e)
    }
  }
  val deckRules = db.cardRulesDao().observeAll("es")
    .catch { e -> if (e is CancellationException) throw e; ErrorLogManager.event("RULES_READ", "Rules observation failed", e); emit(emptyList()) }
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
  val deckChatMessages = MutableStateFlow<List<DeckChatMessage>>(emptyList())
  val deckChatSuggestion = MutableStateFlow<DeckChatSuggestion?>(null)
  val deckChatStatus = MutableStateFlow<String?>(null)
  val deckChatRetryAvailable = MutableStateFlow(false)
  private data class PendingChat(val deck: GeneratedDeck, val text: String, val action: String, val element: String, val criteria: DeckCriteria)
  private var pendingChat: PendingChat? = null
  private var deckChatBase: GeneratedDeck? = null
  private var deckChatSession = 0L

  fun startDeckChat(deck: GeneratedDeck, force: Boolean = false) {
    if (!force && deckChatBase == deck) return
    deckChatSession++
    pendingChat = null
    deckChatRetryAvailable.value = false
    deckChatBase = deck
    deckChatMessages.value = emptyList()
    deckChatSuggestion.value = null
    deckChatStatus.value = null
  }
  fun discardDeckChatSuggestion() { deckChatSuggestion.value = null; pendingChat = null; deckChatRetryAvailable.value = false; deckChatStatus.value = null }
  fun retryDeckChat(deck: GeneratedDeck) {
    val request = pendingChat ?: return
    if (request.deck != deck) { deckChatStatus.value = "El mazo cambió. Envía una nueva consulta."; return }
    sendDeckChat(request.deck, request.text, request.action, request.element, retry = true, criteria = request.criteria)
  }
  fun sendDeckChat(deck: GeneratedDeck, text: String, action: String = "chat", element: String = "", retry: Boolean = false, criteria: DeckCriteria = DeckCriteria()) {
    if (busy.value || text.isBlank()) return
    startDeckChat(deck)
    val question = text.trim().take(2000)
    if (!retry) deckChatMessages.value = (deckChatMessages.value + DeckChatMessage(true, question)).takeLast(20)
    pendingChat = PendingChat(deck, question, action, element, criteria)
    deckChatRetryAvailable.value = true
    deckChatSuggestion.value = null
    deckChatStatus.value = null
    val history = deckChatMessages.value
    val session = deckChatSession
    task("DECK_CHAT") {
      var stage = "PREPARE"
      val requestId = java.util.UUID.randomUUID().toString().take(8)
      ErrorLogManager.event("AI_REQUEST", "id=$requestId stage=PREPARE provider=${connection.value.provider.name}")
      try {
        require(connectionReady.value && connection.value.apiKey.isNotBlank()) { "Introduce tu clave API en Conexiones." }
        val inventory = repository.inventoryFlow.first()
        var rules = db.cardRulesDao().all("es").associateBy { it.cardId }
        val referenceIds = deck.cards.map { it.card.id }.toSet()
        val types = element.takeIf { it.isNotBlank() }?.let { setOf(it) } ?: deck.energyTypes.toSet()
        val pool = criteria.pool(inventory).filter { (types.isEmpty() || it.card.type in types ||
          it.card.type in setOf("Entrenador", "Incoloro") || it.card.id in referenceIds) }
          .sortedWith(compareByDescending<CardWithInventory> { it.card.id in referenceIds }
            .thenByDescending { it.card.id in criteria.required }.thenByDescending { it.card.type == "Entrenador" }.thenByDescending { item -> question.contains(item.card.id, true) || question.contains(item.card.name, true) }.thenBy { it.card.id })
        require(pool.map { it.card.id }.containsAll(criteria.required)) { "Cartas obligatorias fuera de la energía seleccionada. Revisa Energía." }
        if (action == "complete") require(pool.map { it.card.id }.containsAll(deck.cards.filter { it.count > 0 }.map { it.card.id })) { "El borrador contiene cartas fuera de estos criterios. Amplía las expansiones o crea otra lista." }
        val priorityIds = referenceIds + criteria.required
        val priorities = pool.filter { it.card.id in priorityIds || question.contains(it.card.id, true) || question.contains(it.card.name, true) }
        val parents = priorities.map { it.card.evolvesFrom }.filter { it.isNotBlank() }.toSet()
        val ancestors = pool.filter { it.card.rulesName in parents }
        val grandparents = ancestors.map { it.card.evolvesFrom }.filter { it.isNotBlank() }.toSet()
        val selectedPool = (priorities + ancestors + pool.filter { it.card.rulesName in grandparents } +
          pool.filter { it.card.type == "Entrenador" }.take(16) + pool.filter { it.card.type != "Entrenador" })
          .distinctBy { it.card.id }.take(80)
        ErrorLogManager.event("AI_CONTEXT", "id=$requestId pool=${pool.size} selected=${selectedPool.size} expansions=${criteria.expansions.size} required=${criteria.required.size} excluded=${criteria.excluded.size} ownedOnly=${criteria.ownedOnly}")
        loadMissingRules(selectedPool.map { it.card.id }, "es") { deckChatStatus.value = it }
        rules = db.cardRulesDao().all("es").associateBy { it.cardId }
        val facts = withContext(Dispatchers.IO) { com.example.data.repository.LocalCombatRepository.snapshot(getApplication()) }
        val candidates = mutableListOf<AiCandidate>()
        // Keep the complete context below the provider's 60 KB request limit.
        var size = DeckChat.prompt(deck, emptyList(), history, action, criteria).toByteArray(Charsets.UTF_8).size
        val overhead = DeckChat.prompt(deck, emptyList(), emptyList(), action, criteria).toByteArray(Charsets.UTF_8).size
        for (item in selectedPool) {
          val rule = rules[item.card.id]
          val local = facts[item.card.id]
          val effectText = rule?.rulesText?.takeIf { it.isNotBlank() } ?: local?.let { value ->
            (listOf(value.text) + value.attacks.map { a -> listOf(a.name, "Daño: ${a.damage}", a.cost?.joinToString(prefix = "Coste: ") ?: "Coste desconocido", a.effect).joinToString(" · ") }).filter(String::isNotBlank).joinToString("\n")
          }.orEmpty()
          val candidate = AiCandidate(hydrate(item.card, rule), item.ownedCount, effectText.take(3000))
          val cost = (DeckChat.prompt(deck, listOf(candidate), emptyList(), action, criteria).toByteArray(Charsets.UTF_8).size -
            overhead) + 2
          if (size + cost > 57000) continue
          candidates.add(candidate); size += cost
        }
        require(candidates.map { it.card.id }.containsAll(criteria.required)) { "Las cartas obligatorias no caben en la consulta. Reduce los criterios." }
        deckChatStatus.value = "Consultando ${candidates.size} cartas · ${candidates.count { it.text.isNotBlank() }} con efectos completos…"
        stage = "REQUEST"
        ErrorLogManager.event("AI_REQUEST", "id=$requestId stage=REQUEST")
        val result = connectedAssistant.request(connection.value, DeckChat.prompt(deck, candidates, history, action, criteria)) { status ->
          if (deckChatSession == session) deckChatStatus.value = status
        }
        val current = repository.inventoryFlow.first().associate { it.card.id to it.ownedCount }
        stage = "VALIDATE"
        val answer = withContext(Dispatchers.Default) { DeckChat.parse(result, candidates.map { it.copy(owned = if (criteria.ownedOnly) current[it.card.id] ?: 0 else 2) }) }
        answer.proposal?.let { criteria.validate(it.deck) }
        if (action == "complete" && answer.proposal != null) {
          val counts = answer.proposal.deck.cards.associate { it.card.id to it.count }
          require(deck.cards.all { (counts[it.card.id] ?: 0) >= minOf(it.count, current[it.card.id] ?: 0) })
        }
        if (deckChatBase != deck || deckChatSession != session) return@task
        ErrorLogManager.event("AI_SUCCESS", "id=$requestId stage=VALIDATE")
        pendingChat = null; deckChatRetryAvailable.value = false
        deckChatStatus.value = null
        deckChatMessages.value = (history + DeckChatMessage(false, answer.text)).takeLast(20)
        deckChatSuggestion.value = answer.proposal?.let { DeckChatSuggestion(deck, it, criteria) }
        if (answer.proposal == null && pool.size > candidates.size) deckChatStatus.value = "La consulta incluyó ${candidates.size} de ${pool.size} cartas. Selecciona expansiones o cartas obligatorias para priorizar las que necesitas."
      } catch (e: CancellationException) { throw e }
      catch (e: Exception) {
        if (deckChatBase == deck && deckChatSession == session) {
          val safe = e.message?.takeIf { it.startsWith("Cuota") || it.startsWith("La API") || it.startsWith("Modelo o") ||
            it.startsWith("Cartas obligatorias fuera") || it.startsWith("El borrador contiene") || it.startsWith("Fuera de los criterios") || it.startsWith("Revisa los códigos") || it.startsWith("Una carta no puede") || it.startsWith("Las cartas obligatorias") || it.startsWith("La propuesta") || it.startsWith("Faltan cartas obligatorias") || it.startsWith("Introduce tu") || it.startsWith("La respuesta quedó") }
          deckChatStatus.value = safe ?: "No se obtuvo una respuesta válida. Revisa tu conexión y vuelve a intentarlo. Tu mazo se conserva."
        }
        ErrorLogManager.event("DECK_CHAT", "id=$requestId stage=$stage category=${com.example.data.ai.AiFailure.category(e, stage)} status=${(e as? com.example.data.ai.AiHttpFailure)?.status ?: 0}", e)
      }
    }
  }
  private val filter = MutableStateFlow(RulesFilter())
  val matches = filter.flatMapLatest { query -> db.cardRulesDao().filter(query.language, query.minHp,
    query.maxHp, query.element, query.role, RoleClassifier.normalize(query.keyword)) }
    .catch { e -> if (e is CancellationException) throw e; ErrorLogManager.event("RULES_READ", "Filter query failed", e); message.value = "No se puede consultar la caché de efectos."; emit(emptyList()) }
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
  val busy = MutableStateFlow(false)
  val message = MutableStateFlow<String?>(null)
  init {
    viewModelScope.launch(Dispatchers.IO) {
      try { metaSnapshot.value = tournamentRepository.cached() } catch (_: Exception) { }
      finally { metaReady.value = true }
    }
    viewModelScope.launch(Dispatchers.IO) {
      try { val saved = connectionStore.loadProfiles(); profiles.value = saved; saved.active?.let { connection.value = it } }
      catch (_: Exception) { message.value = "No se pudieron leer las conexiones IA. El archivo guardado se conserva; reinicia la app para intentar recuperarlo." }
      finally { connectionReady.value = true }
      try {
        val account = chatGptPlanRepository.account()
        chatGptPlanAccount.value = account
        if (account != null) try { loadChatGptPlanModels() }
          catch (e: CancellationException) { throw e }
          catch (_: Exception) { message.value = "ChatGPT está conectado. Abre el selector para volver a cargar sus modelos." }
      } catch (e: CancellationException) { throw e }
      catch (_: Exception) { message.value = "No se pudo leer la conexión de ChatGPT. Los datos cifrados se conservan." }
    }
  }
  val proposal = MutableStateFlow<AiProposal?>(null)
  val board = MutableStateFlow<SandboxState?>(null)
  private val undo = ArrayDeque<SandboxState>()
  var endpoint: String = ""
  var token: String = ""
  var meta: String = ""
  var goal: String = ""
  var candidateType: String = ""
  val externalPrompt = MutableStateFlow<String?>(null)
  private var externalCandidates: List<AiCandidate>? = null
  private var externalTarget: List<DeckCardEntry>? = null
  private var job: Job? = null

  fun updateFilter(query: RulesFilter) { filter.value = query }
  fun clearToken() { token = ""; message.value = "Token eliminado de la sesión." }
  fun cancel() { job?.cancel(); message.value = "Operación cancelada." }
  private fun task(operation: String, action: suspend () -> Unit) {
    if (busy.value) return
    job = viewModelScope.launch {
      busy.value = true
      message.value = null
      try { action() }
      catch (e: CancellationException) { throw e }
      catch (e: Exception) { ErrorLogManager.event(operation, "Operation failed", e); message.value = e.message ?: "No se pudo completar la operación." }
      finally { busy.value = false }
    }
  }
  fun indexCards(ids: List<String>, language: String) = task("RULES_INDEX") {
    val known = db.cardRulesDao().all(language).map { it.cardId }.toSet()
    val pending = ids.distinct().filter { it !in known }
    if (pending.isEmpty()) { message.value = "Estas cartas ya están indexadas. Abre sus detalles para actualizarlas."; return@task }
    var completed = 0
    pending.forEachIndexed { index, id ->
      currentCoroutineContext().ensureActive()
      try {
        withContext(Dispatchers.IO) { CardDetailsClient.load(getApplication(), id, language) }
        if (db.cardRulesDao().get(id, language) != null) completed++
      } catch (e: CancellationException) { throw e }
      catch (e: Exception) { ErrorLogManager.event("RULES_FETCH", "Card rules unavailable", e) }
      message.value = "Indexando ${index + 1}/${pending.size} · $completed disponibles"
      delay(100)
    }
    message.value = "Indexadas $completed de ${pending.size}. Las cartas sin datos no aparecen en filtros de efectos."
  }

  private suspend fun loadMissingRules(ids: List<String>, language: String, progress: (String) -> Unit) {
    val known = db.cardRulesDao().all(language).associateBy { it.cardId }
    val missing = ids.distinct().filter { known[it]?.rulesText?.contains("Retirada:") != true }
    val limit = missing.take(24)
    kotlinx.coroutines.withTimeoutOrNull(30_000) {
      for (batch in limit.chunked(3)) {
        currentCoroutineContext().ensureActive()
        progress("Cargando datos de cartas… Puedes cancelar.")
        kotlinx.coroutines.coroutineScope {
          batch.map { id -> async(kotlinx.coroutines.Dispatchers.IO) {
            try { CardDetailsClient.load(getApplication(), id, language) }
            catch (e: CancellationException) { throw e }
            catch (_: Exception) { /* Missing effects remain explicit in the prompt. */ }
          } }.forEach { it.await() }
        }
      }
    }

  }

  private fun hydrate(card: com.example.data.model.PokemonCard, rule: CardRulesEntity?): com.example.data.model.PokemonCard {
    if (rule == null) return card
    val normalizedStage = when (RoleClassifier.normalize(rule.stage)) {
      "basic", "basico" -> "basic"; "stage1", "stage 1", "fase 1" -> "1"; "stage2", "stage 2", "fase 2" -> "2"; else -> card.stage
    }
    val type = when (RoleClassifier.normalize(rule.element)) {
      "grass", "planta" -> "Planta"; "fire", "fuego" -> "Fuego"; "water", "agua" -> "Agua"
      "lightning", "rayo" -> "Rayo"; "psychic", "psiquico" -> "Psíquico"; "fighting", "lucha" -> "Lucha"
      "darkness", "oscuridad" -> "Oscuridad"; "metal" -> "Metal"; "dragon" -> "Dragón"
      "colorless", "incoloro" -> "Incoloro"; else -> card.type
    }
    val pokemon = RoleClassifier.normalize(rule.category) == "pokemon"
    val trainer = RoleClassifier.normalize(rule.category) in setOf("trainer", "entrenador")
    return card.copy(hp = rule.hp ?: card.hp, category = if (pokemon) "pokemon" else if (trainer && card.category == "unknown") "trainer" else card.category,
      stage = normalizedStage, type = if (trainer) "Entrenador" else type,
      evolvesFrom = card.evolvesFrom.ifBlank { rule.evolvesFrom })
  }

  fun askAssistant(replace: Boolean, target: GeneratedDeck?, language: String) = task("AI_ASSIST") {
    val (candidates, hydratedTarget) = assistantContext(replace, target, language)
    if (replace) {
      require(target != null && target.totalCardCount == 20) { "Abre un mazo objetivo de 20 cartas en el editor." }
      proposal.value = assistant.suggestReplacements(endpoint, token, meta, hydratedTarget!!, candidates)
    } else proposal.value = assistant.generateDeck(endpoint, token, meta, goal, candidates)
    message.value = "Propuesta validada. Revisa la estrategia antes de abrirla en el editor."
  }

  private suspend fun assistantContext(replace: Boolean, target: GeneratedDeck?, language: String):
    Pair<List<AiCandidate>, List<DeckCardEntry>?> {
    val inventory = repository.inventoryFlow.first()
    loadMissingRules(inventory.filter { it.ownedCount > 0 && (candidateType.isBlank() || it.card.type in setOf(candidateType, "Entrenador", "Incoloro")) }.sortedBy { it.card.id }.take(80).map { it.card.id }, language) { message.value = it }
    val rules = db.cardRulesDao().all(language).associateBy { it.cardId }
    val targetIds = target?.cards?.map { it.card.id }?.toSet() ?: emptySet()
    val candidates = inventory.filter { item ->
      val type = hydrate(item.card, rules[item.card.id]).type
      (item.ownedCount > 0 && (candidateType.isBlank() || type == candidateType || type == "Entrenador")) ||
        (replace && item.card.id in targetIds)
    }.map { item ->
      AiCandidate(hydrate(item.card, rules[item.card.id]), item.ownedCount, rules[item.card.id]?.rulesText ?: "")
    }
    require(candidates.sumOf { minOf(it.owned, 2) } >= 20) { "Necesitas al menos 20 copias disponibles." }

    require(!replace || target?.totalCardCount == 20) { "Abre un mazo objetivo de 20 cartas en el editor." }
    return candidates to if (replace) target!!.cards.map { it.copy(card = hydrate(it.card, rules[it.card.id])) } else null
  }

  fun prepareExternal(replace: Boolean, target: GeneratedDeck?) = task("AI_EXTERNAL_PREPARE") {
    externalPrompt.value = null
    externalCandidates = null
    externalTarget = null
    proposal.value = null
    val (candidates, requestedTarget) = assistantContext(replace, target, "es")
    val text = withContext(Dispatchers.Default) { ExternalAiExchange.prompt(meta, goal, candidates, requestedTarget) }
    externalCandidates = candidates
    externalTarget = requestedTarget
    externalPrompt.value = text
    message.value = "Consulta preparada. Revisa el contenido antes de copiarlo a tu IA."
  }

  fun importExternal(text: String) = task("AI_EXTERNAL_IMPORT") {
    proposal.value = null
    val original = externalCandidates ?: error("Prepara primero una consulta.")
    val current = repository.inventoryFlow.first().associateBy { it.card.id }
    val available = original.map { it.copy(owned = current[it.card.id]?.ownedCount ?: 0) }
    val result = withContext(Dispatchers.Default) {
      try { AiValidator.parse(ExternalAiExchange.response(text), available, externalTarget) }
      catch (e: Exception) {
        val reason = e.message?.takeIf { it != "Failed requirement." && it != "Check failed." }
        throw IllegalArgumentException(reason ?: "La respuesta no cumple el mazo solicitado. Pide a tu IA solo el resultado del formato de la consulta, con 20 cartas, cantidades disponibles y energías.")
      }
    }
    proposal.value = result
    message.value = "Respuesta validada con las cantidades actuales. Revisa el borrador."
  }

  fun clearExternal() {
    if (busy.value) return
    externalPrompt.value = null
    externalCandidates = null
    externalTarget = null
    proposal.value = null
    message.value = "Consulta y propuesta eliminadas de la sesión."
  }

  fun startSandbox(deck: GeneratedDeck?, language: String) = task("SANDBOX_START") {
    require(deck != null) { "Primero crea o abre un mazo." }
    val rules = db.cardRulesDao().all(language).associateBy { it.cardId }
    val cards = deck.cards.map { it.copy(card = hydrate(it.card, rules[it.card.id])) }
    board.value = SandboxEngine.start(cards)
    undo.clear()
    message.value = "Sesión de práctica iniciada. La colección y los mazos guardados no cambian."
  }
  fun updateBoard(transform: (SandboxState) -> SandboxState) {
    val old = board.value ?: return
    try {
      val next = transform(old)
      if (undo.size >= 100) undo.removeFirst()
      undo.addLast(old); board.value = next
    } catch (e: Exception) { message.value = e.message ?: "Acción no permitida." }
  }
  fun undoMove() { if (undo.isNotEmpty()) board.value = undo.removeLast() }
}
