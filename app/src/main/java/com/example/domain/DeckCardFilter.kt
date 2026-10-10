package com.example.domain

import com.example.data.repository.CardWithInventory
import com.example.data.local.CardRulesEntity

data class DeckCardFilter(
  val query: String = "", val category: String = "", val stage: String = "", val element: String = "",
  val expansion: String = "", val rarity: String = "", val onlyEx: Boolean = false,
  val availability: String = "owned", val effect: String = "", val sort: String = "code"
) {
  val activeCount: Int get() = listOf(category, stage, element, expansion, rarity, effect).count { it.isNotEmpty() } + if (onlyEx) 1 else 0
  fun select(inventory: List<CardWithInventory>, counts: Map<String, Int>, rules: Map<String, CardRulesEntity>): List<CardWithInventory> {
    val text = RoleClassifier.normalize(query.trim())
    val selected = inventory.filter { item ->
      val card = item.card
      val pokemon = card.category.equals("pokemon", true)
      val categoryMatches = when (category) {
        "" -> true; "pokemon" -> pokemon; "trainer" -> card.type == "Entrenador"
        "unknown" -> card.category == "unknown"; else -> card.category.equals(category, true)
      }
      val available = when (availability) {
        "owned" -> item.ownedCount > 0; "sufficient" -> item.ownedCount > (counts[card.id] ?: 0)
        "missing" -> item.ownedCount == 0; else -> true
      }
      categoryMatches && available &&
        (text.isBlank() || RoleClassifier.normalize(card.name + " " + card.id).contains(text)) &&
        (stage.isEmpty() || pokemon && card.stage == stage) && (!onlyEx || card.isEx) &&
        (element.isEmpty() || card.type == element) &&
        (expansion.isEmpty() || card.id.substringBeforeLast('-') == expansion) &&
        (rarity.isEmpty() || card.rarity.name == rarity) &&
        (effect.isEmpty() || rules[card.id]?.let { rule -> rule.roles.contains("|$effect|") ||
          RoleClassifier.classify(rule.rulesText).any { it.key == effect } } == true)
    }
    return when (sort) {
      "name" -> selected.sortedWith(compareBy({ RoleClassifier.normalize(it.card.name) }, { it.card.id }))
      "copies" -> selected.sortedWith(compareByDescending<CardWithInventory> { it.ownedCount }.thenBy { it.card.id })
      "rarity" -> selected.sortedWith(compareBy({ it.card.rarity.ordinal }, { it.card.id }))
      else -> selected.sortedBy { it.card.id }
    }
  }
}
