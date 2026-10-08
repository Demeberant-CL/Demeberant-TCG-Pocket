package com.example.zoneimport

import androidx.room.withTransaction
import com.example.data.local.AppDatabase
import com.example.data.repository.CardCatalog
import com.example.data.repository.InventoryRepository
import com.example.data.repository.ParsedCsvCard
import com.example.data.util.CardId
import kotlinx.coroutines.flow.first
import org.json.JSONObject
import org.json.JSONTokener
import java.net.URI
import java.time.Instant

data class ZoneImportPlan internal constructor(
  val playerId: String,
  val readAt: String,
  val collectionComplete: Boolean,
  val cards: List<ParsedCsvCard>,
  val duplicates: Int
) {
  val uniqueCards: Int get() = cards.size
  val totalCopies: Long get() = cards.sumOf { it.quantity.toLong() }
  val sets: Int get() = cards.map { it.setCode }.distinct().size
}

/** Adapter for the actual schema-1 JSON exported by Pocket Zone, not a guessed format. */
object ZoneCollectionImport {
  const val MAX_BYTES = 3 * 1024 * 1024
  private val cardPath = Regex("^/cards/([a-z0-9]+(?:-[a-z0-9]+)*)/([0-9]+)/[^/?#]+/?$", RegexOption.IGNORE_CASE)
  private fun integer(value: Any?, label: String, range: LongRange): Int {
    require(value is Int || value is Long) { "$label debe ser un número entero." }
    val n = (value as Number).toLong()
    require(n in range) { "$label fuera de rango." }
    return n.toInt()
  }

  fun parse(text: String): ZoneImportPlan {
    require(text.length <= MAX_BYTES && text.toByteArray(Charsets.UTF_8).size <= MAX_BYTES) { "JSON demasiado grande (máximo 3 MB)." }
    val tokens = JSONTokener(text.removePrefix("\uFEFF"))
    val root = tokens.nextValue() as? JSONObject ?: error("Se necesita un objeto JSON.")
    require(tokens.nextClean() == '\u0000') { "Contenido extra después del JSON." }
    require(integer(root.opt("schemaVersion"), "Versión", 1L..1L) == 1) { "Versión no compatible." }
    require(root.opt("source") == "pokemon-zone") { "El archivo no procede de Pokémon Zone." }
    val player = root.opt("friendId") as? String ?: error("Falta el ID del jugador.")
    require(Regex("[0-9]{16}").matches(player)) { "ID de jugador inválido." }
    val url = URI(root.opt("url") as? String ?: error("Falta la URL de origen."))
    require(url.scheme == "https" && url.host in setOf("www.pokemon-zone.com", "pokemon-zone.com") &&
      url.userInfo == null && url.port == -1 && url.query == null && url.fragment == null &&
      url.path == "/players/$player/cards/") { "La URL debe corresponder a las cartas del mismo jugador." }
    val date = root.opt("readAt") as? String ?: error("Falta la fecha de lectura.")
    Instant.parse(date)
    val complete = root.opt("collectionComplete") as? Boolean ?: error("Falta el estado de la colección.")
    val rows = root.optJSONArray("visibleCards") ?: error("Falta la lista visibleCards.")
    require(rows.length() in 1..10000) { "La lista de cartas está vacía o es demasiado grande." }
    val parsed = linkedMapOf<String, ParsedCsvCard>()
    var duplicates = 0
    for (i in 0 until rows.length()) {
      val row = rows.optJSONObject(i) ?: error("Fila ${i + 1}: objeto inválido.")
      val match = cardPath.matchEntire(row.opt("cardPath") as? String ?: "")
        ?: error("Fila ${i + 1}: ruta de carta inválida.")
      val id = CardId.normalize("${match.groupValues[1]}-${match.groupValues[2]}")
      val quantity = integer(row.opt("quantity"), "Cantidad en fila ${i + 1}", 0L..99999L)
      // Catalog identity and rarity are authoritative; names/slugs are never used as IDs.
      val card = CardCatalog.getCardById(id) ?: error("Carta $id ausente del catálogo. No se importó ninguna carta.")
      val previous = parsed[id]
      if (previous != null) {
        require(previous.quantity == quantity) { "Cantidades contradictorias para $id. No se importó ninguna carta." }
        duplicates++
      } else {
        val (set, number) = CardId.split(id)
        parsed[id] = ParsedCsvCard(set, number, card.name, card.rarity.symbol, quantity, quantity > 0, null)
      }
    }
    return ZoneImportPlan(player, date, complete, parsed.values.toList(), duplicates)
  }

  /** Updates and verifies both real collection tables atomically; retries never add copies. */
  suspend fun apply(db: AppDatabase, plan: ZoneImportPlan) = db.withTransaction {
    InventoryRepository.fromDatabase(db).processParsedCsvCards(plan.cards)
    val stored = db.inventoryDao().getAllCards().associateBy { it.cardId }
    val users = db.userCardDao().getAllUserCardsFlow().first().associateBy { it.cardId }
    plan.cards.forEach { row ->
      val id = CardId.normalize("${row.setCode}-${row.cardNumber}")
      check(stored[id]?.quantity == row.quantity && users[id]?.quantity == row.quantity) {
        "Falló la verificación; se canceló toda la importación."
      }
    }
  }
}
