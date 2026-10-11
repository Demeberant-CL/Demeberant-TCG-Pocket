package com.example.domain

/** Exact sampling without replacement. Does not model Pocket's guaranteed opening Basic,
 * searches, extra draw, evolution timing or opposing play. */
object DeckConsistency {
  private fun choose(n: Int, k: Int): Double {
    if (k < 0 || k > n) return 0.0
    var result = 1.0
    val count = minOf(k, n - k)
    for (i in 1..count) result *= (n - count + i).toDouble() / i
    return result
  }

  /** Probability of seeing at least one copy of EACH disjoint card-name group. */
  fun allPieces(deckSize: Int, copies: List<Int>, drawn: Int): Double {
    require(deckSize in 1..20 && drawn in 0..deckSize)
    require(copies.size <= 4 && copies.all { it >= 0 } && copies.sum() <= deckSize)
    val denominator = choose(deckSize, drawn)
    var result = 0.0
    for (mask in 0 until (1 shl copies.size)) {
      val excluded = copies.indices.filter { mask and (1 shl it) != 0 }.sumOf { copies[it] }
      val term = choose(deckSize - excluded, drawn) / denominator
      result += if (Integer.bitCount(mask) % 2 == 0) term else -term
    }
    return result.coerceIn(0.0, 1.0)
  }
}
