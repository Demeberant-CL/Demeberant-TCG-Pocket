package com.example.data.repository

import com.example.data.model.PokemonCard

data class DeckCardEntry(
  val card: PokemonCard,
  val count: Int
)

data class GeneratedDeck(
  val name: String,
  val archetype: String,
  val strategy: String,
  val cards: List<DeckCardEntry>,
  val totalCardCount: Int,
  val validationWarnings: List<String> = emptyList(),
  val energyTypes: List<String> = emptyList(),
  val coverCardId: String? = null
) {
  fun toExportText(): String {
    val sb = StringBuilder()
    sb.appendLine("###### Mazo Pokémon TCG Pocket: $name")
    sb.appendLine("Arquetipo: $archetype")
    if (energyTypes.isNotEmpty()) sb.appendLine("Energías: ${energyTypes.joinToString(", ")}")
    sb.appendLine("Cartas ($totalCardCount/20):")
    cards.forEach { entry ->
      sb.appendLine("${entry.count}x ${entry.card.name} (${entry.card.id})")
    }
    sb.appendLine("Estrategia: $strategy")
    validationWarnings.forEach { sb.appendLine("Advertencia: $it") }
    return sb.toString().trimEnd()
  }
}

object DeckBuilderEngine {

  fun buildArchetypeDeck(
    userPrompt: String,
    onlyFromInventory: Boolean,
    ownedMap: Map<String, Int>
  ): GeneratedDeck {
    val promptLower = userPrompt.lowercase()

    val (deckName, archetype, strategy, coreKeywords) = when {
      promptLower.contains("pikachu") || promptLower.contains("rayo") -> Quad(
        "Pikachu ex Turbo Relámpago",
        "Pikachu ex Beatdown",
        "Llena tu banca con Pokémon básicos de tipo Rayo para que Círculo Eléctrico inflija 90 de daño por solo 2 energías.",
        listOf("Pikachu ex", "Pikachu", "Raichu", "Zapdos ex", "Blitzle", "Zebstrika", "Helioptile", "Heliolisk")
      )
      promptLower.contains("mewtwo") || promptLower.contains("psiquico") || promptLower.contains("gardevoir") -> Quad(
        "Mewtwo ex & Gardevoir Dimensión Psíquica",
        "Mewtwo ex Control / Energy Acceleration",
        "Usa la habilidad Abrazo Psíquico de Gardevoir para cargar rápidamente a Mewtwo ex con 4 energías y arrasar con Impulso Psíquico (150 daño).",
        listOf("Mewtwo ex", "Ralts", "Kirlia", "Gardevoir", "Abra", "Kadabra", "Alakazam")
      )
      promptLower.contains("charizard") || promptLower.contains("moltres") || promptLower.contains("fuego") -> Quad(
        "Charizard ex & Moltres ex Llamas Carmesí",
        "Charizard ex Heavy Hitter",
        "Activa Danza Infernal de Moltres ex en los primeros turnos para unir energías fuego en banca a Charizard ex y rematar con 200 de daño.",
        listOf("Charmander", "Charmeleon", "Charizard ex", "Moltres ex", "Vulpix", "Ninetales", "Ponyta", "Rapidash", "Blaine")
      )
      promptLower.contains("starmie") || promptLower.contains("blastoise") || promptLower.contains("agua") -> Quad(
        "Starmie ex & Blastoise Marea Veloz",
        "Starmie ex Tempo",
        "Starmie ex ataca rápido con 2 energías haciendo 90 de daño y tiene coste de retirada 0 para alternar con Blastoise.",
        listOf("Staryu", "Starmie ex", "Squirtle", "Wartortle", "Blastoise ex", "Psyduck")
      )
      promptLower.contains("marowak") || promptLower.contains("lucha") || promptLower.contains("machamp") -> Quad(
        "Marowak ex & Machamp Rompe rocas",
        "Lucha / Marowak ex High-Roll",
        "Bumerán Óseo de Marowak ex inflige hasta 160 de daño por 2 energías. Machamp resiste como tanque pesado en juego tardío.",
        listOf("Cubone", "Marowak ex", "Machop", "Machoke", "Machamp", "Hitmonchan", "Hitmonlee", "Sandshrew", "Sandslash")
      )
      else -> Quad(
        "Pikachu ex Turbo Relámpago",
        "Meta Beatdown",
        "Estrategia balanceada orientada a ganar rápido tomando 3 puntos mediante ataques agresivos de bajo coste.",
        listOf("Pikachu ex", "Pikachu", "Zapdos ex", "Raichu", "Blitzle", "Zebstrika")
      )
    }

    val canonicalOwned = ownedMap.entries.groupBy {
      runCatching { com.example.data.util.CardId.normalize(it.key) }.getOrDefault(it.key)
    }.mapValues { (_, entries) -> entries.maxOf { it.value } }
    val pool = CardCatalog.ALL_CARDS.filter { it.id.startsWith("A1-") }.sortedBy { it.id }.filter {
      !onlyFromInventory || (canonicalOwned[it.id] ?: 0) > 0
    }
    val quantities = linkedMapOf<String, DeckCardEntry>()
    fun nameCount(name: String) = quantities.values.filter { it.card.name.equals(name, true) }.sumOf { it.count }
    fun total() = quantities.values.sumOf { it.count }
    fun tryAdd(card: PokemonCard, desired: Int) {
      val parent = card.evolvesFrom.takeIf { it.isNotBlank() } ?: evolutionParents[card.rulesName.lowercase()]
      if (parent != null && nameCount(parent) == 0) return
      val previous = quantities[card.id]?.count ?: 0
      val ownedLimit = if (onlyFromInventory) canonicalOwned[card.id] ?: 0 else 2
      val count = minOf(desired, 2 - nameCount(card.name), ownedLimit - previous, 20 - total())
      if (count > 0) quantities[card.id] = DeckCardEntry(card, previous + count)
    }
    coreKeywords.forEach { name ->
      pool.filter { it.name.equals(name, true) }.forEach { tryAdd(it, 2) }
    }
    pool.filter { it.type == "Entrenador" }.forEach { tryAdd(it, 2) }
    val energyType = when {
      archetype.contains("Psíquico") || deckName.contains("Mewtwo") -> "Psíquico"
      deckName.contains("Charizard") -> "Fuego"
      deckName.contains("Starmie") -> "Agua"
      deckName.contains("Marowak") -> "Lucha"
      else -> "Rayo"
    }
    // Fill with compatible, known cards. Never mix arbitrary evolution lines or energies.
    val filler = pool.filter { it.type in setOf(energyType, "Incoloro", "Entrenador") }
    repeat(3) { filler.forEach { tryAdd(it, 2) } }
    val entries = quantities.values.toList()
    val warnings = validate(entries).toMutableList()
    if (coreKeywords.none { name -> entries.any { it.card.name.equals(name, true) } }) {
      warnings.add("No tienes piezas del arquetipo solicitado.")
    }
    return GeneratedDeck(deckName, archetype, strategy, entries, total(), warnings, listOf(energyType))
  }

