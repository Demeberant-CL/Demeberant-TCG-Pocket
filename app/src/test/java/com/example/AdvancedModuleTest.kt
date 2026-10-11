package com.example

import androidx.room.Room
import com.example.data.local.*
import com.example.data.model.*
import com.example.data.repository.*
import com.example.data.network.*
import com.example.data.util.ErrorLogManager
import com.example.domain.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.mockwebserver.*
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.util.concurrent.TimeUnit
import kotlin.random.Random

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AdvancedModuleTest {
  private fun candidates(): List<AiCandidate> = (1..10).map { i ->
    AiCandidate(PokemonCard("A1-${i.toString().padStart(3, '0')}", "Basic$i", BoosterPack.UNKNOWN,
      CardRarity.ONE_DIAMOND, 70, "Planta", "", "", category = "pokemon", stage = "basic"), 2, "Heal 10 damage.")
  }
  private fun proposal(cards: List<AiCandidate> = candidates()): JSONObject = JSONObject()
    .put("name", "Propuesta").put("strategy", "Estrategia").put("energies", JSONArray(listOf("Planta")))
    .put("cards", JSONArray(cards.map { JSONObject().put("id", it.card.id).put("count", 2) }))
    .put("replacements", JSONArray())

  @Test fun classifierRecognizesSpanishEnglishAndDoesNotMistakeOwnDiscardForMilling() {
    assertTrue(CardRole.HEAL in RoleClassifier.classify("Cura 20 puntos de daño."))
    assertTrue(CardRole.DRAW in RoleClassifier.classify("Draw 2 cards."))
    assertTrue(CardRole.ENERGY in RoleClassifier.classify("Une una energía de tu zona de energía."))
    assertTrue(CardRole.MILL in RoleClassifier.classify("Descarta la primera carta del mazo de tu rival."))
    assertFalse(CardRole.MILL in RoleClassifier.classify("Discard a card from your hand."))
  }

  @Test fun rulesSqlFiltersUseHpTypeKeywordsAndRoles() = runBlocking {
    val db = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), AppDatabase::class.java).build()
    try {
      db.cardRulesDao().upsert(CardRulesEntity("A1-001", "es", 70, "Grass", "Cura 20 daño", "cura 20 dano", "|heal|", 100))
      db.cardRulesDao().upsert(CardRulesEntity("A1-002", "es", 90, "Grass", "Roba 2 cartas", "roba 2 cartas", "|draw|", 100))
      assertEquals("A1-001", db.cardRulesDao().filter("es", 60, 80, "Grass", "heal", "cura").first().single().cardId)
      assertTrue(db.cardRulesDao().filter("es", null, null, "", "mill", "").first().isEmpty())
      assertTrue(db.cardRulesDao().filter("en", null, null, "", "", "").first().isEmpty())
    } finally { db.close() }
  }

  @Test fun migration4To5RetainsOldCollectionAndDecksAndCreatesRulesCache() = runBlocking {
    val context = RuntimeEnvironment.getApplication()
    val name = "advanced-migration.db"; context.deleteDatabase(name)
    val schema = JSONObject(javaClass.getResourceAsStream("/room-v4.json")!!.bufferedReader().use { it.readText() }).getJSONObject("database")
    android.database.sqlite.SQLiteDatabase.openOrCreateDatabase(context.getDatabasePath(name).apply { parentFile?.mkdirs() }, null).use { raw ->
      val tables = schema.getJSONArray("entities")
      repeat(tables.length()) { i ->
        val entity = tables.getJSONObject(i)
        raw.execSQL(entity.getString("createSql").replace("$" + "{TABLE_NAME}", entity.getString("tableName")))
      }
      raw.execSQL("INSERT INTO inventory_cards(cardId,cardName,packName,rarity,quantity,isWishlist,acquisitionDate) VALUES('A1-001','Bulbasaur','','♦',3,1,100)")
      raw.execSQL("INSERT INTO saved_decks(id,name,archetype,strategy,cardListSerialized,totalCards,createdAt) VALUES(1,'Anterior','Manual','','A1-001:2',2,100)")
      val setup = schema.getJSONArray("setupQueries")
      repeat(setup.length()) { raw.execSQL(setup.getString(it)) }
      raw.version = 4
    }
    val db = Room.databaseBuilder(context, AppDatabase::class.java, name).addMigrations(AppDatabase.MIGRATION_4_5).build()
    try {
      assertEquals(3, db.inventoryDao().getAllCards().single().quantity)
      assertTrue(db.inventoryDao().getAllCards().single().isWishlist)
      assertEquals("Anterior", db.savedDeckDao().getDeckById(1)!!.name)
      db.cardRulesDao().upsert(CardRulesEntity("A1-001", "es", 70, "Grass", "", "", "|", 100))
      assertEquals(70, db.cardRulesDao().get("A1-001", "es")!!.hp)
    } finally { db.close(); context.deleteDatabase(name) }
  }

  @Test fun sandboxOpeningConservesTwentyUniqueInstancesAndIncludesBasic() {
    val entries = candidates().map { DeckCardEntry(it.card, 2) }
    val board = SandboxEngine.start(entries, Random(42))
    assertEquals(5, board.hand.size); assertEquals(15, board.drawPile.size)
    assertTrue(board.hand.any { it.basic })
    assertEquals(20, (board.hand + board.drawPile).map { it.instanceId }.distinct().size)
    assertEquals(board, SandboxEngine.start(entries, Random(42)))
  }

  @Test fun sandboxLimitsBenchEnergyAndDoesNotLoseCardsDuringMoves() {
    var board = SandboxEngine.start(candidates().map { DeckCardEntry(it.card, 2) }, Random(1))
    val active = board.hand.first()
    board = SandboxEngine.move(board, active.instanceId, BoardZone.ACTIVE)
    repeat(3) { board = SandboxEngine.move(board, board.hand.first().instanceId, BoardZone.BENCH) }
    assertTrue(runCatching { SandboxEngine.move(board, board.hand.first().instanceId, BoardZone.BENCH) }.isFailure)
    board = SandboxEngine.attachEnergy(board, active.instanceId)
    assertTrue(runCatching { SandboxEngine.attachEnergy(board, active.instanceId) }.isFailure)
    board = SandboxEngine.nextTurn(board)
    board = SandboxEngine.attachEnergy(board, active.instanceId)
    assertEquals(2, board.active!!.energies)
    board = SandboxEngine.move(board, active.instanceId, BoardZone.DISCARD)
    assertEquals(0, board.discard.single().energies)
    assertEquals(20, (board.drawPile + board.hand + board.bench + board.discard).size)
  }

  @Test fun sandboxUndoRestoresBoardAndNeverWritesCollection() = runBlocking {
    val context = RuntimeEnvironment.getApplication()
    val repository = InventoryRepository.fromDatabase(AppDatabase.getDatabase(context))
    val before = repository.inventoryFlow.first()
    val viewModel = com.example.ui.viewmodel.AdvancedViewModel(context)
    val initial = SandboxEngine.start(candidates().map { DeckCardEntry(it.card, 2) }, Random(17))
    viewModel.board.value = initial
    viewModel.updateBoard { SandboxEngine.move(it, it.hand.first().instanceId, BoardZone.ACTIVE) }
    assertNotEquals(initial, viewModel.board.value)
    viewModel.undoMove()
    assertEquals(initial, viewModel.board.value)
    viewModel.undoMove()
    assertEquals(initial, viewModel.board.value)
    assertEquals(before, repository.inventoryFlow.first())
  }

  @Test fun sandboxRejectsIncompleteDeckWithoutTouchingSourceDeck() {
    val entries = candidates().take(1).map { DeckCardEntry(it.card, 2) }
    assertTrue(runCatching { SandboxEngine.start(entries) }.isFailure)
    assertEquals(2, entries.single().count)
  }

  @Test fun aiValidatorAcceptsOnlyLegalInventoryBoundDecks() {
    val good = AiValidator.parse(proposal().toString(), candidates())
    assertEquals(20, good.deck.totalCardCount)
    val bad = proposal()
    bad.getJSONArray("cards").getJSONObject(0).put("id", "B4B-999")
    assertTrue(runCatching { AiValidator.parse(bad.toString(), candidates()) }.isFailure)
    assertTrue(runCatching { AiValidator.parse(proposal().toString(), candidates().map { it.copy(owned = 1) }) }.isFailure)
  }

  @Test fun aiValidatorRejectsDuplicateNamesFractionalCountsAndMissingBasic() {
    val duplicateNames = candidates().map { it.copy(card = it.card.copy(name = "Same", rulesName = "Same")) }
    assertTrue(runCatching { AiValidator.parse(proposal().toString(), duplicateNames) }.isFailure)
    val fractional = proposal(); fractional.getJSONArray("cards").getJSONObject(0).put("count", 1.5)
    assertTrue(runCatching { AiValidator.parse(fractional.toString(), candidates()) }.isFailure)
    val evolved = candidates().map { it.copy(card = it.card.copy(stage = "1", evolvesFrom = "Missing")) }
    assertTrue(runCatching { AiValidator.parse(proposal().toString(), evolved) }.isFailure)
  }

  @Test fun replacementValidationRequiresExactlyTheMissingChanges() {
    val all = candidates()
    val target = all.map { DeckCardEntry(it.card, 2) }
    val replacement = AiCandidate(all[0].card.copy(id = "B4B-101", name = "Replacement", rulesName = "Replacement"), 2, "")
    val owned = all.mapIndexed { i, c -> if (i == 0) c.copy(owned = 0) else c } + replacement
    val output = proposal(all.drop(1) + replacement)
    output.put("replacements", JSONArray().put(JSONObject().put("removedId", all[0].card.id)
      .put("addedId", replacement.card.id).put("count", 2).put("reason", "Mismo tipo")))
    assertEquals(1, AiValidator.parse(output.toString(), owned, target).replacements.size)
    output.put("replacements", JSONArray())
    assertTrue(runCatching { AiValidator.parse(output.toString(), owned, target) }.isFailure)
  }

  @Test fun missingReplacementCountIsRejectedWithoutGuessingAndExplained() {
    val output = proposal()
    output.put("replacements", JSONArray().put(JSONObject().put("removedId", "A1-001")
      .put("addedId", "A1-002").put("reason", "Ejemplo")))
    val error = runCatching { AiValidator.parse(output.toString(), candidates()) }.exceptionOrNull()
    assertTrue(error is IllegalArgumentException)
    assertTrue(error!!.message.orEmpty().contains("omitió la cantidad"))
    assertTrue(error.message.orEmpty().contains("se conserva"))
  }

  @Test fun httpInterceptorNeverLogsCredentialsQueriesOrBodies() {
    val server = MockWebServer(); server.start()
    try {
      val logs = mutableListOf<String>()
      val client = OkHttpClient.Builder().addInterceptor(DiagnosticInterceptor { tag, message, _ -> logs.add("$tag $message") }).build()
      server.enqueue(MockResponse().setBody("PRIVATE_RESPONSE"))
      val request = Request.Builder().url(server.url("/assist?token=QUERY_SECRET")).header("Authorization", "Bearer AUTH_SECRET")
        .post("PRIVATE_BODY".toRequestBodyCompat()).build()
      client.newCall(request).execute().use { assertEquals(200, it.code) }
      val joined = logs.joinToString()
      listOf("QUERY_SECRET", "AUTH_SECRET", "PRIVATE_BODY", "PRIVATE_RESPONSE").forEach { assertFalse(joined.contains(it)) }
      assertTrue(joined.contains("status=200")); assertTrue(joined.contains("ms="))
    } finally { server.shutdown() }
  }

  @Test fun aiRepositorySendsContextAndHandlesServerErrors() = runBlocking {
    val server = MockWebServer(); server.start()
    try {
      val repository = AIAssistantRepository(OkHttpClient(), true)
      server.enqueue(MockResponse().setBody(proposal().toString()).setHeader("Content-Type", "application/json"))
      assertEquals(20, repository.generateDeck(server.url("/assist").toString(), "TOKEN", "Meta con fecha", "Control", candidates()).deck.totalCardCount)
      val request = server.takeRequest(2, TimeUnit.SECONDS)!!
      assertEquals("Bearer TOKEN", request.getHeader("Authorization"))
      assertEquals("generate", JSONObject(request.body.readUtf8()).getString("mode"))
      server.enqueue(MockResponse().setResponseCode(429).setBody("private provider error"))
      val error = runCatching { repository.generateDeck(server.url("/assist").toString(), "TOKEN", "", "", candidates()) }.exceptionOrNull()!!
      assertTrue(error.message!!.contains("429")); assertFalse(error.message!!.contains("private"))
    } finally { server.shutdown() }
  }

  @Test fun cancelledAiCallPropagatesCancellation() = runBlocking {
    val server = MockWebServer(); server.start()
    try {
      server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
      val task = async { AIAssistantRepository(OkHttpClient(), true).generateDeck(server.url("/assist").toString(), "TOKEN", "", "", candidates()) }
      assertNotNull(withContext(Dispatchers.IO) { server.takeRequest(2, TimeUnit.SECONDS) })
      task.cancel()
      assertTrue(runCatching { task.await() }.exceptionOrNull() is CancellationException)
    } finally { server.shutdown() }
  }

  @Test fun logExportRedactsSecretsAndWritesDailyFilesAsynchronously() = runBlocking {
    val context = RuntimeEnvironment.getApplication()
    ErrorLogManager.init(context)
    ErrorLogManager.event("TEST", "Bearer TOKEN_SECRET sk-proj-secret password=abc")
    val logs = ErrorLogManager.readLogs(context)
    assertFalse(logs.contains("TOKEN_SECRET")); assertFalse(logs.contains("sk-proj-secret")); assertFalse(logs.contains("password=abc"))
    assertTrue(logs.contains("REDACTED"))
  }

  @Test fun externalPromptPreservesContextAndReplacementTarget() {
    val cards = candidates()
    val text = ExternalAiExchange.prompt("Fuente y fecha", "Control", cards, cards.map { DeckCardEntry(it.card, 2) })
    val context = JSONObject(text.substringAfter("CONTEXTO:\n"))
    assertEquals(10, context.getJSONArray("cards").length())
    assertEquals(10, context.getJSONArray("target").length())
    assertEquals("Control", context.getString("goal"))
    assertEquals(2, context.getJSONArray("cards").getJSONObject(0).getInt("quantity"))
    assertTrue(runCatching { ExternalAiExchange.prompt("", "", emptyList(), null) }.isFailure)
  }

  @Test fun connectedActionsSeparateCompletionFromImprovementReference() {
    assertTrue(AiDeckAction.CREATE.canUse(null))
    assertFalse(AiDeckAction.COMPLETE.canUse(null))
    assertFalse(AiDeckAction.COMPLETE.canUse(19))
    assertTrue(AiDeckAction.COMPLETE.canUse(20))
    assertFalse(AiDeckAction.IMPROVE.canUse(0))
    assertTrue(AiDeckAction.IMPROVE.canUse(1))
    assertTrue(AiDeckAction.IMPROVE.canUse(20))
    val cards = candidates()
    val reference = cards.map { DeckCardEntry(it.card, 2) }
    val prompt = ExternalAiExchange.prompt("", "Mejorar consistencia", cards, null, reference)
    val context = JSONObject(prompt.substringAfter("CONTEXTO:\n"))
    assertEquals(0, context.getJSONArray("target").length())
    assertEquals(10, context.getJSONArray("reference").length())
    assertTrue(prompt.contains("puedes cambiar cualquier carta"))
    assertEquals(20, AiValidator.parse(proposal().toString(), cards).deck.totalCardCount)
    assertTrue(runCatching { ExternalAiExchange.prompt("", "", cards, reference, reference) }.isFailure)
    assertTrue(runCatching { ExternalAiExchange.prompt("", "", cards, null, emptyList()) }.isFailure)
  }

  @Test fun externalJsonAcceptsSingleCodeBlockButRejectsProseAndInvalidCollection() {
    val raw = proposal().toString()
    assertEquals(raw, ExternalAiExchange.response("  " + raw + "  "))
    assertEquals(20, AiValidator.parse(ExternalAiExchange.response("```json\n" + raw + "\n```"), candidates()).deck.totalCardCount)
    assertTrue(runCatching { AiValidator.parse(ExternalAiExchange.response("Aquí tienes: " + raw), candidates()) }.isFailure)
    assertTrue(runCatching { ExternalAiExchange.response("```json\n" + raw + "\n```\nTexto extra") }.isFailure)
    assertTrue(runCatching { ExternalAiExchange.response("x".repeat(100001)) }.isFailure)
    assertTrue(runCatching { AiValidator.parse(raw, candidates().map { it.copy(owned = 1) }) }.isFailure)
  }

  private fun String.toRequestBodyCompat(): RequestBody =
    this.toRequestBody("text/plain".toMediaType())
}
