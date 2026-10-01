package com.example.data.util

import com.example.data.repository.CardWithInventory

/** Exactly one collection status is selected; the booster filter is independent. */
enum class CollectionFilter {
  ALL, OWNED, MISSING, FAVORITES;

  fun matches(item: CardWithInventory): Boolean = when (this) {
    ALL -> true
    OWNED -> item.ownedCount > 0
    MISSING -> item.ownedCount == 0
    FAVORITES -> item.isWishlist
  }
}
