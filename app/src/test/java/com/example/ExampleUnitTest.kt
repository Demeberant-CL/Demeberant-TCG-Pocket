package com.example

import com.example.data.api.GeminiDeckAnalyzer
import com.example.data.engine.TcgProbabilityEngine
import com.example.data.local.AppDatabase
import com.example.data.local.InventoryCardEntity
import com.example.data.local.SavedDeckEntity
import com.example.data.local.UserCardEntity
import com.example.data.model.BoosterPack
import com.example.data.model.CardRarity
import com.example.data.repository.CardCatalog
import com.example.data.repository.CardWithInventory
import com.example.data.repository.DeckBuilderEngine
import com.example.data.util.ErrorLogManager
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleUnitTest {

  @Test
  fun testCardCatalogNotEmptyAndHandlesDynamicCards() {
    val cards = CardCatalog.ALL_CARDS
    assertTrue(cards.isNotEmpty())
    val charizard = CardCatalog.getCardById("A1-036")
    assertNotNull(charizard)
    assertEquals("Charizard ex", charizard?.name)
    assertEquals(BoosterPack.CHARIZARD, charizard?.pack)
    assertEquals(CardRarity.FOUR_DIAMONDS, charizard?.rarity)

    // Dynamic expansion card resolution
    val dynamicCard = CardCatalog.registerCard("A2-110", "Carta importada de prueba")
    assertNotNull(dynamicCard)
    assertTrue(dynamicCard?.name?.isNotBlank() == true)
  }

  @Test
  fun testRoomDatabaseSavedDecksAndUserCards() = runBlocking {
    val context = RuntimeEnvironment.getApplication()
    val db = androidx.room.Room.inMemoryDatabaseBuilder(
      context,
      AppDatabase::class.java
    ).allowMainThreadQueries().build()

    // Test SavedDeckDao
    val savedDeckDao = db.savedDeckDao()
    val deckEntity = SavedDeckEntity(
      name = "Mi Mazo Fuego",
      archetype = "Charizard ex Llamas",
      strategy = "Acelerar con Moltres ex",
      cardListSerialized = "A1-036:2;A1-047:2;A1-033:2",
      totalCards = 6
    )
    val insertedId = savedDeckDao.insertDeck(deckEntity)
    assertTrue(insertedId > 0)

    val fetchedDeck = savedDeckDao.getDeckById(insertedId)
    assertNotNull(fetchedDeck)
    assertEquals("Mi Mazo Fuego", fetchedDeck?.name)
    assertEquals(6, fetchedDeck?.totalCards)

    // Test UserCardDao
    val userCardDao = db.userCardDao()
    val userCard = UserCardEntity(
      cardId = "A1-036",
      setCode = "A1",
      cardNumber = "36",
      name = "Charizard ex",
      rarity = "♦♦♦♦",
      quantity = 2,
      isRegistered = true
    )
    userCardDao.insertUserCard(userCard)
    val fetchedCard = userCardDao.getUserCardById("A1-036")
    assertNotNull(fetchedCard)
    assertEquals("Charizard ex", fetchedCard?.name)
    assertEquals(2, fetchedCard?.quantity)

    db.close()
  }

  @Test
  fun testProbabilityEngineCalculations() {
    // 10 packs for Crown (0.04% per pack): 1 - (1 - 0.0004)^10 ~ 0.00399 (0.399%)
    val cumProb = TcgProbabilityEngine.calculateCumulativeProbability(CardRarity.CROWN, 10)
    assertTrue(cumProb > 0.003 && cumProb < 0.005)

    // Recommend pack calculation
    val inventory = listOf(
      CardWithInventory(CardCatalog.getCardById("A1-036")!!, 0, true)
    )
    val rec = TcgProbabilityEngine.recommendPack(listOf("A1-036"), inventory)
    assertNotNull(rec)
    assertEquals(BoosterPack.CHARIZARD, rec.recommendedPack)
    assertTrue(rec.score > 0)
    assertTrue(rec.reasoning.contains("Charizard"))
  }

  @Test
  fun testErrorLogManagerWriteAndRead() = runBlocking {
    val context = RuntimeEnvironment.getApplication()
    ErrorLogManager.clearLogs(context)
    ErrorLogManager.log(context, "UNIT_TEST", "Mensaje de prueba para logger")

    val logs = ErrorLogManager.readLogs(context)
    assertTrue(logs.contains("[UNIT_TEST] Mensaje de prueba para logger"))
  }

  @Test
  fun testDeckBuilderProduces20Cards() {
    val deck = DeckBuilderEngine.buildArchetypeDeck(
      userPrompt = "Pikachu ex Turbo",
      onlyFromInventory = false,
      ownedMap = emptyMap()
    )

    assertNotNull(deck)
    assertEquals(20, deck.totalCardCount)
    assertTrue(deck.cards.isNotEmpty())
    val export = deck.toExportText()
    assertTrue(export.startsWith("###### Mazo Pokémon TCG Pocket:"))
    assertTrue(export.contains("Pikachu ex"))
  }
}
