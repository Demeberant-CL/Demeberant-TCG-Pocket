package com.example.data.util

import com.example.data.local.InventoryCardEntity
import com.example.data.local.SavedDeckEntity
import com.example.data.model.CardRarity
import com.example.data.preferences.UserPreferences
import org.json.JSONArray
import org.json.JSONObject

data class BackupSnapshot(val cards: List<InventoryCardEntity>, val decks: List<SavedDeckEntity>,
  val preferences: UserPreferences)

object AppBackup {
  private const val FORMAT = "demeberant-tcg-pocket-backup"
  fun encode(value: BackupSnapshot): String = JSONObject().put("format", FORMAT).put("version", 1)
    .put("createdAt", System.currentTimeMillis())
    .put("inventory", JSONArray(value.cards.map { card -> JSONObject().put("id", card.cardId)
      .put("name", card.cardName).put("rarity", card.rarity).put("pack", card.packName)
      .put("quantity", card.quantity).put("wishlist", card.isWishlist).put("acquiredAt", card.acquisitionDate) }))
    .put("decks", JSONArray(value.decks.map { deck -> JSONObject().put("name", deck.name)
      .put("archetype", deck.archetype).put("strategy", deck.strategy).put("cards", deck.cardListSerialized)
      .put("total", deck.totalCards).put("createdAt", deck.createdAt) }))
    .put("preferences", JSONObject().put("dark", value.preferences.isDarkMode)
      .put("language", value.preferences.language).put("theme", value.preferences.themeName)).toString(2)

  fun decode(text: String): BackupSnapshot {
    require(text.length <= 8_000_000) { "Respaldo demasiado grande." }
    val root = JSONObject(text)
    require(root.getString("format") == FORMAT && root.getInt("version") == 1) { "Formato de respaldo no compatible." }
    val rows = root.getJSONArray("inventory")
    val cards = (0 until rows.length()).map { i ->
      val row = rows.getJSONObject(i)
      val quantity = row.getInt("quantity")
      require(quantity >= 0 && row.get("quantity").toString() == quantity.toString()) { "Cantidad no válida." }
      val name = row.getString("name")
      require(name.isNotBlank()) { "Nombre vacío." }
      InventoryCardEntity(CardId.normalize(row.getString("id")), name, row.getString("pack"),
        CardRarity.fromSymbol(row.getString("rarity")).symbol, quantity, row.getBoolean("wishlist"), row.getLong("acquiredAt"))
    }
    require(cards.map { it.cardId }.distinct().size == cards.size) { "Cartas duplicadas." }
    val entries = root.getJSONArray("decks")
    val decks = (0 until entries.length()).map { i ->
      val row = entries.getJSONObject(i)
      val content = row.getString("cards")
      val refs = DeckCodec.references(content)
      DeckCodec.energies(content)
      val total = row.getInt("total")
      require(total == refs.sumOf { it.second } && row.getString("name").isNotBlank()) { "Mazo no válido." }
      SavedDeckEntity(name = row.getString("name"), archetype = row.getString("archetype"),
        strategy = row.getString("strategy"), cardListSerialized = content, totalCards = total, createdAt = row.getLong("createdAt"))
    }
    val prefs = root.getJSONObject("preferences")
    val language = prefs.getString("language")
    val theme = prefs.getString("theme")
    require(language in setOf("es", "en", "ja") && theme in setOf("dark", "blue", "light")) { "Ajustes no válidos." }
    return BackupSnapshot(cards, decks, UserPreferences(prefs.getBoolean("dark"), language, theme))
  }
}
