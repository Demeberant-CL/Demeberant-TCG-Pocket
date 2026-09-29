package com.example.data.util

object TcgdexHelper {
  /**
   * Genera la URL pública de la imagen de la carta en la API / CDN de TCGdex.
   * Por defecto intenta en idioma español ('es') según la especificación Neutral Spanish First.
   */
  fun getCardImageUrl(cardFullId: String, lang: String = "es"): String {
    val parts = cardFullId.split("-")
    val rawSet = if (parts.size > 1) parts[0] else "A1"
    val rawNum = if (parts.size > 1) parts[1] else cardFullId
    val cleanNum = rawNum.replace(Regex("^[^0-9]*"), "").trimStart('0').ifEmpty { "1" }
    val cleanSet = rawSet.lowercase()

    return "https://assets.tcgdex.net/$lang/tcgp/$cleanSet/$cleanNum/high.png"
  }

  fun getCardImageUrl(setId: String, cardNum: String, lang: String = "es"): String {
    val cleanSet = setId.lowercase().trim()
    val cleanNum = cardNum.replace(Regex("^[^0-9]*"), "").trimStart('0').ifEmpty { "1" }
    return "https://assets.tcgdex.net/$lang/tcgp/$cleanSet/$cleanNum/high.png"
  }
}
