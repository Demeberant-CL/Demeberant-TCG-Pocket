package com.example

import com.example.data.ai.*
import com.example.data.meta.*
import com.example.data.util.DiagnosticSummary
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import javax.crypto.spec.SecretKeySpec

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ProfilesMetaTest {
  @Test fun reopenedStoreKeepsSelectedProviderModelAndAllKeys() {
    val context = RuntimeEnvironment.getApplication()
    val key = SecretKeySpec(ByteArray(32) { 3 }, "AES")
    val store = AiConnectionStore(context) { key }
    store.clear()
    try {
      store.save(AiConnection(apiKey = "gemini-secret", model = "saved-gemini-model", id = "g"))
      store.save(AiConnection(AiProvider.OPENAI, "saved-openai-model", "openai-secret", id = "o"))
      store.select("g")
      val reopened = AiConnectionStore(context) { key }
      assertEquals("g", reopened.loadProfiles().activeId)
      assertEquals("saved-gemini-model", reopened.load()!!.model)
      assertEquals("openai-secret", reopened.loadProfiles().entries.first { it.id == "o" }.apiKey)
      reopened.remove("o")
      assertEquals("gemini-secret", AiConnectionStore(context) { key }.load()!!.apiKey)
      assertEquals("g", store.loadProfiles().activeId)
    } finally { store.clear() }
  }

  @Test fun interruptedWriteRecoversBackupWhenBaseFileIsAbsent() {
    val context = RuntimeEnvironment.getApplication()
    val key = SecretKeySpec(ByteArray(32) { 4 }, "AES")
    val store = AiConnectionStore(context) { key }
    store.clear()
    try {
      store.save(AiConnection(apiKey = "recover-secret", id = "g"))
      val base = java.io.File(context.noBackupFilesDir, "ai-connection.enc")
      val backup = java.io.File(base.path + ".bak")
      assertTrue(base.renameTo(backup))
      assertEquals("recover-secret", AiConnectionStore(context) { key }.load()!!.apiKey)
      assertTrue(base.exists())
    } finally { store.clear() }
  }

  @Test fun corruptCredentialsAndInvalidUpdatesNeverOverwriteSavedFile() {
    val context = RuntimeEnvironment.getApplication()
    val key = SecretKeySpec(ByteArray(32) { 5 }, "AES")
    val store = AiConnectionStore(context) { key }
    store.clear()
    try {
      val entry = AiConnection(apiKey = "preserved-secret", id = "g")
      store.save(entry)
      val file = java.io.File(context.noBackupFilesDir, "ai-connection.enc")
      val original = file.readBytes()
      assertTrue(runCatching { store.saveProfiles(AiProfiles(listOf(entry, entry), "g")) }.isFailure)
      assertArrayEquals(original, file.readBytes())
      val damaged = original.copyOf().also { it[it.lastIndex] = (it.last().toInt() xor 1).toByte() }
      file.writeBytes(damaged)
      assertTrue(runCatching { store.remove("g") }.isFailure)
      assertTrue(runCatching { store.select("g") }.isFailure)
      assertTrue(runCatching { store.save(AiConnection(apiKey = "another-secret")) }.isFailure)
      assertArrayEquals(damaged, file.readBytes())
    } finally { store.clear() }
  }

  @Test fun maximumSupportedPlaintextCanBeReadWithEncryptionOverhead() {
    val context = RuntimeEnvironment.getApplication()
    val key = SecretKeySpec(ByteArray(32) { 6 }, "AES")
    val store = AiConnectionStore(context) { key }
    store.clear()
    try {
      val entry = AiConnection(apiKey = "key", id = "g", label = "")
      val row = org.json.JSONObject().put("provider", entry.provider.name).put("model", entry.model)
        .put("key", entry.apiKey).put("endpoint", entry.endpoint).put("id", entry.id).put("label", "")
      val overhead = org.json.JSONObject().put("version", 2).put("activeId", "g")
        .put("profiles", org.json.JSONArray().put(row)).toString().toByteArray().size
      val value = entry.copy(label = "x".repeat(200000 - overhead))
      store.save(value)
      assertEquals(200029L, java.io.File(context.noBackupFilesDir, "ai-connection.enc").length())
      assertEquals(value, AiConnectionStore(context) { key }.load())
    } finally { store.clear() }
  }

  @Test fun switchingAndUpdatingProfilesPreservesIndependentSecrets() {
    val store = AiConnectionStore(RuntimeEnvironment.getApplication()) { SecretKeySpec(ByteArray(32) { 8 }, "AES") }
    store.clear()
    val gemini = AiConnection(apiKey = "gemini-secret", id = "g", label = "Gemini personal")
    val other = AiConnection(AiProvider.COMPATIBLE, "model", "other-secret", "https://example.com/v1/chat/completions", "o", "Otra")
    store.save(gemini); store.save(other)
    assertEquals(2, store.loadProfiles().entries.size)
    store.saveProfiles(store.loadProfiles().copy(activeId = "g"))
    assertEquals("gemini-secret", store.load()!!.apiKey)
    store.save(gemini.copy(model = "different-model"))
    assertEquals("other-secret", store.loadProfiles().entries.first { it.id == "o" }.apiKey)
    assertFalse(store.loadProfiles().toString().contains("secret"))
    store.clear()
  }
  @Test fun legacyEncryptedConnectionLoadsWithoutChangingKey() {
    val context = RuntimeEnvironment.getApplication()
    val key = SecretKeySpec(ByteArray(32) { 9 }, "AES")
    val old = org.json.JSONObject().put("provider", "GEMINI").put("model", "legacy-model")
      .put("key", "legacy-secret").put("endpoint", "").toString().toByteArray()
    val cipher = javax.crypto.Cipher.getInstance("AES/GCM/NoPadding")
    cipher.init(javax.crypto.Cipher.ENCRYPT_MODE, key)
    val encrypted = cipher.doFinal(old)
    val file = java.io.File(context.noBackupFilesDir, "ai-connection.enc")
    file.writeBytes(byteArrayOf(cipher.iv.size.toByte()) + cipher.iv + encrypted)
    val store = AiConnectionStore(context) { key }
    assertEquals("legacy-secret", store.load()!!.apiKey)
    store.save(AiConnection(AiProvider.OPENAI, "model", "new-secret", id = "new"))
    assertEquals("legacy-secret", store.loadProfiles().entries.first { it.id == "legacy-connection" }.apiKey)
    store.clear()
  }
  @Test fun modelDiscoveryUsesGetAndFiltersGeminiGenerationModels() = kotlinx.coroutines.runBlocking {
    var sent: okhttp3.Request? = null
    val client = okhttp3.OkHttpClient.Builder().addInterceptor { chain ->
      sent = chain.request()
      okhttp3.Response.Builder().request(chain.request()).protocol(okhttp3.Protocol.HTTP_1_1).code(200).message("OK")
        .body(okhttp3.ResponseBody.create(null, """{"models":[{"name":"models/gemini-2.5-flash","supportedGenerationMethods":["generateContent"]},{"name":"models/embedding","supportedGenerationMethods":["embedContent"]}]}""" )).build()
    }.build()
    assertEquals(listOf("gemini-2.5-flash"), ConnectedAiRepository(client).models(AiConnection(apiKey = "test-key")))
    assertEquals("GET", sent!!.method)
    assertEquals("test-key", sent!!.header("x-goog-api-key"))
    assertNull(sent!!.url.query)
    Unit
  }
  @Test fun modelCatalogueExcludesSpecializedAndUnsupportedModels() {
    fun gemini(id: String, method: String = "generateContent") = AiModelCompatibility.accepts(AiProvider.GEMINI,
      org.json.JSONObject().put("supportedGenerationMethods", org.json.JSONArray().put(method)), id)
    assertTrue(gemini("gemini-3.8-flash"))
    assertTrue(gemini("gemini-3.1-pro-preview"))
    assertFalse(gemini("gemini-2.5-flash-preview-tts"))
    assertFalse(gemini("gemini-3.1-flash-lite-image"))
    assertFalse(gemini("gemini-2.5-flash", "generateContentExtra"))
    assertFalse(gemini("gemma-3-27b-it"))
    val row = org.json.JSONObject()
    assertTrue(AiModelCompatibility.accepts(AiProvider.OPENAI, row, "gpt-5.4"))
    assertFalse(AiModelCompatibility.accepts(AiProvider.OPENAI, row, "gpt-5.4-pro"))
    assertFalse(AiModelCompatibility.accepts(AiProvider.OPENAI, row, "gpt-audio"))
    assertFalse(AiModelCompatibility.accepts(AiProvider.COMPATIBLE, row, "text-model"))
    row.put("supported_endpoints", org.json.JSONArray().put("chat/completions"))
      .put("architecture", org.json.JSONObject().put("output_modalities", org.json.JSONArray().put("text")))
    assertTrue(AiModelCompatibility.accepts(AiProvider.COMPATIBLE, row, "text-model"))
  }
  @Test fun metaCacheRoundTripPreservesCardsEnergyAndCounts() {
    val deck = MetaDeck("Sample", 3, 7, 2, 1, mapOf("A1-001" to 2, "P-A-005" to 2), listOf("Planta"), "abc")
    val value = MetaSnapshot("2026-10-03T12:00:00Z", 2, 3, listOf(deck), 1)
    assertEquals(value, TournamentRepository.parse(TournamentRepository.encode(value)))
    assertEquals("Psíquico", TournamentRepository.energyNames["Psychic"])
  }
  @Test fun summaryOnlyAcceptsFixedErrorCategories() {
    val text = DiagnosticSummary.create(sequenceOf("[2026-10-03T09:00:00Z] [AI_CONNECTED] category=QUOTA private-secret"), "1", 34)
    assertTrue(text.contains("QUOTA")); assertFalse(text.contains("private-secret"))
  }
}
