package com.example

import com.example.data.repository.CardCatalog
import com.example.data.repository.CardWithInventory
import com.example.data.util.CollectionFilter
import org.junit.Assert.*
import org.junit.Test

class CollectionFilterTest {
  private val card = CardCatalog.ALL_CARDS.first()

  @Test fun ownedExcludesZeroAndIncludesMultipleCopies() {
    assertFalse(CollectionFilter.OWNED.matches(CardWithInventory(card, 0, false)))
    assertTrue(CollectionFilter.OWNED.matches(CardWithInventory(card, 1, false)))
    assertTrue(CollectionFilter.OWNED.matches(CardWithInventory(card, 2, false)))
  }

  @Test fun missingExcludesOwnedAndFavoritesDoNotRequireMissing() {
    assertTrue(CollectionFilter.MISSING.matches(CardWithInventory(card, 0, false)))
    assertFalse(CollectionFilter.MISSING.matches(CardWithInventory(card, 1, true)))
    assertTrue(CollectionFilter.FAVORITES.matches(CardWithInventory(card, 1, true)))
    assertTrue(CollectionFilter.FAVORITES.matches(CardWithInventory(card, 0, true)))
    assertFalse(CollectionFilter.FAVORITES.matches(CardWithInventory(card, 0, false)))
    assertTrue(CollectionFilter.ALL.matches(CardWithInventory(card, 0, false)))
  }
  @Test fun repeatedRequiresMoreThanOneCopy() {
    assertFalse(CollectionFilter.REPEATED.matches(CardWithInventory(card, 0, true)))
    assertFalse(CollectionFilter.REPEATED.matches(CardWithInventory(card, 1, true)))
    assertTrue(CollectionFilter.REPEATED.matches(CardWithInventory(card, 2, false)))
    assertTrue(CollectionFilter.REPEATED.matches(CardWithInventory(card, 5, false)))
  }
}