  private val evolutionParents = mapOf(
    "ivysaur" to "Bulbasaur", "venusaur" to "Ivysaur", "venusaur ex" to "Ivysaur",
    "venomoth" to "Venonat", "weepinbell" to "Bellsprout", "exeggutor" to "Exeggcute",
    "exeggutor ex" to "Exeggcute", "whimsicott" to "Cottonee", "lilligant" to "Petilil",
    "charmeleon" to "Charmander", "charizard" to "Charmeleon", "charizard ex" to "Charmeleon",
    "ninetales" to "Vulpix", "rapidash" to "Ponyta", "centiskorch" to "Sizzlipede",
    "wartortle" to "Squirtle", "blastoise" to "Wartortle", "blastoise ex" to "Wartortle",
    "starmie ex" to "Staryu", "frosmoth" to "Snom", "raichu" to "Pikachu",
    "zebstrika" to "Blitzle", "eelektrik" to "Tynamo", "heliolisk" to "Helioptile",
    "kadabra" to "Abra", "alakazam" to "Kadabra", "slowbro" to "Slowpoke",
    "haunter" to "Gastly", "gengar" to "Haunter", "gengar ex" to "Haunter",
    "kirlia" to "Ralts", "gardevoir" to "Kirlia", "golurk" to "Golett",
    "sandslash" to "Sandshrew", "dugtrio" to "Diglett", "machoke" to "Machop",
    "machamp" to "Machoke", "marowak ex" to "Cubone", "mienshao" to "Mienfoo",
    "muk" to "Grimer", "dragonair" to "Dratini", "dragonite" to "Dragonair",
    "raticate" to "Rattata", "fearow" to "Spearow", "wigglytuff" to "Jigglypuff",
    "dodrio" to "Doduo", "cinccino" to "Minccino", "aerodactyl" to "Old Amber"
  )

  fun validate(cards: List<DeckCardEntry>): List<String> = buildList {
    val total = cards.sumOf { it.count }
    if (total != 20) add("Mazo incompleto: $total/20 cartas.")
    if (cards.any { it.count <= 0 }) add("Hay cantidades no válidas.")
    val counts = cards.groupBy { it.card.rulesName.lowercase() }.mapValues { (_, entries) -> entries.sumOf { it.count } }
    if (counts.any { it.value > 2 }) add("Máximo de dos copias por nombre excedido.")
    cards.forEach { entry ->
      (entry.card.evolvesFrom.takeIf { it.isNotBlank() } ?: evolutionParents[entry.card.rulesName.lowercase()])?.let { parent ->
        if ((counts[parent.lowercase()] ?: 0) == 0) add("${entry.card.name} necesita $parent.")
      }
      if (entry.card.type == "Sin verificar") add("Datos de ${entry.card.name} sin verificar.")
    }
    if (cards.none { it.card.type != "Entrenador" && it.card.type != "Sin verificar" && it.card.name.lowercase() !in evolutionParents }) {
      add("Falta un Pokémon básico con datos verificados.")
    }
  }.distinct()

}

private data class Quad<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)
