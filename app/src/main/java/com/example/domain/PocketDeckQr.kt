package com.example.domain

import com.example.data.repository.GeneratedDeck
import com.example.data.util.CardId
import com.example.data.util.DeckCodec
import org.json.JSONArray
import java.io.ByteArrayOutputStream
import java.util.Base64
import java.util.Locale

data class QrCardIdentity(val id: String, val entity: Int, val kind: String, val name: String)
data class PocketDeckPayload(val trainers: List<Int>, val pokemon: List<Int>, val energies: List<Int>)

/** Binary format adapted from Kevin Gutowski's tcgp-deck-qr (MIT); see THIRD_PARTY_NOTICES. */
object PocketDeckQr {
  fun readIdentities(text: String): Map<String, QrCardIdentity> {
    val rows = JSONArray(text)
    val entries = (0 until rows.length()).map { index ->
      val row = rows.getJSONObject(index)
      val entity = row.getInt("entity")
      val kind = row.getString("kind")
      require(entity > 0 && entity <= 999999 && kind in setOf("pokemon", "trainer"))
      QrCardIdentity(CardId.normalize(row.getString("id")), entity, kind, row.getString("name"))
    }
    require(entries.map { it.id }.distinct().size == entries.size)
    return entries.associateBy { it.id }
  }

  fun resolve(deck: GeneratedDeck, identities: Map<String, QrCardIdentity>): PocketDeckPayload {
    require(deck.cards.sumOf { it.count } == 20 && deck.totalCardCount == 20) { "El QR requiere exactamente 20 cartas." }
    require(deck.cards.all { it.count in 1..2 }) { "Las cantidades deben ser 1 o 2." }
    require(deck.cards.map { CardId.normalize(it.card.id) }.distinct().size == deck.cards.size) { "Hay cartas duplicadas." }
    val resolved = deck.cards.map { entry ->
      val identity = identities[CardId.normalize(entry.card.id)] ?: error("Carta sin identificador del juego: " + entry.card.id)
      identity to entry.count
    }
    require(resolved.groupBy { it.first.name.replace('’', '\'').lowercase(Locale.ROOT) }.values.all { entries -> entries.sumOf { it.second } <= 2 }) {
      "Máximo de dos copias por nombre, incluidas variantes."
    }
    require(deck.energyTypes.size in 1..3 && deck.energyTypes.distinct().size == deck.energyTypes.size &&
      deck.energyTypes.all { it in DeckCodec.energyNames }) { "Selecciona entre una y tres energías del juego." }
    val trainers = mutableListOf<Int>()
    val pokemon = mutableListOf<Int>()
    resolved.forEach { (identity, count) ->
      repeat(count) { if (identity.kind == "trainer") trainers.add(identity.entity) else pokemon.add(identity.entity) }
    }
    require(pokemon.isNotEmpty()) { "El mazo necesita Pokémon." }
    return PocketDeckPayload(trainers, pokemon, deck.energyTypes.map { DeckCodec.energyNames.indexOf(it) + 1 })
  }

  fun encode(payload: PocketDeckPayload): String {
    require(payload.trainers.size + payload.pokemon.size == 20 && payload.pokemon.isNotEmpty())
    require(payload.energies.size in 1..3 && payload.energies.distinct().size == payload.energies.size &&
      payload.energies.all { it in 1..8 }) { "Energía no admitida por el editor del juego." }
    val bytes = ByteArrayOutputStream()
    fun write24(value: Int) {
      require(value in 1..0xFFFFFF)
      bytes.write(value ushr 16 and 255); bytes.write(value ushr 8 and 255); bytes.write(value and 255)
    }
    bytes.write(payload.trainers.size)
    payload.trainers.forEach { require(it in 1..999999); write24(it + 10000000) }
    bytes.write(payload.pokemon.size)
    payload.pokemon.forEach { require(it in 1..999999); write24(it) }
    bytes.write(payload.energies.size)
    payload.energies.forEach(bytes::write)
    return Base64.getEncoder().encodeToString(bytes.toByteArray())
  }
}
