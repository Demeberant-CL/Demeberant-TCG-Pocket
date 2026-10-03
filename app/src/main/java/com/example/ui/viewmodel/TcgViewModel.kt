package com.example.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.api.GeminiDeckAnalyzer
import com.example.data.api.MetaAnalysisResult
import com.example.data.local.AppDatabase
import com.example.data.local.SavedDeckEntity
import com.example.data.model.BoosterPack
import com.example.data.preferences.UserPreferences
import com.example.data.preferences.UserPreferencesRepository
import com.example.data.repository.CardCatalog
import com.example.data.repository.CardWithInventory
import com.example.data.repository.DeckBuilderEngine
import com.example.data.repository.DeckCardEntry
import com.example.data.repository.GeneratedDeck
import com.example.data.repository.InventoryRepository
import com.example.data.repository.ParsedCsvCard
import com.example.data.util.ErrorLogManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CancellationException
import com.example.data.util.CollectionCsv
import com.example.data.util.CardId
import com.example.data.util.AppBackup
import com.example.data.util.BackupSnapshot
import com.example.data.util.DeckCodec

class TcgViewModel(application: Application) : AndroidViewModel(application) {

  private val repository: InventoryRepository
  private val preferencesRepository: UserPreferencesRepository

  init {
    ErrorLogManager.init(application)
    CardCatalog.loadBundled(application)
    val db = AppDatabase.getDatabase(application)
    repository = InventoryRepository.fromDatabase(db)
    preferencesRepository = UserPreferencesRepository(application)
  }

  private val initialization = kotlinx.coroutines.CompletableDeferred<Unit>()

