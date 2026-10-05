package com.example.data.util

object TcgdexHelper {
  @Volatile private var communityImages: Map<String, String> = emptyMap()
  @Volatile private var communityThumbnails: Map<String, String> = emptyMap()

  fun loadImageIndex(context: android.content.Context) {
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
    val primary = getCardImageUrl(id, language)
    val languages = listOf(primary, getCardImageUrl(id, "en")).distinct()
    return languages.flatMap { low ->
      if (highResolution) listOf(low.replace("low.webp", "high.webp"), low) else listOf(low)
    } + CardId.normalize(id).let { key ->
      if (highResolution) listOfNotNull(communityImages[key], communityThumbnails[key])
      else listOfNotNull(communityThumbnails[key], communityImages[key])
    }
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
