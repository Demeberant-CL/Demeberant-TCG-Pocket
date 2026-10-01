package com.example.data.util

object TcgdexHelper {
  fun getCardImageUrl(cardFullId: String, lang: String = "es"): String {
    val (set, number) = CardId.split(cardFullId)
    return getCardImageUrl(set, number, lang)
  }

  fun getCardImageUrl(setId: String, cardNum: String, lang: String = "es"): String {
    val (set, number) = CardId.split("$setId-$cardNum")
    val language = lang.takeIf { it in setOf("es", "en", "ja") } ?: "es"
    val assetSet = when (set) {
      "PROMO-A" -> "P-A"
      "PROMO-B" -> "P-B"
      else -> Regex("^([AB][0-9]+)([A-Z]+)$").matchEntire(set)?.let {
        it.groupValues[1] + it.groupValues[2].lowercase(java.util.Locale.ROOT)
      } ?: set
    }
    return "https://assets.tcgdex.net/$language/tcgp/$assetSet/$number/low.webp"
  }
}
