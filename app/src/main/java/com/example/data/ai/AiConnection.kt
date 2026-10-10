package com.example.data.ai

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import org.json.JSONObject
import org.json.JSONArray
import java.util.UUID
import com.example.data.util.readBytesBounded
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

// Each provider has an independent credential; changing provider never reuses a key.
enum class AiProvider(val label: String, val defaultModel: String,
  val chatEndpoint: String = "", val keysUrl: String = "") {
  GEMINI("Gemini", "gemini-3.8-flash", keysUrl = "https://aistudio.google.com/apikey"),
  OPENAI("OpenAI (ChatGPT)", "gpt-5-mini", "https://api.openai.com/v1/chat/completions", "https://platform.openai.com/api-keys"),
  GROQ("Groq", "", "https://api.groq.com/openai/v1/chat/completions", "https://console.groq.com/keys"),
  OPENROUTER("OpenRouter", "", "https://openrouter.ai/api/v1/chat/completions", "https://openrouter.ai/settings/keys"),
  MISTRAL("Mistral", "", "https://api.mistral.ai/v1/chat/completions", "https://console.mistral.ai/api-keys"),
  DEEPSEEK("DeepSeek", "", "https://api.deepseek.com/chat/completions", "https://platform.deepseek.com/api_keys"),
  COMPATIBLE("Otra API compatible con OpenAI", "");
  fun endpoint(custom: String): String = if (this == COMPATIBLE) custom.trim() else chatEndpoint
}
data class AiConnection(val provider: AiProvider = AiProvider.GEMINI, val model: String = provider.defaultModel,
  val apiKey: String = "", val endpoint: String = "", val id: String = UUID.randomUUID().toString(),
  val label: String = provider.label) {
  override fun toString() = "AiConnection(provider=$provider, credentials=hidden)"
}

data class AiProfiles(val entries: List<AiConnection> = emptyList(), val activeId: String = "") {
  val active: AiConnection? get() = entries.firstOrNull { it.id == activeId } ?: entries.firstOrNull()
  override fun toString() = "AiProfiles(count=${entries.size}, credentials=hidden)"
}

/** Encrypted by Android Keystore and excluded from Android backup and collection exports. */
class AiConnectionStore(context: Context, private val keyProvider: () -> SecretKey = ::deviceKey) {
  private val file = AtomicFile(File(context.noBackupFilesDir, "ai-connection.enc"))
  @Synchronized fun save(connection: AiConnection) {
    val old = loadProfiles()
    val entries = old.entries.filterNot { it.id == connection.id } + connection
    saveProfiles(AiProfiles(entries, connection.id))
  }
  @Synchronized fun saveProfiles(profiles: AiProfiles) {
    require(profiles.entries.size <= 30)
    require(profiles.entries.map { it.id }.distinct().size == profiles.entries.size && profiles.entries.all { it.id.isNotBlank() })
    require(if (profiles.entries.isEmpty()) profiles.activeId.isEmpty() else profiles.entries.any { it.id == profiles.activeId })
    val rows = JSONArray()
    profiles.entries.forEach { c -> rows.put(JSONObject().put("provider", c.provider.name).put("model", c.model)
      .put("key", c.apiKey).put("endpoint", c.endpoint).put("id", c.id).put("label", c.label)) }
    val json = JSONObject().put("version", 2).put("activeId", profiles.activeId).put("profiles", rows)
      .toString().toByteArray(Charsets.UTF_8)
    require(json.size <= 200000) { "Demasiadas conexiones o datos de configuración." }
    val cipher = Cipher.getInstance("AES/GCM/NoPadding")
    cipher.init(Cipher.ENCRYPT_MODE, keyProvider())
    val encrypted = cipher.doFinal(json)
    val output = file.startWrite()
    try { output.write(cipher.iv.size); output.write(cipher.iv); output.write(encrypted); file.finishWrite(output) }
    catch (e: Exception) { file.failWrite(output); throw e }
  }
  @Synchronized fun select(id: String): AiProfiles {
    val saved = loadProfiles()
    require(saved.entries.any { it.id == id }) { "La conexión ya no está guardada." }
    return saved.copy(activeId = id).also(::saveProfiles)
  }
  @Synchronized fun remove(id: String): AiProfiles {
    val saved = loadProfiles()
    require(saved.entries.any { it.id == id }) { "La conexión ya no está guardada." }
    val remaining = saved.entries.filterNot { it.id == id }
    val active = if (saved.activeId == id) remaining.firstOrNull()?.id ?: "" else saved.activeId
    return AiProfiles(remaining, active).also(::saveProfiles)
  }
  fun load(): AiConnection? = loadProfiles().active
  @Synchronized fun loadProfiles(): AiProfiles {
    // AtomicFile may recover its last complete write from .bak after interruption.
    if (!file.baseFile.exists() && !File(file.baseFile.path + ".bak").exists()) return AiProfiles()
    val data = file.openRead().use { it.readBytesBounded(200029) }
    require(data.size in 30..200029)
    val ivSize = data[0].toInt() and 255
    require(ivSize == 12)
    val cipher = Cipher.getInstance("AES/GCM/NoPadding")
    cipher.init(Cipher.DECRYPT_MODE, keyProvider(), GCMParameterSpec(128, data.copyOfRange(1, 13)))
    val json = JSONObject(cipher.doFinal(data.copyOfRange(13, data.size)).toString(Charsets.UTF_8))
    fun entry(row: JSONObject) = AiConnection(AiProvider.valueOf(row.getString("provider")), row.getString("model"),
      row.getString("key"), row.getString("endpoint"), row.optString("id", "legacy-connection"),
      row.optString("label", row.getString("provider")))
    val rows = json.optJSONArray("profiles")
    if (rows == null) { val old = entry(json); return AiProfiles(listOf(old), old.id) }
    require(rows.length() <= 30)
    val entries = (0 until rows.length()).map { entry(rows.getJSONObject(it)) }
    require(entries.map { it.id }.distinct().size == entries.size)
    val activeId = json.optString("activeId").takeIf { id -> entries.any { it.id == id } }
      ?: entries.firstOrNull()?.id.orEmpty()
    return AiProfiles(entries, activeId)
  }
  fun clear() { file.delete() }
  companion object {
    private fun deviceKey(): SecretKey {
      val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
      (store.getKey("pocket-ai-user-key", null) as? SecretKey)?.let { return it }
      return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
        init(KeyGenParameterSpec.Builder("pocket-ai-user-key", KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
          .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
      }.generateKey()
    }
  }
}
