package com.example.data.model

enum class CardRarity(val symbol: String, val displayName: String) {
  ONE_DIAMOND("♦", "1 Diamante"),
  TWO_DIAMONDS("♦♦", "2 Diamantes"),
  THREE_DIAMONDS("♦♦♦", "3 Diamantes"),
  FOUR_DIAMONDS("♦♦♦♦", "4 Diamantes"),
  ONE_STAR("★", "1 Estrella"),
  TWO_STARS("★★", "2 Estrellas"),
  THREE_STARS("★★★", "3 Estrellas (Inmersiva)"),
  CROWN("♛", "Corona");

  companion object {
    fun fromSymbol(symbol: String): CardRarity {
      return entries.find { it.symbol == symbol.trim() } ?: ONE_DIAMOND
    }
  }
}
