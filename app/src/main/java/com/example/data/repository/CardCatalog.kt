package com.example.data.repository

import com.example.data.model.BoosterPack
import com.example.data.model.CardRarity
import com.example.data.model.PokemonCard
import java.util.concurrent.ConcurrentHashMap

object CardCatalog {
  private val bundledCards = ConcurrentHashMap<String, PokemonCard>()
  var sourceRevision: String = ""
    private set

  @Synchronized fun loadBundled(context: android.content.Context) {
    if (bundledCards.isNotEmpty()) return
    val root = org.json.JSONObject(context.assets.open("pocket-catalog.json").bufferedReader().use { it.readText() })
    val rows = root.getJSONArray("cards")
    val types = mapOf("grass" to "Planta", "fire" to "Fuego", "water" to "Agua",
      "lightning" to "Rayo", "psychic" to "Psíquico", "fighting" to "Lucha",
      "darkness" to "Oscuridad", "metal" to "Metal", "dragon" to "Dragón", "colorless" to "Incoloro")
    val cards = (0 until rows.length()).map { index ->
      val row = rows.getJSONObject(index)
      val id = com.example.data.util.CardId.normalize(row.getString("id"))
      val name = row.getString("name")
      val category = row.getString("category")
      val packs = row.getJSONArray("packs").let { a -> (0 until a.length()).map { a.getString(it) } }
      val rarity = CardRarity.fromSymbol(row.getString("rarity"))
      val pack = if (id.startsWith("A1-") && packs.size == 1) when (packs[0]) {
        "Mewtwo" -> BoosterPack.MEWTWO
        "Charizard" -> BoosterPack.CHARIZARD
        "Pikachu" -> BoosterPack.PIKACHU
        else -> BoosterPack.UNKNOWN
      } else BoosterPack.UNKNOWN
      PokemonCard(id, name, pack, rarity, 0,
        if (category in setOf("item", "tool", "supporter", "Fossil")) "Entrenador"
        else types[row.optString("element")] ?: "Sin verificar", "", "",
        isEx = name.endsWith(" ex", true), isFullArt = rarity in setOf(CardRarity.ONE_STAR, CardRarity.TWO_STARS),
        isSecretRare = rarity == CardRarity.CROWN, isImmersive = rarity == CardRarity.THREE_STARS,
        category = category, stage = row.getString("stage"), evolvesFrom = row.getString("evolvesFrom"),
        packNames = packs, source = "Catálogo comunitario · 2026-10-01")
    }
    require(cards.map { it.id }.distinct().size == cards.size)
    com.example.data.util.TcgdexHelper.loadImageIndex(context)
    bundledCards.putAll(cards.associateBy { it.id })
    sourceRevision = root.getString("revision")
  }


  // Dynamic storage for all expansion cards (A1A, A2, A2A, A2B, A3, A3A, A3B, A4, B1, B2, B3, B4, PROMO-A, etc.)
  private val dynamicCards = ConcurrentHashMap<String, PokemonCard>()

