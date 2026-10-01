package com.example.data.util

import com.example.data.repository.CardWithInventory
import kotlin.math.pow

data class PackCoverage(val set: String, val pack: String, val missing: Int, val wishes: Int, val targets: List<String>) {
  val score: Int get() = missing + wishes * 3 + targets.size * 5
}
object CollectionInsights {
  fun coverage(inventory: List<CardWithInventory>, targets: Set<String> = emptySet()): List<PackCoverage> {
    val groups = linkedMapOf<Pair<String, String>, MutableList<CardWithInventory>>()
    inventory.forEach { item ->
      val set = CardId.split(item.card.id).first
      if (!set.startsWith("PROMO-")) item.card.packNames.forEach { pack ->
        groups.getOrPut(set to pack) { mutableListOf() }.add(item)
      }
    }
    return groups.map { (key, cards) ->
      val missing = cards.filter { it.ownedCount == 0 }
      PackCoverage(key.first, key.second, missing.size, missing.count { it.isWishlist },
        missing.filter { it.card.id in targets }.map { "${it.card.id} · ${it.card.name}" })
    }.sortedWith(compareByDescending<PackCoverage> { it.score }.thenBy { it.set }.thenBy { it.pack })
  }
  fun cumulativeChance(probability: Double, attempts: Int): Double {
    require(probability.isFinite() && probability in 0.0..1.0 && attempts >= 0)
    return 1.0 - (1.0 - probability).pow(attempts)
  }
  fun drawChance(deck: Int, targets: Int, draws: Int): Double {
    require(deck in 1..100 && targets in 0..deck && draws in 0..deck)
    if (draws > deck - targets) return 1.0
    var none = 1.0
    repeat(draws) { i -> none *= (deck - targets - i).toDouble() / (deck - i) }
    return 1.0 - none
  }
}
