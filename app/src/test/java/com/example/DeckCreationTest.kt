package com.example

import com.example.data.model.*
import com.example.data.repository.*
import com.example.data.local.CardRulesEntity
import com.example.domain.*
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DeckCreationTest {
  private fun card(id: String, name: String = id, category: String = "pokemon", stage: String = "basic") =
    PokemonCard(id, name, BoosterPack.UNKNOWN, CardRarity.ONE_DIAMOND, 60,
      if (category == "pokemon") "Fuego" else "Entrenador", "", "", category = category, stage = stage)
  @Test fun manualFiltersCombineSubtypeCopiesExpansionAndStageWithoutGuessingUnknowns() {
    val inventory = listOf(
      CardWithInventory(card("A1-001", "Poción", "item"), 2, false),
      CardWithInventory(card("A1-002", "Profesor", "supporter"), 1, false),
      CardWithInventory(card("A1-003", "Charmander"), 2, false),
      CardWithInventory(card("A2-001", "Desconocida", "unknown", "unknown"), 3, false))
    assertEquals(listOf("A1-001"), DeckCardFilter(category = "item", availability = "sufficient")
      .select(inventory, mapOf("A1-001" to 1), emptyMap()).map { it.card.id })
    assertTrue(DeckCardFilter(category = "item", availability = "sufficient")
      .select(inventory, mapOf("A1-001" to 2), emptyMap()).isEmpty())
    assertEquals(listOf("A1-003"), DeckCardFilter(stage = "basic", element = "Fuego", expansion = "A1")
      .select(inventory, emptyMap(), emptyMap()).map { it.card.id })
    assertEquals("Poción", DeckCardFilter(query = "pocion").select(inventory, emptyMap(), emptyMap()).single().card.name)
    assertTrue(DeckCardFilter(category = "tool").select(inventory, emptyMap(), emptyMap()).isEmpty())
  }
  @Test fun effectsUseCachedTagsAndRetainPromoExpansionIdentity() {
    val item = CardWithInventory(card("PROMO-A-001"), 1, false)
    val rules = CardRulesEntity(item.card.id, "es", 60, "Fuego", "Roba 2 cartas.", "roba 2 cartas", "|draw|", 1)
    val filter = DeckCardFilter(effect = "draw", expansion = "PROMO-A")
    assertTrue(filter.select(listOf(item), emptyMap(), emptyMap()).isEmpty())
    assertEquals(listOf(item), filter.select(listOf(item), emptyMap(), mapOf(item.card.id to rules)))
  }
  private val candidates get() = (1..10).map { AiCandidate(card("A1-${it.toString().padStart(3, '0')}"), 2, "") }
  private fun proposal(): JSONObject = JSONObject().put("name", "Prueba").put("strategy", "Lista para revisar")
    .put("energies", JSONArray(listOf("Fuego"))).put("replacements", JSONArray())
    .put("cards", JSONArray(candidates.map { JSONObject().put("id", it.card.id).put("count", 2) }))
  @Test fun chatCanAnswerWithoutChangesAndValidatesProposalsAgainstCurrentCopies() {
    assertNull(DeckChat.parse("""{"message":"Puedes revisar las energías.","proposal":null}""", candidates).proposal)
    val response = JSONObject().put("message", "Sugiero esta baraja").put("proposal", proposal()).toString()
    assertEquals(20, DeckChat.parse(response, candidates).proposal!!.deck.totalCardCount)
    try { DeckChat.parse(response, candidates.map { it.copy(owned = 1) }); fail("Must reject excess copies") }
    catch (_: IllegalArgumentException) { }
    val invalid = proposal().put("cards", JSONArray().put(JSONObject().put("id", "A1-999").put("count", 2)))
    try { DeckChat.parse(JSONObject().put("message", "Cambios").put("proposal", invalid).toString(), candidates); fail("Must reject invented IDs") }
    catch (_: IllegalStateException) { }
  }
  @Test fun applyingRejectsChangedDraftAndChangedCollection() {
    val proposed = DeckChat.parse(JSONObject().put("message", "Lista").put("proposal", proposal()).toString(), candidates).proposal!!
    val base = proposed.deck.copy(name = "Anterior")
    val suggestion = DeckChatSuggestion(base, proposed)
    val owned = candidates.associate { it.card.id to it.owned }
    DeckChat.requireApplicable(suggestion, base, owned)
    try { DeckChat.requireApplicable(suggestion, base.copy(strategy = "Nueva nota"), owned); fail("Stale draft") }
    catch (_: IllegalArgumentException) { }
    try { DeckChat.requireApplicable(suggestion, base, owned.mapValues { 0 }); fail("Stale inventory") }
    catch (_: IllegalArgumentException) { }
  }
  @Test fun reviewDiffShowsActualQuantitiesAndConversationIncludesCurrentEnergies() {
    val entries = candidates.map { DeckCardEntry(it.card, 2) }
    val before = GeneratedDeck("Mi mazo", "Manual", "Notas", entries.dropLast(1), 18, energyTypes = listOf("Fuego"))
    val after = GeneratedDeck("Mi mazo", "IA", "", entries, 20, energyTypes = listOf("Fuego"))
    val (removed, added) = DeckChat.changes(before, after)
    assertTrue(removed.isEmpty()); assertEquals(listOf(DeckCardChange("A1-010", 2)), added)
    val prompt = DeckChat.prompt(before, candidates, listOf(DeckChatMessage(true, "Mantén mis Pokémon")), "complete")
    val context = JSONObject(prompt.substringAfter("CONTEXTO:\n"))
    assertEquals("Fuego", context.getJSONObject("deck").getJSONArray("energies").getString(0))
    assertEquals("Mantén mis Pokémon", context.getJSONArray("history").getJSONObject(0).getString("text"))
    assertEquals(18, context.getJSONObject("deck").getJSONArray("cards").length() * 2)
  }
}
