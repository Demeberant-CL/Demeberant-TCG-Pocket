package com.example.domain

import com.example.data.repository.DeckCardEntry
import com.example.data.repository.DeckBuilderEngine
import com.example.data.repository.GeneratedDeck
import com.example.data.util.CardId
import com.example.data.util.DeckCodec
import com.example.data.model.PokemonCard
import org.json.JSONObject

data class AiCandidate(val card: PokemonCard, val owned: Int, val text: String)
data class AiReplacement(val removedId: String, val addedId: String, val count: Int, val reason: String)
data class AiProposal(val deck: GeneratedDeck, val replacements: List<AiReplacement>)

object AiValidator {
  fun requireAvailable(deck: GeneratedDeck, owned: Map<String, Int>) {
    require(deck.totalCardCount == 20 && deck.cards.sumOf { it.count } == 20)
    require(deck.cards.all { it.count in 1..2 && it.count <= (owned[it.card.id] ?: 0) }) {
      "Tu colección cambió o no tiene las copias necesarias. Genera una nueva propuesta."
    }
  }
  fun parse(text: String, candidates: List<AiCandidate>, target: List<DeckCardEntry>? = null): AiProposal {
    require(text.length <= 100_000)
    val root = JSONObject(text)
    val refs = DeckCodec.references(JSONObject().put("format", 2).put("energies", root.getJSONArray("energies"))
      .put("cards", root.getJSONArray("cards")).toString())
    val byId = candidates.associateBy { it.card.id }
    val entries = refs.map { (id, count) ->
      val candidate = byId[id] ?: error("La IA propuso un ID no permitido.")
      require(count <= candidate.owned) { "La IA excedió la colección disponible." }
      DeckCardEntry(candidate.card, count)
    }
    val warnings = DeckBuilderEngine.validate(entries)
    require(warnings.isEmpty()) { "Propuesta no válida: ${warnings.joinToString(" ")}" }
    val energies = DeckCodec.energies(JSONObject().put("format", 2).put("cards", root.getJSONArray("cards"))
      .put("energies", root.getJSONArray("energies")).toString())
    require(energies.isNotEmpty()) { "Falta la selección de energías." }
    val name = root.getString("name").trim()
    require(name.isNotEmpty() && name.length <= 120)
    val rows = root.getJSONArray("replacements")
    val replacements = (0 until rows.length()).map { i ->
      val row = rows.getJSONObject(i)
      require(row.has("count") && !row.isNull("count")) {
        "La IA omitió la cantidad de un reemplazo. Reintenta la consulta; el mazo abierto se conserva."
      }
      val count = row.optInt("count", -1)
      require(count in 1..2 && row.get("count").toString() == count.toString()) {
        "La IA devolvió una cantidad de reemplazo no válida. No se aplicaron cambios."
      }
      AiReplacement(CardId.normalize(row.getString("removedId")), CardId.normalize(row.getString("addedId")),
        count, row.getString("reason").take(1000))
    }
    if (target == null) require(replacements.isEmpty())
    else {
      require(target.sumOf { it.count } == 20)
      val old = target.associate { it.card.id to it.count }
      val next = entries.associate { it.card.id to it.count }
      old.forEach { (id, count) ->
        require((next[id] ?: 0) >= minOf(count, byId[id]?.owned ?: 0)) { "La IA quitó cartas que ya tenías." }
      }
      val removed = old.mapValues { (id, count) -> maxOf(0, count - (next[id] ?: 0)) }.filterValues { it > 0 }
      val added = next.mapValues { (id, count) -> maxOf(0, count - (old[id] ?: 0)) }.filterValues { it > 0 }
      require(replacements.groupBy { it.removedId }.mapValues { (_, list) -> list.sumOf { it.count } } == removed)
      require(replacements.groupBy { it.addedId }.mapValues { (_, list) -> list.sumOf { it.count } } == added)
    }
    return AiProposal(GeneratedDeck(name, "Asistente IA", root.getString("strategy").take(5000), entries, 20,
      energyTypes = energies), replacements)
  }
}
