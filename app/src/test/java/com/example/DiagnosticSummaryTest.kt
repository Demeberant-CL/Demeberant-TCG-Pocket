package com.example

import com.example.data.util.DiagnosticSummary
import com.example.data.util.ErrorLogManager
import com.example.domain.AiValidator
import com.example.domain.DeckAutomation
import com.example.data.repository.*
import com.example.data.model.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DiagnosticSummaryTest {
  @Test fun sqlFloodDoesNotHideQrFailureOrCopyPrivateMessages() {
    val lines = sequence {
      repeat(10000) { yield("[2026-10-03T00:30:00Z] [ROOM_QUERY] DROP PRIVATE_COLLECTION") }
      yield("[2026-10-03T00:34:53Z] [QR_EXPORT] PRIVATE_AI_QUERY token=PRIVATE_SECRET")
      yield("IllegalArgumentException")
      yield("  at com.example.domain.PocketDeckQr.resolve(PocketDeckQr.kt:40)")
      yield("[2026-10-03T00:35:00Z] [HTTP_END] GET private.host/path?key=PRIVATE_SECRET status=429")
    }
    val report = DiagnosticSummary.create(lines, "1.0.1057", 34)
    assertTrue(report.contains("10000")); assertTrue(report.contains("QR_EXPORT"))
    assertTrue(report.contains("PocketDeckQr.kt:40")); assertTrue(report.contains("HTTP 429"))
    assertFalse(report.contains("PRIVATE_")); assertFalse(report.contains("private.host"))
    assertTrue(report.length <= DiagnosticSummary.MAX_CHARS)
  }
  @Test fun repeatedFailuresAreGroupedAndLatestDistinctErrorsRemainBounded() {
    val lines = sequence {
      repeat(200) { i ->
        yield("[2026-10-03T00:34:53Z] [QR_EXPORT] Could not create")
        yield("IllegalArgumentException")
        yield("  at com.example.domain.PocketDeckQr.resolve(PocketDeckQr.kt:${i + 1})")
      }
      repeat(3) {
        yield("[2026-10-03T00:35:00Z] [HTTP_FAIL] SECRET_BODY")
        yield("StreamResetException")
      }
    }
    val report = DiagnosticSummary.create(lines, "1.0.1057", 34, 10)
    assertTrue(report.contains("3 vez/veces")); assertTrue(report.contains("203"))
    assertTrue(report.contains("PocketDeckQr.kt:200")); assertFalse(report.contains("PocketDeckQr.kt:1)"))
    assertTrue(report.contains("saturación")); assertTrue(report.length <= 6000)
  }
  @Test fun noRecognizedFailureIsNotPresentedAsProofOfSuccess() {
    val report = DiagnosticSummary.create(emptySequence(), "1.0.1", 34)
    assertTrue(report.contains("sin registros")); assertTrue(report.contains("no confirma todos los flujos"))
    assertTrue(report.contains("juego no queda registrado"))
  }
  @Test fun redactionCoversGeminiKeysAndQuotedJsonCredentials() {
    val key = "AIza" + "a".repeat(35)
    val text = ErrorLogManager.redact("key=$key {\"token\":\"PRIVATE_TOKEN\",\"password\":\"PRIVATE_PASSWORD\"} Bearer PRIVATE_BEARER")
    assertFalse(text.contains(key)); assertFalse(text.contains("PRIVATE_"))
  }
  @Test fun briefReportCanBeSavedAsUtf8AndOmitsPrivateExceptionText() = runBlocking {
    val context = RuntimeEnvironment.getApplication()
    ErrorLogManager.init(context)
    ErrorLogManager.clearLogs(context)
    ErrorLogManager.event("QR_EXPORT", "PRIVATE_BODY", IllegalArgumentException("SECRET_QUERY"))
    val report = ErrorLogManager.briefReport(context)
    assertTrue(report.contains("QR_EXPORT")); assertFalse(report.contains("PRIVATE_BODY")); assertFalse(report.contains("SECRET_QUERY"))
    val file = File(context.cacheDir, "brief-diagnostic.txt")
    ErrorLogManager.saveBriefReport(context, android.net.Uri.fromFile(file), report)
    assertEquals(report, file.readText(Charsets.UTF_8))
    file.delete()
  }
  @Test fun usingProposalRechecksInventoryAfterTheResponseAndDoesNotMutateIt() {
    val pool = (1..10).map { i -> CardWithInventory(PokemonCard("A1-${i.toString().padStart(3,'0')}", "Basic$i",
      BoosterPack.UNKNOWN, CardRarity.ONE_DIAMOND, 70, "Planta", "", "", category="pokemon",stage="basic"),2,false) }
    val deck = DeckAutomation.build(pool, "Planta")
    val current = pool.associate { it.card.id to it.ownedCount }.toMutableMap()
    AiValidator.requireAvailable(deck, current)
    current[deck.cards.first().card.id] = 0
    val before = current.toMap()
    assertTrue(runCatching { AiValidator.requireAvailable(deck,current) }.isFailure)
    assertEquals(before,current)
  }
}
