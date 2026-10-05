package com.example.data.model

enum class CardRarity(val symbol: String, val displayName: String) {
  ONE_DIAMOND("♦", "1 Diamante"),
  TWO_DIAMONDS("♦♦", "2 Diamantes"),
  THREE_DIAMONDS("♦♦♦", "3 Diamantes"),
  FOUR_DIAMONDS("♦♦♦♦", "4 Diamantes"),
  ONE_STAR("★", "1 Estrella"),
  TWO_STARS("★★", "2 Estrellas"),
  THREE_STARS("★★★", "3 Estrellas (Inmersiva)"),
  CROWN("♛", "Corona"),
  SHINY_ONE("✷", "1 Brillo"),
  SHINY_TWO("✷✷", "2 Brillos");

  companion object {
    fun fromSymbol(symbol: String): CardRarity {
      val value = symbol.trim()
      return entries.find { it.symbol == value || it.displayName.equals(value, ignoreCase = true) || it.name == value }
        ?: throw IllegalArgumentException("Rareza no reconocida: $value")
    }
  }
}
