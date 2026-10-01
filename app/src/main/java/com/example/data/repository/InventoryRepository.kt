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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map

 data class CardWithInventory(val card: PokemonCard, val ownedCount: Int, val isWishlist: Boolean)
 data class ParsedCsvCard(val setCode: String, val cardNumber: String, val name: String,
   val rarity: String, val quantity: Int, val isRegistered: Boolean)

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
          card.rarity.symbol, parsed.quantity, old?.isWishlist ?: false,
          old?.acquisitionDate ?: System.currentTimeMillis()))
        val (set, number) = CardId.split(id)
        userEntities.add(UserCardEntity(card.id, set, number, card.name, card.rarity.symbol,
          parsed.quantity, parsed.quantity > 0, old?.isWishlist ?: false))
      }
      inventoryDao.insertCards(inventoryEntities)
      userCardDao.insertUserCards(userEntities)
    }
  }

  suspend fun isInventoryEmpty(): Boolean = inventoryDao.getCardCount() == 0

  companion object {
    fun fromDatabase(db: AppDatabase) = InventoryRepository(db)
  }
}
