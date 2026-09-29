package com.example.data.engine

import com.example.data.model.BoosterPack
import com.example.data.model.CardRarity
import com.example.data.repository.CardCatalog
import com.example.data.repository.CardWithInventory
import kotlin.math.pow

data class RarityProbability(
  val rarity: CardRarity,
  val slot4Probability: Double,
  val slot5Probability: Double,
  val totalProbabilityPerPack: Double,
  val description: String
)

data class PackRecommendation(
  val recommendedPack: BoosterPack,
  val score: Double,
  val reasoning: String,
  val targetCardsFound: List<String>,
  val missingCardsCount: Int,
  val successProbabilitySlot5: Double
)

object TcgProbabilityEngine {

  /**
   * Probabilidades oficiales de Pokémon TCG Pocket por cada ranura (Slot):
   * - Cada sobre contiene 5 cartas.
   * - Slots 1 a 3: Cartas de 1 diamante (100%).
   * - Slot 4: 2 diamantes (90%), 3 diamantes (5%), 4 diamantes (4.16%), 1 estrella (0.84%).
   * - Slot 5: 2 diamantes (60%), 3 diamantes (20%), 4 diamantes (6.86%), 1 estrella (4.342%),
   *           2 estrellas (1.714%), 3 estrellas inmersivas (0.222%), Corona (0.04%).
   */
  val RARITY_RATES = mapOf(
    CardRarity.ONE_DIAMOND to RarityProbability(
      rarity = CardRarity.ONE_DIAMOND,
      slot4Probability = 0.0,
      slot5Probability = 0.0,
      totalProbabilityPerPack = 1.0, // Garantizado en slots 1-3
      description = "1 Diamante: Garantizado 3 cartas por sobre (Slots 1 a 3)"
    ),
    CardRarity.TWO_DIAMONDS to RarityProbability(
      rarity = CardRarity.TWO_DIAMONDS,
      slot4Probability = 0.90,
      slot5Probability = 0.60,
      totalProbabilityPerPack = 1.0 - (1.0 - 0.90) * (1.0 - 0.60), // 0.96 (96%)
      description = "2 Diamantes: 90% en Slot 4, 60% en Slot 5"
    ),
    CardRarity.THREE_DIAMONDS to RarityProbability(
      rarity = CardRarity.THREE_DIAMONDS,
      slot4Probability = 0.05,
      slot5Probability = 0.20,
      totalProbabilityPerPack = 1.0 - (1.0 - 0.05) * (1.0 - 0.20), // 0.24 (24%)
      description = "3 Diamantes: 5% en Slot 4, 20% en Slot 5"
    ),
    CardRarity.FOUR_DIAMONDS to RarityProbability(
      rarity = CardRarity.FOUR_DIAMONDS,
      slot4Probability = 0.0416,
      slot5Probability = 0.0686,
      totalProbabilityPerPack = 1.0 - (1.0 - 0.0416) * (1.0 - 0.0686), // ~0.1073 (10.73%)
      description = "4 Diamantes (ex): 4.16% en Slot 4, 6.86% en Slot 5"
    ),
    CardRarity.ONE_STAR to RarityProbability(
      rarity = CardRarity.ONE_STAR,
      slot4Probability = 0.0084,
      slot5Probability = 0.04342,
      totalProbabilityPerPack = 1.0 - (1.0 - 0.0084) * (1.0 - 0.04342), // ~0.0514 (5.14%)
      description = "1 Estrella (Ilustración Rara): 0.84% en Slot 4, 4.342% en Slot 5"
    ),
    CardRarity.TWO_STARS to RarityProbability(
      rarity = CardRarity.TWO_STARS,
      slot4Probability = 0.0,
      slot5Probability = 0.01714,
      totalProbabilityPerPack = 0.01714, // 1.714%
      description = "2 Estrellas (Súper Rara): 1.714% exclusivamente en Slot 5"
    ),
    CardRarity.THREE_STARS to RarityProbability(
      rarity = CardRarity.THREE_STARS,
      slot4Probability = 0.0,
      slot5Probability = 0.00222,
      totalProbabilityPerPack = 0.00222, // 0.222%
      description = "3 Estrellas (Arte Inmersivo): 0.222% exclusivamente en Slot 5"
    ),
    CardRarity.CROWN to RarityProbability(
      rarity = CardRarity.CROWN,
      slot4Probability = 0.0,
      slot5Probability = 0.0004,
      totalProbabilityPerPack = 0.0004, // 0.04%
      description = "Corona (Ultra Rara): 0.04% exclusivamente en Slot 5 (1 en 2500)"
    )
  )

  /**
   * Calcula la probabilidad acumulada de obtener al menos una carta de una rareza
   * específica tras abrir [packsToOpen] sobres.
   * Fórmula: P_acumulada = 1 - (1 - P_sobre)^n
   */
  fun calculateCumulativeProbability(rarity: CardRarity, packsToOpen: Int): Double {
    if (packsToOpen <= 0) return 0.0
    val pPack = RARITY_RATES[rarity]?.totalProbabilityPerPack ?: 0.01
    return 1.0 - (1.0 - pPack).pow(packsToOpen.toDouble())
  }

