package com.example

import androidx.room.Room
import com.example.data.api.GeminiDeckAnalyzer
import com.example.data.local.AppDatabase
import com.example.data.local.InventoryCardEntity
import com.example.data.local.SavedDeckEntity
import com.example.data.repository.CardCatalog
import com.example.data.repository.CardWithInventory
import com.example.data.repository.DeckBuilderEngine
import com.example.data.repository.InventoryRepository
import com.example.data.util.CardId
import com.example.data.util.CollectionCsv
import com.example.data.util.TcgdexHelper
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CollectionRegressionTest {
  @Before fun loadCatalog() { CardCatalog.loadBundled(RuntimeEnvironment.getApplication()) }
  @Test fun canonicalIdsPreservePromoSets() {
    assertEquals("A1-001", CardId.normalize(" a1-1 "))
    assertEquals("PROMO-A-001", CardId.normalize("promo-a-1"))
    assertEquals("PROMO-A" to "001", CardId.split("PROMO-A-1"))
    assertTrue(TcgdexHelper.getCardImageUrl("PROMO-A-1").contains("/P-A/001/"))
    assertTrue(TcgdexHelper.getCardImageUrl("A1-1").contains("/A1/001/"))
    assertTrue(TcgdexHelper.getCardImageUrl("A1A-1").contains("/A1a/001/"))
    assertNull(CardCatalog.getCardById("invalid-999999"))
  }

  @Test fun csvRoundTripSupportsQuotesMultilineAndPromo() {
    val card = CardCatalog.registerCard("PROMO-B-999", "Carta, con \"comillas\"\ny salto", "★★")
    val text = CollectionCsv.export(listOf(CardWithInventory(card, 3, false)))
    val result = CollectionCsv.parse("\uFEFF" + text.replace("\n", "\r\n"))
    assertEquals(1, result.size)
    assertEquals("PROMO-B", result[0].setCode)
    assertEquals(card.name.replace("\n", "\r\n"), result[0].name)
    assertEquals("★★", result[0].rarity)
    assertEquals(3, result[0].quantity)
  }

  @Test fun csvRejectsInvalidQuantitiesAndDuplicateAliases() {
    val header = "Set,ID,Nombre,Rareza,Cantidad\n"
    assertTrue(runCatching { CollectionCsv.parse(header + "A1,1,Bulbasaur,♦,-1") }.isFailure)
    assertTrue(runCatching { CollectionCsv.parse(header + "A1,1,Bulbasaur,♦,1\nA1,001,Bulbasaur,♦,2") }.isFailure)
    assertTrue(runCatching { CollectionCsv.parse(header + "A1,1,\"Sin cerrar,♦,1") }.isFailure)
    assertTrue(runCatching { CollectionCsv.parse("nombre,cantidad\nBulbasaur,2") }.isFailure)
  }

  @Test fun bundledCsvIsCanonicalAndImportsWithoutStaticAliases() = runBlocking {
    val context = RuntimeEnvironment.getApplication()
    val parsed = CollectionCsv.parse(context.assets.open("coleccion-pokemon-2026-09-29.csv").bufferedReader().use { it.readText() })
    assertEquals(1067, parsed.size)
    val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
    try {
      val repository = InventoryRepository.fromDatabase(db)
      repository.processParsedCsvCards(parsed)
      val inventory = repository.inventoryFlow.first()
      assertEquals(1, inventory.count { it.card.id == "A1-001" })
      assertTrue(inventory.none { it.card.id == "A1-1" })
      assertEquals(1, inventory.first { it.card.id == "A1-001" }.ownedCount)
      assertEquals(parsed.sumOf { it.quantity }, inventory.sumOf { it.ownedCount })
    } finally { db.close() }
  }

  @Test fun persistedMetadataIsRestoredWithoutPlaceholderNames() = runBlocking {
    val db = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), AppDatabase::class.java).build()
    try {
      db.inventoryDao().insertCard(InventoryCardEntity("B4A-998", "Carta real persistida", "Sobre sin verificar", "2 Estrellas", 4))
      val restored = InventoryRepository.fromDatabase(db).inventoryFlow.first().first { it.card.id == "B4A-998" }
      assertEquals("Carta real persistida", restored.card.name)
      assertEquals("★★", restored.card.rarity.symbol)
      assertEquals(0, restored.card.hp)
      assertEquals(4, restored.ownedCount)
    } finally { db.close() }
  }

  @Test fun builderNeverAddsBlaineTwiceOrExceedsInventory() {
    val deck = DeckBuilderEngine.buildArchetypeDeck("Charizard", true, mapOf("A1-221" to 2))
    assertEquals(2, deck.totalCardCount)
    assertEquals(1, deck.cards.size)
    assertTrue(deck.validationWarnings.isNotEmpty())
  }

  @Test fun builderChecksEvolutionAndTwoCopiesAcrossVariants() {
    val card = CardCatalog.getCardById("A1-132")!!
    assertTrue(DeckBuilderEngine.validate(listOf(com.example.data.repository.DeckCardEntry(card, 2))).any { it.contains("Kirlia") })
    val deck = DeckBuilderEngine.buildArchetypeDeck("Charizard", false, emptyMap())
    assertTrue(deck.cards.groupBy { it.card.name }.all { (_, entries) -> entries.sumOf { it.count } <= 2 })
    assertEquals(deck.cards.sumOf { it.count }, deck.totalCardCount)
    assertTrue(deck.validationWarnings.none { it.contains("necesita") })
  }

  @Test fun localAnalysisSumsArtVariants() {
    val inventory = listOf(
      CardWithInventory(CardCatalog.getCardById("A1-036")!!, 2, false),
      CardWithInventory(CardCatalog.getCardById("A1-280")!!, 0, false)
    )
    val insight = GeminiDeckAnalyzer.computeLocalMetaAnalysis(inventory).deckInsights.first { it.deckName.contains("Charizard") }
    assertEquals(20, insight.completionRatePercent)
    assertTrue(insight.ownedCardsSummary.contains("Charizard ex x2"))
  }

  @Test fun migrationRetainsCollectionFlagsAndSavedDecks() = runBlocking {
    val context = RuntimeEnvironment.getApplication()
    val name = "migration-regression.db"
    context.deleteDatabase(name)
    var db = Room.databaseBuilder(context, AppDatabase::class.java, name).build()
    try {
      db.inventoryDao().insertCards(listOf(
        InventoryCardEntity("A1-001", "Bulbasaur", "Sobre Mewtwo", "♦", 0),
        InventoryCardEntity("A1-1", "Bulbasaur", "Sobre Mewtwo", "♦", 3, true),
        InventoryCardEntity("PROMO-A-1", "Promo", "Sobre sin verificar", "★", 2)
      ))
      val deckId = db.savedDeckDao().insertDeck(SavedDeckEntity(name = "Guardado", archetype = "A1", strategy = "Prueba", cardListSerialized = "A1-1:2"))
      db.close()
      android.database.sqlite.SQLiteDatabase.openDatabase(context.getDatabasePath(name).path, null,
        android.database.sqlite.SQLiteDatabase.OPEN_READWRITE).use { it.version = 3 }
      db = Room.databaseBuilder(context, AppDatabase::class.java, name).addMigrations(AppDatabase.MIGRATION_3_4).build()
      val cards = db.inventoryDao().getAllCards()
      assertEquals(2, cards.size)
      assertEquals(3, cards.first { it.cardId == "A1-001" }.quantity)
      assertTrue(cards.first { it.cardId == "A1-001" }.isWishlist)
      assertNotNull(cards.find { it.cardId == "PROMO-A-001" })
      assertEquals("Guardado", db.savedDeckDao().getDeckById(deckId)?.name)
    } finally { db.close(); context.deleteDatabase(name) }
  }
}
