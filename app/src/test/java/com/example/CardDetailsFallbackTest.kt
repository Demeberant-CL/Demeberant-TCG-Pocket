package com.example

import com.example.data.api.CardDetailsClient
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CardDetailsFallbackTest {
  private val context get() = RuntimeEnvironment.getApplication()
  private val cache get() = File(context.cacheDir, "details-es-A1-001.json")
  private val body = """{"id":"A1-001","hp":70,"category":"Pokemon","types":["Grass"],"stage":"Basic","attacks":[{"name":"Vine Whip","damage":40}]}"""
  @Before fun clearCache() { cache.delete() }

  @Test fun missingSpanishUsesEnglishAndReusesCacheWithoutAnotherRequest() {
    val server = MockWebServer()
    server.start()
    try {
      server.enqueue(MockResponse().setResponseCode(404))
      server.enqueue(MockResponse().setBody(body))
      val client = OkHttpClient()
      val result = CardDetailsClient.load(context, "A1-001", "es", client, server.url("/v2/").toString())
      assertEquals(70, result.hp)
      assertTrue(result.source.contains("inglés"))
      assertEquals("/v2/es/cards/A1-001", server.takeRequest().path)
      assertEquals("/v2/en/cards/A1-001", server.takeRequest().path)
      val cached = CardDetailsClient.load(context, "A1-001", "es", client, server.url("/v2/").toString())
      assertTrue(cached.source.contains("copia guardada"))
      assertTrue(cached.source.contains("inglés"))
      assertEquals(2, server.requestCount)
    } finally { server.shutdown(); cache.delete() }
  }

  @Test fun serviceFailureUsesExpiredCacheAndDoesNotRequestEnglish() {
    cache.writeText(body)
    assertTrue(cache.setLastModified(System.currentTimeMillis() - 172_800_000L))
    val server = MockWebServer()
    server.start()
    try {
      server.enqueue(MockResponse().setResponseCode(503))
      val result = CardDetailsClient.load(context, "A1-001", "es", OkHttpClient(), server.url("/v2/").toString())
      assertEquals(70, result.hp)
      assertTrue(result.source.contains("copia guardada"))
      assertEquals(1, server.requestCount)
    } finally { server.shutdown(); cache.delete() }
  }

  @Test fun mismatchedIdentityCannotBeSavedAsAnotherCard() {
    val server = MockWebServer()
    server.start()
    try {
      server.enqueue(MockResponse().setBody(body.replace("A1-001", "A1-002")))
      assertTrue(runCatching { CardDetailsClient.load(context, "A1-001", "es", OkHttpClient(), server.url("/v2/").toString()) }.isFailure)
      assertFalse(cache.exists())
      assertEquals(1, server.requestCount)
    } finally { server.shutdown(); cache.delete() }
  }
}
