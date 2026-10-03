package com.example.domain

/** Completion preserves available cards; improvement uses the deck only as a reference. */
enum class AiDeckAction(val label: String, val description: String) {
  CREATE("Crear mazo", "Crea una baraja de 20 cartas usando tu colección."),
  COMPLETE("Completar faltantes", "Conserva las copias disponibles y sustituye solo las que faltan en un mazo de 20 cartas."),
  IMPROVE("Mejorar mi mazo", "Usa el mazo abierto como referencia. Puede cambiar cualquier carta por otra disponible.");

  fun canUse(cardCount: Int?): Boolean = when (this) {
    CREATE -> true
    COMPLETE -> cardCount == 20
    IMPROVE -> cardCount != null && cardCount in 1..20
  }
}
