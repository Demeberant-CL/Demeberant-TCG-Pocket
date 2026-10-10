package com.example

import com.example.data.model.*
import com.example.data.repository.*
import com.example.domain.*
import org.junit.Assert.*
import org.junit.Test

class LocalDeckPlannerTest {
  private fun card(n: Int, name: String = "Basic$n", type: String = "Agua", stage: String = "basic", parent: String = "") =
    PokemonCard("A1-${n.toString().padStart(3,'0')}", name, BoosterPack.UNKNOWN, CardRarity.ONE_DIAMOND,
      70,type,"","",rulesName=name,category=if(type=="Entrenador") "item" else "pokemon",stage=stage,evolvesFrom=parent)
  private fun owned(card: PokemonCard, count: Int = 2) = CardWithInventory(card,count,false)
  private fun attack(cost: List<String>, damage: String = "60") = CombatData(100,1,listOf(CombatAttack(cost,damage)))
  @Test fun completeDeckPreservesInventoryAndNameLimitsAcrossPrintings() {
    val base = card(1,"Core")
    val pool = listOf(owned(base,1),owned(base.copy(id="B1-999"),1)) + (2..18).map { owned(card(it)) }
    val before = pool.toList()
    val plans = LocalDeckPlanner.recommend(pool,options=PlannerOptions(type="Agua"))
    assertEquals(before,pool)
    plans.forEach { p ->
      assertEquals(20,p.deck.totalCardCount)
      assertTrue(p.deck.cards.all { it.count <= pool.first { c -> c.card.id==it.card.id }.ownedCount })
      assertTrue(p.deck.cards.groupBy { it.card.rulesName }.values.all { it.sumOf { c -> c.count }<=2 })
      assertTrue(p.deck.validationWarnings.isEmpty())
    }
  }
  @Test fun selectedEvolutionNeedsTheWholeLineAndSelectedPrinting() {
    val b = card(1,"Base");val m=card(2,"Middle",stage="1",parent="Base")
    val h=card(3,"Hero",stage="2",parent="Middle")
    val alternate=h.copy(id="B1-003")
    val pool=listOf(owned(b),owned(m),owned(h,1),owned(alternate))+(4..18).map { owned(card(it)) }
    val p=LocalDeckPlanner.recommend(pool,options=PlannerOptions(type="Agua",anchorId=h.id)).first()
    assertTrue(p.deck.cards.any { it.card.id==h.id })
    assertTrue(p.deck.cards.any { it.card.rulesName=="Base" })
    assertTrue(p.deck.cards.any { it.card.rulesName=="Middle" })
    assertTrue(runCatching { LocalDeckPlanner.recommend(pool.filter { it.card.id!=m.id },options=PlannerOptions(type="Agua",anchorId=h.id)) }.isFailure)
  }
  @Test fun realAttackCostsOverridePrintedTypeAndAutoSupportsDragonCosts() {
    val hero=card(1,"Dragon","Dragón")
    val wrong=card(2,"Wrong")
    val pool=listOf(owned(hero),owned(wrong))+(3..18).map { owned(card(it)) }
    val facts=mapOf(hero.id to attack(listOf("Water","Lightning"),"120"),wrong.id to attack(listOf("Fire")))
    val auto=LocalDeckPlanner.recommend(pool,facts,PlannerOptions(anchorId=hero.id)).first()
    assertEquals(setOf("Agua","Rayo"),auto.deck.energyTypes.toSet())
    assertFalse(auto.deck.cards.any { it.card.id==wrong.id })
    assertTrue(runCatching { LocalDeckPlanner.recommend(pool,facts,PlannerOptions(type="Agua",anchorId=hero.id)) }.isFailure)
  }
  @Test fun relevantDrawAndSearchBeatUnknownTrainersAndWrongEnergySupport() {
    val basic=card(1);val draw=card(2,"Draw","Entrenador");val search=card(3,"Search","Entrenador")
    val dead=card(4,"Fire support","Entrenador")
    val pool=listOf(owned(basic),owned(draw),owned(search),owned(dead))+(5..18).map { owned(card(it,type="Entrenador")) }
    val data=mapOf(basic.id to attack(listOf("Water")),draw.id to CombatData(text="Draw 2 cards."),
      search.id to CombatData(text="Search your deck for a Basic Pokémon."),dead.id to CombatData(text="Une 1 Energía {R} a tu Pokémon {R}."))
    val p=LocalDeckPlanner.recommend(pool,data,PlannerOptions(type="Agua",anchorId=basic.id)).first()
    assertTrue(p.deck.cards.any { it.card.id==draw.id });assertTrue(p.deck.cards.any { it.card.id==search.id })
    assertFalse(p.deck.cards.any { it.card.id==dead.id })
  }
  @Test fun missingFactsAreExplicitAndSmallCollectionsDoNotInventCards() {
    val only=owned(card(1),1)
    val p=LocalDeckPlanner.recommend(listOf(only),options=PlannerOptions(type="Agua")).single()
    assertEquals(1,p.deck.totalCardCount);assertEquals(0,p.knownCopies)
    assertTrue(p.cautions.any { "valoración parcial" in it })
    assertTrue(p.cautions.any { "1/20" in it })
    assertTrue(runCatching { LocalDeckPlanner.recommend(listOf(owned(card(2,stage="unknown")))) }.isFailure)
  }
  @Test fun shuffledInputIsDeterministicAndSearchIsBounded() {
    val pool=(1..200).map { owned(card(it)) }
    val p=LocalDeckPlanner.recommend(pool,options=PlannerOptions(type="Agua"))
    assertEquals(p,LocalDeckPlanner.recommend(pool.reversed(),options=PlannerOptions(type="Agua")))
    assertTrue(p.size in 1..3)
  }
}
