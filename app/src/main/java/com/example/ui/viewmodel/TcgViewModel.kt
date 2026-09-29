package com.example.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.api.GeminiDeckAnalyzer
import com.example.data.api.MetaAnalysisResult
import com.example.data.local.AppDatabase
import com.example.data.model.BoosterPack
import com.example.data.model.CardRarity
import com.example.data.model.PokemonCard
import com.example.data.repository.CardCatalog
import com.example.data.repository.CardWithInventory
import com.example.data.repository.DeckBuilderEngine
import com.example.data.repository.GeneratedDeck
import com.example.data.repository.InventoryRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.random.Random

class TcgViewModel(application: Application) : AndroidViewModel(application) {

  private val repository: InventoryRepository

  init {
    val db = AppDatabase.getDatabase(application)
    repository = InventoryRepository(db.inventoryDao())
  }

  val inventoryList: StateFlow<List<CardWithInventory>> = repository.inventoryFlow
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

  // Pack Simulation
  private val _openedPackCards = MutableStateFlow<List<PokemonCard>>(emptyList())
  val openedPackCards = _openedPackCards.asStateFlow()

  private val _isOpeningPack = MutableStateFlow(false)
  val isOpeningPack = _isOpeningPack.asStateFlow()

  private val _currentPackType = MutableStateFlow(BoosterPack.CHARIZARD)
  val currentPackType = _currentPackType.asStateFlow()

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
      val existing = repository.inventoryFlow.first()
      if (existing.all { it.ownedCount == 0 }) {
        try {
          val assetCsv = getApplication<Application>().assets.open("coleccion-pokemon-2026-09-29.csv")
            .bufferedReader().use { it.readText() }
          importCsv(assetCsv)
        } catch (_: Exception) {}
      }
    }
    runGeminiMetaAnalysis()
    generateDeck("Pikachu ex Turbo")
  }

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
      repository.toggleWishlist(cardId)
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
          return@launch
        }

        val firstLine = lines[0]
        val delimiter = if (firstLine.contains(";")) ";" else ","
        val headers = lines[0].split(delimiter).map { it.trim().lowercase().removeSurrounding("\"") }

        val setIndex = headers.indexOfFirst { it == "set" || it.contains("expansion") || it.contains("coleccion") }
        val idIndex = headers.indexOfFirst { it == "id" || it.contains("card_id") || it.contains("código") || it.contains("numero") }
        val nameIndex = headers.indexOfFirst { it.contains("nombre") || it.contains("name") }
        val qtyIndex = headers.indexOfFirst { it.contains("cantidad") || it.contains("quantity") || it.contains("count") || it.contains("copias") }

        val quantities = mutableMapOf<String, Int>()
        val startIndex = if (headers.any { it.contains("id") || it.contains("set") || it.contains("nombre") }) 1 else 0

        for (i in startIndex until lines.size) {
          val rawParts = lines[i].split(delimiter).map { it.trim().removeSurrounding("\"") }
          if (rawParts.isNotEmpty()) {
            val qty = if (qtyIndex != -1 && rawParts.size > qtyIndex) rawParts[qtyIndex].toIntOrNull() ?: 1 else 1
            if (qty > 0) {
              if (setIndex != -1 && idIndex != -1 && rawParts.size > maxOf(setIndex, idIndex)) {
                val setStr = rawParts[setIndex].trim().uppercase()
                val idStr = rawParts[idIndex].trim()
                val idNum = idStr.toIntOrNull() ?: 0
                val formatted3 = "$setStr-${idNum.toString().padStart(3, '0')}"
                val formattedRaw = "$setStr-$idStr"
                quantities[formatted3] = qty
                quantities[formattedRaw] = qty
              } else if (idIndex != -1 && rawParts.size > idIndex) {
                val directId = rawParts[idIndex].trim().uppercase()
                quantities[directId] = qty
              } else if (nameIndex != -1 && rawParts.size > nameIndex) {
                val nameStr = rawParts[nameIndex].trim().lowercase()
                CardCatalog.ALL_CARDS.find { it.name.lowercase() == nameStr }?.let { matched ->
                  quantities[matched.id] = qty
                }
              }
            }
          }
        }

        repository.setQuantitiesBatch(quantities)
        _csvStatusMessage.value = "¡Importación exitosa! Se cargaron ${quantities.size} registros de cartas."
        runGeminiMetaAnalysis()
      } catch (e: Exception) {
        _csvStatusMessage.value = "Error al procesar CSV: ${e.localizedMessage}"
      }
    }
  }

  fun generateCsvContent(): String {
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
    return sb.toString().trimEnd()
  }

  fun setDeckPrompt(prompt: String) {
    _deckBuildPrompt.value = prompt
  }

  fun toggleOnlyFromInventoryDeck() {
    _onlyFromInventoryDeck.value = !_onlyFromInventoryDeck.value
  }

  fun generateDeck(customPrompt: String? = null) {
    viewModelScope.launch {
      _isGeneratingDeck.value = true
      val prompt = customPrompt ?: _deckBuildPrompt.value
      val ownedMap = inventoryList.value.associate { it.card.id.uppercase() to it.ownedCount }

      val deck = DeckBuilderEngine.buildArchetypeDeck(
        userPrompt = prompt,
        onlyFromInventory = _onlyFromInventoryDeck.value,
        ownedMap = ownedMap
      )
      _generatedDeck.value = deck
      _isGeneratingDeck.value = false
    }
  }

  fun runGeminiMetaAnalysis() {
    viewModelScope.launch {
      _isAnalyzingMeta.value = true
      val currentInventory = inventoryList.value
      val result = GeminiDeckAnalyzer.computeLocalMetaAnalysis(currentInventory)
      _metaAnalysis.value = result
      _isAnalyzingMeta.value = false
    }
  }

  fun selectPackToOpen(pack: BoosterPack) {
    _currentPackType.value = pack
  }

  fun openPack(pack: BoosterPack) {
    viewModelScope.launch {
      _isOpeningPack.value = true
      val packCards = CardCatalog.getCardsByPack(pack).ifEmpty { CardCatalog.ALL_CARDS }

      val cards1D = packCards.filter { it.rarity == CardRarity.ONE_DIAMOND }.ifEmpty { packCards }
      val cards2D = packCards.filter { it.rarity == CardRarity.TWO_DIAMONDS }.ifEmpty { packCards }
      val cards3D = packCards.filter { it.rarity == CardRarity.THREE_DIAMONDS }.ifEmpty { packCards }
      val cards4D = packCards.filter { it.rarity == CardRarity.FOUR_DIAMONDS }.ifEmpty { packCards }
      val cardsStar1 = packCards.filter { it.rarity == CardRarity.ONE_STAR }.ifEmpty { packCards }
      val cardsStar2 = packCards.filter { it.rarity == CardRarity.TWO_STARS }.ifEmpty { packCards }
      val cardsStar3 = packCards.filter { it.rarity == CardRarity.THREE_STARS }.ifEmpty { packCards }
      val cardsCrown = packCards.filter { it.rarity == CardRarity.CROWN }.ifEmpty { packCards }

      val slot1 = cards1D.random()
      val slot2 = cards1D.random()
      val slot3 = cards1D.random()

      val rollSlot4 = Random.nextDouble() * 100.0
      val slot4 = when {
        rollSlot4 < 90.0 -> cards2D.random()
        rollSlot4 < 95.0 -> cards3D.random()
        rollSlot4 < 99.16 -> cards4D.random()
        else -> cardsStar1.random()
      }

      val rollSlot5 = Random.nextDouble() * 100.0
      val slot5 = when {
        rollSlot5 < 60.0 -> cards2D.random()
        rollSlot5 < 80.0 -> cards3D.random()
        rollSlot5 < 86.86 -> cards4D.random()
        rollSlot5 < 91.202 -> cardsStar1.random()
        rollSlot5 < 92.916 -> cardsStar2.random()
        rollSlot5 < 93.138 -> cardsStar3.random()
        rollSlot5 < 93.178 -> cardsCrown.random()
        else -> cards2D.random()
      }

      val drawnCards = listOf(slot1, slot2, slot3, slot4, slot5)
      _openedPackCards.value = drawnCards

      val toAdd = mutableMapOf<String, Int>()
      drawnCards.forEach { c ->
        val currentOwned = inventoryList.value.find { it.card.id == c.id }?.ownedCount ?: 0
        toAdd[c.id] = (toAdd[c.id] ?: currentOwned) + 1
      }
      repository.setQuantitiesBatch(toAdd)

      _isOpeningPack.value = false
    }
  }
}
