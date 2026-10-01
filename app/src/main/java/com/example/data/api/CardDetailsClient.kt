package com.example.data.api

import android.content.Context
import com.example.data.util.CardId
import com.example.data.util.readBytesBounded
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import org.json.JSONObject

data class CardDetails(val hp: Int?, val category: String, val stage: String, val description: String,
  val attacks: List<String>, val retreat: Int?, val source: String)

object CardDetailsClient {
  fun load(context: Context, id: String, language: String): CardDetails {
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
      val attacks = root.optJSONArray("attacks")
      return CardDetails(if (root.has("hp")) root.getInt("hp") else null, root.optString("category"),
        root.optString("stage"), root.optString("effect"), if (attacks == null) emptyList() else
        (0 until attacks.length()).map { i -> val attack = attacks.getJSONObject(i)
          listOf(attack.optString("name"), attack.optString("damage"), attack.optString("effect"))
            .filter { it.isNotBlank() }.joinToString(" · ")
        }, if (root.has("retreat")) root.getInt("retreat") else null,
        if (cached) "TCGdex · copia en caché" else "TCGdex")
    }
    val cache = File(context.cacheDir, "details-$lang-$canonical.json")
    val cached = runCatching { parse(cache.readText(), true) }.getOrNull()
    val connection = URL("https://api.tcgdex.net/v2/$lang/cards/$apiId").openConnection() as HttpURLConnection
    try {
      connection.connectTimeout = 8000; connection.readTimeout = 8000
      require(connection.responseCode == 200) { "Detalles no disponibles (HTTP ${connection.responseCode})." }
      val text = connection.inputStream.use { it.readBytesBounded(256_000).toString(Charsets.UTF_8) }
      val value = parse(text, false)
      runCatching { cache.writeText(text) }
      return value
    } catch (e: Exception) { return cached ?: throw e }
    finally { connection.disconnect() }
  }
}
