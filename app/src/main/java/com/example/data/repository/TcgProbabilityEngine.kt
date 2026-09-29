package com.example.data.repository

import com.example.data.model.BoosterPack
import com.example.data.model.CardRarity
import com.example.data.model.PokemonCard
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale
import kotlin.math.pow
import kotlin.random.Random

object TcgProbabilityEngine {

  // Slot probabilities (percentage 0.0 - 100.0)
  // Slot 1 to 3: 100% 1 Diamond
  // Slot 4: 2D: 90%, 3D: 5%, 4D: 4.16%, 1 Star: 0.84%
  // Slot 5: 2D: 60%, 3D: 20%, 4D: 6.86%, 1 Star: 4.342%, 2 Star: 1.714%, 3 Star: 0.222%, Crown: 0.04%
  val SLOT4_DISTRIBUTION: List<Pair<CardRarity, Double>> = listOf(
    CardRarity.DIAMOND_2 to 90.0,
    CardRarity.DIAMOND_3 to 5.0,
    CardRarity.DIAMOND_4 to 4.16,
    CardRarity.STAR_1 to 0.84
  )

  val SLOT5_DISTRIBUTION: List<Pair<CardRarity, Double>> = listOf(
    CardRarity.DIAMOND_2 to 60.0,
    CardRarity.DIAMOND_3 to 20.0,
    CardRarity.DIAMOND_4 to 6.86,
    CardRarity.STAR_1 to 4.342,
    CardRarity.STAR_2 to 1.714,
    CardRarity.STAR_3 to 0.222,
    CardRarity.CROWN to 0.04
  )

  fun getIndividualCardSlot5Rate(card: PokemonCard): Double {
    return when (card.rarity) {
      CardRarity.DIAMOND_1 -> 0.0
      CardRarity.DIAMOND_2 -> 2.000 // 60% / 30
      CardRarity.DIAMOND_3 -> 1.666 // 20% / 12
      CardRarity.DIAMOND_4 -> 1.372 // 6.86% / 5
      CardRarity.STAR_1 -> 0.3618   // 4.342% / 12
      CardRarity.STAR_2 -> 0.212    // official displayed 0.212% (1.714% / 8 cards)
      CardRarity.STAR_3 -> 0.222    // 0.222% / 1 card
      CardRarity.CROWN -> 0.040     // 0.040% / 1 card
    }
  }

  /**
   * Process a JSON string request and return strict JSON string response.
   */
  fun processJsonRequest(
    requestJson: String,
    cardsWithInventory: List<CardWithInventory> = emptyList()
  ): String {
    return try {
      val trimmed = requestJson.trim()
      if (!trimmed.startsWith("{") || !trimmed.endsWith("}")) {
        return createErrorJson("Formato de entrada no válido. Debe ser un objeto JSON.")
      }

      val json = JSONObject(trimmed)
      val action = json.optString("action", "")

      when (action.lowercase()) {
        "calculate_probability" -> handleCalculateProbability(json)
        "recommend_pack" -> handleRecommendPack(json)
        "calculate_card_probability" -> handleCalculateCardProbability(json)
        "inventory_analysis" -> handleInventoryAnalysis(cardsWithInventory)
        "simulate_pack" -> handleSimulatePack(json)
        else -> createErrorJson("Acción desconocida o no soportada: '$action'. Acciones válidas: calculate_probability, recommend_pack, calculate_card_probability, inventory_analysis, simulate_pack")
      }
    } catch (e: Exception) {
      createErrorJson("Error al procesar la petición JSON: ${e.message}")
    }
  }

