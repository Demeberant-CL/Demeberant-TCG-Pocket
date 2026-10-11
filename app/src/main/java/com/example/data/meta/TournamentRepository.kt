package com.example.data.meta

import android.content.Context
import android.util.AtomicFile
import com.example.data.network.await
import com.example.data.util.readBytesBounded
import kotlinx.coroutines.*
import okhttp3.*
import org.json.*
import java.io.File
import java.time.Instant
import java.util.concurrent.TimeUnit

data class MetaDeck(val name: String, val count: Int, val wins: Int, val losses: Int, val ties: Int,
  val cards: Map<String, Int>, val energies: List<String>, val tournamentId: String)
data class MetaSnapshot(val updated: String, val tournaments: Int, val players: Int, val decks: List<MetaDeck>, val skipped: Int)

class NoRecentTournamentResults : IllegalStateException("No hay resultados completos recientes. Se conserva el meta guardado.")
class TournamentSourceUnavailable(code: Int) : java.io.IOException("Limitless no está disponible (HTTP $code). Se conserva el meta guardado.")

/** Public tournament data only. Never sends collection or AI credentials to Limitless. */
class TournamentRepository(context: Context, private val client: OkHttpClient = OkHttpClient.Builder()
  .callTimeout(25, TimeUnit.SECONDS).followRedirects(false).build(),
  private val refreshTimeoutMillis: Long = 90_000L) {
  private val cache = AtomicFile(File(context.filesDir, "pocket-meta.json"))
  suspend fun cached(): MetaSnapshot? = withContext(Dispatchers.IO) {
    if (!cache.baseFile.exists() && !File(cache.baseFile.path + ".bak").exists()) null
    else parse(JSONObject(cache.openRead().use { it.readBytesBounded(2_000_000).toString(Charsets.UTF_8) }))
  }
  private suspend fun get(path: String): String {
    client.newCall(Request.Builder().url("https://play.limitlesstcg.com/api/$path").build()).await().use { r ->
      if (!r.isSuccessful) throw TournamentSourceUnavailable(r.code)
      return r.body!!.byteStream().use { it.readBytesBounded(2000000).toString(Charsets.UTF_8) }
    }
  }
  suspend fun refresh(onProgress: (Int, Int) -> Unit = { _, _ -> }): MetaSnapshot =
    withTimeoutOrNull(refreshTimeoutMillis) { fetchSnapshot(onProgress) }
      ?: throw java.io.IOException("La actualización tardó demasiado. Se conserva el meta guardado; vuelve a intentarlo cuando mejore la conexión.")

  private suspend fun fetchSnapshot(onProgress: (Int, Int) -> Unit): MetaSnapshot = withContext(Dispatchers.IO) {
    val tournaments = JSONArray(get("tournaments?game=POCKET&limit=12"))
    val limit = minOf(tournaments.length(), 12)
    val groups = linkedMapOf<String, MetaDeck>(); var included = 0; var players = 0; var skipped = 0
    val cutoff = Instant.now().minusSeconds(30L * 86400)
    for (n in 0 until limit) {
      ensureActive()
      onProgress(n + 1, limit)
      val t = tournaments.getJSONObject(n)
      if (t.optString("game") != "POCKET") { skipped++; continue }
      val date = runCatching { Instant.parse(t.getString("date")) }.getOrNull() ?: continue
      if (date.isBefore(cutoff) || date.isAfter(Instant.now())) continue
      val id = t.getString("id"); require(id.matches(Regex("[a-zA-Z0-9]{1,60}")))
      val details = JSONObject(get("tournaments/$id/details"))
      if (details.optString("game") != "POCKET" || details.optString("id") != id ||
        !details.optBoolean("isPublic") || !details.optBoolean("decklists") ||
        details.optJSONArray("specialRules")?.length()?.let { it > 0 } == true ||
        details.optJSONArray("bannedCards")?.length()?.let { it > 0 } == true ||
        details.optString("format") !in setOf("STANDARD", "null", "")) { skipped++; continue }
      val rows = JSONArray(get("tournaments/$id/standings"))
      if (rows.length() == 0 || (0 until rows.length()).any { rows.getJSONObject(it).optInt("placing", 0) < 1 }) { skipped++; continue }
      included++
      for (i in 0 until rows.length()) {
        val row = rows.getJSONObject(i); val list = row.optJSONObject("decklist") ?: continue
        val cards = linkedMapOf<String, Int>()
        for (section in listOf("pokemon", "trainer")) {
          val entries = list.optJSONArray(section) ?: continue
          for (j in 0 until entries.length()) {
            val c = entries.getJSONObject(j); val number = c.optString("number").toIntOrNull() ?: continue
            val set = c.optString("set"); if (!set.matches(Regex("[A-Za-z0-9-]{1,12}"))) continue
            val key = "$set-${number.toString().padStart(3, '0')}"
            cards[key] = (cards[key] ?: 0) + c.optInt("count")
          }
        }
        if (cards.values.sum() != 20 || cards.values.any { it !in 1..2 }) { skipped++; continue }
        val energyRows = list.optJSONArray("energy") ?: JSONArray()
        val energies = (0 until energyRows.length()).mapNotNull { energyNames[energyRows.optString(it)] }.distinct()
        if (energies.size !in 1..3 || energies.size != energyRows.length()) { skipped++; continue }
        val archetype = row.optJSONObject("deck")?.optString("name")?.take(100)?.ifBlank { null } ?: "Sin clasificar"
        val record = row.optJSONObject("record") ?: JSONObject()
        val old = groups[archetype]; players++
        groups[archetype] = MetaDeck(archetype, (old?.count ?: 0) + 1,
          (old?.wins ?: 0) + record.optInt("wins").coerceAtLeast(0), (old?.losses ?: 0) + record.optInt("losses").coerceAtLeast(0),
          (old?.ties ?: 0) + record.optInt("ties").coerceAtLeast(0), old?.cards ?: cards, old?.energies ?: energies, old?.tournamentId ?: id)
      }
      delay(250)
    }
    if (players == 0) throw NoRecentTournamentResults()
    ensureActive()
    val snapshot = MetaSnapshot(Instant.now().toString(), included, players, groups.values.sortedByDescending { it.count }, skipped)
    val bytes = encode(snapshot).toString().toByteArray(Charsets.UTF_8)
    val out = cache.startWrite()
    try { out.write(bytes); cache.finishWrite(out) } catch (e: Exception) { cache.failWrite(out); throw e }
    snapshot
  }
  companion object {
    val energyNames = mapOf("Grass" to "Planta", "Fire" to "Fuego", "Water" to "Agua", "Lightning" to "Rayo",
      "Psychic" to "Psíquico", "Fighting" to "Lucha", "Darkness" to "Oscuridad", "Metal" to "Metal")
    fun encode(s: MetaSnapshot) = JSONObject().put("updated", s.updated).put("tournaments", s.tournaments).put("players", s.players).put("skipped", s.skipped)
      .put("decks", JSONArray(s.decks.map { d -> JSONObject().put("name", d.name).put("count", d.count).put("wins", d.wins).put("losses", d.losses)
        .put("ties", d.ties).put("cards", JSONObject(d.cards)).put("energies", JSONArray(d.energies)).put("tournamentId", d.tournamentId) }))
    fun parse(j: JSONObject): MetaSnapshot {
      val rows = j.getJSONArray("decks")
      return MetaSnapshot(j.getString("updated"), j.getInt("tournaments"), j.getInt("players"), (0 until rows.length()).map { i ->
        val d = rows.getJSONObject(i); val cards = d.getJSONObject("cards"); val energies = d.getJSONArray("energies")
        MetaDeck(d.getString("name"), d.getInt("count"), d.getInt("wins"), d.getInt("losses"), d.getInt("ties"),
          cards.keys().asSequence().associateWith { cards.getInt(it) }, (0 until energies.length()).map { energies.getString(it) }, d.getString("tournamentId"))
      }, j.optInt("skipped"))
    }
  }
}
