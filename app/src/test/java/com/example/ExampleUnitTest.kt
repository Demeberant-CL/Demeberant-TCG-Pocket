package com.example

import com.example.data.api.GeminiDeckAnalyzer
import com.example.data.local.AppDatabase
import com.example.data.local.InventoryCardEntity
import com.example.data.model.BoosterPack
import com.example.data.model.CardRarity
import com.example.data.repository.CardCatalog
import com.example.data.repository.CardWithInventory
import com.example.data.repository.DeckBuilderEngine
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
  fun testCardCatalogNotEmpty() {
    val cards = CardCatalog.ALL_CARDS
    assertTrue(cards.isNotEmpty())
    val charizard = CardCatalog.getCardById("A1-036")
    assertNotNull(charizard)
    assertEquals("Charizard ex", charizard?.name)
    assertEquals(BoosterPack.CHARIZARD, charizard?.pack)
    assertEquals(CardRarity.FOUR_DIAMONDS, charizard?.rarity)
  }

  @Test
  fun testRoomDatabaseInsertAndQuery() = runBlocking {
    val context = RuntimeEnvironment.getApplication()
    val db = androidx.room.Room.inMemoryDatabaseBuilder(
      context,
      AppDatabase::class.java
    ).allowMainThreadQueries().build()

    val dao = db.inventoryDao()
    val card = InventoryCardEntity(
      cardId = "A1-228",
      cardName = "Charizard ex (Inmersiva)",
      packName = "Sobre Charizard",
      rarity = "3 Estrellas (Inmersiva)",
      quantity = 1,
      isWishlist = true,
      acquisitionDate = 1700000000000L
    )

    dao.insertCard(card)
    val fetched = dao.getCardById("A1-228")
    assertNotNull(fetched)
    assertEquals("Charizard ex (Inmersiva)", fetched?.cardName)
    assertEquals("3 Estrellas (Inmersiva)", fetched?.rarity)
    assertEquals(1700000000000L, fetched?.acquisitionDate)

    db.close()
  }

  @Test
  fun testGeminiLocalMetaAnalysis() {
    val charizardCard = CardCatalog.getCardById("A1-036")!!
    val moltresCard = CardCatalog.getCardById("A1-047")!!
    val cards = listOf(
      CardWithInventory(charizardCard, 2, false),
      CardWithInventory(moltresCard, 1, false)
    )

    val result = GeminiDeckAnalyzer.computeLocalMetaAnalysis(cards)
    assertNotNull(result)
    assertTrue(result.deckInsights.isNotEmpty())
    val charizardDeck = result.deckInsights.find { it.deckName.contains("Charizard") }
    assertNotNull(charizardDeck)
    assertTrue(charizardDeck!!.completionRatePercent > 0)
    assertTrue(charizardDeck.ownedCardsSummary.contains("Charizard ex"))
  }

  @Test
  fun testDeckBuilderProduces20CardsAndValidExport() {
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

  @Test
  fun testCsvParsing() {
    val csv = "card_id,quantity\nA1-036,2\nA1-096,1\n"
    val lines = csv.lines().filter { it.isNotBlank() }
    val map = mutableMapOf<String, Int>()
    for (i in 1 until lines.size) {
      val parts = lines[i].split(",")
      map[parts[0]] = parts[1].toInt()
    }
    assertEquals(2, map["A1-036"])
    assertEquals(1, map["A1-096"])
  }
}
