package com.example

import com.example.data.meta.*
import kotlinx.coroutines.*
import okhttp3.*
import okhttp3.mockwebserver.*
import org.json.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File
import java.time.Instant
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TournamentNetworkTest {
  private val context get() = RuntimeEnvironment.getApplication()
  private val base get() = File(context.filesDir, "pocket-meta.json")
  private val saved = MetaSnapshot("2026-10-04T12:00:00Z", 1, 1,
    listOf(MetaDeck("Guardado", 1, 2, 1, 0, mapOf("A1-001" to 2), listOf("Planta"), "saved")), 0)

  private fun withCache(test: () -> Unit) {
    base.delete(); File(base.path + ".bak").delete()
    base.writeText(TournamentRepository.encode(saved).toString())
    try { test() } finally { base.delete(); File(base.path + ".bak").delete() }
  }

  private fun client(respond: (Request) -> Pair<Int, String>): OkHttpClient =
    OkHttpClient.Builder().addInterceptor { chain ->
      val (status, body) = respond(chain.request())
      Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
        .code(status).message("Fixture").body(ResponseBody.create(null, body)).build()
    }.build()

  private fun tournament(id: String, game: String = "POCKET") = JSONObject()
    .put("id", id).put("game", game).put("date", Instant.now().minusSeconds(60).toString())

  @Test fun successfulSampleReportsProgressAndRejectsAnotherGame() = withCache {
    runBlocking {
      val requested = mutableListOf<String>()
      val progress = mutableListOf<Pair<Int, Int>>()
      val entries = JSONArray((1..10).map { JSONObject().put("set", "A1").put("number", it).put("count", 2) })
      val row = JSONObject().put("placing", 1).put("decklist", JSONObject()
        .put("pokemon", entries).put("energy", JSONArray().put("Grass")))
        .put("record", JSONObject().put("wins", 3).put("losses", 1)).put("deck", JSONObject().put("name", "Ejemplo"))
      val http = client { request ->
        requested += request.url.encodedPath
        200 to when (request.url.encodedPath) {
          "/api/tournaments" -> JSONArray().put(tournament("foreign", "PTCG")).put(tournament("valid")).toString()
          "/api/tournaments/valid/details" -> JSONObject().put("id", "valid").put("game", "POCKET")
            .put("isPublic", true).put("decklists", true).toString()
          "/api/tournaments/valid/standings" -> JSONArray().put(row).toString()
          else -> error("Unexpected request")
        }
      }
      val repo = TournamentRepository(context, http)
      val result = repo.refresh { current, total -> progress += current to total }
      assertEquals(listOf(1 to 2, 2 to 2), progress)
      assertEquals(1, result.tournaments); assertEquals(1, result.players)
      assertEquals(1, result.skipped); assertEquals(20, result.decks.single().cards.values.sum())
      assertFalse(requested.any { "foreign" in it })
      assertEquals(result, TournamentRepository(context).cached())
    }
  }

  @Test fun unavailableSourcePreservesSavedSample() = withCache {
    runBlocking {
      val repo = TournamentRepository(context, client { 503 to "private response" })
      val original = base.readBytes()
      val failure = runCatching { repo.refresh() }.exceptionOrNull()
      assertNotNull(failure)
      assertFalse(failure!!.message.orEmpty().contains("private response"))
      assertArrayEquals(original, base.readBytes()); assertEquals(saved, repo.cached())
    }
  }

  @Test fun refreshInspectsAtMostTwelveTournamentsAndChecksDetailsIdentity() = withCache {
    runBlocking {
      var details = 0
      val http = client { request ->
        if (request.url.encodedPath == "/api/tournaments") {
          200 to JSONArray((1..13).map { tournament("t$it") }).toString()
        } else {
          assertTrue(request.url.encodedPath.endsWith("/details"))
          details++
          200 to JSONObject().put("id", "different").put("game", "POCKET")
            .put("isPublic", true).put("decklists", true).toString()
        }
      }
      val repo = TournamentRepository(context, http)
      assertTrue(runCatching { repo.refresh() }.isFailure)
      assertEquals(12, details); assertEquals(saved, repo.cached())
    }
  }

  @Test fun stalledDownloadTimesOutAndExplicitCancellationPropagatesWithoutChangingCache() = withCache {
    val server = MockWebServer()
    server.start()
    try {
      val http = OkHttpClient.Builder().addInterceptor { chain ->
        chain.proceed(chain.request().newBuilder().url(server.url(chain.request().url.encodedPath)).build())
      }.callTimeout(2, TimeUnit.SECONDS).build()
      server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
      runBlocking {
        val repo = TournamentRepository(context, http, refreshTimeoutMillis = 100)
        val failure = runCatching { repo.refresh() }.exceptionOrNull()
        assertTrue(failure is java.io.IOException)
        assertEquals(saved, repo.cached())
      }
      assertNotNull(server.takeRequest(2, TimeUnit.SECONDS))
      server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
      runBlocking {
        val repo = TournamentRepository(context, http)
        val pending = async { repo.refresh() }
        withContext(Dispatchers.IO) { assertNotNull(server.takeRequest(2, TimeUnit.SECONDS)) }
        pending.cancelAndJoin()
        assertTrue(pending.isCancelled); assertEquals(saved, repo.cached())
      }
    } finally { server.shutdown() }
  }
}
