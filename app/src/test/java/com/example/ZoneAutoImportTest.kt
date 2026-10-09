package com.example

import androidx.room.Room
import com.example.data.local.AppDatabase
import com.example.data.repository.CardCatalog
import com.example.zoneimport.ZoneAutoImport
import com.example.zoneimport.ZoneCollectionImport
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
class ZoneAutoImportTest {
  private val player = "0000000000000001"
  @Before fun catalog() { CardCatalog.loadBundled(RuntimeEnvironment.getApplication()) }
  private fun payload() = JSONObject().put("schemaVersion", 1).put("source", "pokemon-zone")
    .put("friendId", player).put("url", "https://www.pokemon-zone.com/players/$player/cards/")
    .put("readAt", "2026-10-08T00:00:00Z").put("collectionComplete", false)
    .put("syncConfirmed", true).put("scanCompleted", true)
    .put("visibleCards", JSONArray().put(JSONObject().put("cardPath", "/cards/a1/1/bulbasaur/").put("quantity", 2)))

  @Test fun onlyCompletedConfirmedSyncCanProduceAutomaticPlan() {
    val plan = ZoneAutoImport.prepare(payload().toString(), player)
    assertEquals(1, plan.uniqueCards)
    assertEquals(2L, plan.totalCopies)
    assertFalse(plan.collectionComplete) // Native completion is not a Nintendo completeness claim.
    for (key in listOf("syncConfirmed", "scanCompleted")) {
      for (value in listOf<Any>(false, "true", 1, JSONObject.NULL)) {
        assertTrue(runCatching { ZoneAutoImport.prepare(payload().put(key, value).toString(), player) }.isFailure)
      }
      assertTrue(runCatching { ZoneAutoImport.prepare(payload().apply { remove(key) }.toString(), player) }.isFailure)
    }
  }

  @Test fun staleOtherPlayerResultIsRejectedBeforeWriting() {
    assertTrue(runCatching { ZoneAutoImport.prepare(payload().toString(), "0000000000000002") }.isFailure)
  }

  @Test fun emptyOrMalformedFinishedResultCannotOverwriteCollection() {
    assertTrue(runCatching { ZoneAutoImport.prepare(payload().put("visibleCards", JSONArray()).toString(), player) }.isFailure)
    assertTrue(runCatching { ZoneAutoImport.prepare(payload().put("visibleCards", JSONArray()
      .put(JSONObject().put("cardPath", "/cards/a1/1/bulbasaur/").put("quantity", "NaN"))).toString(), player) }.isFailure)
  }

  @Test fun nativeResultUsesTheSameAtomicVerifiedIdempotentImporter() = runBlocking {
    val db = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), AppDatabase::class.java).build()
    try {
      val plan = ZoneAutoImport.prepare(payload().toString(), player)
      repeat(2) { ZoneCollectionImport.apply(db, plan) }
      assertEquals(2, db.inventoryDao().getCardById("A1-001")!!.quantity)
      assertEquals(2, db.userCardDao().getUserCardById("A1-001")!!.quantity)
      for (key in listOf("syncConfirmed", "scanCompleted")) {
        assertTrue(runCatching { ZoneAutoImport.prepare(payload().put(key, false).toString(), player) }.isFailure)
        assertEquals(2, db.inventoryDao().getCardById("A1-001")!!.quantity)
      }
    } finally { db.close() }
  }
  @Test fun synchronizationNewsCountsOnlyPositiveChangesAndReimportProducesZero() {
    val plan = ZoneAutoImport.prepare(payload().toString(),player)
    assertEquals(1 to 2L,ZoneAutoImport.additions(plan,emptyMap()))
    assertEquals(0 to 1L,ZoneAutoImport.additions(plan,mapOf("A1-001" to 1)))
    assertEquals(0 to 0L,ZoneAutoImport.additions(plan,mapOf("A1-001" to 2)))
    assertEquals(0 to 0L,ZoneAutoImport.additions(plan,mapOf("A1-001" to 5,"A1-033" to 10)))
  }

}
