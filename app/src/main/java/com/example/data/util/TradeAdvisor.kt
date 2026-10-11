package com.example.data.util

import com.example.data.local.SavedDeckEntity
import com.example.data.model.CardRarity
import com.example.data.repository.CardWithInventory
import com.example.data.repository.ParsedCsvCard

data class TradeSettings(val reserve: Int = 2, val blocked: Set<String> = emptySet(),
  val expansion: String = "", val targetDeck: String = "", val targetName: String = "")
data class TradeChoice(val offer: CardWithInventory, val request: CardWithInventory,
  val remaining: Int, val score: Int, val reasons: List<String>, val peerMatched: Boolean)
data class TradeAdvice(val offers: List<CardWithInventory>, val wanted: List<CardWithInventory>,
  val choices: List<TradeChoice>, val protected: Map<String, Int>, val deckMissing: Int,
  val invalidDecks: Int = 0)

/** Suggestions to arrange in-game, never an assertion of eligibility or a trade execution. */
object TradeAdvisor {
  private fun possible(item: CardWithInventory): Boolean = !item.card.id.startsWith("PROMO") &&
    item.card.rarity in setOf(CardRarity.ONE_DIAMOND, CardRarity.TWO_DIAMONDS,
      CardRarity.THREE_DIAMONDS, CardRarity.FOUR_DIAMONDS, CardRarity.ONE_STAR, CardRarity.TWO_STARS)
  fun analyze(inventory: List<CardWithInventory>, decks: List<SavedDeckEntity>, settings: TradeSettings,
    peer: List<ParsedCsvCard>? = null): TradeAdvice {
    require(settings.reserve in 1..2)
    val owned = inventory.associateBy { CardId.normalize(it.card.id) }
    val protected = mutableMapOf<String, Int>()
    var invalid = 0
    decks.forEach { deck ->
      val refs = runCatching { DeckCodec.references(deck.cardListSerialized) }.getOrNull()
      if (refs == null) invalid++ else refs.forEach { (id, n) ->
        val key = CardId.normalize(id)
        protected[key] = maxOf(protected[key] ?: 0, n)
      }
    }
    val target = runCatching { DeckCodec.references(settings.targetDeck) }.getOrDefault(emptyList())
      .associate { CardId.normalize(it.first) to it.second }
    target.forEach { (id, n) -> protected[id] = maxOf(protected[id] ?: 0, n) }
    val other = peer?.associateBy { CardId.normalize("${it.setCode}-${it.cardNumber}") }
    fun missing(item: CardWithInventory): Int = ((target[item.card.id] ?: 0) - item.ownedCount).coerceAtLeast(0)
    fun priority(item: CardWithInventory): Int = missing(item) * 100 + (if (item.isWishlist) 60 else 0) +
      (if (settings.expansion.isNotBlank() && item.card.id.substringBeforeLast('-') == settings.expansion) 30 else 0) +
      (if (item.ownedCount == 0) 10 else 0)
    val offers = inventory.filter { item -> invalid == 0 && possible(item) && item.card.id !in settings.blocked &&
      !item.isWishlist && item.ownedCount > maxOf(settings.reserve, protected[item.card.id] ?: 0) &&
      (other == null || other[item.card.id]?.quantity == 0) }
      .sortedWith(compareByDescending<CardWithInventory> { it.ownedCount - maxOf(settings.reserve, protected[it.card.id] ?: 0) }.thenBy { it.card.id })
    val wanted = inventory.filter { possible(it) && (it.ownedCount == 0 || missing(it) > 0 || (it.isWishlist && it.ownedCount < 2)) &&
      (other == null || (other[it.card.id]?.quantity ?: 0) > settings.reserve) }
      .sortedWith(compareByDescending<CardWithInventory> { priority(it) }.thenBy { it.card.id })
    val byRarity = offers.groupBy { it.card.rarity }
    val choices = wanted.mapNotNull { request ->
      val give = byRarity[request.card.rarity]?.firstOrNull { it.card.id != request.card.id } ?: return@mapNotNull null
      val reasons = buildList {
        if (missing(request) > 0) add("Faltan ${missing(request)} copias para tu mazo objetivo")
        if (request.isWishlist) add("Está en tus Deseos")
        if (settings.expansion.isNotBlank() && request.card.id.substringBeforeLast('-') == settings.expansion) add("Avanza tu expansión objetivo")
        if (request.ownedCount == 0) add("Añade una carta distinta a tu colección")
        add("Conservas tu reserva y las copias exactas de tus mazos")
      }
      TradeChoice(give, request, give.ownedCount - 1, priority(request), reasons, other != null)
    }.take(30)
    return TradeAdvice(offers, wanted, choices, protected,
      target.entries.sumOf { (id, n) -> (n - (owned[id]?.ownedCount ?: 0)).coerceAtLeast(0) }, invalid)
  }
}
