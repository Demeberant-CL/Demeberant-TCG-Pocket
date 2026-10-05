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
    val ownedMap = inventory.groupBy { it.card.name.lowercase() }.mapValues { (_, cards) -> cards.sumOf { it.ownedCount } }

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
        pct >= 90 -> "Tienes casi todas las piezas de esta plantilla A1; revisa la composición completa."
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
        "Plantilla A1",
        "Rayo Beatdown",
        listOf("Pikachu ex" to 2, "Zapdos ex" to 2, "Raichu" to 1, "Sabrina" to 2),
        "Sobre Pikachu"
      ),
      checkDeck(
        "Mewtwo ex & Gardevoir",
        "Plantilla A1",
        "Psíquico Aceleración",
        listOf("Mewtwo ex" to 2, "Ralts" to 2, "Kirlia" to 2, "Gardevoir" to 2, "Sabrina" to 2),
        "Sobre Mewtwo"
      ),
      checkDeck(
        "Charizard ex & Moltres ex",
        "Plantilla A1",
        "Fuego Heavy Hitter",
        listOf("Charizard ex" to 2, "Moltres ex" to 2, "Charmeleon" to 2, "Charmander" to 2, "Blaine" to 2),
        "Sobre Charizard"
      ),
      checkDeck(
        "Starmie ex & Blastoise",
        "Plantilla A1",
        "Agua Tempo",
        listOf("Staryu" to 2, "Starmie ex" to 2, "Blastoise ex" to 1, "Wartortle" to 2, "Squirtle" to 2),
        "Sobre Charizard"
      )
    )

    val bestDeck = insights.maxByOrNull { it.completionRatePercent } ?: insights[0]
    val (bestPack, reasoning) = when {
      bestDeck.deckName.contains("Pikachu") -> Pair("Sobre Pikachu", "Pikachu ex Turbo es la plantilla A1 con mayor porcentaje de piezas disponibles.")
      bestDeck.deckName.contains("Mewtwo") -> Pair("Sobre Mewtwo", "Posees piezas clave para la sinergia Mewtwo ex + Gardevoir.")
      bestDeck.deckName.contains("Charizard") -> Pair("Sobre Charizard", "Tienes cartas de fuego compatibles con Charizard ex y Moltres ex.")
      else -> Pair("Sobre Charizard", "Ofrece Starmie ex y Charizard ex con alto valor competitivo.")
    }

    return MetaAnalysisResult(
      overview = "Análisis local de plantillas A1. No consulta Gemini ni verifica el meta competitivo actual.",
      bestPackToOpenNext = bestPack,
      packReasoning = reasoning,
      deckInsights = insights
    )
  }
}
