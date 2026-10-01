package com.example.data.util

import com.example.data.repository.CardWithInventory

/** Exactly one collection view is selected at a time. */
enum class CollectionFilter {
  ALL, OWNED, MISSING, FAVORITES, REPEATED;

  fun matches(item: CardWithInventory): Boolean = when (this) {
    ALL -> true
    OWNED -> item.ownedCount > 0
    MISSING -> item.ownedCount == 0
    FAVORITES -> item.isWishlist
    REPEATED -> item.ownedCount > 1
  }
}
