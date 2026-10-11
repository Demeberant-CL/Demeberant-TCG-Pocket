package com.example

import com.example.data.model.*
import com.example.data.repository.*
import com.example.domain.*
import org.junit.Assert.*
import org.junit.Test

class DeckPlayGuideTest {
  private fun card(id: String, name: String, type: String, stage: String = "basic", parent: String = "") =
    PokemonCard(id, name, BoosterPack.UNKNOWN, CardRarity.ONE_DIAMOND, 70, type, "", "", rulesName = name,
      category = if (type == "Entrenador") "item" else "pokemon", stage = stage, evolvesFrom = parent)
  @Test fun guideNamesOnlySelectedCardsAndKeepsPrintedAttackConditions() {
    val basic = card("A1-001", "Núcleo", "Agua")
    val next = card("A1-002", "Evolución", "Agua", "1", "Núcleo")
    val draw = card("A1-003", "Robo", "Entrenador")
    val absent = card("A1-004", "Ausente", "Entrenador")
    val deck = GeneratedDeck("Prueba", "", "", listOf(DeckCardEntry(basic,2),DeckCardEntry(next,2),DeckCardEntry(draw,2)),6,emptyList(),listOf("Agua"))
    val facts = mapOf(next.id to CombatData(100,1,listOf(CombatAttack(listOf("Water"),"80+","Solo si hay daño.","Golpe condicionado"))),
      draw.id to CombatData(text="Draw 2 cards."), absent.id to CombatData(text="Draw 4 cards."))
    val text = DeckPlayGuide.generate(deck,facts)
    assertTrue(text.contains("Núcleo → Evolución"))
    assertTrue(text.contains("Golpe condicionado"))
    assertTrue(text.contains("Solo si hay daño."))
    assertTrue(text.contains("2× Robo"))
    assertFalse(text.contains("Ausente"))
  }
  @Test fun missingFactsAndMissingBasicsStayExplicit() {
    val trainer = card("A1-003", "Objeto", "Entrenador")
    val deck = GeneratedDeck("Prueba","","",listOf(DeckCardEntry(trainer,2)),2,listOf("Mazo incompleto"),listOf("Agua","Fuego"))
    val text = DeckPlayGuide.generate(deck,emptyMap())
    assertTrue(text.contains("Falta un Pokémon básico"))
    assertTrue(text.contains("0/2"))
    assertTrue(text.contains("Varias energías"))
    assertTrue(text.contains("Mazo incompleto"))
  }
}
