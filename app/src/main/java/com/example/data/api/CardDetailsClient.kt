package com.example.data.api

import android.content.Context
import com.example.data.util.CardId
import com.example.data.util.readBytesBounded
import java.io.File
import com.example.data.network.PocketHttp
import okhttp3.Request
import com.example.data.local.AppDatabase
import com.example.data.local.CardRulesEntity
import com.example.domain.RoleClassifier
import kotlinx.coroutines.runBlocking
import org.json.JSONObject

data class CardDetails(val hp: Int?, val category: String, val stage: String, val description: String,
  val attacks: List<String>, val retreat: Int?, val source: String, val element: String = "", val abilityText: String = "", val evolvesFrom: String = "")

object CardDetailsClient {
  fun load(context: Context, id: String, language: String, client: okhttp3.OkHttpClient = PocketHttp.detailsClient, baseUrl: String = "https://api.tcgdex.net/v2/"): CardDetails {
    val canonical = CardId.normalize(id)
    val (set, number) = CardId.split(canonical)
    val apiSet = when (set) {
      "PROMO-A" -> "P-A"; "PROMO-B" -> "P-B"
      else -> Regex("^([AB][0-9]+)([A-Z]+)$").matchEntire(set)?.let {
        it.groupValues[1] + it.groupValues[2].lowercase(java.util.Locale.ROOT)
      } ?: set
    }
    val lang = language.takeIf { it in setOf("es", "en", "ja") } ?: "es"
    val apiId = "$apiSet-$number"
    fun parse(text: String, cached: Boolean): CardDetails {
      val root = JSONObject(text)
      require(root.getString("id").equals(apiId, true)) { "Respuesta de carta incorrecta." }
      val contentLanguage = root.optString("_pocketLanguage", lang)
      val source = "TCGdex" + (if (contentLanguage != lang) " · inglés" else "") + (if (cached) " · copia guardada" else "")
      val attacks = root.optJSONArray("attacks")
      return CardDetails(if (root.has("hp")) root.getInt("hp") else null, root.optString("category"),
        root.optString("stage"), root.optString("effect"), if (attacks == null) emptyList() else
        (0 until attacks.length()).map { i -> val attack = attacks.getJSONObject(i)
          listOf(attack.optString("name"), attack.optString("damage"), attack.optString("effect"))
            .filter { it.isNotBlank() }.joinToString(" · ")
        }, if (root.has("retreat")) root.getInt("retreat") else null,
        source,
        root.optJSONArray("types")?.optString(0) ?: "",
        root.optJSONArray("abilities")?.let { a -> (0 until a.length()).joinToString("\n") { i ->
          val ability = a.getJSONObject(i); ability.optString("name") + " · " + ability.optString("effect")
        } } ?: "", root.optString("evolveFrom"))
    }
    val cache = File(context.cacheDir, "details-$lang-$canonical.json")
    val cached = runCatching { parse(cache.readText(), true) }.getOrNull()
    fun persist(value: CardDetails) {
      val rules = (listOf(value.description, value.abilityText) + value.attacks).filter { it.isNotBlank() }.joinToString("\n")
      val tags = RoleClassifier.classify(rules).joinToString("", prefix = "|") { it.key + "|" }
      try {
        runBlocking(kotlinx.coroutines.Dispatchers.IO) {
          AppDatabase.getDatabase(context).cardRulesDao().upsert(CardRulesEntity(canonical, lang, value.hp,
            value.element, rules, RoleClassifier.normalize(rules), tags, System.currentTimeMillis(), source = value.source, category = value.category, stage = value.stage, evolvesFrom = value.evolvesFrom))
        }
      } catch (e: Exception) { com.example.data.util.ErrorLogManager.event("RULES_WRITE", "Rules cache write failed", e) }
    }
    // Reuse verified details for a day; expired copies still work when offline.
    if (cached != null && System.currentTimeMillis() - cache.lastModified() in 0..86_400_000L) {
      persist(cached)
      return cached
    }
    try {
      val candidates = listOf(lang, "en").distinct()
      for ((index, candidate) in candidates.withIndex()) {
        val request = Request.Builder().url("${baseUrl.trimEnd('/')}/$candidate/cards/$apiId").build()
        client.newCall(request).execute().use { response ->
          if (response.code == 404 && index < candidates.lastIndex) return@use
          require(response.isSuccessful) { "Detalles no disponibles (HTTP ${response.code})." }
          val body = response.body ?: error("Respuesta vacía.")
          val text = body.byteStream().use { it.readBytesBounded(256_000).toString(Charsets.UTF_8) }
          val annotated = JSONObject(text).put("_pocketLanguage", candidate).toString()
          val value = parse(annotated, false)
          runCatching { cache.writeText(annotated) }
          persist(value)
          return value
        }
      }
      error("Detalles no disponibles.")
    } catch (e: Exception) {
      com.example.data.util.ErrorLogManager.event("DETAILS", "Details unavailable; cache=${cached != null}", e)
      if (cached != null) { persist(cached); return cached }
      throw e
    }
  }
}
