package com.example.domain

import com.example.data.model.PokemonCard
import com.example.data.repository.*
import com.example.data.util.DeckCodec
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/** Offline, bounded heuristic search. Scores rank candidates; they are never win probabilities. */
data class CombatAttack(val cost: List<String>?, val damage: String, val effect: String = "")
data class CombatData(val hp: Int? = null, val retreat: Int? = null,
  val attacks: List<CombatAttack> = emptyList(), val text: String = "")
enum class DeckStyle(val label: String) { BALANCED("Equilibrado"), FAST("Rápido"), RESILIENT("Resistente") }
data class PlannerOptions(val type: String? = null, val style: DeckStyle = DeckStyle.BALANCED, val anchorId: String? = null)
data class DeckPlan(val deck: GeneratedDeck, val score: Int, val basics: Int, val trainers: Int,
  val knownCopies: Int, val reasons: List<String>, val cautions: List<String>)

object LocalDeckPlanner {
  private fun name(value: String) = value.trim().lowercase(Locale.ROOT)
  fun energy(value: String): String? = when (RoleClassifier.normalize(value)) {
    "grass", "planta" -> "Planta"; "fire", "fuego" -> "Fuego"; "water", "agua" -> "Agua"
    "lightning", "rayo", "electric" -> "Rayo"; "psychic", "psiquico" -> "Psíquico"
    "fighting", "lucha" -> "Lucha"; "darkness", "dark", "oscuridad" -> "Oscuridad"
    "metal", "steel" -> "Metal"; "colorless", "incoloro" -> "Incoloro"; else -> null
  }
  private fun trainer(card: PokemonCard) = card.type == "Entrenador"
  private fun basic(card: PokemonCard) = !trainer(card) && card.stage == "basic"
  private fun roles(data: CombatData?) = RoleClassifier.classify(data?.text.orEmpty() + "\n" +
    data?.attacks.orEmpty().joinToString("\n") { it.effect })
  private fun usable(attack: CombatAttack, palette: List<String>): Boolean = attack.cost?.all {
    energy(it)?.let { e -> e == "Incoloro" || e in palette } == true
  } == true
  private fun compatible(card: PokemonCard, palette: List<String>, data: CombatData?): Boolean {
    if (trainer(card)) {
      val text = RoleClassifier.normalize(data?.text.orEmpty())
      if (CardRole.ENERGY in roles(data)) {
        val pairs = listOf("Agua" to "water", "Fuego" to "fire", "Planta" to "grass", "Rayo" to "lightning",
          "Psíquico" to "psychic", "Lucha" to "fighting", "Oscuridad" to "darkness", "Metal" to "metal")
        val symbols = mapOf("g" to "Planta", "r" to "Fuego", "w" to "Agua", "l" to "Rayo",
          "p" to "Psíquico", "f" to "Lucha", "d" to "Oscuridad", "m" to "Metal")
        val target = pairs.filter { (spanish, english) ->
          text.contains("$english energy") || Regex("energias? (de tipo )?" + RoleClassifier.normalize(spanish)).containsMatchIn(text)
        }.map { it.first } + Regex("energias?\\s+\\{([grwlpfdm])\\}").findAll(text)
          .mapNotNull { symbols[it.groupValues[1]] }.toList()
        if (target.isNotEmpty() && target.none { it in palette }) return false
      }
      return true
    }
    if (card.stage !in setOf("basic", "1", "2")) return false
    val known = data?.attacks.orEmpty().filter { it.cost != null }
    return if (known.isNotEmpty()) known.any { usable(it, palette) }
    else card.type in palette || card.type == "Incoloro"
  }
  private fun strength(card: PokemonCard, data: CombatData?, palette: List<String>, style: DeckStyle): Double {
    if (trainer(card)) return 4.0
    val delay = when (card.stage) { "1" -> 1; "2" -> 2; else -> 0 }
    val attacks = data?.attacks.orEmpty().filter { usable(it, palette) }
    val attackScore = attacks.maxOfOrNull { a ->
      val raw = Regex("^\\d+").find(a.damage.trim())?.value?.toDoubleOrNull() ?: 0.0
      val damage = raw * if (a.damage.contains("×") || a.damage.contains("x")) 0.5 else if (a.damage.contains('+')) 0.8 else 1.0
      val cost = a.cost!!.size
      val tempo = if (style == DeckStyle.FAST) 1.4 else 0.8
      damage / (1.0 + cost + delay * tempo) / 4.0 - cost * tempo
    } ?: ((card.attackDamage.toDoubleOrNull() ?: 0.0) / 20.0 - 2.0)
    val hp = (data?.hp ?: card.hp).coerceAtLeast(0)
    val support = roles(data)
    return attackScore + hp / (if (style == DeckStyle.RESILIENT) 24.0 else 45.0) +
      (if (CardRole.ENERGY in support) 4.0 else 0.0) +
      (if (CardRole.DRAW in support || CardRole.SEARCH in support) 2.5 else 0.0) -
      (data?.retreat ?: 1) * 0.6 - delay * (if (style == DeckStyle.FAST) 2.0 else 0.5) -
      (if (card.isEx) 0.8 else 0.0)
  }

