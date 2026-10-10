package com.example

import com.example.data.ai.*
import kotlinx.coroutines.runBlocking
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import okhttp3.mockwebserver.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File
import javax.crypto.spec.SecretKeySpec

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ConnectedAiTest {
  @Test fun geminiUsesHeaderJsonModeAndBoundedOutputWithoutQueryKey() = runBlocking {
    var sent: Request? = null
    val client = OkHttpClient.Builder().addInterceptor { chain ->
      sent = chain.request()
      Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK")
        .body("""{"candidates":[{"finishReason":"STOP","content":{"parts":[{"thought":true,"text":"private thinking"},{"text":"{\"name\":\"Deck\"}"}]}}]}""".toResponseBody("application/json".toMediaType())).build()
    }.build()
    val output = ConnectedAiRepository(client).request(AiConnection(apiKey = "test-secret"), "cards-only")
    assertEquals("{\"name\":\"Deck\"}", output)
    assertEquals("test-secret", sent!!.header("x-goog-api-key"))
    assertNull(sent!!.url.query)
    val buffer = okio.Buffer(); sent!!.body!!.writeTo(buffer)
    val json = JSONObject(buffer.readUtf8())
    assertEquals(4096, json.getJSONObject("generationConfig").getInt("maxOutputTokens"))
    assertEquals("application/json", json.getJSONObject("generationConfig").getString("responseMimeType"))
  }
  @Test fun compatibleQuotaDoesNotRetryOrExposeProviderBody() = runBlocking {
    val server = MockWebServer(); server.start()
    try {
      server.enqueue(MockResponse().setResponseCode(429).setBody("SECRET BODY"))
      val config = AiConnection(AiProvider.COMPATIBLE, "model", "test-secret", server.url("/v1/chat/completions").toString())
      try { ConnectedAiRepository(allowLocalTests = true).request(config, "cards-only"); fail() }
      catch (e: IllegalStateException) { assertTrue(e.message!!.startsWith("Cuota")); assertFalse(e.message!!.contains("SECRET")) }
      assertEquals(1, server.requestCount)
      assertEquals("Bearer test-secret", server.takeRequest().getHeader("Authorization"))
    } finally { server.shutdown() }
  }
  @Test fun transientServiceErrorRetriesSameRequestWithoutProviderFallback() = runBlocking {
    val server = MockWebServer(); server.start()
    try {
      server.enqueue(MockResponse().setResponseCode(503).setBody("PRIVATE"))
      server.enqueue(MockResponse().setBody("""{"choices":[{"finish_reason":"stop","message":{"content":"ok"}}]}"""))
      val config = AiConnection(AiProvider.COMPATIBLE, "model", "test", server.url("/v1/chat/completions").toString())
      val statuses = mutableListOf<String>()
      assertEquals("ok", ConnectedAiRepository(allowLocalTests = true, retryWait = {}).request(config, "same context") { statuses.add(it) })
      assertEquals(2, server.requestCount)
      assertEquals(server.takeRequest().body.readUtf8(), server.takeRequest().body.readUtf8())
      assertEquals(1, statuses.size)
    } finally { server.shutdown() }
  }
  @Test fun serviceRetriesAreBoundedAndAuthenticationIsNotRetried() = runBlocking {
    for ((code, count) in listOf(503 to 3, 401 to 1)) {
      val server = MockWebServer(); server.start()
      try {
        repeat(count) { server.enqueue(MockResponse().setResponseCode(code).setBody("PRIVATE")) }
        val config = AiConnection(AiProvider.COMPATIBLE, "model", "test", server.url("/v1/chat/completions").toString())
        assertTrue(runCatching { ConnectedAiRepository(allowLocalTests = true, retryWait = {}).request(config, "cards") }.isFailure)
        assertEquals(count, server.requestCount)
      } finally { server.shutdown() }
    }
  }
  @Test fun rejectsInsecureCredentialUrlsAndOversizedPromptsBeforeNetwork() = runBlocking {
    val repo = ConnectedAiRepository()
    for (url in listOf("http://example.com/chat", "https://user:pass@example.com/chat", "https://example.com/chat?key=secret")) {
      try { repo.request(AiConnection(AiProvider.COMPATIBLE, "model", "test-key", url), "prompt"); fail() }
      catch (_: IllegalArgumentException) { }
    }
    try { repo.request(AiConnection(apiKey = "test-key"), "a".repeat(60001)); fail() }
    catch (_: IllegalArgumentException) { }
  }
  @Test fun incompleteResponsesAreNotImported() = runBlocking {
    val client = OkHttpClient.Builder().addInterceptor { chain ->
      Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK")
        .body("""{"choices":[{"finish_reason":"length","message":{"content":"partial"}}]}""".toResponseBody("application/json".toMediaType())).build()
    }.build()
    try { ConnectedAiRepository(client).request(AiConnection(AiProvider.OPENAI, "gpt-5-mini", "test-key"), "cards"); fail() }
    catch (e: IllegalArgumentException) { assertTrue(e.message!!.contains("incompleta")) }
  }
  @Test fun encryptedConnectionPersistsOutsideBackupAndCanBeDeleted() {
    val context = RuntimeEnvironment.getApplication()
    val store = AiConnectionStore(context) { SecretKeySpec(ByteArray(32) { 7 }, "AES") }
    val connection = AiConnection(AiProvider.OPENAI, "gpt-5-mini", "private-user-secret", "")
    try {
      store.save(connection)
      assertEquals(connection, store.load())
      assertFalse(File(context.noBackupFilesDir, "ai-connection.enc").readBytes().toString(Charsets.UTF_8).contains(connection.apiKey))
      assertFalse(connection.toString().contains(connection.apiKey))
      store.clear(); assertNull(store.load())
    } finally { store.clear() }
  }
}