  private fun handleCalculateProbability(json: JSONObject): String {
    val targetRarityKey = json.optString("target_rarity", "")
    if (targetRarityKey.isBlank()) {
      return createErrorJson("Falta el campo obligatorio 'target_rarity'.")
    }

    val packsToOpen = json.optInt("packs_to_open", 1)
    if (packsToOpen <= 0) {
      return createErrorJson("'packs_to_open' debe ser un número entero mayor que cero.")
    }

    val rarity = CardRarity.fromKey(targetRarityKey)
    val probPerPack = rarity.getPackAppearanceRatePercent()

    // Cumulative probability: 1 - (1 - p)^N
    val pDec = (probPerPack / 100.0).coerceIn(0.0, 1.0)
    val cumulativeDec = 1.0 - (1.0 - pDec).pow(packsToOpen.toDouble())
    val cumulativePercent = round3(cumulativeDec * 100.0)

    val explanation = when (rarity) {
      CardRarity.CROWN -> "La probabilidad base por sobre es 0.04% en el quinto slot."
      CardRarity.STAR_3 -> "La probabilidad base por sobre es 0.222% exclusivamente en el quinto slot."
      CardRarity.STAR_2 -> "La probabilidad base por sobre es 1.714% exclusivamente en el quinto slot."
      CardRarity.STAR_1 -> "La probabilidad base por sobre es 0.84% en el cuarto slot y 4.342% en el quinto slot."
      CardRarity.DIAMOND_4 -> "La probabilidad base por sobre es 4.16% en el cuarto slot y 6.86% en el quinto slot."
      CardRarity.DIAMOND_3 -> "La probabilidad base por sobre es 5% en el cuarto slot y 20% en el quinto slot."
      CardRarity.DIAMOND_2 -> "La probabilidad base por sobre es 90% en el cuarto slot y 60% en el quinto slot."
      CardRarity.DIAMOND_1 -> "100% de probabilidad en los slots 1, 2 y 3 de cada sobre."
    }

    val res = JSONObject()
    res.put("probability_per_pack", round3(probPerPack))
    res.put("cumulative_probability_${packsToOpen}_packs", cumulativePercent)
    res.put("cumulative_probability", cumulativePercent)
    res.put("explanation", explanation)
    return res.toString(2)
  }

  private fun handleRecommendPack(json: JSONObject): String {
    val targetCardsArray = json.optJSONArray("target_cards")
    val targetCardList = mutableListOf<String>()

    if (targetCardsArray != null) {
      for (i in 0 until targetCardsArray.length()) {
        targetCardList.add(targetCardsArray.getString(i).trim())
      }
    } else {
      val single = json.optString("target_card", "")
      if (single.isNotBlank()) targetCardList.add(single.trim())
    }

    if (targetCardList.isEmpty()) {
      return createErrorJson("Debe especificar al menos una carta objetivo en 'target_cards'.")
    }

    val cards = targetCardList.mapNotNull { CardCatalog.getCardById(it) }
    if (cards.isEmpty()) {
      return createErrorJson("Ninguna de las cartas solicitadas existe en el catálogo A1 de Pokémon TCG Pocket.")
    }

    // Count occurrences of packs
    val packCounts = mutableMapOf<BoosterPack, Int>()
    cards.forEach { card ->
      if (card.pack != BoosterPack.SHARED) {
        packCounts[card.pack] = (packCounts[card.pack] ?: 0) + 1
      }
    }

    val bestPack = packCounts.maxByOrNull { it.value }?.key ?: BoosterPack.CHARIZARD

    // Calculate slot 5 probability for target cards in that best pack
    var totalSlot5Prob = 0.0
    val cardsInBestPack = cards.filter { it.pack == bestPack || it.pack == BoosterPack.SHARED }

    cardsInBestPack.forEach { card ->
      totalSlot5Prob += getIndividualCardSlot5Rate(card)
    }

    // Round according to prompt format
    val successProbSlot5 = round3(totalSlot5Prob)

    val reasoning = if (cardsInBestPack.size == cards.size) {
      if (cards.size > 1) {
        "Ambas cartas objetivo son exclusivas del sobre ${bestPack.codeName}."
      } else {
        "La carta objetivo es exclusiva del sobre ${bestPack.codeName}."
      }
    } else {
      "El sobre ${bestPack.codeName} contiene la mayor cantidad de cartas objetivo (${cardsInBestPack.size}/${cards.size})."
    }

    val res = JSONObject()
    res.put("recommended_pack", bestPack.codeName)
    res.put("reasoning", reasoning)
    res.put("success_probability_slot_5", successProbSlot5)
    return res.toString(2)
  }

