package com.example.data.util

import com.example.data.repository.CardCatalog
import com.example.data.repository.DeckCardEntry
import org.json.JSONArray
import org.json.JSONObject

object DeckCodec {
  val energyNames = listOf("Planta", "Fuego", "Agua", "Rayo", "Psíquico", "Lucha", "Oscuridad", "Metal")
  fun encode(cards: List<DeckCardEntry>, energies: List<String>): String =
    JSONObject().put("format", 2).put("energies", JSONArray(energies)).put("cards",
      JSONArray(cards.map { JSONObject().put("id", it.card.id).put("count", it.count) })).toString()

  fun references(text: String): List<Pair<String, Int>> {
    val result = if (text.trim().startsWith("{")) {
      val root = JSONObject(text)
      require(root.getInt("format") == 2) { "Formato de mazo no compatible." }
      val cards = root.getJSONArray("cards")
      (0 until cards.length()).map { i ->
        val card = cards.getJSONObject(i)
        val count = card.getInt("count")
        require(card.get("count").toString() == count.toString()) { "Cantidad de mazo no válida." }
        CardId.normalize(card.getString("id")) to count
      }
    } else text.split(";").filter { it.isNotBlank() }.map {
      val parts = it.split(":")
      require(parts.size == 2) { "Carta de mazo no válida." }
      CardId.normalize(parts[0]) to (parts[1].toIntOrNull() ?: error("Cantidad de mazo no válida."))
    }
    require(result.all { it.second in 1..2 } && result.sumOf { it.second } <= 20) { "Cantidades de mazo no válidas." }
    require(result.map { it.first }.distinct().size == result.size) { "Carta duplicada en el mazo." }
    return result
  }

  fun decode(text: String): List<DeckCardEntry> = references(text).map { (id, count) ->
    DeckCardEntry(CardCatalog.getCardById(id) ?: error("Carta $id no disponible."), count)
  }

  fun energies(text: String): List<String> {
    if (!text.trim().startsWith("{")) return emptyList()
    val root = JSONObject(text)
    require(root.getInt("format") == 2)
    val array = root.getJSONArray("energies")
    val result = (0 until array.length()).map { array.getString(it) }
    require(result.size <= 3 && result.distinct().size == result.size && result.all { it in energyNames }) {
      "Energías no válidas."
    }
    return result
  }
}
