package com.example.domain

import com.example.data.repository.GeneratedDeck

/** Uses only the selected list and known card facts; advice is distinct from printed effects. */
object DeckPlayGuide {
  fun generate(deck: GeneratedDeck, data: Map<String, CombatData>): String {
    val pokemon = deck.cards.filter { it.card.type != "Entrenador" }
    val basics = pokemon.filter { it.card.stage == "basic" }
    fun playable(a: CombatAttack) = a.cost != null && a.cost.all { c ->
      LocalDeckPlanner.energy(c)?.let { it == "Incoloro" || it in deck.energyTypes } == true
    }
    val attacker = pokemon.maxByOrNull { row ->
      data[row.card.id]?.attacks.orEmpty().filter(::playable).maxOfOrNull { a ->
        (Regex("^\\d+").find(a.damage)?.value?.toDoubleOrNull() ?: 0.0) / (1 + a.cost!!.size)
      } ?: 0.0
    }
    val roles = deck.cards.associate { row -> row.card.id to RoleClassifier.classify(data[row.card.id]?.text.orEmpty() +
      data[row.card.id]?.attacks.orEmpty().joinToString("\n") { it.effect }) }
    fun support(role: CardRole) = deck.cards.filter { role in roles[it.card.id].orEmpty() }.joinToString { "${it.count}× ${it.card.rulesName}" }
    val known = deck.cards.filter { data[it.card.id] != null }.sumOf { it.count }
    return buildString {
      appendLine("OBJETIVO")
      appendLine(attacker?.let { "Prepara a ${it.card.rulesName} como atacante y conserva un Pokémon de reserva. Prioriza ataques que puedas pagar con las energías elegidas." }
        ?: "Elige tu atacante principal antes de jugar; este mazo no tiene Pokémon identificados.")
      appendLine("\nINICIO")
      appendLine(if (basics.isEmpty()) "Falta un Pokémon básico: revisa la lista antes de jugar."
        else "Básicos disponibles: ${basics.joinToString { "${it.count}× ${it.card.rulesName}" }}. Elige el activo según su retirada, PS y coste de ataque; prepara el resto en banca.")
      appendLine("\nPREPARACIÓN Y ENERGÍA")
      appendLine("Energías elegidas: ${deck.energyTypes.joinToString().ifBlank { "sin elegir" }}.")
      pokemon.filter { it.card.evolvesFrom.isNotBlank() }.forEach {
        appendLine("${it.card.evolvesFrom} → ${it.card.rulesName}: conserva la etapa anterior y prepara la evolución; verifica las restricciones de turno en el juego.")
      }
      attacker?.let { row ->
        data[row.card.id]?.attacks.orEmpty().filter(::playable).forEach { a ->
          appendLine("${row.card.rulesName} · ${a.name.ifBlank { "Ataque" }}: coste ${a.cost!!.joinToString { LocalDeckPlanner.energy(it) ?: it }.ifBlank { "sin energía" }}; daño ${a.damage.ifBlank { "según efecto" }}.")
          if (a.effect.isNotBlank()) appendLine("Texto del efecto: ${a.effect}")
        }
      }
      appendLine("\nSECUENCIA SUGERIDA")
      appendLine("1. Revisa tu mano y reúne el atacante y sus evoluciones antes de gastar recursos.")
      support(CardRole.SEARCH).takeIf(String::isNotBlank)?.let { appendLine("2. Búsqueda identificada: $it. Úsala cuando necesites una pieza que su texto permita buscar.") }
      support(CardRole.DRAW).takeIf(String::isNotBlank)?.let { appendLine("Robo identificado: $it. Revisa primero qué cartas puedes jugar; evita perder recursos necesarios con efectos que devuelvan o descarten tu mano.") }
      support(CardRole.ENERGY).takeIf(String::isNotBlank)?.let { appendLine("Energía identificada: $it. Comprueba objetivo y condiciones antes de contar con la aceleración.") }
      support(CardRole.HEAL).takeIf(String::isNotBlank)?.let { appendLine("Recuperación identificada: $it. Úsala si cambia la supervivencia del Pokémon y sus condiciones lo permiten.") }
      support(CardRole.SWITCH).takeIf(String::isNotBlank)?.let { appendLine("Cambio identificado: $it. Puede ayudar a preparar un nuevo activo; lee si actúa sobre tu equipo o el rival.") }
      appendLine("3. Ataca cuando puedas pagar el coste. Compara PS rivales, daño, efectos y puntos que perderías antes de retirar o exponer un Pokémon ex.")
      appendLine("4. Prepara un segundo atacante en banca; conserva recursos para continuar si cae el primero.")
      appendLine("\nRIESGOS Y REVISIÓN")
      if (deck.energyTypes.size > 1) appendLine("Varias energías pueden retrasar un ataque: no cuentes con recibir un tipo concreto cada turno.")
      if (support(CardRole.DRAW).isBlank()) appendLine("Sin robo identificado: puede ser difícil reunir piezas a tiempo.")
      if (pokemon.any { it.card.isEx }) appendLine("Los Pokémon ex conceden más puntos al caer; cuida su exposición.")
      deck.validationWarnings.forEach { appendLine(it) }
      appendLine("Datos locales: $known/${deck.totalCardCount} cartas. Revisa los textos incompletos o en inglés en el visor.")
      append("Orientación calculada desde la lista y los datos disponibles. No simula al rival ni garantiza victorias.")
    }
  }
}