  private fun handleCalculateCardProbability(json: JSONObject): String {
    val cardId = json.optString("card_id", "").ifBlank { json.optString("target_card", "") }
    if (cardId.isBlank()) {
      return createErrorJson("Falta el campo obligatorio 'card_id'.")
    }

    val card = CardCatalog.getCardById(cardId)
      ?: return createErrorJson("Carta con ID '$cardId' no encontrada en el catálogo.")

    val packsToOpen = json.optInt("packs_to_open", 1).coerceAtLeast(1)
    val pack = if (card.pack == BoosterPack.SHARED) BoosterPack.CHARIZARD else card.pack

    val totalOfRarityInPack = CardCatalog.getCardsByRarity(pack, card.rarity).size.coerceAtLeast(1)
    val pSlot4 = (card.rarity.slot4RatePercent / 100.0) / totalOfRarityInPack
    val pSlot5 = (card.rarity.slot5RatePercent / 100.0) / totalOfRarityInPack

    val pInPack = 1.0 - (1.0 - pSlot4) * (1.0 - pSlot5)
    val pInPackPercent = round4(pInPack * 100.0)

    val cumulativeDec = 1.0 - (1.0 - pInPack).pow(packsToOpen.toDouble())
    val cumulativePercent = round3(cumulativeDec * 100.0)

    val res = JSONObject()
    res.put("card_id", card.id)
    res.put("card_name", card.name)
    res.put("pack", pack.codeName)
    res.put("rarity", card.rarity.displayName)
    res.put("probability_per_pack_percent", pInPackPercent)
    res.put("cumulative_probability_${packsToOpen}_packs", cumulativePercent)
    res.put("packs_for_50_pct", packsNeededForProbability(pInPack, 0.50))
    res.put("packs_for_90_pct", packsNeededForProbability(pInPack, 0.90))
    return res.toString(2)
  }

  private fun handleInventoryAnalysis(cardsWithInventory: List<CardWithInventory>): String {
    val totalInCatalog = CardCatalog.ALL_CARDS.size
    val totalOwnedCards = cardsWithInventory.count { it.ownedCount > 0 }
    val totalCopies = cardsWithInventory.sumOf { it.ownedCount }
    val wishlistCards = cardsWithInventory.filter { it.isWishlist }

    val packBreakdown = JSONObject()
    listOf(BoosterPack.CHARIZARD, BoosterPack.MEWTWO, BoosterPack.PIKACHU).forEach { pack ->
      val packCards = CardCatalog.getCardsByPack(pack)
      val packCardIds = packCards.map { it.id }.toSet()
      val ownedInPack = cardsWithInventory.count { it.card.id in packCardIds && it.ownedCount > 0 }
      val packPct = if (packCards.isNotEmpty()) round2((ownedInPack.toDouble() / packCards.size) * 100.0) else 0.0

      val packObj = JSONObject()
      packObj.put("total_cards", packCards.size)
      packObj.put("owned_cards", ownedInPack)
      packObj.put("completion_percent", packPct)
      packBreakdown.put(pack.codeName, packObj)
    }

    val res = JSONObject()
    res.put("total_catalog_cards", totalInCatalog)
    res.put("unique_cards_owned", totalOwnedCards)
    res.put("total_card_copies", totalCopies)
    res.put("overall_completion_percent", round2((totalOwnedCards.toDouble() / totalInCatalog) * 100.0))
    res.put("wishlist_count", wishlistCards.size)
    res.put("pack_completion", packBreakdown)
    return res.toString(2)
  }

