package com.example.data.repository

import com.example.data.network.PocketHttp
import com.example.data.network.await
import com.example.data.util.readBytesBounded
import com.example.domain.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

/** OpenAI credentials stay on the supplied HTTPS backend, never in this APK. */
class AIAssistantRepository(private val client: OkHttpClient = PocketHttp.client,
  private val allowInsecureForTests: Boolean = false) {
  suspend fun generateDeck(endpoint: String, token: String, meta: String, goal: String,
    candidates: List<AiCandidate>): AiProposal = request(endpoint, token, "generate", meta, goal, candidates, null)

  suspend fun suggestReplacements(endpoint: String, token: String, meta: String, targetDeck: List<DeckCardEntry>,
    userCollection: List<AiCandidate>): AiProposal =
    request(endpoint, token, "replacements", meta, "", userCollection, targetDeck)

  private suspend fun request(endpoint: String, token: String, mode: String, meta: String, goal: String,
    candidates: List<AiCandidate>, target: List<DeckCardEntry>?): AiProposal = withContext(Dispatchers.IO) {
    val url = endpoint.trim().toHttpUrl()
    require(url.isHttps || (allowInsecureForTests && url.host in setOf("localhost", "127.0.0.1")))
    require(url.username.isEmpty() && url.password.isEmpty() && url.query == null)
    require(token.isNotBlank()) { "Falta el token del servidor." }
    require(candidates.isNotEmpty() && candidates.size <= 2000) { "Selecciona una colección de hasta 2000 cartas." }
    require(meta.length <= 16_000 && goal.length <= 2000)
    val payload = JSONObject().put("mode", mode).put("meta", meta).put("goal", goal)
      .put("cards", JSONArray(candidates.map { item -> JSONObject().put("id", item.card.id).put("name", item.card.rulesName)
        .put("quantity", minOf(item.owned, 2)).put("type", item.card.type).put("category", item.card.category)
        .put("stage", item.card.stage).put("evolvesFrom", item.card.evolvesFrom).put("effects", item.text.take(2000)) }))
      .put("target", JSONArray(target?.map { JSONObject().put("id", it.card.id).put("count", it.count) } ?: emptyList<JSONObject>()))
    val text = payload.toString()
    require(text.toByteArray().size <= 800_000) { "Contexto demasiado grande. Acota el tipo de cartas." }
    val request = Request.Builder().url(url).header("Authorization", "Bearer $token")
      .post(text.toRequestBody("application/json".toMediaType())).build()
    client.newCall(request).await().use { response ->
      require(response.isSuccessful) { "Servidor IA: HTTP ${response.code}. Revisa configuración o cuota." }
      val body = response.body ?: error("El servidor no devolvió datos.")
      val result = body.byteStream().use { it.readBytesBounded(100_000).toString(Charsets.UTF_8) }
      AiValidator.parse(result, candidates, target)
    }
  }
}
