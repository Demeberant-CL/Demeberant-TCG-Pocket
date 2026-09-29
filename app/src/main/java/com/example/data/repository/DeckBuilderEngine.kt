package com.example.data.repository

import com.example.data.model.PokemonCard

data class DeckCardEntry(
  val card: PokemonCard,
  val count: Int
)

data class GeneratedDeck(
  val name: String,
  val archetype: String,
  val strategy: String,
  val cards: List<DeckCardEntry>,
  val totalCardCount: Int
) {
  fun toExportText(): String {
    val sb = StringBuilder()
    sb.appendLine("###### Mazo Pokémon TCG Pocket: $name")
    sb.appendLine("Arquetipo: $archetype")
    sb.appendLine("Cartas ($totalCardCount/20):")
    cards.forEach { entry ->
      sb.appendLine("${entry.count}x ${entry.card.name} (${entry.card.id})")
    }
    sb.appendLine("Estrategia: $strategy")
    return sb.toString().trimEnd()
  }
}

object DeckBuilderEngine {

  fun buildArchetypeDeck(
    userPrompt: String,
    onlyFromInventory: Boolean,
    ownedMap: Map<String, Int>
  ): GeneratedDeck {
    val promptLower = userPrompt.lowercase()

    val (deckName, archetype, strategy, coreKeywords) = when {
      promptLower.contains("pikachu") || promptLower.contains("rayo") -> Quad(
        "Pikachu ex Turbo Relámpago",
        "Pikachu ex Beatdown",
        "Llena tu banca con Pokémon básicos de tipo Rayo para que Círculo Eléctrico inflija 90 de daño por solo 2 energías.",
        listOf("Pikachu ex", "Pikachu", "Raichu", "Zapdos ex", "Blitzle", "Zebstrika", "Helioptile", "Heliolisk")
      )
      promptLower.contains("mewtwo") || promptLower.contains("psiquico") || promptLower.contains("gardevoir") -> Quad(
        "Mewtwo ex & Gardevoir Dimensión Psíquica",
        "Mewtwo ex Control / Energy Acceleration",
        "Usa la habilidad Abrazo Psíquico de Gardevoir para cargar rápidamente a Mewtwo ex con 4 energías y arrasar con Impulso Psíquico (150 daño).",
        listOf("Mewtwo ex", "Gardevoir", "Haunter", "Gengar", "Alakazam", "Kadabra", "Abra")
      )
      promptLower.contains("charizard") || promptLower.contains("moltres") || promptLower.contains("fuego") -> Quad(
        "Charizard ex & Moltres ex Llamas Carmesí",
        "Charizard ex Heavy Hitter",
        "Activa Danza Infernal de Moltres ex en los primeros turnos para unir energías fuego en banca a Charizard ex y rematar con 200 de daño.",
        listOf("Charizard ex", "Charizard", "Charmeleon", "Charmander", "Moltres ex", "Ninetales", "Vulpix", "Rapidash", "Ponyta", "Blaine")
      )
      promptLower.contains("starmie") || promptLower.contains("blastoise") || promptLower.contains("agua") -> Quad(
        "Starmie ex & Blastoise Marea Veloz",
        "Starmie ex Tempo",
        "Starmie ex ataca rápido con 2 energías haciendo 90 de daño y tiene coste de retirada 0 para alternar con Blastoise.",
        listOf("Starmie ex", "Staryu", "Blastoise ex", "Blastoise", "Wartortle", "Squirtle", "Goldeen", "Psyduck")
      )
      promptLower.contains("marowak") || promptLower.contains("lucha") || promptLower.contains("machamp") -> Quad(
        "Marowak ex & Machamp Rompe rocas",
        "Lucha / Marowak ex High-Roll",
        "Bumerán Óseo de Marowak ex inflige hasta 160 de daño por 2 energías. Machamp resiste como tanque pesado en juego tardío.",
        listOf("Marowak ex", "Cubone", "Machamp", "Machoke", "Hitmonchan", "Hitmonlee", "Sandslash", "Sandshrew")
      )
      else -> Quad(
        "Pikachu ex Turbo Relámpago",
        "Meta Beatdown",
        "Estrategia balanceada orientada a ganar rápido tomando 3 puntos mediante ataques agresivos de bajo coste.",
        listOf("Pikachu ex", "Pikachu", "Zapdos ex", "Raichu", "Blitzle", "Zebstrika")
      )
    }

    val availableCards = CardCatalog.ALL_CARDS.filter { card ->
      if (!onlyFromInventory) true
      else (ownedMap[card.id.uppercase()] ?: 0) >= 1
    }

    val pool = if (availableCards.size >= 10) availableCards else CardCatalog.ALL_CARDS

    val deckList = mutableListOf<DeckCardEntry>()
    var currentCount = 0

    fun tryAddCard(card: PokemonCard, desiredQty: Int) {
      if (currentCount >= 20) return
      val maxPossible = if (onlyFromInventory) {
        val owned = ownedMap[card.id.uppercase()] ?: 0
        minOf(desiredQty, owned, 2)
      } else {
        minOf(desiredQty, 2)
      }
      if (maxPossible <= 0) return

      val toAdd = minOf(maxPossible, 20 - currentCount)
      if (toAdd > 0) {
        deckList.add(DeckCardEntry(card, toAdd))
        currentCount += toAdd
      }
    }

    coreKeywords.forEach { kw ->
      val card = pool.find { it.name.equals(kw, ignoreCase = true) }
      if (card != null) {
        tryAddCard(card, 2)
      }
    }

    val supporters = pool.filter { it.type == "Entrenador" }
    supporters.forEach { card ->
      tryAddCard(card, 2)
    }

    if (currentCount < 20) {
      pool.forEach { card ->
        if (deckList.none { it.card.id == card.id }) {
          tryAddCard(card, 1)
        }
      }
    }

    return GeneratedDeck(
      name = deckName,
      archetype = archetype,
      strategy = strategy,
      cards = deckList,
      totalCardCount = currentCount
    )
  }
}

private data class Quad<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)
