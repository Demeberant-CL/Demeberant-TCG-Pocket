package com.example.data.util

object TcgdexHelper {
  @Volatile private var spanishImages: Map<String, String> = emptyMap()
  @Volatile private var communityImages: Map<String, String> = emptyMap()
  @Volatile private var communityThumbnails: Map<String, String> = emptyMap()

  fun loadImageIndex(context: android.content.Context) {
    val spanishRoot = org.json.JSONObject(context.assets.open("pocket-spanish-image-index.json").bufferedReader().use { it.readText() }).getJSONObject("images")
    spanishImages = spanishRoot.keys().asSequence().associateWith { key ->
      val path = spanishRoot.getString(key)
      require(path.startsWith("/cards/") && "/es_ES/" in path && path.endsWith(".png"))
      "https://ptcgp-assets.centropkmn.com$path"
    }
    val root = org.json.JSONObject(context.assets.open("pocket-image-index.json").bufferedReader().use { it.readText() })
    val revision = root.getString("revision")
    require(revision.matches(Regex("[a-f0-9]{40}")))
    val paths = root.getJSONArray("paths")
    val images = (0 until paths.length()).associate { i ->
      val path = paths.getString(i)
      require(path.matches(Regex("(?:[AB][0-9]+[a-z]*|PROMO-[AB])/[0-9]+\\.webp")))
      val (set, number) = path.removeSuffix(".webp").split("/")
      CardId.normalize("$set-$number") to
        "https://cdn.jsdelivr.net/gh/flibustier/pokemon-tcg-exchange@$revision/public/images/cards-by-set/$path"
    }
    communityImages = images
    // All indexed paths have a thumbnail in the pinned revision; do not guess for future indices.
    communityThumbnails = if (root.optString("thumbnailRevision") == revision) images.mapValues { (_, url) ->
      url.replace("/cards-by-set/", "/cards-by-set/thumbnails/")
    } else emptyMap()
  }

  fun imageCandidates(id: String, language: String = "es", highResolution: Boolean = false): List<String> {
    val key = CardId.normalize(id)
    val spanish = if (language == "es") listOfNotNull(spanishImages[key]) else emptyList()
    val primary = getCardImageUrl(id, language)
    val languages = listOf(primary, getCardImageUrl(id, "en")).distinct()
    val remote = languages.flatMap { low ->
      // Readers must never fall back to a thumbnail pretending it is high resolution.
      if (highResolution) listOf(low.replace("low.webp", "high.webp")) else listOf(low)
    }
    val community = if (highResolution) listOfNotNull(communityImages[key])
      else listOfNotNull(communityThumbnails[key], communityImages[key])
    val supportedHighSets = setOf("A1", "A1A", "A2", "A2A", "A2B", "A3", "A3A", "A4")
    // Known older sets have Spanish high assets. For newer sets use published explicit Spanish paths first.
    return (if (highResolution && key.substringBeforeLast('-') in supportedHighSets) remote.take(1) + spanish + remote.drop(1) + community
      else spanish + remote + community).distinct()
  }

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
