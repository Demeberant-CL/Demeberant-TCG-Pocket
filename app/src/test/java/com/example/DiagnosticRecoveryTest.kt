package com.example

import com.example.data.ai.*
import com.example.data.network.MissingResourceInterceptor
import com.example.data.util.DiagnosticSummary
import com.example.data.util.ErrorLogManager
import okhttp3.*
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test

class DiagnosticRecoveryTest {
  @Test fun repeatedMissingResourcesDoNotHitNetworkUntilExpiryOrManualRetry() {
    var time = 0L
    var calls = 0
    val cache = MissingResourceInterceptor { time }
    val client = OkHttpClient.Builder().addInterceptor(cache).addInterceptor { chain ->
      calls++
      Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(404)
        .message("Missing").body("".toResponseBody(null)).build()
    }.build()
    val url = "https://assets.tcgdex.net/es/tcgp/A4b/196/low.webp"
    fun get() = client.newCall(Request.Builder().url(url).build()).execute().use { it.code }
    assertEquals(404, get()); assertEquals(404, get()); assertEquals(1, calls)
    time = 3_600_000L
    assertEquals(404, get()); assertEquals(2, calls)
    cache.reset(listOf(url))
    assertEquals(404, get()); assertEquals(3, calls)
  }
  @Test fun transientFailureAndOtherHostsAreNeverNegativeCached() {
    var calls = 0
    val client = OkHttpClient.Builder().addInterceptor(MissingResourceInterceptor { 0L }).addInterceptor { chain ->
      calls++
      Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
        .code(if (chain.request().url.host == "assets.tcgdex.net") 503 else 404)
        .message("Failure").body("".toResponseBody(null)).build()
    }.build()
    listOf("https://assets.tcgdex.net/x", "https://other.example/x").forEach { url ->
      repeat(2) { client.newCall(Request.Builder().url(url).build()).execute().close() }
    }
    assertEquals(4, calls)
  }
  @Test fun aiStatusCategoriesDistinguishServiceQuotaModelAndValidation() {
    assertEquals("SERVICE", AiFailure.category(AiHttpFailure(503, "safe")))
    assertEquals("QUOTA", AiFailure.category(AiHttpFailure(429, "safe")))
    assertEquals("MODEL", AiFailure.category(AiHttpFailure(404, "safe")))
    assertEquals("AUTH", AiFailure.category(AiHttpFailure(403, "safe")))
    assertEquals("TIMEOUT", AiFailure.category(java.net.SocketTimeoutException("PRIVATE")))
    assertEquals("INVALID_DECK", AiFailure.category(IllegalArgumentException("PRIVATE"), "VALIDATE"))
  }
  @Test fun summaryIncludesChatCategoryAndTraceWithoutPrivateContent() {
    val report = DiagnosticSummary.create(sequenceOf(
      "[2026-10-10T15:00:00Z] [DECK_CHAT] id=123 stage=REQUEST category=SERVICE status=503 PRIVATE_BODY",
      "IllegalStateException", "  at com.example.data.ai.ConnectedAiRepository.request(ConnectedAiRepository.kt:90)",
      "[2026-10-10T15:00:01Z] [HTTP_END] GET assets.tcgdex.net/private status=404",
      "[2026-10-10T15:00:02Z] [UI_STALL] screen=DECKS blockedMs=9000",
      "  at com.example.MainActivity.onCreate(MainActivity.kt:50)"
    ), "1.0.1", 34)
    assertTrue(report.contains("SERVICE · HTTP 503")); assertTrue(report.contains("DECK_CHAT"))
    assertTrue(report.contains("Imágenes")); assertTrue(report.contains("UI_STALL"))
    assertTrue(report.contains("ConnectedAiRepository.kt:90")); assertFalse(report.contains("PRIVATE"))
    assertFalse(report.contains("assets.tcgdex.net"))
  }
  @Test fun nestedCauseTraceKeepsFramesButNeverExceptionMessages() {
    val cause = java.io.IOException("PRIVATE_KEY")
    val outer = IllegalStateException("PRIVATE_QUERY", cause)
    val trace = ErrorLogManager.safeTrace(outer)
    assertTrue(trace.contains("Caused by: IOException")); assertTrue(trace.contains("DiagnosticRecoveryTest"))
    assertFalse(trace.contains("PRIVATE"))
  }
  @Test fun androidExitOnlyCountsCrashAndAnrReasons() {
    val report = DiagnosticSummary.create(sequenceOf(
      "[2026-10-10T15:00:00Z] [PROCESS_EXIT] reason=10 status=0",
      "[2026-10-10T15:00:01Z] [PROCESS_EXIT] reason=6 status=0"
    ), "1.0.1", 34)
    assertTrue(report.contains("fallos registrados: 1"))
    assertTrue(report.contains("Cierre registrado por Android"))
  }
}
