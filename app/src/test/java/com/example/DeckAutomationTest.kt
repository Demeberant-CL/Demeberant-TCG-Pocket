package com.example

import com.example.data.model.*
import com.example.data.repository.*
import com.example.domain.DeckAutomation
import org.junit.Assert.*
import org.junit.Test

class DeckAutomationTest {
  private fun card(id: Int, type: String = "Agua", stage: String = "basic", parent: String = "", name: String = "Basic$id") =
    PokemonCard("A1-${id.toString().padStart(3,'0')}", name, BoosterPack.UNKNOWN, CardRarity.ONE_DIAMOND,
      0, type, "", "", category = "pokemon", stage = stage, evolvesFrom = parent)
  private fun owned(card: PokemonCard, count: Int = 2) = CardWithInventory(card, count, false)

  @Test fun starterRespectsInventoryNamesParentsAndIncludesEnergy() {
    val pool = (1..12).map { owned(card(it), if (it == 1) 1 else 3) } +
      owned(card(25, stage = "1", parent = "Absent", name = "Evolution")) +
      owned(card(26, name = "Basic2")) + owned(card(27, type = "Sin verificar", stage = "unknown"))
    val before = pool.toList()
    val result = DeckAutomation.build(pool, "Agua")
    assertEquals(20, result.totalCardCount)
    assertEquals(listOf("Agua"), result.energyTypes)
    assertTrue(result.cards.all { entry -> entry.count <= pool.first { it.card.id == entry.card.id }.ownedCount })
    assertTrue(result.cards.groupBy { it.card.rulesName }.values.all { it.sumOf { entry -> entry.count } <= 2 })
    assertFalse(result.cards.any { it.card.name == "Evolution" || it.card.type == "Sin verificar" })
    assertEquals(before, pool)
  }

  @Test fun matchingEvolutionIsNotCrowdedOutByBasicFillersAcrossExpansions() {
    val basic = card(1, name = "Core")
    val evolution = card(2, stage = "1", parent = "Core", name = "Evolved").copy(id = "B1-002")
    val pool = (3..15).map { owned(card(it)) } + owned(basic) + owned(evolution)
    val result = DeckAutomation.build(pool, "Agua")
    assertTrue(result.cards.any { it.card.id == "B1-002" })
    assertEquals(20, result.totalCardCount)
    assertTrue(result.validationWarnings.isEmpty())
  }
  @Test fun insufficientInventoryStaysAnExplicitDraftAndUnknownDoesNotBecomeBasic() {
    val draft = DeckAutomation.build(listOf(owned(card(1),1)), "Agua")
    assertEquals(1,draft.totalCardCount)
    assertTrue(draft.validationWarnings.any { it.contains("1/20") })
    assertTrue(runCatching { DeckAutomation.build(listOf(owned(card(2,stage="unknown"))),"Agua") }.isFailure)
    assertTrue(runCatching { DeckAutomation.build(emptyList(),"Dragón") }.isFailure)
  }

  @Test fun historicalTemplatesNowIncludeTheirEnergy() {
    assertEquals(listOf("Psíquico"),DeckBuilderEngine.buildArchetypeDeck("Mewtwo",false,emptyMap()).energyTypes)
    assertEquals(listOf("Fuego"),DeckBuilderEngine.buildArchetypeDeck("Charizard",false,emptyMap()).energyTypes)
  }

  @Test fun energiesUseKnownTypesWithoutInventingDragonColorlessOrTrainerEnergy() {
    val cards = listOf(DeckCardEntry(card(1,"Agua"),2),DeckCardEntry(card(2,"Rayo"),1),
      DeckCardEntry(card(3,"Entrenador"),2),DeckCardEntry(card(4,"Incoloro"),2),
      DeckCardEntry(card(5,"Dragón"),2),DeckCardEntry(card(6,"Sin verificar"),2))
    assertEquals(listOf("Agua","Rayo"),DeckAutomation.energies(cards))
    assertTrue(DeckAutomation.energies(cards.drop(2)).isEmpty())
  }
}
