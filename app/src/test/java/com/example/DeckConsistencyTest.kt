package com.example

import com.example.domain.DeckConsistency
import org.junit.Assert.*
import org.junit.Test

class DeckConsistencyTest {
  @Test fun twoCopiesInEightOfTwenty() {
    assertEquals(1.0 - (12.0 * 11) / (20 * 19), DeckConsistency.allPieces(20, listOf(2), 8), 1e-12)
  }
  @Test fun twoDistinctSingletonsNeedBoth() {
    assertEquals(8.0 * 7 / (20 * 19), DeckConsistency.allPieces(20, listOf(1, 1), 8), 1e-12)
  }
  @Test fun boundariesAndMissingPiece() {
    assertEquals(0.0, DeckConsistency.allPieces(20, listOf(2, 2), 0), 1e-12)
    assertEquals(1.0, DeckConsistency.allPieces(20, listOf(2, 2, 2), 20), 1e-12)
    assertEquals(0.0, DeckConsistency.allPieces(20, listOf(2, 0), 8), 1e-12)
    assertTrue(runCatching { DeckConsistency.allPieces(20, listOf(2), 21) }.isFailure)
  }
}