  fun recommend(inventory: List<CardWithInventory>, data: Map<String, CombatData> = emptyMap(),
    options: PlannerOptions = PlannerOptions()): List<DeckPlan> {
    require(options.type == null || options.type in DeckCodec.energyNames) { "Elige una energía válida." }
    val owned = inventory.filter { it.ownedCount > 0 && it.card.type != "Sin verificar" }
      .distinctBy { it.card.id }.sortedBy { it.card.id }
    require(owned.any { basic(it.card) }) { "No tienes Pokémon básicos con datos verificados." }
    val anchor = options.anchorId?.let { id -> owned.firstOrNull { it.card.id == id }
      ?: throw IllegalArgumentException("La carta principal no está en tu colección.") }
    require(anchor == null || !trainer(anchor.card)) { "La carta principal debe ser un Pokémon." }
    val palettes = if (options.type != null) listOf(listOf(options.type)) else buildList {
      DeckCodec.energyNames.forEach { add(listOf(it)) }
      owned.forEach { item -> data[item.card.id]?.attacks.orEmpty().forEach { attack ->
        val types = attack.cost?.mapNotNull(::energy)?.filter { it != "Incoloro" }?.distinct()?.sorted().orEmpty()
        if (types.size in 2..3) add(types)
      } }
    }.distinct().take(24)
    val roleMap = owned.associate { it.card.id to roles(data[it.card.id]) }
    val named = owned.filter { !trainer(it.card) && it.card.rulesName.length >= 4 }
      .map { name(it.card.rulesName) to RoleClassifier.normalize(it.card.rulesName) }.distinct()
    val namedTargets = owned.filter { trainer(it.card) }.associate { item ->
      val text = RoleClassifier.normalize(data[item.card.id]?.text.orEmpty())
      item.card.id to if (text.isBlank()) emptySet() else named.filter { text.contains(it.second) }.map { it.first }.toSet()
    }
    val candidates = mutableListOf<DeckPlan>()
    for (palette in palettes) {
      val pool = owned.filter { compatible(it.card, palette, data[it.card.id]) }
      val rating = pool.associate { it.card.id to strength(it.card, data[it.card.id], palette, options.style) }
      val costs = pool.associate { item -> item.card.id to data[item.card.id]?.attacks.orEmpty()
        .filter { usable(it, palette) }.map { it.cost!!.size } }
      val byName = pool.groupBy { name(it.card.rulesName) }.mapValues { (_, variants) ->
        variants.sortedWith(compareByDescending<CardWithInventory> { it.card.id == options.anchorId }
          .thenByDescending { rating.getValue(it.card.id) }
          .thenByDescending { minOf(2, it.ownedCount) }.thenBy { it.card.id })
      }
      fun chain(card: PokemonCard, path: Set<String> = emptySet()): List<String>? {
        val key = name(card.rulesName)
        if (key in path || path.size >= 4) return null
        if (basic(card) || trainer(card)) return listOf(key)
        val parent = name(card.evolvesFrom)
        if (parent.isBlank()) return null
        val prev = byName[parent]?.firstOrNull()?.card ?: return null
        return chain(prev, path + key)?.plus(key)
      }
      val anchors = if (anchor != null) pool.filter { it.card.id == anchor.card.id }
      else pool.filter { !trainer(it.card) && chain(it.card) != null }
        .sortedWith(compareByDescending<CardWithInventory> {
          rating.getValue(it.card.id) + if (it.card.stage != "basic") 2.0 else 0.0
        }.thenBy { it.card.id }).distinctBy { name(it.card.rulesName) }.take(16)
      val supportPool = pool.filter { !trainer(it.card) && chain(it.card) != null }
        .sortedWith(compareByDescending<CardWithInventory> { rating.getValue(it.card.id) }
          .thenBy { it.card.id }).distinctBy { name(it.card.rulesName) }.take(36)
      val trainerPool = pool.filter { trainer(it.card) }.sortedWith(compareByDescending<CardWithInventory> {
        roleMap[it.card.id].orEmpty().sumOf { role -> when(role) {
          CardRole.DRAW, CardRole.SEARCH -> 5; CardRole.ENERGY -> 4; CardRole.SWITCH -> 3; else -> 2
        } }
      }.thenBy { it.card.id }).distinctBy { name(it.card.rulesName) }.take(100)
      for (hero in anchors) {
        val lineage = chain(hero.card) ?: continue
        val entries = linkedMapOf<String, DeckCardEntry>()
        fun count(key: String) = entries.values.filter { name(it.card.rulesName) == key }.sumOf { it.count }
        fun total() = entries.values.sumOf { it.count }
        fun addName(key: String, amount: Int, target: MutableMap<String, DeckCardEntry> = entries) {
          var remaining = amount
          for (item in byName[key].orEmpty()) {
            val previous = target[item.card.id]?.count ?: 0
            val currentName = target.values.filter { name(it.card.rulesName) == key }.sumOf { it.count }
            val n = minOf(remaining, item.ownedCount - previous, 2 - currentName)
            if (n > 0) { target[item.card.id] = DeckCardEntry(item.card, previous + n); remaining -= n }
            if (remaining <= 0) break
          }
        }
        val coreCopies = lineage.minOf { key -> minOf(2, byName[key].orEmpty().sumOf { it.ownedCount }) }
        lineage.forEach { addName(it, coreCopies) }
        if (!entries.values.any { basic(it.card) }) {
          pool.firstOrNull { basic(it.card) }?.let { addName(name(it.card.rulesName), 1) } ?: continue
        }
        fun score(rows: Collection<DeckCardEntry>): Double {
          val pokemon = rows.filter { !trainer(it.card) }
          val tc = rows.filter { trainer(it.card) }.sumOf { it.count }
          val bc = pokemon.filter { basic(it.card) }.sumOf { it.count }
          val families = pokemon.filter { basic(it.card) }.map { name(it.card.rulesName) }.distinct().size
          val rs = rows.flatMap { e -> List(e.count) { roleMap[e.card.id].orEmpty() } }
          val draws = rs.count { CardRole.DRAW in it }
          val searches = rs.count { CardRole.SEARCH in it }
          val accelerators = rs.count { CardRole.ENERGY in it }
          val healing = rs.count { CardRole.HEAL in it }
          val attacker = pokemon.maxOfOrNull { rating.getValue(it.card.id) } ?: 0.0
          val averageCost = pokemon.flatMap { costs[it.card.id].orEmpty() }.average().takeIf { !it.isNaN() } ?: 2.0
          val tcTarget = when(options.style) { DeckStyle.FAST -> 12; DeckStyle.RESILIENT -> 10; else -> 11 }
          var value = rows.sumOf { it.count } * 4.0 + attacker * 2.0 +
            pokemon.sumOf { rating.getValue(it.card.id) * it.count } * 0.35
          value += minOf(draws, 4) * 4.5 + minOf(searches, 3) * 4.0 + minOf(accelerators, 2) * if (averageCost >= 3) 5.0 else 2.0
          value += minOf(healing, 2) * if (options.style == DeckStyle.RESILIENT) 3.0 else 1.0
          value -= abs(tc - tcTarget) * 1.3 + (bc - 6).coerceAtLeast(0) * 1.5 + (families - 3).coerceAtLeast(0) * 3.0
          value -= (palette.size - 1) * 6.0
          val quantities = rows.groupBy { name(it.card.rulesName) }.mapValues { (_, group) -> group.sumOf { it.count } }
          rows.filter { !trainer(it.card) && it.card.evolvesFrom.isNotBlank() }.forEach { child ->
            val parentCount = quantities[name(child.card.evolvesFrom)] ?: 0
            value -= (child.count - parentCount).coerceAtLeast(0) * 8.0
          }
          // Avoid dead named-target support when its required Pokémon is absent.
          rows.filter { trainer(it.card) }.forEach { entry ->
            val specified = namedTargets[entry.card.id].orEmpty()
            if (specified.isNotEmpty() && pokemon.none { name(it.card.rulesName) in specified }) value -= 14.0 * entry.count
          }
          return value
        }
        // Whole evolution packages compete with each trainer addition. Never fill by catalog order.
        repeat(20) {
          if (total() >= 20) return@repeat
          val next = mutableListOf<LinkedHashMap<String, DeckCardEntry>>()
          trainerPool.forEach { item ->
            val key = name(item.card.rulesName)
            if (count(key) < 2) {
              val copy = LinkedHashMap(entries); addName(key, 1, copy)
              if (copy.values.sumOf { it.count } > total()) next.add(copy)
            }
          }
          supportPool.forEach { item ->
            val family = chain(item.card) ?: return@forEach
            val copy = LinkedHashMap(entries)
            family.forEach { key ->
              val has = copy.values.filter { name(it.card.rulesName) == key }.sumOf { it.count }
              if (has < 1) addName(key, 1, copy)
            }
            if (copy == entries && count(name(item.card.rulesName)) < 2) {
              // Duplicates of an evolution also duplicate its preevolutions where possible.
              family.forEach { key -> if (count(key) < 2) addName(key, 1, copy) }
            }
            val n = copy.values.sumOf { it.count }
            if (n in (total() + 1)..20) next.add(copy)
          }
          val best = next.maxWithOrNull(compareBy<LinkedHashMap<String, DeckCardEntry>> { score(it.values) }
            .thenBy { it.keys.sorted().joinToString() })
          if (best != null) { entries.clear(); entries.putAll(best) }
        }
        val rows = entries.values.toList()
        val warnings = DeckBuilderEngine.validate(rows)
        val basics = rows.filter { basic(it.card) }.sumOf { it.count }
        val tc = rows.filter { trainer(it.card) }.sumOf { it.count }
        val known = rows.filter { e -> data[e.card.id]?.let { d ->
          if (trainer(e.card)) d.text.isNotBlank() else d.attacks.any { usable(it, palette) }
        } == true }.sumOf { it.count }
        val cautions = buildList {
          addAll(warnings)
          if (known < rows.sumOf { it.count }) add("${rows.sumOf { it.count } - known} cartas sin efectos o costes completos: valoración parcial.")
          if (palette.size > 1) add("Varias energías: la energía generada puede retrasar los ataques.")
          if (rows.none { CardRole.DRAW in roleMap[it.card.id].orEmpty() }) add("Sin robo de cartas identificado.")
          if (rows.none { CardRole.SEARCH in roleMap[it.card.id].orEmpty() }) add("Sin búsqueda identificada.")
        }
        val reasons = listOf("Núcleo: ${lineage.joinToString(" → ") { byName[it]!!.first().card.rulesName }}",
          "$basics básicos · $tc entrenadores · ${known}/${rows.sumOf { it.count }} cartas con datos de combate",
          "Comparación local por ritmo, soporte, evoluciones y energías; no estima victorias.")
        val strategy = (reasons + cautions).joinToString("\n")
        candidates.add(DeckPlan(GeneratedDeck("${hero.card.rulesName} · ${options.style.label}", "Constructor local",
          strategy, rows, rows.sumOf { it.count }, warnings, palette), score(rows).roundToInt(), basics, tc, known, reasons, cautions))
      }
    }
    require(candidates.isNotEmpty()) { "No hay líneas completas compatibles. Prueba otra energía o carta principal." }
    val sorted = candidates.sortedWith(compareByDescending<DeckPlan> { it.deck.totalCardCount == 20 }
      .thenByDescending { it.score }.thenBy { it.deck.name }.thenBy { it.deck.energyTypes.joinToString() })
    val selected = mutableListOf<DeckPlan>()
    sorted.forEach { plan ->
      val names = plan.deck.cards.map { name(it.card.rulesName) }.toSet()
      if (selected.size < 3 && selected.none { prior ->
          val old = prior.deck.cards.map { name(it.card.rulesName) }.toSet()
          names.intersect(old).size.toDouble() / names.union(old).size.coerceAtLeast(1) > 0.85
        }) selected.add(plan)
    }
    return selected.ifEmpty { listOf(sorted.first()) }
  }
}
