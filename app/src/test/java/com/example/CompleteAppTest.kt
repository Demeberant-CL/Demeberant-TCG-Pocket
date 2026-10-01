package com.example

import androidx.room.Room
import com.example.data.local.AppDatabase
import com.example.data.local.InventoryCardEntity
import com.example.data.local.SavedDeckEntity
import com.example.data.preferences.UserPreferences
import com.example.data.repository.*
import com.example.data.util.*
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
class CompleteAppTest {
  @Before fun loadCatalog() { CardCatalog.loadBundled(RuntimeEnvironment.getApplication()) }

  @Test fun catalogHasAllSnapshotCardsWithoutPlaceholderHealth() {
    val cards = CardCatalog.ALL_CARDS.filter { it.source.startsWith("Catálogo comunitario") }
    assertEquals(4317, cards.size)
    assertEquals(4317, cards.map { it.id }.distinct().size)
    assertEquals(24, cards.map { CardId.split(it.id).first }.distinct().size)
    assertTrue(cards.all { it.hp == 0 && it.attackDamage.isBlank() })
    assertNotNull(CardCatalog.getCardById("B4B-001"))
    assertEquals("Gardevoir", CardCatalog.getCardById("A1-132")!!.name)
  }

  @Test fun csvPreservesWishesAndOldCsvLeavesThemUnspecified() {
    val card = CardCatalog.getCardById("A1-001")!!
    val parsed = CollectionCsv.parse(CollectionCsv.export(listOf(CardWithInventory(card, 2, true))))
    assertEquals(true, parsed.single().wishlist)
    assertNull(CollectionCsv.parse("Set,ID,Nombre,Rareza,Cantidad\nA1,1,Bulbasaur,♦,2").single().wishlist)
    assertTrue(runCatching { CollectionCsv.parse("Set,ID,Nombre,Rareza,Cantidad,Deseos\nA1,1,Bulbasaur,♦,2,quizas") }.isFailure)
  }

  @Test fun backupRoundTripContainsAllPersonalState() {
    val entries = listOf(DeckCardEntry(CardCatalog.getCardById("A1-001")!!, 2))
    val deck = SavedDeckEntity(name = "Borrador", archetype = "Manual", strategy = "Notas",
      cardListSerialized = DeckCodec.encode(entries, listOf("Planta")), totalCards = 2)
    val snapshot = BackupSnapshot(listOf(InventoryCardEntity("A1-001", "Bulbasaur", "Sobre Mewtwo", "♦", 3, true, 100)),
      listOf(deck), UserPreferences(false, "en", "light"))
    val restored = AppBackup.decode(AppBackup.encode(snapshot))
    assertEquals(snapshot.cards, restored.cards)
    assertEquals(snapshot.preferences, restored.preferences)
    assertEquals(deck.name, restored.decks.single().name)
    assertEquals(listOf("Planta"), DeckCodec.energies(restored.decks.single().cardListSerialized))
    assertEquals(2, DeckCodec.decode(restored.decks.single().cardListSerialized).single().count)
  }

  @Test fun malformedBackupIsRejectedBeforeRestore() {
    val snapshot = BackupSnapshot(listOf(InventoryCardEntity("A1-001", "Bulbasaur", "", "♦", 1)),
      emptyList(), UserPreferences())
    val text = AppBackup.encode(snapshot)
    assertTrue(runCatching { AppBackup.decode(text.replace("\"version\": 1", "\"version\": 9")) }.isFailure)
    assertTrue(runCatching { AppBackup.decode(text.replace("\"quantity\": 1", "\"quantity\": -1")) }.isFailure)
    assertTrue(runCatching { AppBackup.decode(text.replace("\"quantity\": 1", "\"quantity\": 1.5")) }.isFailure)
  }

