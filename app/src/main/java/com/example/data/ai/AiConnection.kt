package com.example.data.ai

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import org.json.JSONObject
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

// Each provider has an independent credential; changing provider never reuses a key.
enum class AiProvider(val label: String, val defaultModel: String) {
  GEMINI("Gemini", "gemini-3.8-flash"), OPENAI("OpenAI (ChatGPT)", "gpt-5-mini"),
  COMPATIBLE("Otra API compatible con OpenAI", "")
}
data class AiConnection(val provider: AiProvider = AiProvider.GEMINI, val model: String = provider.defaultModel,
  val apiKey: String = "", val endpoint: String = "") {
  override fun toString() = "AiConnection(provider=$provider, credentials=hidden)"
}

/** Encrypted by Android Keystore and excluded from Android backup and collection exports. */
class AiConnectionStore(context: Context, private val keyProvider: () -> SecretKey = ::deviceKey) {
  private val file = AtomicFile(File(context.noBackupFilesDir, "ai-connection.enc"))
  fun save(connection: AiConnection) {
    val json = JSONObject().put("provider", connection.provider.name).put("model", connection.model)
      .put("key", connection.apiKey).put("endpoint", connection.endpoint).toString().toByteArray(Charsets.UTF_8)
    val cipher = Cipher.getInstance("AES/GCM/NoPadding")
    cipher.init(Cipher.ENCRYPT_MODE, keyProvider())
    val encrypted = cipher.doFinal(json)
    val output = file.startWrite()
    try { output.write(cipher.iv.size); output.write(cipher.iv); output.write(encrypted); file.finishWrite(output) }
    catch (e: Exception) { file.failWrite(output); throw e }
  }
  fun load(): AiConnection? {
    if (!file.baseFile.exists()) return null
    val data = file.readFully()
    require(data.size in 30..16384)
    val ivSize = data[0].toInt() and 255
    require(ivSize == 12)
    val cipher = Cipher.getInstance("AES/GCM/NoPadding")
    cipher.init(Cipher.DECRYPT_MODE, keyProvider(), GCMParameterSpec(128, data.copyOfRange(1, 13)))
    val json = JSONObject(cipher.doFinal(data.copyOfRange(13, data.size)).toString(Charsets.UTF_8))
    return AiConnection(AiProvider.valueOf(json.getString("provider")), json.getString("model"),
      json.getString("key"), json.getString("endpoint"))
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
