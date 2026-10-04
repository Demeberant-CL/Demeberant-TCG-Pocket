package com.example.data.util

import java.util.Locale

/** Canonical IDs retain the entire set name, including PROMO-A and PROMO-B. */
object CardId {
  private val pattern = Regex("^([A-Z0-9]+(?:-[A-Z0-9]+)*)-([0-9]+)$")

  fun normalize(id: String): String {
    val match = pattern.matchEntire(id.trim().uppercase(Locale.ROOT))
      ?: throw IllegalArgumentException("ID de carta no válido: $id")
    val number = match.groupValues[2].toIntOrNull()
      ?: throw IllegalArgumentException("Número de carta no válido: $id")
    require(number > 0) { "El número de carta debe ser mayor que cero." }
    return "${match.groupValues[1]}-${number.toString().padStart(3, '0')}"
  }

  fun split(id: String): Pair<String, String> {
    val canonical = normalize(id)
    return canonical.substringBeforeLast('-') to canonical.substringAfterLast('-')
  }
}