  private fun handleSimulatePack(json: JSONObject): String {
    val packName = json.optString("pack", "Charizard")
    val pack = when (packName.lowercase().trim()) {
      "mewtwo" -> BoosterPack.MEWTWO
      "pikachu" -> BoosterPack.PIKACHU
      else -> BoosterPack.CHARIZARD
    }

    val cards = simulateOpenBoosterPack(pack)
    val res = JSONObject()
    res.put("pack_opened", pack.codeName)
    val array = JSONArray()
    cards.forEachIndexed { index, card ->
      val cardObj = JSONObject()
      cardObj.put("slot", index + 1)
      cardObj.put("id", card.id)
      cardObj.put("name", card.name)
      cardObj.put("rarity", card.rarity.displayName)
      cardObj.put("symbol", card.rarity.symbol)
      array.put(cardObj)
    }
    res.put("cards", array)
    return res.toString(2)
  }

  /**
   * Simulates opening 1 booster pack with 5 cards adhering to exact official rules.
   */
  fun simulateOpenBoosterPack(pack: BoosterPack): List<PokemonCard> {
    val result = mutableListOf<PokemonCard>()

    // Slots 1 to 3: 1 Diamond cards (100% chance each)
    val d1Cards = CardCatalog.getCardsByRarity(pack, CardRarity.DIAMOND_1)
    repeat(3) {
      val card = d1Cards.randomOrNull() ?: CardCatalog.ALL_CARDS.first { it.rarity == CardRarity.DIAMOND_1 }
      result.add(card)
    }

    // Slot 4: 2D (90%), 3D (5%), 4D (4.16%), 1 Star (0.84%)
    val slot4Rarity = rollRarity(SLOT4_DISTRIBUTION)
    val slot4Pool = CardCatalog.getCardsByRarity(pack, slot4Rarity)
    val slot4Card = slot4Pool.randomOrNull() ?: CardCatalog.getCardsByPack(pack).random()
    result.add(slot4Card)

    // Slot 5: 2D (60%), 3D (20%), 4D (6.86%), 1 Star (4.342%), 2 Star (1.714%), 3 Star (0.222%), Crown (0.04%)
    val slot5Rarity = rollRarity(SLOT5_DISTRIBUTION)
    val slot5Pool = CardCatalog.getCardsByRarity(pack, slot5Rarity)
    val slot5Card = slot5Pool.randomOrNull() ?: CardCatalog.getCardsByPack(pack).random()
    result.add(slot5Card)

    return result
  }

  private fun rollRarity(distribution: List<Pair<CardRarity, Double>>): CardRarity {
    val roll = Random.nextDouble(0.0, 100.0)
    var cumulative = 0.0
    for ((rarity, weight) in distribution) {
      cumulative += weight
      if (roll <= cumulative) {
        return rarity
      }
    }
    return distribution.first().first
  }

  fun packsNeededForProbability(pPerPack: Double, targetProbability: Double): Int {
    if (pPerPack <= 0.0) return Int.MAX_VALUE
    if (pPerPack >= 1.0) return 1
    // 1 - (1 - p)^N >= T  => (1 - p)^N <= 1 - T => N >= ln(1 - T) / ln(1 - p)
    val n = kotlin.math.ln(1.0 - targetProbability) / kotlin.math.ln(1.0 - pPerPack)
    return kotlin.math.ceil(n).toInt().coerceAtLeast(1)
  }

  private fun createErrorJson(message: String): String {
    val err = JSONObject()
    err.put("error", message)
    return err.toString(2)
  }

  private fun round3(value: Double): Double {
    return String.format(Locale.US, "%.3f", value).toDouble()
  }

  private fun round4(value: Double): Double {
    return String.format(Locale.US, "%.4f", value).toDouble()
  }

  private fun round2(value: Double): Double {
    return String.format(Locale.US, "%.2f", value).toDouble()
  }
}
