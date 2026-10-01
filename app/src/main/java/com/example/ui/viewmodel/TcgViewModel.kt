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

class TcgViewModel(application: Application) : AndroidViewModel(application) {

  private val repository: InventoryRepository
  private val preferencesRepository: UserPreferencesRepository

  init {
    ErrorLogManager.init(application)
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
    _collectionFilter
  ) { list, query, filter ->
    list.filter { item ->
      val matchesQuery = query.isBlank() ||
        item.card.name.contains(query, ignoreCase = true) ||
        item.card.id.contains(query, ignoreCase = true) ||
        item.card.type.contains(query, ignoreCase = true)

      matchesQuery && filter.matches(item)
    }
  }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

  init {
    viewModelScope.launch {
      try {
        if (repository.isInventoryEmpty()) {
          val assetCsv = withContext(Dispatchers.IO) {
            getApplication<Application>().assets.open("coleccion-pokemon-2026-09-29.csv")
              .bufferedReader().use { it.readText() }
          }
          val parsed = withContext(Dispatchers.Default) { CollectionCsv.parse(assetCsv) }
          repository.processParsedCsvCards(parsed)
        }
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
      generateDeck("Pikachu ex Turbo")
    }
  }

  // Preferences Actions
  fun setDarkMode(isDark: Boolean) {
    viewModelScope.launch {
      preferencesRepository.setDarkMode(isDark)
    }
  }

  fun setLanguage(languageCode: String) {
    viewModelScope.launch {
      preferencesRepository.setLanguage(languageCode)
    }
  }

  fun setThemeName(themeName: String) {
    viewModelScope.launch {
      preferencesRepository.setThemeName(themeName)
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
        generateDeck()
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

  // Saved Decks Persistence
  fun saveCurrentDeck(customName: String? = null) {
    viewModelScope.launch {
      val deck = _generatedDeck.value ?: return@launch
      if (deck.validationWarnings.isNotEmpty()) {
        _csvStatusMessage.value = "El mazo necesita correcciones antes de guardarse: ${deck.validationWarnings.joinToString(" ")}"
        return@launch
      }
      try {
        val serializedCards = deck.cards.joinToString(";") { "${it.card.id}:${it.count}" }
        val entity = SavedDeckEntity(
          name = customName ?: deck.name,
          archetype = deck.archetype,
          strategy = deck.strategy,
          cardListSerialized = serializedCards,
          totalCards = deck.totalCardCount
        )
        repository.saveDeck(entity)
        _csvStatusMessage.value = "¡Mazo '${entity.name}' guardado correctamente en tu base de datos!"
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        ErrorLogManager.logError(getApplication(), "SAVE_DECK", "Error al guardar mazo en base de datos", e)
      }
    }
  }

  fun loadSavedDeck(savedDeck: SavedDeckEntity) {
    try {
      val cardEntries = mutableListOf<DeckCardEntry>()
      val parts = savedDeck.cardListSerialized.split(";").filter { it.isNotBlank() }
      parts.forEach { part ->
        val pair = part.split(":")
        if (pair.size == 2) {
          val cardId = CardId.normalize(pair[0])
          val count = pair[1].toIntOrNull() ?: 1
          val card = CardCatalog.getCardById(cardId)
            ?: throw IllegalArgumentException("Carta $cardId no disponible en la colección.")
          cardEntries.add(DeckCardEntry(card, count))
        }
      }
      _generatedDeck.value = GeneratedDeck(
        name = savedDeck.name,
        archetype = savedDeck.archetype,
        strategy = savedDeck.strategy,
        cards = cardEntries,
        totalCardCount = cardEntries.sumOf { it.count },
        validationWarnings = DeckBuilderEngine.validate(cardEntries)
      )
      _deckBuildPrompt.value = savedDeck.name
    } catch (e: Exception) {
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