  private val STATIC_CARDS: List<PokemonCard> = listOf(
    // Planta
    PokemonCard("A1-001", "Bulbasaur", BoosterPack.MEWTWO, CardRarity.ONE_DIAMOND, 70, "Planta", "Látigo Cepa", "40"),
    PokemonCard("A1-002", "Ivysaur", BoosterPack.MEWTWO, CardRarity.TWO_DIAMONDS, 90, "Planta", "Hoja Afilada", "60"),
    PokemonCard("A1-003", "Venusaur", BoosterPack.MEWTWO, CardRarity.THREE_DIAMONDS, 160, "Planta", "Rayo Solar", "100"),
    PokemonCard("A1-004", "Venusaur ex", BoosterPack.MEWTWO, CardRarity.FOUR_DIAMONDS, 190, "Planta", "Tormenta Floral", "100", isEx = true),
    PokemonCard("A1-016", "Venonat", BoosterPack.CHARIZARD, CardRarity.ONE_DIAMOND, 60, "Planta", "Placaje", "20"),
    PokemonCard("A1-017", "Venomoth", BoosterPack.CHARIZARD, CardRarity.TWO_DIAMONDS, 80, "Planta", "Polvo Veneno", "30"),
    PokemonCard("A1-018", "Bellsprout", BoosterPack.CHARIZARD, CardRarity.ONE_DIAMOND, 50, "Planta", "Látigo Cepa", "20"),
    PokemonCard("A1-019", "Weepinbell", BoosterPack.CHARIZARD, CardRarity.TWO_DIAMONDS, 80, "Planta", "Hoja Afilada", "40"),
    PokemonCard("A1-022", "Exeggutor", BoosterPack.CHARIZARD, CardRarity.THREE_DIAMONDS, 130, "Planta", "Bomba Huevo", "80"),
    PokemonCard("A1-023", "Exeggutor ex", BoosterPack.CHARIZARD, CardRarity.FOUR_DIAMONDS, 160, "Planta", "Pisoteo Masivo", "80", isEx = true),
    PokemonCard("A1-024", "Tangela", BoosterPack.CHARIZARD, CardRarity.ONE_DIAMOND, 80, "Planta", "Atadura", "30"),
    PokemonCard("A1-026", "Pinsir", BoosterPack.CHARIZARD, CardRarity.TWO_DIAMONDS, 90, "Planta", "Guillotina", "50"),
    PokemonCard("A1-027", "Cottonee", BoosterPack.CHARIZARD, CardRarity.ONE_DIAMOND, 50, "Planta", "Esporagodón", "10"),
    PokemonCard("A1-028", "Whimsicott", BoosterPack.CHARIZARD, CardRarity.TWO_DIAMONDS, 80, "Planta", "Viento Feérico", "40"),
    PokemonCard("A1-029", "Petilil", BoosterPack.CHARIZARD, CardRarity.ONE_DIAMOND, 60, "Planta", "Absorber", "20"),
    PokemonCard("A1-030", "Lilligant", BoosterPack.CHARIZARD, CardRarity.TWO_DIAMONDS, 100, "Planta", "Danza Pétalo", "50"),

    // Fuego
    PokemonCard("A1-033", "Charmander", BoosterPack.CHARIZARD, CardRarity.ONE_DIAMOND, 60, "Fuego", "Ascuas", "30"),
    PokemonCard("A1-034", "Charmeleon", BoosterPack.CHARIZARD, CardRarity.TWO_DIAMONDS, 90, "Fuego", "Garra Ígnea", "60"),
    PokemonCard("A1-035", "Charizard", BoosterPack.CHARIZARD, CardRarity.THREE_DIAMONDS, 150, "Fuego", "Giro Fuego", "150"),
    PokemonCard("A1-036", "Charizard ex", BoosterPack.CHARIZARD, CardRarity.FOUR_DIAMONDS, 180, "Fuego", "Torbellino Carmesí", "200", isEx = true),
    PokemonCard("A1-037", "Vulpix", BoosterPack.CHARIZARD, CardRarity.ONE_DIAMOND, 50, "Fuego", "Fuego Fatuo", "20"),
    PokemonCard("A1-038", "Ninetales", BoosterPack.CHARIZARD, CardRarity.TWO_DIAMONDS, 90, "Fuego", "Llamarada", "90"),
    PokemonCard("A1-042", "Ponyta", BoosterPack.CHARIZARD, CardRarity.ONE_DIAMOND, 60, "Fuego", "Cociente Ígneo", "20"),
    PokemonCard("A1-043", "Rapidash", BoosterPack.CHARIZARD, CardRarity.TWO_DIAMONDS, 90, "Fuego", "Patada Fuego", "40"),
    PokemonCard("A1-044", "Magmar", BoosterPack.CHARIZARD, CardRarity.ONE_DIAMOND, 80, "Fuego", "Puño Fuego", "50"),
    PokemonCard("A1-047", "Moltres ex", BoosterPack.CHARIZARD, CardRarity.FOUR_DIAMONDS, 140, "Fuego", "Danza Infernal", "70", isEx = true),
    PokemonCard("A1-049", "Salandit", BoosterPack.CHARIZARD, CardRarity.ONE_DIAMOND, 60, "Fuego", "Ascuas", "20"),
    PokemonCard("A1-052", "Centiskorch", BoosterPack.CHARIZARD, CardRarity.TWO_DIAMONDS, 130, "Fuego", "Látigo Llameante", "80"),

    // Agua
    PokemonCard("A1-053", "Squirtle", BoosterPack.CHARIZARD, CardRarity.ONE_DIAMOND, 60, "Agua", "Pistola Agua", "20"),
    PokemonCard("A1-054", "Wartortle", BoosterPack.CHARIZARD, CardRarity.TWO_DIAMONDS, 80, "Agua", "Rayo Burbuja", "40"),
    PokemonCard("A1-055", "Blastoise", BoosterPack.CHARIZARD, CardRarity.THREE_DIAMONDS, 150, "Agua", "Hidrobomba", "100"),
    PokemonCard("A1-056", "Blastoise ex", BoosterPack.CHARIZARD, CardRarity.FOUR_DIAMONDS, 180, "Agua", "Cañón Hidráulico", "160", isEx = true),
    PokemonCard("A1-057", "Psyduck", BoosterPack.CHARIZARD, CardRarity.ONE_DIAMOND, 60, "Agua", "Jaque Dolor", "20"),
    PokemonCard("A1-059", "Poliwag", BoosterPack.CHARIZARD, CardRarity.ONE_DIAMOND, 60, "Agua", "Bofetón", "20"),
    PokemonCard("A1-066", "Shellder", BoosterPack.CHARIZARD, CardRarity.ONE_DIAMOND, 60, "Agua", "Lengüetazo", "20"),
    PokemonCard("A1-068", "Krabby", BoosterPack.CHARIZARD, CardRarity.ONE_DIAMOND, 70, "Agua", "Pinza", "30"),
    PokemonCard("A1-072", "Goldeen", BoosterPack.CHARIZARD, CardRarity.ONE_DIAMOND, 60, "Agua", "Cornada", "20"),
    PokemonCard("A1-074", "Staryu", BoosterPack.CHARIZARD, CardRarity.ONE_DIAMOND, 50, "Agua", "Bofetón", "20"),
    PokemonCard("A1-076", "Starmie ex", BoosterPack.CHARIZARD, CardRarity.FOUR_DIAMONDS, 130, "Agua", "Giro Rápido", "90", isEx = true),
    PokemonCard("A1-085", "Ducklett", BoosterPack.CHARIZARD, CardRarity.ONE_DIAMOND, 50, "Agua", "Picotazo", "20"),
    PokemonCard("A1-091", "Bruxish", BoosterPack.CHARIZARD, CardRarity.TWO_DIAMONDS, 90, "Agua", "Onda Psíquica", "40"),
    PokemonCard("A1-092", "Snom", BoosterPack.CHARIZARD, CardRarity.ONE_DIAMOND, 50, "Agua", "Nieve", "10"),
    PokemonCard("A1-093", "Frosmoth", BoosterPack.CHARIZARD, CardRarity.TWO_DIAMONDS, 90, "Agua", "Viento Helado", "40"),

    // Rayo
    PokemonCard("A1-095", "Pikachu", BoosterPack.PIKACHU, CardRarity.ONE_DIAMOND, 60, "Rayo", "Impactrueno", "30"),
    PokemonCard("A1-096", "Pikachu ex", BoosterPack.PIKACHU, CardRarity.FOUR_DIAMONDS, 120, "Rayo", "Círculo Eléctrico", "90", isEx = true),
    PokemonCard("A1-097", "Raichu", BoosterPack.PIKACHU, CardRarity.THREE_DIAMONDS, 100, "Rayo", "Rayo", "140"),
    PokemonCard("A1-101", "Zapdos ex", BoosterPack.PIKACHU, CardRarity.FOUR_DIAMONDS, 130, "Rayo", "Huracán Eléctrico", "100", isEx = true),
    PokemonCard("A1-105", "Blitzle", BoosterPack.PIKACHU, CardRarity.ONE_DIAMOND, 60, "Rayo", "Chispa", "20"),
    PokemonCard("A1-106", "Zebstrika", BoosterPack.PIKACHU, CardRarity.TWO_DIAMONDS, 90, "Rayo", "Nitrocarga Eléctrica", "50"),
    PokemonCard("A1-107", "Tynamo", BoosterPack.PIKACHU, CardRarity.ONE_DIAMOND, 40, "Rayo", "Chispa", "10"),
    PokemonCard("A1-108", "Eelektrik", BoosterPack.PIKACHU, CardRarity.TWO_DIAMONDS, 80, "Rayo", "Onda Trueno", "40"),
    PokemonCard("A1-110", "Helioptile", BoosterPack.PIKACHU, CardRarity.ONE_DIAMOND, 60, "Rayo", "Destello", "20"),
    PokemonCard("A1-111", "Heliolisk", BoosterPack.PIKACHU, CardRarity.TWO_DIAMONDS, 90, "Rayo", "Voltio Cambio", "50"),

    // Psíquico
    PokemonCard("A1-115", "Abra", BoosterPack.MEWTWO, CardRarity.ONE_DIAMOND, 50, "Psíquico", "Teletransporte", "10"),
    PokemonCard("A1-116", "Kadabra", BoosterPack.MEWTWO, CardRarity.TWO_DIAMONDS, 80, "Psíquico", "Confusión", "40"),
    PokemonCard("A1-117", "Alakazam", BoosterPack.MEWTWO, CardRarity.THREE_DIAMONDS, 130, "Psíquico", "Premonición", "90"),
    PokemonCard("A1-118", "Slowpoke", BoosterPack.MEWTWO, CardRarity.ONE_DIAMOND, 70, "Psíquico", "Bostezo", "20"),
    PokemonCard("A1-119", "Slowbro", BoosterPack.MEWTWO, CardRarity.TWO_DIAMONDS, 110, "Psíquico", "Paz Mental", "50"),
    PokemonCard("A1-121", "Haunter", BoosterPack.MEWTWO, CardRarity.TWO_DIAMONDS, 80, "Psíquico", "Lengüetazo", "30"),
    PokemonCard("A1-122", "Gengar", BoosterPack.MEWTWO, CardRarity.THREE_DIAMONDS, 130, "Psíquico", "Sombra Vil", "70"),
    PokemonCard("A1-123", "Gengar ex", BoosterPack.MEWTWO, CardRarity.FOUR_DIAMONDS, 170, "Psíquico", "Sombra Mortal", "100", isEx = true),
    PokemonCard("A1-129", "Mewtwo ex", BoosterPack.MEWTWO, CardRarity.FOUR_DIAMONDS, 150, "Psíquico", "Impulso Psíquico", "150", isEx = true),
    PokemonCard("A1-130", "Gardevoir", BoosterPack.MEWTWO, CardRarity.THREE_DIAMONDS, 110, "Psíquico", "Abrazo Psíquico", "60"),
    PokemonCard("A1-133", "Woobat", BoosterPack.MEWTWO, CardRarity.ONE_DIAMOND, 60, "Psíquico", "Ataque Ala", "20"),

    // Lucha
    PokemonCard("A1-135", "Golett", BoosterPack.MEWTWO, CardRarity.ONE_DIAMOND, 80, "Lucha", "Puñetazo", "30"),
    PokemonCard("A1-136", "Golurk", BoosterPack.MEWTWO, CardRarity.TWO_DIAMONDS, 130, "Lucha", "Mega Puño", "70"),
    PokemonCard("A1-137", "Sandshrew", BoosterPack.MEWTWO, CardRarity.ONE_DIAMOND, 70, "Lucha", "Arañazo", "20"),
    PokemonCard("A1-138", "Sandslash", BoosterPack.MEWTWO, CardRarity.TWO_DIAMONDS, 100, "Lucha", "Cuchillada", "60"),
    PokemonCard("A1-139", "Diglett", BoosterPack.MEWTWO, CardRarity.ONE_DIAMOND, 40, "Lucha", "Excavar", "20"),
    PokemonCard("A1-140", "Dugtrio", BoosterPack.MEWTWO, CardRarity.TWO_DIAMONDS, 80, "Lucha", "Bofetón Lodo", "40"),
    PokemonCard("A1-141", "Mankey", BoosterPack.MEWTWO, CardRarity.ONE_DIAMOND, 60, "Lucha", "Golpe Kárate", "20"),
    PokemonCard("A1-144", "Machoke", BoosterPack.MEWTWO, CardRarity.TWO_DIAMONDS, 90, "Lucha", "Sumisión", "50"),
    PokemonCard("A1-145", "Machamp", BoosterPack.MEWTWO, CardRarity.THREE_DIAMONDS, 150, "Lucha", "Puño Sísmico", "100"),
    PokemonCard("A1-150", "Onix", BoosterPack.MEWTWO, CardRarity.TWO_DIAMONDS, 110, "Lucha", "Atadura Pétrea", "50"),
    PokemonCard("A1-151", "Cubone", BoosterPack.MEWTWO, CardRarity.ONE_DIAMOND, 60, "Lucha", "Golpe Hueso", "30"),
    PokemonCard("A1-153", "Marowak ex", BoosterPack.MEWTWO, CardRarity.FOUR_DIAMONDS, 140, "Lucha", "Bumerán Óseo", "160", isEx = true),
    PokemonCard("A1-154", "Hitmonlee", BoosterPack.MEWTWO, CardRarity.ONE_DIAMOND, 70, "Lucha", "Patada Salto", "30"),
    PokemonCard("A1-155", "Hitmonchan", BoosterPack.MEWTWO, CardRarity.ONE_DIAMOND, 80, "Lucha", "Cometa Puño", "40"),
    PokemonCard("A1-156", "Rhyhorn", BoosterPack.MEWTWO, CardRarity.ONE_DIAMOND, 80, "Lucha", "Cornada", "30"),
    PokemonCard("A1-160", "Mienfoo", BoosterPack.MEWTWO, CardRarity.ONE_DIAMOND, 60, "Lucha", "Palmeo", "20"),
    PokemonCard("A1-161", "Mienshao", BoosterPack.MEWTWO, CardRarity.TWO_DIAMONDS, 90, "Lucha", "Patada Alta", "50"),

    // Oscuridad / Metal / Dragón / Incoloro / Entrenadores
    PokemonCard("A1-164", "Ekans", BoosterPack.MEWTWO, CardRarity.ONE_DIAMOND, 60, "Oscuridad", "Mordisco", "20"),
    PokemonCard("A1-175", "Muk", BoosterPack.MEWTWO, CardRarity.THREE_DIAMONDS, 130, "Oscuridad", "Lodo Tóxico", "70"),
    PokemonCard("A1-179", "Pawniard", BoosterPack.MEWTWO, CardRarity.ONE_DIAMOND, 60, "Oscuridad", "Corte", "20"),
    PokemonCard("A1-181", "Meltan", BoosterPack.MEWTWO, CardRarity.ONE_DIAMOND, 60, "Metal", "Rayo Haz", "20"),
    PokemonCard("A1-183", "Dratini", BoosterPack.CHARIZARD, CardRarity.ONE_DIAMOND, 60, "Dragón", "Bofetón", "20"),
    PokemonCard("A1-184", "Dragonair", BoosterPack.CHARIZARD, CardRarity.TWO_DIAMONDS, 90, "Dragón", "Ciclón", "40"),
    PokemonCard("A1-185", "Dragonite", BoosterPack.CHARIZARD, CardRarity.THREE_DIAMONDS, 160, "Dragón", "Cometa Draco", "180"),
    PokemonCard("A1-186", "Pidgey", BoosterPack.CHARIZARD, CardRarity.ONE_DIAMOND, 50, "Incoloro", "Tornado", "20"),
    PokemonCard("A1-189", "Rattata", BoosterPack.CHARIZARD, CardRarity.ONE_DIAMOND, 40, "Incoloro", "Mordisco", "10"),
    PokemonCard("A1-190", "Raticate", BoosterPack.CHARIZARD, CardRarity.TWO_DIAMONDS, 80, "Incoloro", "Superdiente", "40"),
    PokemonCard("A1-191", "Spearow", BoosterPack.CHARIZARD, CardRarity.ONE_DIAMOND, 50, "Incoloro", "Picotazo", "20"),
    PokemonCard("A1-192", "Fearow", BoosterPack.CHARIZARD, CardRarity.ONE_DIAMOND, 80, "Incoloro", "Pico Taladro", "40"),
    PokemonCard("A1-193", "Jigglypuff", BoosterPack.CHARIZARD, CardRarity.ONE_DIAMOND, 60, "Incoloro", "Canto", "10"),
    PokemonCard("A1-194", "Wigglytuff", BoosterPack.CHARIZARD, CardRarity.TWO_DIAMONDS, 100, "Incoloro", "Doble Bofetón", "50"),
    PokemonCard("A1-196", "Meowth", BoosterPack.MEWTWO, CardRarity.ONE_DIAMOND, 60, "Incoloro", "Día de Pago", "20"),
    PokemonCard("A1-198", "Farfetch’d", BoosterPack.CHARIZARD, CardRarity.ONE_DIAMOND, 60, "Incoloro", "Golpe Puerro", "40"),
    PokemonCard("A1-199", "Doduo", BoosterPack.CHARIZARD, CardRarity.ONE_DIAMOND, 60, "Incoloro", "Picotazo Doble", "20"),
    PokemonCard("A1-200", "Dodrio", BoosterPack.CHARIZARD, CardRarity.TWO_DIAMONDS, 80, "Incoloro", "Triataque", "50"),
    PokemonCard("A1-203", "Kangaskhan", BoosterPack.CHARIZARD, CardRarity.THREE_DIAMONDS, 100, "Incoloro", "Puño Mareo", "60"),
    PokemonCard("A1-204", "Tauros", BoosterPack.CHARIZARD, CardRarity.TWO_DIAMONDS, 100, "Incoloro", "Embestida", "50"),
    PokemonCard("A1-206", "Eevee", BoosterPack.CHARIZARD, CardRarity.ONE_DIAMOND, 60, "Incoloro", "Placaje", "20"),
    PokemonCard("A1-210", "Aerodactyl", BoosterPack.CHARIZARD, CardRarity.THREE_DIAMONDS, 100, "Incoloro", "Viento Primitivo", "50"),
    PokemonCard("A1-212", "Minccino", BoosterPack.CHARIZARD, CardRarity.ONE_DIAMOND, 60, "Incoloro", "Cosquillas", "20"),
    PokemonCard("A1-213", "Cinccino", BoosterPack.CHARIZARD, CardRarity.TWO_DIAMONDS, 90, "Incoloro", "Plumero", "50"),
    PokemonCard("A1-214", "Wooloo", BoosterPack.CHARIZARD, CardRarity.ONE_DIAMOND, 60, "Incoloro", "Rizo Defensa", "20"),
    PokemonCard("A1-217", "Dome Fossil", BoosterPack.CHARIZARD, CardRarity.ONE_DIAMOND, 40, "Entrenador", "Fósil", ""),
    PokemonCard("A1-221", "Blaine", BoosterPack.CHARIZARD, CardRarity.TWO_DIAMONDS, 0, "Entrenador", "Apoyo", "+30 daño Fuego"),
    PokemonCard("A1-225", "Sabrina", BoosterPack.MEWTWO, CardRarity.TWO_DIAMONDS, 0, "Entrenador", "Apoyo", "Cambia activo rival"),
    PokemonCard("A1-227", "Bulbasaur", BoosterPack.MEWTWO, CardRarity.ONE_STAR, 70, "Planta", "Látigo Cepa", "40", isFullArt = true),
    PokemonCard("A1-239", "Cubone", BoosterPack.MEWTWO, CardRarity.ONE_STAR, 60, "Lucha", "Golpe Hueso", "30", isFullArt = true),
    PokemonCard("A1-280", "Charizard ex", BoosterPack.CHARIZARD, CardRarity.THREE_STARS, 180, "Fuego", "Torbellino Carmesí", "200", isEx = true, isImmersive = true)
  )

