package com.example

import androidx.room.Room
import com.example.data.local.AppDatabase
import com.example.data.local.SavedDeckEntity
import com.example.data.repository.CardCatalog
import com.example.data.repository.InventoryRepository
import com.example.data.util.CardId
import com.example.zoneimport.ZoneCollectionImport
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ZoneCollectionImportTest {
  @Before fun setup() { CardCatalog.loadBundled(RuntimeEnvironment.getApplication()) }
  private fun row(path: String = "/cards/a1/1/bulbasaur/", qty: Any = 2) =
    JSONObject().put("cardPath", path).put("quantity", qty).put("name", "Name is not an ID")
  private fun json(vararg rows: JSONObject) = JSONObject().put("schemaVersion", 1)
    .put("source", "pokemon-zone").put("friendId", "0000000000000001")
    .put("url", "https://www.pokemon-zone.com/players/0000000000000001/cards/")
    .put("readAt", "2026-10-08T00:00:00Z").put("collectionComplete", false)
    .put("visibleCards", JSONArray(rows.toList()))
  private fun reject(root: JSONObject) {
    assertTrue(runCatching { ZoneCollectionImport.parse(root.toString()) }.isFailure)
  }

  @Test fun parsesActualSchemaAndUsesCatalogMetadata() {
    val plan = ZoneCollectionImport.parse("\uFEFF" + json(row()).toString())
    assertEquals(1, plan.uniqueCards)
    assertEquals(2L, plan.totalCopies)
    assertEquals(1, plan.sets)
    assertFalse(plan.collectionComplete)
    assertEquals("Bulbasaur", plan.cards.single().name)
    assertEquals("♦", plan.cards.single().rarity)
    assertNull(plan.cards.single().wishlist)
  }

  @Test fun preservesPromoSetsAndDistinctArtVariants() {
    val plan = ZoneCollectionImport.parse(json(row("/cards/promo-a/1/potion/", 1),
      row("/cards/a1/227/bulbasaur/", 3), row()).toString())
    assertEquals(setOf("PROMO-A", "A1"), plan.cards.map { it.setCode }.toSet())
    assertEquals(3, plan.uniqueCards)
    assertEquals(6L, plan.totalCopies)
    assertEquals(setOf("001", "227"), plan.cards.filter { it.setCode == "A1" }.map { it.cardNumber }.toSet())
  }

  @Test fun collapsesAliasesWithoutAddingCopies() {
    val plan = ZoneCollectionImport.parse(json(row(), row("/cards/A1/001/other-name/", 2)).toString())
    assertEquals(1, plan.uniqueCards)
    assertEquals(2L, plan.totalCopies)
    assertEquals(1, plan.duplicates)
    reject(json(row(), row("/cards/a1/001/bulbasaur/", 3)))
  }

  @Test fun rejectsInvalidQuantitiesWithoutCoercion() {
    listOf<Any>(-1, 100000, "2", "NaN", 1.5, true, JSONObject.NULL).forEach { reject(json(row(qty = it))) }
    reject(json(row()).apply { getJSONArray("visibleCards").getJSONObject(0).remove("quantity") })
    assertEquals(0L, ZoneCollectionImport.parse(json(row(qty = 0)).toString()).totalCopies)
  }

  @Test fun rejectsUnknownCardsEmptyListsAndNonObjects() {
    reject(json(row("/cards/a1/999999/unknown/")))
    reject(json())
    reject(json(row()).put("visibleCards", JSONArray().put("not a card")))
    reject(json(row()).put("visibleCards", JSONArray().put(row()).put(row("/cards/invalid/1/unknown/"))))
  }

  @Test fun rejectsForeignUrlsWrongPlayerAndUnsupportedVersion() {
    listOf("https://evil.test/players/0000000000000001/cards/",
      "https://www.pokemon-zone.com/players/0000000000000002/cards/",
      "https://www.pokemon-zone.com/players/0000000000000001/",
      "http://www.pokemon-zone.com/players/0000000000000001/cards/").forEach { reject(json(row()).put("url", it)) }
    reject(json(row()).put("schemaVersion", 2))
    reject(json(row()).put("schemaVersion", "1"))
    reject(json(row()).put("source", "other"))
    reject(json(row()).put("readAt", "bad date"))
    reject(json(row()).put("collectionComplete", "false"))
    reject(json(row()).put("friendId", 1))
  }

  @Test fun rejectsOversizeTrailingContentAndInvalidPaths() {
    assertTrue(runCatching { ZoneCollectionImport.parse(" ".repeat(ZoneCollectionImport.MAX_BYTES + 1)) }.isFailure)
    assertTrue(runCatching { ZoneCollectionImport.parse(json(row()).toString() + "{}") }.isFailure)
    listOf("/cards/a1/0/bulbasaur/", "/cards/a1/1/", "https://www.pokemon-zone.com/cards/a1/1/bulbasaur/",
      "/cards/a1/1/bulbasaur/?q=1").forEach { reject(json(row(it))) }
  }

  @Test fun repeatImportReplacesQuantitiesAndPreservesFlagsAbsentCardsAndDecks() = runBlocking {
    val db = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), AppDatabase::class.java).build()
    try {
      val repo = InventoryRepository.fromDatabase(db)
      repo.setQuantity("A1-001", 8)
      repo.toggleWishlist("A1-001")
      repo.setQuantity("A1-002", 4)
      val before = db.inventoryDao().getCardById("A1-001")!!
      val deck = repo.saveDeck(SavedDeckEntity(name = "Untouched", archetype = "A1", strategy = "", cardListSerialized = "A1-001:2"))
      val plan = ZoneCollectionImport.parse(json(row()).toString())
      repeat(2) { ZoneCollectionImport.apply(db, plan) }
      val inventory = db.inventoryDao().getCardById("A1-001")!!
      assertEquals(2, inventory.quantity)
      assertTrue(inventory.isWishlist)
      assertEquals(before.acquisitionDate, inventory.acquisitionDate)
      assertEquals(2, db.userCardDao().getUserCardById("A1-001")!!.quantity)
      assertTrue(db.userCardDao().getUserCardById("A1-001")!!.isFavorite)
      assertEquals(4, db.inventoryDao().getCardById("A1-002")!!.quantity)
      assertEquals("Untouched", db.savedDeckDao().getDeckById(deck)!!.name)
      ZoneCollectionImport.apply(db, ZoneCollectionImport.parse(json(row(qty = 0)).toString()))
      assertEquals(0, db.inventoryDao().getCardById("A1-001")!!.quantity)
      assertFalse(db.userCardDao().getUserCardById("A1-001")!!.isRegistered)
      assertTrue(db.inventoryDao().getCardById("A1-001")!!.isWishlist)
    } finally { db.close() }
  }

  @Test fun secondTableFailureRollsBackEveryQuantity() = runBlocking {
    val db = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), AppDatabase::class.java).build()
    try {
      val repo = InventoryRepository.fromDatabase(db)
      repo.setQuantity("A1-001", 8)
      repo.setQuantity("A1-002", 9)
      db.openHelper.writableDatabase.execSQL("CREATE TRIGGER fail_zone_test BEFORE INSERT ON user_cards WHEN NEW.cardId = 'A1-002' BEGIN SELECT RAISE(ABORT, 'test failure'); END")
      val plan = ZoneCollectionImport.parse(json(row(), row("/cards/a1/2/ivysaur/", 3)).toString())
      assertTrue(runCatching { ZoneCollectionImport.apply(db, plan) }.isFailure)
      assertEquals(8, db.inventoryDao().getCardById("A1-001")!!.quantity)
      assertEquals(9, db.inventoryDao().getCardById("A1-002")!!.quantity)
      assertEquals(8, db.userCardDao().getUserCardById("A1-001")!!.quantity)
      assertEquals(9, db.userCardDao().getUserCardById("A1-002")!!.quantity)
    } finally { db.close() }
  }

  @Test fun fullSizeSyntheticCollectionMatchesBothTablesAndVisibleInventory() = runBlocking {
    // Public catalog + synthetic quantities; no user's collection is committed to the repository.
    val rows = CardCatalog.ALL_CARDS.take(1328).mapIndexed { index, card ->
      val (set, number) = CardId.split(card.id)
      row("/cards/${set.lowercase()}/${number.toInt()}/public-card/", if (index < 1086) 2 else 1)
    }
    val plan = ZoneCollectionImport.parse(json(*rows.toTypedArray()).toString())
    assertEquals(1328, plan.uniqueCards)
    assertEquals(2414L, plan.totalCopies)
    val db = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), AppDatabase::class.java).build()
    try {
      repeat(2) { ZoneCollectionImport.apply(db, plan) }
      val stored = db.inventoryDao().getAllCards()
      val users = db.userCardDao().getAllUserCardsFlow().first()
      assertEquals(1328, stored.size)
      assertEquals(2414, stored.sumOf { it.quantity })
      assertEquals(stored.associate { it.cardId to it.quantity }, users.associate { it.cardId to it.quantity })
      val visible = InventoryRepository.fromDatabase(db).inventoryFlow.first().filter { it.ownedCount > 0 }
      assertEquals(stored.associate { it.cardId to it.quantity }, visible.associate { it.card.id to it.ownedCount })
    } finally { db.close() }
  }
}
