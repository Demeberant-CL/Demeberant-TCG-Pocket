package com.example.data.ai

import com.example.data.network.await
import com.example.data.util.readBytesBounded
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/** Bounded transient retries; no private logging or automatic provider changes. */
class ConnectedAiRepository(private val client: OkHttpClient = OkHttpClient.Builder()
  .connectTimeout(15, TimeUnit.SECONDS).readTimeout(90, TimeUnit.SECONDS).callTimeout(100, TimeUnit.SECONDS)
  .retryOnConnectionFailure(false).followRedirects(false).followSslRedirects(false).build(),
  private val allowLocalTests: Boolean = false,
  private val retryWait: suspend (Long) -> Unit = { kotlinx.coroutines.delay(it) }) {
  private suspend fun execute(request: Request, onStatus: (String) -> Unit): Response {
    for (attempt in 0..2) {
      val response = client.newCall(request).await()
      val retryAfter = response.header("Retry-After")?.toLongOrNull()?.takeIf { it in 0..30 }
      val transient = response.code in setOf(502, 503, 504) || (response.code == 429 && retryAfter != null)
      if (!transient || attempt == 2) return response
      val pause = retryAfter?.times(1000) ?: (1000L shl attempt) + kotlin.random.Random.nextLong(250)
      response.close()
      onStatus("Servicio ocupado. Reintentando ${attempt + 1}/2…")
      retryWait(pause)
      kotlinx.coroutines.currentCoroutineContext().ensureActive()
    }
    error("La API no está disponible temporalmente. Puedes reintentar.")
  }
  suspend fun models(config: AiConnection): List<String> = withContext(Dispatchers.IO) {
    require(config.apiKey.isNotBlank()) { "Introduce tu clave API." }
    val base = when (config.provider) {
      AiProvider.GEMINI -> "https://generativelanguage.googleapis.com/v1beta/models"
      else -> {
        val endpoint = config.provider.endpoint(config.endpoint)
        require(endpoint.endsWith("/chat/completions")) { "La URL debe terminar en /chat/completions." }
        endpoint.removeSuffix("/chat/completions") + "/models"
      }
    }
    val url = base.toHttpUrl()
    require(url.isHttps || (allowLocalTests && url.host in setOf("localhost", "127.0.0.1")))
    require(url.username.isEmpty() && url.password.isEmpty() && url.query == null && url.fragment == null)
    val builder = Request.Builder().url(url)
    if (config.provider == AiProvider.GEMINI) builder.header("x-goog-api-key", config.apiKey)
    else builder.header("Authorization", "Bearer ${config.apiKey}")
    client.newCall(builder.build()).await().use { response ->
      require(response.isSuccessful) { "La API no permite listar modelos (HTTP ${response.code}). Puedes introducir el modelo manualmente." }
      val body = response.body ?: error("La API no devolvió modelos.")
      val json = JSONObject(body.byteStream().use { it.readBytesBounded(if (config.provider == AiProvider.OPENROUTER) 4000000 else 1000000).toString(Charsets.UTF_8) })
      val gemini = config.provider == AiProvider.GEMINI
      val rows = json.optJSONArray(if (gemini) "models" else "data") ?: JSONArray()
      (0 until minOf(rows.length(), 1000)).mapNotNull { n ->
        val row = rows.optJSONObject(n) ?: return@mapNotNull null
        val id = row.optString(if (gemini) "name" else "id").removePrefix("models/")
        id.takeIf { AiModelCompatibility.accepts(config.provider, row, it) }
      }.distinct().sorted()
    }
  }
  suspend fun request(config: AiConnection, prompt: String, onStatus: (String) -> Unit = {}): String = withContext(Dispatchers.IO) {
    require(config.apiKey.isNotBlank() && config.apiKey.length <= 4096) { "Introduce tu clave API." }
    require(config.model.matches(Regex("[a-zA-Z0-9._:/-]{1,120}"))) { "Revisa el identificador del modelo." }
    require(prompt.toByteArray(Charsets.UTF_8).size <= 60000) { "Hay demasiadas cartas. Selecciona un tipo para reducir la consulta." }
    val gemini = config.provider == AiProvider.GEMINI
    val endpoint = when (config.provider) {
      AiProvider.GEMINI -> "https://generativelanguage.googleapis.com/v1beta/models/${config.model}:generateContent"
      else -> config.provider.endpoint(config.endpoint)
    }
    val url = endpoint.toHttpUrl()
    require(url.isHttps || (allowLocalTests && url.host in setOf("localhost", "127.0.0.1"))) { "La URL debe usar HTTPS." }
    require(url.username.isEmpty() && url.password.isEmpty() && url.query == null && url.fragment == null) { "Usa una URL sin credenciales, parámetros ni fragmentos." }
    if (gemini) require(!config.model.contains('/') && !config.model.contains(':')) { "Usa el nombre del modelo Gemini sin prefijos." }
    val payload = if (gemini) JSONObject()
      .put("contents", JSONArray().put(JSONObject().put("role", "user").put("parts", JSONArray().put(JSONObject().put("text", prompt)))))
      .put("generationConfig", JSONObject().put("responseMimeType", "application/json").put("maxOutputTokens", 4096))
    else JSONObject().put("model", config.model).put("messages", JSONArray().put(JSONObject().put("role", "user").put("content", prompt)))
      .put(if (config.provider in setOf(AiProvider.OPENAI, AiProvider.GROQ, AiProvider.COMPATIBLE)) "max_completion_tokens" else "max_tokens", 4096)
    val builder = Request.Builder().url(url).post(payload.toString().toRequestBody("application/json".toMediaType()))
    if (gemini) builder.header("x-goog-api-key", config.apiKey) else builder.header("Authorization", "Bearer ${config.apiKey}")
    execute(builder.build(), onStatus).use { response ->
      if (!response.isSuccessful) throw AiHttpFailure(response.code, when (response.code) {
        401, 403 -> "La API rechazó la clave o el acceso al modelo. Revisa tu configuración."
        429 -> "Cuota o límite de consultas alcanzado. Cambia de conexión y reintenta."
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
