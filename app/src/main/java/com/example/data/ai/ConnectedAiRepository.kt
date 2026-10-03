package com.example.data.ai

import com.example.data.network.await
import com.example.data.util.readBytesBounded
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/** No private bodies, credentials, or custom URLs are logged. No retries or provider fallback. */
class ConnectedAiRepository(private val client: OkHttpClient = OkHttpClient.Builder()
  .connectTimeout(15, TimeUnit.SECONDS).readTimeout(90, TimeUnit.SECONDS).callTimeout(100, TimeUnit.SECONDS)
  .retryOnConnectionFailure(false).followRedirects(false).followSslRedirects(false).build(),
  private val allowLocalTests: Boolean = false) {
  suspend fun request(config: AiConnection, prompt: String): String = withContext(Dispatchers.IO) {
    require(config.apiKey.isNotBlank() && config.apiKey.length <= 4096) { "Introduce tu clave API." }
    require(config.model.matches(Regex("[a-zA-Z0-9._:/-]{1,120}"))) { "Revisa el identificador del modelo." }
    require(prompt.toByteArray(Charsets.UTF_8).size <= 60000) { "Hay demasiadas cartas. Selecciona un tipo para reducir la consulta." }
    val gemini = config.provider == AiProvider.GEMINI
    val endpoint = when (config.provider) {
      AiProvider.GEMINI -> "https://generativelanguage.googleapis.com/v1beta/models/${config.model}:generateContent"
      AiProvider.OPENAI -> "https://api.openai.com/v1/chat/completions"
      AiProvider.COMPATIBLE -> config.endpoint.trim()
    }
    val url = endpoint.toHttpUrl()
    require(url.isHttps || (allowLocalTests && url.host in setOf("localhost", "127.0.0.1"))) { "La URL debe usar HTTPS." }
    require(url.username.isEmpty() && url.password.isEmpty() && url.query == null && url.fragment == null) { "Usa una URL sin credenciales, parámetros ni fragmentos." }
    if (gemini) require(!config.model.contains('/') && !config.model.contains(':')) { "Usa el nombre del modelo Gemini sin prefijos." }
    val payload = if (gemini) JSONObject()
      .put("contents", JSONArray().put(JSONObject().put("role", "user").put("parts", JSONArray().put(JSONObject().put("text", prompt)))))
      .put("generationConfig", JSONObject().put("responseMimeType", "application/json").put("maxOutputTokens", 4096))
    else JSONObject().put("model", config.model).put("messages", JSONArray().put(JSONObject().put("role", "user").put("content", prompt)))
      .put("max_completion_tokens", 4096)
    val builder = Request.Builder().url(url).post(payload.toString().toRequestBody("application/json".toMediaType()))
    if (gemini) builder.header("x-goog-api-key", config.apiKey) else builder.header("Authorization", "Bearer ${config.apiKey}")
    client.newCall(builder.build()).await().use { response ->
      if (!response.isSuccessful) error(when (response.code) {
        401, 403 -> "La API rechazó la clave o el acceso al modelo. Revisa tu configuración."
        429 -> "Cuota o límite de consultas alcanzado. No se cambiará a otro proveedor."
        400, 404 -> "Modelo o formato no compatible. Revisa el modelo y la URL."
        else -> "La API no está disponible (HTTP ${response.code}). Inténtalo más tarde."
      })
      val body = response.body ?: error("La API no devolvió una respuesta.")
      val json = JSONObject(body.byteStream().use { it.readBytesBounded(100000).toString(Charsets.UTF_8) })
      if (gemini) {
        val candidate = json.optJSONArray("candidates")?.optJSONObject(0) ?: error("La IA no devolvió un mazo.")
        require(candidate.optString("finishReason") == "STOP") { "La respuesta quedó incompleta o fue bloqueada. No se importó ningún mazo." }
        val parts = candidate.getJSONObject("content").getJSONArray("parts")
        (0 until parts.length()).map { parts.getJSONObject(it) }.filter { !it.optBoolean("thought", false) }
          .joinToString("") { it.optString("text") }.also { require(it.isNotBlank()) }
      } else {
        val choice = json.getJSONArray("choices").getJSONObject(0)
        require(choice.optString("finish_reason") == "stop") { "La respuesta quedó incompleta. No se importó ningún mazo." }
        choice.getJSONObject("message").getString("content")
      }
    }
  }
}
