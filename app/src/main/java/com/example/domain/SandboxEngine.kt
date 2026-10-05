package com.example.domain

import com.example.data.repository.DeckCardEntry
import kotlin.random.Random

enum class BoardZone { HAND, ACTIVE, BENCH, DISCARD }
data class BoardCard(val instanceId: Int, val cardId: String, val name: String, val basic: Boolean,
  val pokemon: Boolean, val energies: Int = 0, val damage: Int = 0)
data class SandboxState(val drawPile: List<BoardCard>, val hand: List<BoardCard>, val active: BoardCard? = null,
  val bench: List<BoardCard> = emptyList(), val discard: List<BoardCard> = emptyList(), val turn: Int = 1,
  val attachedThisTurn: Boolean = false, val openingGuaranteed: Boolean = true)

object SandboxEngine {
  fun start(entries: List<DeckCardEntry>, random: Random = Random.Default): SandboxState {
    require(entries.sumOf { it.count } == 20 && entries.all { it.count in 1..2 })
    require(entries.groupBy { it.card.rulesName.lowercase() }.all { (_, cards) -> cards.sumOf { it.count } <= 2 })
    var instance = 0
    val cards = entries.flatMap { entry -> List(entry.count) { BoardCard(instance++, entry.card.id,
      entry.card.name, entry.card.category == "pokemon" && entry.card.stage == "basic",
      entry.card.category == "pokemon") } }
    require(cards.any { it.basic }) { "El mazo necesita un Pokémon básico identificado." }
    val shuffled = cards.shuffled(random).toMutableList()
    val hand = shuffled.take(5).toMutableList()
    shuffled.subList(0, 5).clear()
    // Manual practice approximation; not a reproduction of the game's hidden shuffle algorithm.
    if (hand.none { it.basic }) {
      val index = shuffled.indexOfFirst { it.basic }
      val basic = shuffled.removeAt(index)
      shuffled.add(hand.removeAt(hand.lastIndex))
      hand.add(basic)
    }
    return SandboxState(shuffled, hand)
  }
  fun draw(state: SandboxState): SandboxState {
    require(state.drawPile.isNotEmpty()) { "No quedan cartas en el mazo." }
    return state.copy(drawPile = state.drawPile.drop(1), hand = state.hand + state.drawPile.first())
  }
  fun nextTurn(state: SandboxState): SandboxState =
    (if (state.drawPile.isNotEmpty()) draw(state) else state).copy(turn = state.turn + 1, attachedThisTurn = false)
  fun move(state: SandboxState, instanceId: Int, destination: BoardZone): SandboxState {
    val card = (state.hand + state.bench + state.discard + listOfNotNull(state.active))
      .find { it.instanceId == instanceId } ?: error("Carta no disponible.")
    if (destination == BoardZone.ACTIVE) require(state.active == null || state.active.instanceId == instanceId) { "El puesto activo está ocupado." }
    if (destination == BoardZone.BENCH) require(state.bench.size < 3 || state.bench.any { it.instanceId == instanceId }) { "La banca admite tres Pokémon." }
    if (destination in setOf(BoardZone.ACTIVE, BoardZone.BENCH)) require(card.pokemon) { "Solo Pokémon en Activo o Banca." }
    val cleared = state.copy(hand = state.hand.filter { it.instanceId != instanceId },
      active = state.active?.takeUnless { it.instanceId == instanceId }, bench = state.bench.filter { it.instanceId != instanceId },
      discard = state.discard.filter { it.instanceId != instanceId })
    val moved = if (destination == BoardZone.HAND || destination == BoardZone.DISCARD) card.copy(energies = 0, damage = 0) else card
    return when (destination) {
      BoardZone.HAND -> cleared.copy(hand = cleared.hand + moved)
      BoardZone.ACTIVE -> cleared.copy(active = moved)
      BoardZone.BENCH -> cleared.copy(bench = cleared.bench + moved)
      BoardZone.DISCARD -> cleared.copy(discard = cleared.discard + moved)
    }
  }
  fun swapActive(state: SandboxState, benchId: Int): SandboxState {
    val chosen = state.bench.find { it.instanceId == benchId } ?: error("Pokémon no disponible en la banca.")
    return state.copy(active = chosen, bench = state.bench.filter { it.instanceId != benchId } + listOfNotNull(state.active))
  }
  fun attachEnergy(state: SandboxState, id: Int): SandboxState {
    require(!state.attachedThisTurn) { "Ya asignaste energía este turno." }
    require(state.active?.instanceId == id || state.bench.any { it.instanceId == id }) { "Solo Activo o Banca." }
    return state.copy(active = state.active?.let { if (it.instanceId == id) it.copy(energies = it.energies + 1) else it },
      bench = state.bench.map { if (it.instanceId == id) it.copy(energies = it.energies + 1) else it }, attachedThisTurn = true)
  }
  fun damage(state: SandboxState, id: Int, amount: Int): SandboxState {
    require(amount in 0..999)
    require(state.active?.instanceId == id || state.bench.any { it.instanceId == id })
    return state.copy(active = state.active?.let { if (it.instanceId == id) it.copy(damage = amount) else it },
      bench = state.bench.map { if (it.instanceId == id) it.copy(damage = amount) else it })
  }
}
