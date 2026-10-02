package com.example.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
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
  private val filter = MutableStateFlow(RulesFilter())
  val matches = filter.flatMapLatest { query -> db.cardRulesDao().filter(query.language, query.minHp,
    query.maxHp, query.element, query.role, RoleClassifier.normalize(query.keyword)) }
    .catch { e -> if (e is CancellationException) throw e; ErrorLogManager.event("RULES_READ", "Filter query failed", e); message.value = "No se puede consultar la caché de efectos."; emit(emptyList()) }
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
  val busy = MutableStateFlow(false)
  val message = MutableStateFlow<String?>(null)
  val proposal = MutableStateFlow<AiProposal?>(null)
  val board = MutableStateFlow<SandboxState?>(null)
  private val undo = ArrayDeque<SandboxState>()
  var endpoint: String = ""
  var token: String = ""
  var meta: String = ""
  var goal: String = ""
  var candidateType: String = ""
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
    val pending = ids.distinct().filter { it !in known }.take(25)
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
    return card.copy(category = if (pokemon) "pokemon" else if (trainer) "trainer" else card.category,
      stage = normalizedStage, type = if (trainer) "Entrenador" else type,
      evolvesFrom = card.evolvesFrom.ifBlank { rule.evolvesFrom })
  }

  fun askAssistant(replace: Boolean, target: GeneratedDeck?, language: String) = task("AI_ASSIST") {
    val inventory = repository.inventoryFlow.first()
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
    if (replace) {
      require(target != null && target.totalCardCount == 20) { "Abre un mazo objetivo de 20 cartas en el editor." }
      val hydrated = target.cards.map { it.copy(card = hydrate(it.card, rules[it.card.id])) }
      proposal.value = assistant.suggestReplacements(endpoint, token, meta, hydrated, candidates)
    } else proposal.value = assistant.generateDeck(endpoint, token, meta, goal, candidates)
    message.value = "Propuesta validada. Revisa la estrategia antes de abrirla en el editor."
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
