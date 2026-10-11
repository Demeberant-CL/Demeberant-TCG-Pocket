package com.example

import com.example.data.local.SavedDeckEntity
import com.example.data.model.*
import com.example.data.repository.*
import com.example.data.util.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TradeAdvisorTest {
  private fun card(id: String, n: Int, wish: Boolean = false, rarity: CardRarity = CardRarity.ONE_DIAMOND) = CardWithInventory(
    PokemonCard(id, id, BoosterPack.UNKNOWN, rarity, 70, "Planta", "", "", category = "pokemon", stage = "basic"), n, wish)
  private fun deck(vararg cards: CardWithInventory) = SavedDeckEntity(name = "Objetivo", archetype = "", strategy = "",
    cardListSerialized = DeckCodec.encode(cards.map { DeckCardEntry(it.card, 2) }, listOf("Planta")), totalCards = cards.size * 2)
  @Test fun reservesProtectSavedDecksWithoutAddingAcrossDecks() {
    val offer = card("A1-001", 3); val receive = card("A1-002", 0)
    val d = deck(offer)
    val result = TradeAdvisor.analyze(listOf(offer, receive), listOf(d, d.copy(name = "Otro")), TradeSettings(reserve = 1))
    assertEquals(2, result.protected[offer.card.id])
    assertEquals(2, result.choices.single().remaining)
    assertTrue(TradeAdvisor.analyze(listOf(offer.copy(ownedCount = 2), receive), listOf(d), TradeSettings(reserve = 1)).offers.isEmpty())
  }
  @Test fun blocksWishesAndUnparsedDecksPreventUnsafeOffers() {
    val offer = card("A1-001", 5); val receive = card("A1-002", 0)
    assertTrue(TradeAdvisor.analyze(listOf(offer, receive), emptyList(), TradeSettings(blocked = setOf(offer.card.id))).choices.isEmpty())
    assertTrue(TradeAdvisor.analyze(listOf(offer.copy(isWishlist = true), receive), emptyList(), TradeSettings()).offers.isEmpty())
    val broken = deck(offer).copy(cardListSerialized = "broken")
    val result = TradeAdvisor.analyze(listOf(offer, receive), listOf(broken), TradeSettings())
    assertEquals(1, result.invalidDecks); assertTrue(result.offers.isEmpty())
  }
  @Test fun targetDeckPrioritizesSecondCopyBeforeGenericMissing() {
    val offer = card("A1-001", 4); val generic = card("A1-002", 0)
    val target = card("A1-003", 1); val d = deck(target)
    val advice = TradeAdvisor.analyze(listOf(offer, generic, target), listOf(d), TradeSettings(targetDeck = d.cardListSerialized))
    assertEquals(target.card.id, advice.choices.first().request.card.id)
    assertEquals(1, advice.deckMissing)
    assertFalse(advice.choices.first().peerMatched)
  }
  @Test fun incompatibleRarityAndExcludedCardsNeverPair() {
    val offer = card("A1-001", 3)
    val missing = card("A1-002", 0, rarity = CardRarity.TWO_DIAMONDS)
    val promo = card("PROMO-001", 0); val crown = card("A1-003", 3, rarity = CardRarity.CROWN)
    val advice = TradeAdvisor.analyze(listOf(offer, missing, promo, crown), emptyList(), TradeSettings())
    assertTrue(advice.choices.isEmpty()); assertFalse(advice.wanted.any { it.card.id == promo.card.id })
    assertFalse(advice.offers.any { it.card.id == crown.card.id })
  }
  @Test fun expansionGoalWinsOverGenericMissing() {
    val advice = TradeAdvisor.analyze(listOf(card("A1-001", 4), card("A1-002", 0), card("A2-001", 0)), emptyList(), TradeSettings(expansion = "A2"))
    assertEquals("A2-001", advice.choices.first().request.card.id)
  }
  @Test fun csvRequiresExplicitReciprocalSurplus() {
    val offer = card("A1-001", 3); val receive = card("A1-002", 0)
    val rows = listOf(ParsedCsvCard("A1", "001", "Offer", "♦", 0, true),
      ParsedCsvCard("A1", "002", "Receive", "♦", 3, true))
    assertTrue(TradeAdvisor.analyze(listOf(offer, receive), emptyList(), TradeSettings(), rows).choices.single().peerMatched)
    assertTrue(TradeAdvisor.analyze(listOf(offer, receive), emptyList(), TradeSettings(), rows.drop(1)).choices.isEmpty())
    assertTrue(TradeAdvisor.analyze(listOf(offer, receive), emptyList(), TradeSettings(), rows.map { it.copy(quantity = 2) }).choices.isEmpty())
  }
  @Test fun backupRetainsNewDesignAndTradeProtectionWithLegacyDefaults() {
    val prefs = com.example.data.preferences.UserPreferences(visualStyle = com.example.data.preferences.VisualStyle.ALBUM,
      trade = TradeSettings(1, setOf("A1-001"), "A2", deck(card("A1-002", 0)).cardListSerialized))
    val text = AppBackup.encode(BackupSnapshot(emptyList(), emptyList(), prefs))
    assertEquals(prefs, AppBackup.decode(text).preferences)
    val root = org.json.JSONObject(text)
    root.getJSONObject("preferences").remove("trade")
    assertEquals(TradeSettings(), AppBackup.decode(root.toString()).preferences.trade)
  }
}