  // Preferences DataStore Flow
  val userPreferences: StateFlow<UserPreferences> = preferencesRepository.userPreferencesFlow
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), UserPreferences())

  // Collection inventory Flow
  val inventoryList: StateFlow<List<CardWithInventory>> = repository.inventoryFlow
    .catch { error ->
      _csvStatusMessage.value = "No se puede leer la colección: ${error.localizedMessage}"
      ErrorLogManager.logError(getApplication(), "INVENTORY_READ", "Error al leer la colección", error)
      emit(emptyList())
    }
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

  // Saved Decks Flow
  val savedDecks: StateFlow<List<SavedDeckEntity>> = repository.savedDecksFlow
    .catch { error ->
      _csvStatusMessage.value = "No se pueden leer los mazos: ${error.localizedMessage}"
      ErrorLogManager.logError(getApplication(), "DECKS_READ", "Error al leer mazos", error)
      emit(emptyList())
    }
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

  private val _searchQuery = MutableStateFlow("")
  val searchQuery = _searchQuery.asStateFlow()

  private val _expansionFilter = MutableStateFlow<String?>(null)
  val expansionFilter = _expansionFilter.asStateFlow()
  private val _rarityFilter = MutableStateFlow<com.example.data.model.CardRarity?>(null)
  val rarityFilter = _rarityFilter.asStateFlow()
  fun setExpansionFilter(value: String?) { _expansionFilter.value = value }
  fun setRarityFilter(value: com.example.data.model.CardRarity?) { _rarityFilter.value = value }

  private val _collectionFilter = MutableStateFlow(com.example.data.util.CollectionFilter.ALL)
  val collectionFilter = _collectionFilter.asStateFlow()

  private val _csvStatusMessage = MutableStateFlow<String?>(null)
  val csvStatusMessage = _csvStatusMessage.asStateFlow()

  // Deck Builder
  private val _generatedDeck = MutableStateFlow<GeneratedDeck?>(null)
  val generatedDeck = _generatedDeck.asStateFlow()

  private val _deckBuildPrompt = MutableStateFlow("Pikachu ex Turbo")
  val deckBuildPrompt = _deckBuildPrompt.asStateFlow()

  private val _onlyFromInventoryDeck = MutableStateFlow(true)
  val onlyFromInventoryDeck = _onlyFromInventoryDeck.asStateFlow()

  private val _isGeneratingDeck = MutableStateFlow(false)
  val isGeneratingDeck = _isGeneratingDeck.asStateFlow()

  // Meta Analysis
  private val _metaAnalysis = MutableStateFlow<MetaAnalysisResult?>(null)
  val metaAnalysis = _metaAnalysis.asStateFlow()

  private val _isAnalyzingMeta = MutableStateFlow(false)
  val isAnalyzingMeta = _isAnalyzingMeta.asStateFlow()

  val filteredCards: StateFlow<List<CardWithInventory>> = combine(
    inventoryList,
    _searchQuery,
    _collectionFilter,
    _expansionFilter,
    _rarityFilter
  ) { list, query, filter, expansion, rarity ->
    list.filter { item ->
      val matchesQuery = query.isBlank() ||
        item.card.name.contains(query, ignoreCase = true) ||
        item.card.id.contains(query, ignoreCase = true) ||
        item.card.type.contains(query, ignoreCase = true)

      matchesQuery && filter.matches(item) &&
        (expansion == null || CardId.split(item.card.id).first == expansion) &&
        (rarity == null || item.card.rarity == rarity)
    }
  }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

  init {
    viewModelScope.launch {
      try {
        // Ensure persisted card names and rarities have been hydrated before generation.
        repository.inventoryFlow.first()
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        _csvStatusMessage.value = "No se pudo cargar la colección: ${e.localizedMessage}"
        ErrorLogManager.logError(getApplication(), "INIT_ASSET_LOAD", "Error al cargar la colección", e)
      } finally {
        initialization.complete(Unit)
      }
      runGeminiMetaAnalysis()

    }
  }

  // Preferences Actions
  fun setThemeMode(mode: com.example.data.preferences.ThemeMode) {
    viewModelScope.launch {
      try { preferencesRepository.setThemeMode(mode) }
      catch (e: CancellationException) { throw e }
      catch (e: Exception) {
        ErrorLogManager.event("SETTINGS_WRITE", "Theme preference could not be saved", e)
        _csvStatusMessage.value = "No se pudo guardar el tema."
      }
    }
  }

  fun setProfileAvatar(id: String) {
    viewModelScope.launch {
      try { preferencesRepository.setAvatar(id) }
      catch (e: CancellationException) { throw e }
      catch (e: Exception) {
        ErrorLogManager.event("SETTINGS_WRITE", "Avatar preference could not be saved", e)
        _csvStatusMessage.value = "No se pudo guardar el avatar."
      }
    }
  }

  // Collection Filter Actions
  fun setSearchQuery(query: String) {
    _searchQuery.value = query
  }

  fun setCollectionFilter(filter: com.example.data.util.CollectionFilter) {
    _collectionFilter.value = filter
  }

  fun toggleWishlist(cardId: String) {
    viewModelScope.launch {
      try {
        repository.toggleWishlist(cardId)
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        ErrorLogManager.logError(getApplication(), "TOGGLE_WISHLIST", "Error al alternar wishlist para $cardId", e)
      }
    }
  }

  fun clearCsvStatusMessage() {
    _csvStatusMessage.value = null
  }

  fun importCsv(csvText: String) {
    viewModelScope.launch {
      try {
        initialization.await()
        val parsed = withContext(Dispatchers.Default) { CollectionCsv.parse(csvText) }
        repository.processParsedCsvCards(parsed)
        _csvStatusMessage.value = "Importación exitosa: ${parsed.size} cartas procesadas."
        runGeminiMetaAnalysis()
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        _csvStatusMessage.value = "Error al procesar CSV: ${e.localizedMessage}"
        ErrorLogManager.logError(getApplication(), "CSV_IMPORT", "Error al importar CSV", e)
      }
    }
  }

  suspend fun generateCsvContent(): String {
    initialization.await()
    val cards = repository.inventoryFlow.first()
    return withContext(Dispatchers.Default) { CollectionCsv.export(cards) }
  }

  // Deck Builder Actions
  fun setDeckPrompt(prompt: String) {
    _deckBuildPrompt.value = prompt
  }

  fun toggleOnlyFromInventoryDeck() {
    _onlyFromInventoryDeck.value = !_onlyFromInventoryDeck.value
  }

  fun generateDeck(customPrompt: String? = null) {
    viewModelScope.launch {
      try {
        _isGeneratingDeck.value = true
        val prompt = customPrompt ?: _deckBuildPrompt.value
        initialization.await()
        val inventory = repository.inventoryFlow.first()
        val ownedMap = inventory.associate { it.card.id to it.ownedCount }

        val deck = withContext(Dispatchers.Default) { DeckBuilderEngine.buildArchetypeDeck(
          userPrompt = prompt,
          onlyFromInventory = _onlyFromInventoryDeck.value,
          ownedMap = ownedMap
        ) }
        editingDeckId = 0
        _automaticEnergies.value = false
        _generatedDeck.value = deck
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        ErrorLogManager.logError(getApplication(), "DECK_BUILDER", "Fallo al generar mazo con prompt: $customPrompt", e)
      } finally {
        _isGeneratingDeck.value = false
      }
    }
  }

  private var editingDeckId = 0L
  private val _automaticEnergies = MutableStateFlow(true)
  val automaticEnergies = _automaticEnergies.asStateFlow()

  fun useAutomaticEnergies() {
    _automaticEnergies.value = true
    _generatedDeck.value = _generatedDeck.value?.let { it.copy(energyTypes = com.example.domain.DeckAutomation.energies(it.cards)) }
  }

  fun createWithMyCards(type: String) {
    viewModelScope.launch {
      try {
        _isGeneratingDeck.value = true
        initialization.await()
        val inventory = repository.inventoryFlow.first()
        val draft = withContext(Dispatchers.Default) { com.example.domain.DeckAutomation.build(inventory, type) }
        editingDeckId = 0
        _automaticEnergies.value = true
        _generatedDeck.value = draft
        reportMessage("Borrador preparado con tus cartas. Revisa y pulsa Guardar mazo.")
      } catch (e: CancellationException) { throw e }
      catch (e: Exception) { reportMessage(e.message ?: "No se pudo crear el borrador.") }
      finally { _isGeneratingDeck.value = false }
    }
  }
  fun reportMessage(value: String) { _csvStatusMessage.value = value }

  fun setQuantity(id: String, quantity: Int) {
    viewModelScope.launch {
      try { initialization.await(); repository.setQuantity(id, quantity); reportMessage("Cantidad guardada.") }
      catch (e: CancellationException) { throw e }
      catch (e: Exception) { reportMessage("No se pudo guardar: ${e.localizedMessage}") }
    }
  }

  suspend fun generateBackupContent(): String {
    initialization.await()
    val (cards, decks) = repository.snapshot()
    val prefs = preferencesRepository.userPreferencesFlow.first()
    return withContext(Dispatchers.Default) { AppBackup.encode(BackupSnapshot(cards, decks, prefs)) }
  }

  fun restoreBackup(value: BackupSnapshot) {
    viewModelScope.launch {
      try {
        initialization.await()
        repository.restoreSnapshot(value.cards, value.decks)
        try { preferencesRepository.restore(value.preferences, value.hasAvatarPreference) }
        catch (e: CancellationException) { throw e }
        catch (e: Exception) { reportMessage("Colección y mazos restaurados; no se pudieron restaurar los ajustes."); return@launch }
        reportMessage("Respaldo restaurado sin borrar las cartas ausentes.")
        runGeminiMetaAnalysis()
      } catch (e: CancellationException) { throw e }
      catch (e: Exception) { reportMessage("No se pudo restaurar: ${e.localizedMessage}") }
    }
  }

  fun openAiProposal(deck: GeneratedDeck, onOpened: () -> Unit = {}, onRejected: (String) -> Unit = {}) = viewModelScope.launch {
    try {
      initialization.await()
      val current = repository.inventoryFlow.first().associate { it.card.id to it.ownedCount }
      com.example.domain.AiValidator.requireAvailable(deck, current)
      _automaticEnergies.value = false
      editingDeckId = 0
      _generatedDeck.value = deck
      reportMessage("Propuesta de IA abierta como borrador. Revisa y guarda si quieres conservarla.")
      onOpened()
    } catch (e: CancellationException) { throw e }
    catch (_: Exception) {
      val reason = "No se abrió la propuesta. Revisa tu colección actual y genera un mazo nuevo."
      reportMessage(reason)
      onRejected(reason)
      ErrorLogManager.event("AI_OPEN", "Proposal could not be opened with current inventory")
    }
  }

  fun openTournamentDeck(deck: GeneratedDeck) {
    require(deck.totalCardCount == 20 && deck.cards.sumOf { it.count } == 20)
    require(deck.cards.all { it.count in 1..2 } && deck.energyTypes.size in 1..3)
    require(deck.cards.groupBy { it.card.rulesName.lowercase() }.values.all { rows -> rows.sumOf { it.count } <= 2 })
    _automaticEnergies.value = false; editingDeckId = 0; _generatedDeck.value = deck
    reportMessage("Mazo de torneo abierto como borrador. Puede incluir cartas que te faltan.")
  }

  fun newManualDeck() {
    _automaticEnergies.value = true
    editingDeckId = 0
    _generatedDeck.value = GeneratedDeck("Mi mazo", "Manual", "", emptyList(), 0,
      listOf("Mazo incompleto: 0/20 cartas."))
  }
  fun editDeckName(value: String) { _generatedDeck.value = _generatedDeck.value?.copy(name = value) }
  fun editDeckStrategy(value: String) { _generatedDeck.value = _generatedDeck.value?.copy(strategy = value) }
  fun toggleDeckEnergy(value: String) {
    require(value in DeckCodec.energyNames)
    val deck = _generatedDeck.value ?: return
    val energies = if (value in deck.energyTypes) deck.energyTypes - value else deck.energyTypes + value
    if (energies.size > 3) { reportMessage("Máximo de tres energías."); return }
    _automaticEnergies.value = false
    _generatedDeck.value = deck.copy(energyTypes = energies)
  }
  fun editDeckQuantity(id: String, count: Int) {
    val deck = _generatedDeck.value ?: return
    val card = CardCatalog.getCardById(id) ?: return
    if (count !in 0..2) return
    val cards = deck.cards.mapNotNull { entry ->
      if (entry.card.id != id) entry else if (count > 0) entry.copy(count = count) else null
    } + if (count > 0 && deck.cards.none { it.card.id == id }) listOf(DeckCardEntry(card, count)) else emptyList()
    if (cards.sumOf { it.count } > 20 || cards.groupBy { it.card.rulesName.lowercase() }.any { (_, list) -> list.sumOf { it.count } > 2 }) {
      reportMessage("Máximo de 20 cartas y dos copias por nombre."); return
    }
    _generatedDeck.value = deck.copy(cards = cards, totalCardCount = cards.sumOf { it.count },
      validationWarnings = DeckBuilderEngine.validate(cards),
      energyTypes = if (_automaticEnergies.value) com.example.domain.DeckAutomation.energies(cards) else deck.energyTypes)
  }

  // Saved Decks Persistence
  fun saveCurrentDeck(customName: String? = null, allowDraft: Boolean = false) {
    viewModelScope.launch {
      val deck = _generatedDeck.value ?: return@launch
      if (!allowDraft && deck.validationWarnings.isNotEmpty()) {
        _csvStatusMessage.value = "El mazo necesita correcciones antes de guardarse: ${deck.validationWarnings.joinToString(" ")}"
        return@launch
      }
      try {
        require(deck.cards.isNotEmpty() && (customName ?: deck.name).isNotBlank()) { "Añade cartas y un nombre." }
        val serializedCards = DeckCodec.encode(deck.cards, deck.energyTypes)
        val entity = SavedDeckEntity(
          id = editingDeckId,
          name = customName ?: deck.name,
          archetype = deck.archetype,
          strategy = deck.strategy,
          cardListSerialized = serializedCards,
          totalCards = deck.totalCardCount
        )
        editingDeckId = repository.saveDeck(entity)
        _csvStatusMessage.value = "¡Mazo '${entity.name}' guardado correctamente en tu base de datos!"
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        reportMessage("Error al guardar: ${e.localizedMessage}")
        ErrorLogManager.logError(getApplication(), "SAVE_DECK", "Error al guardar mazo en base de datos", e)
      }
    }
  }

  fun loadSavedDeck(savedDeck: SavedDeckEntity) {
    try {
      val cardEntries = DeckCodec.decode(savedDeck.cardListSerialized)
      editingDeckId = savedDeck.id
      val savedEnergies = DeckCodec.energies(savedDeck.cardListSerialized)
      _automaticEnergies.value = savedEnergies.isEmpty()
      _generatedDeck.value = GeneratedDeck(
        name = savedDeck.name,
        archetype = savedDeck.archetype,
        strategy = savedDeck.strategy,
        cards = cardEntries,
        totalCardCount = cardEntries.sumOf { it.count },
        validationWarnings = DeckBuilderEngine.validate(cardEntries),
        energyTypes = savedEnergies.ifEmpty { com.example.domain.DeckAutomation.energies(cardEntries) }
      )
      _deckBuildPrompt.value = savedDeck.name
    } catch (e: Exception) {
      reportMessage("Error al abrir mazo: ${e.localizedMessage}")
      ErrorLogManager.logError(getApplication(), "LOAD_DECK", "Error al cargar mazo guardado ${savedDeck.id}", e)
    }
  }

  fun deleteSavedDeck(deckId: Long) {
    viewModelScope.launch {
      try {
        repository.deleteDeck(deckId)
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        ErrorLogManager.logError(getApplication(), "DELETE_DECK", "Error al eliminar mazo $deckId", e)
      }
    }
  }

  fun runGeminiMetaAnalysis() {
    viewModelScope.launch {
      try {
        _isAnalyzingMeta.value = true
        initialization.await()
        val currentInventory = repository.inventoryFlow.first()
        val result = withContext(Dispatchers.Default) { GeminiDeckAnalyzer.computeLocalMetaAnalysis(currentInventory) }
        _metaAnalysis.value = result
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        ErrorLogManager.logError(getApplication(), "META_ANALYSIS", "Error al computar análisis del meta", e)
      } finally {
        _isAnalyzingMeta.value = false
      }
    }
  }
}