  val ALL_CARDS: List<PokemonCard>
    get() = ((if (bundledCards.isEmpty()) STATIC_CARDS else bundledCards.values.toList()) + dynamicCards.values.toList()).distinctBy { it.id }.sortedBy { it.id }

  fun registerCard(
    id: String,
    name: String,
    raritySymbol: String = "♦",
    pack: BoosterPack = BoosterPack.UNKNOWN,
    type: String = "Sin verificar"
  ): PokemonCard {
    val cleanId = com.example.data.util.CardId.normalize(id)
    bundledCards[cleanId]?.let { return it }
    if (bundledCards.isEmpty()) STATIC_CARDS.find { it.id == cleanId }?.let { return it }
    val rarity = CardRarity.fromSymbol(raritySymbol)
    val newCard = PokemonCard(
      id = cleanId,
      name = name.ifBlank { "Carta $cleanId" },
      pack = pack,
      rarity = rarity,
      hp = 0,
      type = type,
      attackName = "",
      attackDamage = "",
      isEx = name.endsWith(" ex", ignoreCase = true),
      isFullArt = rarity in listOf(CardRarity.ONE_STAR, CardRarity.TWO_STARS),
      isSecretRare = rarity == CardRarity.CROWN,
      isImmersive = rarity == CardRarity.THREE_STARS
    )
    dynamicCards[cleanId] = newCard
    return newCard
  }

  /** A lookup never fabricates a card or mutates the catalog. */
  fun getCardById(id: String): PokemonCard? {
    val clean = runCatching { com.example.data.util.CardId.normalize(id) }.getOrNull() ?: return null
    return bundledCards[clean] ?: (if (bundledCards.isEmpty()) STATIC_CARDS.find { it.id == clean } else null) ?: dynamicCards[clean]
  }

  fun getCardsByPack(pack: BoosterPack): List<PokemonCard> = ALL_CARDS.filter { it.pack == pack }
}