  @Test fun restoreRetainsAbsentCardsAndIsIdempotentForDecks() = runBlocking {
    val db = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), AppDatabase::class.java).build()
    try {
      val repository = InventoryRepository.fromDatabase(db)
      repository.setQuantity("A1-001", 2)
      repository.toggleWishlist("A1-001")
      repository.setQuantity("A1-002", 5)
      val snapshot = BackupSnapshot(listOf(InventoryCardEntity("A1-001", "Bulbasaur", "", "♦", 7, true)),
        listOf(SavedDeckEntity(name = "Restaurado", archetype = "Manual", strategy = "",
          cardListSerialized = "A1-001:2", totalCards = 2)), UserPreferences())
      repeat(2) { repository.restoreSnapshot(snapshot.cards, snapshot.decks) }
      val inventory = InventoryRepository.fromDatabase(db).inventoryFlow.first()
      assertEquals(7, inventory.first { it.card.id == "A1-001" }.ownedCount)
      assertTrue(inventory.first { it.card.id == "A1-001" }.isWishlist)
      assertEquals(5, inventory.first { it.card.id == "A1-002" }.ownedCount)
      assertEquals(1, repository.savedDecksFlow.first().size)
    } finally { db.close() }
  }

  @Test fun quantityChangesRetainFlagsAndOldCsvDoesNotClearWishes() = runBlocking {
    val db = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), AppDatabase::class.java).build()
    try {
      val repository = InventoryRepository.fromDatabase(db)
      repository.toggleWishlist("A1-001")
      repository.setQuantity("A1-001", 3)
      repository.processParsedCsvCards(CollectionCsv.parse("Set,ID,Nombre,Rareza,Cantidad\nA1,1,Bulbasaur,♦,1"))
      val card = repository.inventoryFlow.first().first { it.card.id == "A1-001" }
      assertTrue(card.isWishlist); assertEquals(1, card.ownedCount)
      assertTrue(db.userCardDao().getUserCardById("A1-001")!!.isFavorite)
    } finally { db.close() }
  }

  @Test fun deckCodecKeepsLegacyDecksAndRejectsInvalidNewDrafts() {
    assertEquals("A1-001" to 2, DeckCodec.references("A1-1:2").single())
    assertTrue(runCatching { DeckCodec.references("A1-1:3") }.isFailure)
    assertTrue(runCatching { DeckCodec.references("""{"format":2,"cards":[{"id":"A1-001","count":1.5}],"energies":[]}""") }.isFailure)
    assertTrue(runCatching { DeckCodec.references("A1-1:1;A1-001:1") }.isFailure)
    assertTrue(runCatching { DeckCodec.references("A1-1:2;incorrecto") }.isFailure)
  }

  @Test fun packCoverageUsesMissingWishesAndTargetsAcrossExpansions() {
    val card = CardCatalog.getCardById("A1-001")!!
    val coverage = CollectionInsights.coverage(listOf(CardWithInventory(card, 0, true)), setOf(card.id)).single()
    assertEquals(9, coverage.score)
    assertEquals("Mewtwo", coverage.pack)
    assertTrue(CollectionInsights.coverage(listOf(CardWithInventory(card, 1, true))).all { it.score == 0 })
  }

  @Test fun tradeComparisonRequiresExplicitMissingCardsAndKeepsReserve() {
    val bulbasaur = CardCatalog.getCardById("A1-001")!!
    val caterpie = CardCatalog.getCardById("A1-005")!!
    val mine = listOf(CardWithInventory(bulbasaur, 3, false), CardWithInventory(caterpie, 0, true))
    val peer = CollectionCsv.parse("Set,ID,Nombre,Rareza,Cantidad\nA1,1,Bulbasaur,♦,0\nA1,5,Caterpie,♦,3")
    val proposals = TradePlanner.compare(mine, peer, 2)
    assertEquals(1, proposals.size)
    assertEquals(bulbasaur.id, proposals.single().offer.single().card.id)
    assertEquals(caterpie.id, proposals.single().request.single().card.id)
    assertTrue(TradePlanner.compare(mine, peer.drop(1), 2).isEmpty())
    assertTrue(TradePlanner.compare(mine.map { it.copy(ownedCount = if (it.card.id == bulbasaur.id) 2 else 0) }, peer, 2).isEmpty())
  }

  @Test fun probabilityModelsMatchExactBoundaryAndDrawCases() {
    assertEquals(0.19, CollectionInsights.cumulativeChance(0.1, 2), 1e-10)
    assertEquals(0.0, CollectionInsights.cumulativeChance(1.0, 0), 0.0)
    assertEquals(1.0, CollectionInsights.cumulativeChance(1.0, 1), 0.0)
    assertEquals(1.0 - 210.0 / 380, CollectionInsights.drawChance(20, 2, 5), 1e-10)
    assertEquals(0.0, CollectionInsights.drawChance(20, 0, 5), 0.0)
    assertTrue(runCatching { CollectionInsights.cumulativeChance(Double.NaN, 1) }.isFailure)
  }
}
