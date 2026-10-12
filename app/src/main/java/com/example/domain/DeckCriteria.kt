package com.example.domain

import com.example.data.repository.CardWithInventory
import com.example.data.repository.GeneratedDeck
import com.example.data.util.CardId

/** Explicit restrictions shared by offline and connected generation. */
data class DeckCriteria(val expansions: Set<String> = emptySet(), val required: Set<String> = emptySet(),
  val excluded: Set<String> = emptySet(), val ownedOnly: Boolean = true, val inputValid: Boolean = true) {
  fun pool(inventory: List<CardWithInventory>): List<CardWithInventory> {
    require(inputValid) { "Revisa los códigos de las cartas obligatorias y excluidas." }
    require(required.size <= 20) { "Las cartas obligatorias superan las 20 posiciones del mazo." }
    require(required.intersect(excluded).isEmpty()) { "Una carta no puede ser obligatoria y excluida." }
    val allowed = inventory.filter { it.card.id !in excluded &&
      (expansions.isEmpty() || CardId.split(it.card.id).first in expansions) && (!ownedOnly || it.ownedCount > 0) }
    val missing = required - allowed.map { it.card.id }.toSet()
    require(missing.isEmpty()) { "Fuera de los criterios o sin copias: ${missing.sorted().joinToString()}. Revisa expansiones, exclusiones o usa Catálogo." }
    return if (ownedOnly) allowed else allowed.map { it.copy(ownedCount = 2) }
  }
  fun validate(deck: GeneratedDeck) {
    val ids = deck.cards.map { it.card.id }.toSet()
    require(ids.intersect(excluded).isEmpty()) { "La propuesta incluye una carta excluida." }
    require(ids.containsAll(required)) { "Faltan cartas obligatorias: ${(required - ids).joinToString()}." }
    require(expansions.isEmpty() || ids.all { CardId.split(it).first in expansions }) { "La propuesta usa una expansión no seleccionada." }
  }
  companion object {
    fun codes(text: String): Set<String> = text.split(Regex("[,;\\s]+"))
      .filter { it.isNotBlank() }.map { CardId.normalize(it) }.toSet()
  }
}