  /**
   * Promedio esperado de sobres para conseguir una carta de dicha rareza: E = 1 / P
   */
  fun getExpectedPacks(rarity: CardRarity): Double {
    val pPack = RARITY_RATES[rarity]?.totalProbabilityPerPack ?: return 100.0
    return if (pPack > 0) 1.0 / pPack else Double.POSITIVE_INFINITY
  }

  /**
   * Recomendador inteligente de sobres:
   * Evalúa la colección del usuario o una lista de cartas deseadas y recomienda
   * cuál de los 3 sobres (Charizard, Mewtwo o Pikachu) ofrece mayor valor y probabilidades
   * de conseguir cartas nuevas o piezas de arquetipos competitivos.
   */
  fun recommendPack(
    targetCardIds: List<String>,
    inventory: List<CardWithInventory>
  ): PackRecommendation {
    val missingCards = inventory.filter { it.ownedCount == 0 }
    val wishlistMissing = inventory.filter { it.isWishlist && it.ownedCount == 0 }

    val packScores = mutableMapOf<BoosterPack, Double>()
    val packMissingCounts = mutableMapOf<BoosterPack, Int>()
    val packTargetsFound = mutableMapOf<BoosterPack, MutableList<String>>()

    BoosterPack.entries.forEach { pack ->
      packScores[pack] = 0.0
      packMissingCounts[pack] = 0
      packTargetsFound[pack] = mutableListOf()
    }

    // Ponderación de cartas objetivo
    targetCardIds.forEach { targetId ->
      val card = CardCatalog.getCardById(targetId)
      if (card != null) {
        val pack = card.pack
        val weight = when (card.rarity) {
          CardRarity.CROWN -> 20.0
          CardRarity.THREE_STARS -> 15.0
          CardRarity.TWO_STARS -> 12.0
          CardRarity.FOUR_DIAMONDS -> 10.0
          CardRarity.ONE_STAR -> 8.0
          CardRarity.THREE_DIAMONDS -> 5.0
          CardRarity.TWO_DIAMONDS -> 3.0
          CardRarity.ONE_DIAMOND -> 1.0
        }
        packScores[pack] = (packScores[pack] ?: 0.0) + weight
        packTargetsFound[pack]?.add("${card.name} (${card.id})")
      }
    }

    // Ponderación de cartas faltantes en la colección
    missingCards.forEach { item ->
      val pack = item.card.pack
      packMissingCounts[pack] = (packMissingCounts[pack] ?: 0) + 1
      val mult = if (item.isWishlist) 4.0 else 1.0
      val baseWeight = when (item.card.rarity) {
        CardRarity.FOUR_DIAMONDS -> 6.0
        CardRarity.THREE_DIAMONDS -> 3.0
        CardRarity.TWO_DIAMONDS -> 2.0
        CardRarity.ONE_STAR -> 5.0
        else -> 1.0
      }
      packScores[pack] = (packScores[pack] ?: 0.0) + (baseWeight * mult)
    }

    val bestEntry = packScores.maxByOrNull { it.value }
    val bestPack = bestEntry?.key ?: BoosterPack.CHARIZARD
    val bestScore = bestEntry?.value ?: 10.0
    val missingInBest = packMissingCounts[bestPack] ?: 0
    val targetsInBest = packTargetsFound[bestPack] ?: emptyList()

    val reasoning = when {
      targetsInBest.isNotEmpty() -> {
        "Contiene ${targetsInBest.size} de tus cartas objetivo (${targetsInBest.joinToString(", ")}). " +
          "Es tu ruta óptima para completar barajas competitivas."
      }
      wishlistMissing.any { it.card.pack == bestPack } -> {
        "Tienes cartas deseadas clave de este sobre pendientes de conseguir con alta probabilidad en Slots 4 y 5."
      }
      else -> {
        "Tienes $missingInBest cartas faltantes en ${bestPack.displayName}. " +
          "Abrir este sobre maximiza la tasa de cartas nuevas por gema/reloj."
      }
    }

    val successSlot5 = when (bestPack) {
      BoosterPack.CHARIZARD -> 0.434 // Concentración de Charizard ex, Moltres ex y Starmie ex
      BoosterPack.MEWTWO -> 0.412 // Mewtwo ex, Marowak ex, Gengar ex
      BoosterPack.PIKACHU -> 0.398 // Pikachu ex, Zapdos ex
      else -> 0.400
    }

    return PackRecommendation(
      recommendedPack = bestPack,
      score = bestScore,
      reasoning = reasoning,
      targetCardsFound = targetsInBest,
      missingCardsCount = missingInBest,
      successProbabilitySlot5 = successSlot5
    )
  }
}
