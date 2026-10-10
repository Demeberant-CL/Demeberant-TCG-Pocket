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
  @Test fun presetRoutingUsesIndependentKeysAndProviderTokenFields() = runBlocking {
    val requests = mutableListOf<Request>()
    val client = OkHttpClient.Builder().addInterceptor { chain ->
      requests.add(chain.request())
      Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK")
        .body("""{"choices":[{"finish_reason":"stop","message":{"content":"ok"}}]}""".toResponseBody("application/json".toMediaType())).build()
    }.build()
    val hosts = mapOf(AiProvider.GROQ to "api.groq.com", AiProvider.OPENROUTER to "openrouter.ai",
      AiProvider.MISTRAL to "api.mistral.ai", AiProvider.DEEPSEEK to "api.deepseek.com")
    for ((provider, host) in hosts) {
      assertEquals("ok", ConnectedAiRepository(client).request(AiConnection(provider, "text-model", "key-${provider.name}", "https://wrong.example/chat/completions"), "cards"))
      val sent = requests.last()
      assertEquals(host, sent.url.host)
      assertEquals("Bearer key-${provider.name}", sent.header("Authorization"))
      assertNull(sent.url.query)
      val buffer = okio.Buffer(); sent.body!!.writeTo(buffer)
      val body = JSONObject(buffer.readUtf8())
      assertEquals(4096, body.getInt(if (provider == AiProvider.GROQ) "max_completion_tokens" else "max_tokens"))
      assertEquals(1, body.getJSONArray("messages").length())
    }
    assertEquals(4, requests.size)
  }
  @Test fun providerModelCataloguesUseTheirRealCapabilityMetadata() = runBlocking {
    val paths = mutableListOf<String>()
    val client = OkHttpClient.Builder().addInterceptor { chain ->
      paths.add(chain.request().url.toString())
      Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK")
        .body("""{"data":[{"id":"llama-3.3-70b-versatile"},{"id":"deepseek-chat"},{"id":"mistral-small-latest","capabilities":{"completion_chat":true}},{"id":"vendor/text:free","architecture":{"output_modalities":["text"]}},{"id":"embedding-model"}]}""".toResponseBody("application/json".toMediaType())).build()
    }.build()
    val expected = mapOf(AiProvider.GROQ to "llama-3.3-70b-versatile", AiProvider.DEEPSEEK to "deepseek-chat",
      AiProvider.MISTRAL to "mistral-small-latest", AiProvider.OPENROUTER to "vendor/text:free")
    for ((provider, id) in expected) {
      assertEquals(listOf(id), ConnectedAiRepository(client).models(AiConnection(provider, apiKey="key")))
      assertEquals(provider.chatEndpoint.removeSuffix("/chat/completions") + "/models", paths.last())
    }
  }
  @Test fun newProfilesSurviveSwitchingAndDoNotLoseLegacyProfiles() {
    val store = AiConnectionStore(RuntimeEnvironment.getApplication()) { SecretKeySpec(ByteArray(32) { 9 }, "AES") }
    store.clear()
    try {
      val entries = AiProvider.entries.map { AiConnection(it, "text-model", "private-${it.name}", "https://example.com/v1/chat/completions", id=it.name) }
      entries.forEach(store::save)
      entries.reversed().forEach { c ->
        store.select(c.id)
        assertEquals(c, store.load())
        assertEquals(entries.toSet(), store.loadProfiles().entries.toSet())
      }
    } finally { store.clear() }
  }

}
