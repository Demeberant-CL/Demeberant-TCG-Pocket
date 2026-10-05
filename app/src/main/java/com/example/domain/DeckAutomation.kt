package com.example.domain

import com.example.data.repository.*
import com.example.data.util.DeckCodec
import java.util.Locale

/** Local starter draft, not a strategic recommendation or an AI result. */
object DeckAutomation {
  fun energies(cards: List<DeckCardEntry>): List<String> = cards
    .filter { it.card.type in DeckCodec.energyNames }
    .groupBy { it.card.type }.entries.sortedWith(compareByDescending<Map.Entry<String, List<DeckCardEntry>>> {
      it.value.sumOf { entry -> entry.count }
    }.thenBy { DeckCodec.energyNames.indexOf(it.key) }).take(3).map { it.key }

  fun build(inventory: List<CardWithInventory>, type: String): GeneratedDeck {
    require(type in DeckCodec.energyNames)
    val pool = inventory.filter { it.ownedCount > 0 && it.card.type in setOf(type, "Incoloro", "Entrenador") }
      .sortedBy { it.card.id }
    val entries = linkedMapOf<String, DeckCardEntry>()
    fun name(value: String) = value.lowercase(Locale.ROOT)
    fun add(item: CardWithInventory) {
      val card = item.card
      val parent = card.evolvesFrom
      if (parent.isNotBlank() && entries.values.none { name(it.card.rulesName) == name(parent) }) return
      // Unknown stages are not invented, and an evolution must name its parent.
      if (card.type != "Entrenador" && card.stage !in setOf("basic", "1", "2")) return
      if (card.type != "Entrenador" && card.stage != "basic" && parent.isBlank()) return
      val sameName = entries.values.filter { name(it.card.rulesName) == name(card.rulesName) }.sumOf { it.count }
      val old = entries[card.id]?.count ?: 0
      val count = minOf(2 - sameName, item.ownedCount - old, 20 - entries.values.sumOf { it.count })
      if (count > 0) entries[card.id] = DeckCardEntry(card, old + count)
    }
    pool.filter { it.card.type == type && it.card.stage == "basic" }.take(3).forEach(::add)
    pool.filter { it.card.type == "Entrenador" }.forEach {
      if (entries.values.filter { e -> e.card.type == "Entrenador" }.sumOf { e -> e.count } < 8) add(it)
    }
    repeat(3) { pool.filter { it.card.type != "Entrenador" }.forEach(::add) }
    val cards = entries.values.toList()
    require(cards.any { it.card.stage == "basic" && it.card.type != "Entrenador" }) {
      "No hay un Pokémon básico con datos suficientes para este tipo. Prueba otro tipo o añade cartas manualmente."
    }
    return GeneratedDeck("Mi mazo de $type", "Borrador local", "Punto de partida con tus cartas. Revisa la estrategia y los costes de ataques; no es una recomendación del meta.",
      cards, cards.sumOf { it.count }, DeckBuilderEngine.validate(cards), listOf(type))
  }
}
