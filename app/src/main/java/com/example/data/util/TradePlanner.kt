package com.example.data.util

import com.example.data.model.CardRarity
import com.example.data.repository.CardWithInventory
import com.example.data.repository.ParsedCsvCard

data class TradeProposal(val rarity: CardRarity, val offer: List<CardWithInventory>, val request: List<CardWithInventory>)
object TradePlanner {
  fun compare(mine: List<CardWithInventory>, peer: List<ParsedCsvCard>, reserve: Int): List<TradeProposal> {
    require(reserve in 1..2)
    val other = peer.associateBy { CardId.normalize("${it.setCode}-${it.cardNumber}") }
    val offer = mine.filter { it.ownedCount > reserve && other[it.card.id]?.quantity == 0 }
    val request = mine.filter { it.ownedCount == 0 && (other[it.card.id]?.quantity ?: 0) > reserve }
      .sortedByDescending { it.isWishlist }
    return CardRarity.entries.mapNotNull { rarity ->
      val give = offer.filter { it.card.rarity == rarity }
      val receive = request.filter { it.card.rarity == rarity }
      if (give.isEmpty() || receive.isEmpty()) null else TradeProposal(rarity, give, receive)
    }
  }
}
