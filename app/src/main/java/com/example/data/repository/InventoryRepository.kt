package com.example.data.repository

import androidx.room.withTransaction
import com.example.data.local.AppDatabase
import com.example.data.local.InventoryCardEntity
import com.example.data.local.SavedDeckEntity
import com.example.data.local.UserCardEntity
import com.example.data.model.BoosterPack
import com.example.data.model.PokemonCard
import com.example.data.util.CardId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map

 data class CardWithInventory(val card: PokemonCard, val ownedCount: Int, val isWishlist: Boolean)
 data class ParsedCsvCard(val setCode: String, val cardNumber: String, val name: String,
   val rarity: String, val quantity: Int, val isRegistered: Boolean, val wishlist: Boolean? = null)

class InventoryRepository private constructor(private val db: AppDatabase) {
  private val inventoryDao = db.inventoryDao()
  private val savedDeckDao = db.savedDeckDao()
  private val userCardDao = db.userCardDao()

  val inventoryFlow: Flow<List<CardWithInventory>> = inventoryDao.getAllCardsFlow().map { entities ->
    val entityMap = entities.associateBy { it.cardId }
    // Register persisted metadata before merging the catalog. Lookup has no side effects.
    val stored = entities.map { entity ->
      CardCatalog.registerCard(entity.cardId, entity.cardName, entity.rarity,
        BoosterPack.entries.find { it.displayName == entity.packName } ?: BoosterPack.UNKNOWN)
    }
    (CardCatalog.ALL_CARDS + stored).distinctBy { it.id }.map { card ->
      val entity = entityMap[card.id]
      CardWithInventory(card, entity?.quantity ?: 0, entity?.isWishlist ?: false)
    }
  }.flowOn(Dispatchers.Default)

  val savedDecksFlow: Flow<List<SavedDeckEntity>> = savedDeckDao.getAllSavedDecksFlow()
  suspend fun saveDeck(deck: SavedDeckEntity): Long = savedDeckDao.insertDeck(deck)
  suspend fun deleteDeck(deckId: Long) = savedDeckDao.deleteDeckById(deckId)

  suspend fun toggleWishlist(cardId: String) = db.withTransaction {
    val id = CardId.normalize(cardId)
    val card = CardCatalog.getCardById(id) ?: return@withTransaction
    val existing = inventoryDao.getCardById(id)
    if (existing == null) {
      inventoryDao.insertCard(InventoryCardEntity(card.id, card.name, card.pack.displayName,
        card.rarity.symbol, quantity = 0, isWishlist = true))
    } else inventoryDao.updateCard(existing.copy(isWishlist = !existing.isWishlist))
    userCardDao.getUserCardById(id)?.let { user ->
      userCardDao.updateUserCard(user.copy(isFavorite = !(existing?.isWishlist ?: false)))
    }
  }

  /** Import updates the supplied cards; cards absent from the CSV remain untouched. */
  suspend fun processParsedCsvCards(csvCards: List<ParsedCsvCard>) {
    require(csvCards.all { it.quantity >= 0 }) { "Cantidad de cartas no válida." }
    db.withTransaction {
      val existing = inventoryDao.getAllCards().associateBy { it.cardId }
      val inventoryEntities = mutableListOf<InventoryCardEntity>()
      val userEntities = mutableListOf<UserCardEntity>()
      csvCards.forEach { parsed ->
        val id = CardId.normalize("${parsed.setCode}-${parsed.cardNumber}")
        val old = existing[id]
        val card = CardCatalog.registerCard(id, parsed.name, parsed.rarity)
        inventoryEntities.add(InventoryCardEntity(card.id, card.name, card.pack.displayName,
          card.rarity.symbol, parsed.quantity, parsed.wishlist ?: old?.isWishlist ?: false,
          old?.acquisitionDate ?: System.currentTimeMillis()))
        val (set, number) = CardId.split(id)
        userEntities.add(UserCardEntity(card.id, set, number, card.name, card.rarity.symbol,
          parsed.quantity, parsed.quantity > 0, parsed.wishlist ?: old?.isWishlist ?: false))
      }
      inventoryDao.insertCards(inventoryEntities)
      userCardDao.insertUserCards(userEntities)
    }
  }

  suspend fun setQuantity(cardId: String, quantity: Int) = db.withTransaction {
    require(quantity in 0..99999) { "Cantidad no válida." }
    val id = CardId.normalize(cardId)
    val card = CardCatalog.getCardById(id) ?: error("Carta no disponible.")
    val old = inventoryDao.getCardById(id)
    inventoryDao.insertCard(InventoryCardEntity(id, card.name, card.pack.displayName, card.rarity.symbol,
      quantity, old?.isWishlist ?: false, old?.acquisitionDate ?: System.currentTimeMillis()))
    val (set, number) = CardId.split(id)
    userCardDao.insertUserCards(listOf(UserCardEntity(id, set, number, card.name, card.rarity.symbol,
      quantity, quantity > 0, old?.isWishlist ?: false)))
  }

  suspend fun snapshot() = db.withTransaction {
    inventoryDao.getAllCards() to savedDeckDao.getAllSavedDecksFlow().first()
  }

  suspend fun restoreSnapshot(cards: List<InventoryCardEntity>, decks: List<SavedDeckEntity>) = db.withTransaction {
    inventoryDao.insertCards(cards)
    userCardDao.insertUserCards(cards.map { card ->
      val (set, number) = CardId.split(card.cardId)
      UserCardEntity(card.cardId, set, number, card.cardName, card.rarity, card.quantity,
        card.quantity > 0, card.isWishlist)
    })
    val signatures = savedDeckDao.getAllSavedDecksFlow().first().map {
      Triple(it.name, it.cardListSerialized, it.strategy)
    }.toMutableSet()
    decks.forEach { deck ->
      if (signatures.add(Triple(deck.name, deck.cardListSerialized, deck.strategy))) savedDeckDao.insertDeck(deck.copy(id = 0))
    }
  }

  suspend fun isInventoryEmpty(): Boolean = inventoryDao.getCardCount() == 0

  companion object {
    fun fromDatabase(db: AppDatabase) = InventoryRepository(db)
  }
}
