package com.example.data.api

import com.example.data.repository.CardWithInventory

data class MetaDeckInsight(
  val deckName: String,
  val tier: String,
  val archetype: String,
  val keyCardsNeeded: List<String>,
  val ownedCardsSummary: String,
  val completionRatePercent: Int,
  val aiRecommendation: String
)

data class MetaAnalysisResult(
  val overview: String,
  val bestPackToOpenNext: String,
  val packReasoning: String,
  val deckInsights: List<MetaDeckInsight>
)

object GeminiDeckAnalyzer {

  fun computeLocalMetaAnalysis(inventory: List<CardWithInventory>): MetaAnalysisResult {
    val ownedMap = inventory.associate { it.card.name.lowercase() to it.ownedCount }

    fun checkDeck(
      name: String,
      tier: String,
      arch: String,
      keyCards: List<Pair<String, Int>>,
      packRec: String
    ): MetaDeckInsight {
      var totalRequired = 0
      var totalOwned = 0
      val ownedSummaries = mutableListOf<String>()

      keyCards.forEach { (cardName, needed) ->
        totalRequired += needed
        val owned = ownedMap[cardName.lowercase()] ?: 0
        totalOwned += minOf(owned, needed)
        if (owned > 0) {
          ownedSummaries.add("$cardName x$owned")
        }
      }

      val pct = if (totalRequired > 0) ((totalOwned.toFloat() / totalRequired) * 100).toInt() else 0
      val rec = when {
        pct >= 90 -> "¡Mazo casi listo para competitivo! Te faltan detalles menores."
        pct >= 50 -> "Tienes la base principal. Prioriza el $packRec para conseguir las copias faltantes."
        else -> "Faltan las piezas centrales clave de este arquetipo."
      }

      return MetaDeckInsight(
        deckName = name,
        tier = tier,
        archetype = arch,
        keyCardsNeeded = keyCards.map { "${it.second}x ${it.first}" },
        ownedCardsSummary = if (ownedSummaries.isEmpty()) "Ninguna pieza en inventario" else ownedSummaries.joinToString(", "),
        completionRatePercent = pct,
        aiRecommendation = rec
      )
    }

    val insights = listOf(
      checkDeck(
        "Pikachu ex Turbo",
        "Tier S",
        "Rayo Beatdown",
        listOf("Pikachu ex" to 2, "Zapdos ex" to 2, "Raichu" to 1, "Sabrina" to 2),
        "Sobre Pikachu"
      ),
      checkDeck(
        "Mewtwo ex & Gardevoir",
        "Tier S",
        "Psíquico Aceleración",
        listOf("Mewtwo ex" to 2, "Gardevoir" to 2, "Gengar ex" to 1, "Sabrina" to 2),
        "Sobre Mewtwo"
      ),
      checkDeck(
        "Charizard ex & Moltres ex",
        "Tier A+",
        "Fuego Heavy Hitter",
        listOf("Charizard ex" to 2, "Moltres ex" to 2, "Charmeleon" to 2, "Charmander" to 2, "Blaine" to 2),
        "Sobre Charizard"
      ),
      checkDeck(
        "Starmie ex & Blastoise",
        "Tier A",
        "Agua Tempo",
        listOf("Starmie ex" to 2, "Blastoise ex" to 1, "Wartortle" to 2, "Squirtle" to 2),
        "Sobre Charizard"
      )
    )

    val bestDeck = insights.maxByOrNull { it.completionRatePercent } ?: insights[0]
    val (bestPack, reasoning) = when {
      bestDeck.deckName.contains("Pikachu") -> Pair("Sobre Pikachu", "Estás a un paso de completar Pikachu ex Turbo (Tier S del meta).")
      bestDeck.deckName.contains("Mewtwo") -> Pair("Sobre Mewtwo", "Posees piezas clave para la sinergia Mewtwo ex + Gardevoir.")
      bestDeck.deckName.contains("Charizard") -> Pair("Sobre Charizard", "Tienes cartas de fuego compatibles con Charizard ex y Moltres ex.")
      else -> Pair("Sobre Charizard", "Ofrece Starmie ex y Charizard ex con alto valor competitivo.")
    }

    return MetaAnalysisResult(
      overview = "Análisis del meta actual (Genética A1): Barajas de ritmo agresivo de 3 puntos dominan el juego.",
      bestPackToOpenNext = bestPack,
      packReasoning = reasoning,
      deckInsights = insights
    )
  }
}
