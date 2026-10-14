package com.example.domain

import java.text.Normalizer
import java.util.Locale

enum class CardRole(val key: String, val label: String) {
  DRAW("draw", "Robar cartas"), SEARCH("search", "Buscar cartas"), HEAL("heal", "Curar"), ENERGY("energy", "Acelerar energía"),
  MILL("mill", "Milling"), SWITCH("switch", "Mover Pokémon")
}
object RoleClassifier {
  fun normalize(text: String): String = Normalizer.normalize(text.lowercase(Locale.ROOT), Normalizer.Form.NFD)
    .replace(Regex("\\p{M}"), "")
  /** Text heuristic; role tags are never treated as official rulings. */
  fun classify(text: String): Set<CardRole> {
    // Effects applying only to the opponent must not be credited as our own draw/acceleration.
    val value = normalize(text).split(Regex("[.\n]"))
      .filterNot { clause -> Regex("^(your opponent|tu rival|el rival)\\b").containsMatchIn(clause.trim()) }
      .joinToString(". ")
    return buildSet {
      if (Regex("\\b(draw|roba|robar|robe)\\b").containsMatchIn(value)) add(CardRole.DRAW)
      val deckToHand = Regex("\\b(put|pon|anade|add)\\b").containsMatchIn(value) &&
        Regex("(deck|baraja|mazo).{0,100}(hand|mano)").containsMatchIn(value)
      if (deckToHand || ((value.contains("search") || value.contains("busca") || value.contains("buscar")) &&
          (value.contains("deck") || value.contains("baraja") || value.contains("mazo")))) add(CardRole.SEARCH)
      if (Regex("\\b(heal|cura|curar|curate|soigne|heals)\\b").containsMatchIn(value)) add(CardRole.HEAL)
      if (Regex("\\b(attach|attaches|une|unir)\\b").containsMatchIn(value) &&
          (value.contains("energy") || value.contains("energia"))) add(CardRole.ENERGY)
      if ((value.contains("opponent") || value.contains("rival")) &&
          (value.contains("deck") || value.contains("baraja") || value.contains("mazo")) &&
          (value.contains("discard") || value.contains("descarta"))) add(CardRole.MILL)
      if (Regex("\\b(switch|cambia|cambiar|retreat|retirada)\\b").containsMatchIn(value)) add(CardRole.SWITCH)
    }
  }
}
