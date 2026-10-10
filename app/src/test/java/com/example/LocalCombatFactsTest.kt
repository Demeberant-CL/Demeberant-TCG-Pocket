package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.CardRulesEntity
import com.example.data.repository.LocalCombatRepository
import com.example.data.model.*
import com.example.data.repository.CardWithInventory
import com.example.domain.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[34])
class LocalCombatFactsTest {
  @Test fun bundledFactsHaveRealCostsAndDoNotUsePlaceholderHealth() = runBlocking {
    val context=ApplicationProvider.getApplicationContext<Context>()
    val facts=LocalCombatRepository.snapshot(context)
    assertTrue(facts.size>=2400)
    assertEquals(190,facts.getValue("A1-004").hp)
    assertEquals(listOf("Grass","Grass","Colorless","Colorless"),facts.getValue("A1-004").attacks[1].cost)
    assertEquals(150,facts.getValue("A1-129").hp)
    assertTrue(facts.getValue("A1-220").text.contains("{W}"))
  }
  @Test fun cachedRetreatHeaderDoesNotTurnEveryCardIntoSwitchSupport() {
    val rule=CardRulesEntity("A1-001","es",60,"Agua","PS: 60\nRetirada: 2\nPlacaje · 20 · Coste: Water, Colorless · Roba 1 carta.","","",1)
    val fact=LocalCombatRepository.fromRule(rule)
    assertEquals(2,fact.retreat)
    assertEquals(listOf("Water","Colorless"),fact.attacks.single().cost)
    assertFalse(RoleClassifier.classify(fact.text).contains(CardRole.SWITCH))
    assertTrue(RoleClassifier.classify(fact.text).contains(CardRole.DRAW))
  }
}
