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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class TcgViewModel(application: Application) : AndroidViewModel(application) {

  private val repository: InventoryRepository
  private val preferencesRepository: UserPreferencesRepository

  init {
    ErrorLogManager.init(application)
    val db = AppDatabase.getDatabase(application)
    repository = InventoryRepository.fromDatabase(db)
    preferencesRepository = UserPreferencesRepository(application)
  }

  // Preferences DataStore Flow
  val userPreferences: StateFlow<UserPreferences> = preferencesRepository.userPreferencesFlow
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), UserPreferences())

  // Collection inventory Flow
  val inventoryList: StateFlow<List<CardWithInventory>> = repository.inventoryFlow
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

  // Saved Decks Flow
  val savedDecks: StateFlow<List<SavedDeckEntity>> = repository.savedDecksFlow
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

  private val _searchQuery = MutableStateFlow("")
  val searchQuery = _searchQuery.asStateFlow()

  private val _selectedPackFilter = MutableStateFlow<BoosterPack?>(null)
  val selectedPackFilter = _selectedPackFilter.asStateFlow()

  private val _onlyWishlistFilter = MutableStateFlow(false)
  val onlyWishlistFilter = _onlyWishlistFilter.asStateFlow()

  private val _onlyMissingFilter = MutableStateFlow(false)
  val onlyMissingFilter = _onlyMissingFilter.asStateFlow()

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
    _selectedPackFilter,
    _onlyWishlistFilter,
    _onlyMissingFilter
  ) { list, query, pack, onlyWishlist, onlyMissing ->
    list.filter { item ->
      val matchesQuery = query.isBlank() ||
        item.card.name.contains(query, ignoreCase = true) ||
        item.card.id.contains(query, ignoreCase = true) ||
        item.card.type.contains(query, ignoreCase = true)

      val matchesPack = pack == null || item.card.pack == pack
      val matchesWishlist = !onlyWishlist || item.isWishlist
      val matchesMissing = !onlyMissing || item.ownedCount == 0

      matchesQuery && matchesPack && matchesWishlist && matchesMissing
    }
  }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

  init {
    viewModelScope.launch {
      try {
        val existing = repository.inventoryFlow.first()
        if (existing.all { it.ownedCount == 0 }) {
          val assetCsv = getApplication<Application>().assets.open("coleccion-pokemon-2026-09-29.csv")
            .bufferedReader().use { it.readText() }
          importCsv(assetCsv)
        }
      } catch (e: Exception) {
        ErrorLogManager.logError(getApplication(), "INIT_ASSET_LOAD", "Error al pre-cargar coleccion-pokemon-2026-09-29.csv", e)
      }
    }
    runGeminiMetaAnalysis()
    generateDeck("Pikachu ex Turbo")
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

  fun setPackFilter(pack: BoosterPack?) {
    _selectedPackFilter.value = pack
  }

  fun toggleOnlyWishlist() {
    _onlyWishlistFilter.value = !_onlyWishlistFilter.value
  }

  fun toggleOnlyMissing() {
    _onlyMissingFilter.value = !_onlyMissingFilter.value
  }

  fun toggleWishlist(cardId: String) {
    viewModelScope.launch {
      try {
        repository.toggleWishlist(cardId)
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
        val lines = csvText.lines().map { it.trim() }.filter { it.isNotBlank() }
        if (lines.isEmpty()) {
          _csvStatusMessage.value = "Error: El archivo CSV está vacío."
          ErrorLogManager.logError(getApplication(), "CSV_IMPORT", "El archivo CSV proporcionado está vacío.")
          return@launch
        }

        val firstLine = lines[0]
        val delimiter = if (firstLine.contains(";")) ";" else ","
        val headers = lines[0].split(delimiter).map { it.trim().lowercase().removeSurrounding("\"") }

        val setIndex = headers.indexOfFirst { it == "set" || it.contains("expansion") || it.contains("coleccion") }
        val idIndex = headers.indexOfFirst { it == "id" || it.contains("card_id") || it.contains("código") || it.contains("numero") }
        val nameIndex = headers.indexOfFirst { it.contains("nombre") || it.contains("name") }
        val rarityIndex = headers.indexOfFirst { it.contains("rare") || it.contains("rarity") }
        val qtyIndex = headers.indexOfFirst { it.contains("cantidad") || it.contains("quantity") || it.contains("count") || it.contains("copias") }

        val parsedCards = mutableListOf<ParsedCsvCard>()
        val startIndex = if (headers.any { it.contains("id") || it.contains("set") || it.contains("nombre") }) 1 else 0

        for (i in startIndex until lines.size) {
          val rawParts = lines[i].split(delimiter).map { it.trim().removeSurrounding("\"") }
          if (rawParts.size >= 2) {
            val setCode = if (setIndex != -1 && rawParts.size > setIndex) rawParts[setIndex].uppercase() else "A1"
            val cardNumber = if (idIndex != -1 && rawParts.size > idIndex) rawParts[idIndex] else i.toString()
            val name = if (nameIndex != -1 && rawParts.size > nameIndex) rawParts[nameIndex] else "Pokémon $cardNumber"
            val rarity = if (rarityIndex != -1 && rawParts.size > rarityIndex) rawParts[rarityIndex] else "♦"
            val qty = if (qtyIndex != -1 && rawParts.size > qtyIndex) rawParts[qtyIndex].toIntOrNull() ?: 1 else 1

            parsedCards.add(
              ParsedCsvCard(
                setCode = setCode,
                cardNumber = cardNumber,
                name = name,
                rarity = rarity,
                quantity = qty,
                isRegistered = qty > 0
              )
            )
          }
        }

        repository.processParsedCsvCards(parsedCards)
        _csvStatusMessage.value = "¡Importación exitosa! Se procesaron ${parsedCards.size} cartas con sus nombres e imágenes reales."
        runGeminiMetaAnalysis()
      } catch (e: Exception) {
        val err = "Error al procesar CSV: ${e.localizedMessage}"
        _csvStatusMessage.value = err
        ErrorLogManager.logError(getApplication(), "CSV_IMPORT", err, e)
      }
    }
  }

  fun generateCsvContent(): String {
    return try {
      val sb = StringBuilder()
      sb.appendLine("\"Set\",\"ID\",\"Nombre\",\"Rareza\",\"Cantidad\",\"Registrada\"")
      val current = inventoryList.value
      current.forEach { item ->
        val parts = item.card.id.split("-")
        val setVal = if (parts.size > 1) parts[0] else "A1"
        val idVal = if (parts.size > 1) parts[1].trimStart('0').ifEmpty { "0" } else item.card.id
        val isReg = if (item.ownedCount > 0) "sí" else "no"
        sb.appendLine("\"$setVal\",\"$idVal\",\"${item.card.name}\",\"${item.card.rarity.symbol}\",\"${item.ownedCount}\",\"$isReg\"")
      }
      sb.toString().trimEnd()
    } catch (e: Exception) {
      ErrorLogManager.logError(getApplication(), "CSV_EXPORT", "Error al serializar colección a CSV", e)
      ""
    }
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
        val ownedMap = inventoryList.value.associate { it.card.id.uppercase() to it.ownedCount }

        val deck = DeckBuilderEngine.buildArchetypeDeck(
          userPrompt = prompt,
          onlyFromInventory = _onlyFromInventoryDeck.value,
          ownedMap = ownedMap
        )
        _generatedDeck.value = deck
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
          val cardId = pair[0]
          val count = pair[1].toIntOrNull() ?: 1
          val card = CardCatalog.getCardById(cardId) ?: CardCatalog.registerCard(cardId, cardId)
          cardEntries.add(DeckCardEntry(card, count))
        }
      }
      _generatedDeck.value = GeneratedDeck(
        name = savedDeck.name,
        archetype = savedDeck.archetype,
        strategy = savedDeck.strategy,
        cards = cardEntries,
        totalCardCount = savedDeck.totalCards
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
      } catch (e: Exception) {
        ErrorLogManager.logError(getApplication(), "DELETE_DECK", "Error al eliminar mazo $deckId", e)
      }
    }
  }

  fun runGeminiMetaAnalysis() {
    viewModelScope.launch {
      try {
        _isAnalyzingMeta.value = true
        val currentInventory = inventoryList.value
        val result = GeminiDeckAnalyzer.computeLocalMetaAnalysis(currentInventory)
        _metaAnalysis.value = result
      } catch (e: Exception) {
        ErrorLogManager.logError(getApplication(), "META_ANALYSIS", "Error al computar análisis del meta", e)
      } finally {
        _isAnalyzingMeta.value = false
      }
    }
  }
}
