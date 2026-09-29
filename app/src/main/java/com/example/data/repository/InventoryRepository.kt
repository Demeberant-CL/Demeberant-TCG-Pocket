package com.example.data.repository

import com.example.data.local.AppDatabase
import com.example.data.local.InventoryCardEntity
import com.example.data.local.InventoryDao
import com.example.data.local.SavedDeckDao
import com.example.data.local.SavedDeckEntity
import com.example.data.local.UserCardDao
import com.example.data.local.UserCardEntity
import com.example.data.model.PokemonCard
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

data class CardWithInventory(
  val card: PokemonCard,
  val ownedCount: Int,
  val isWishlist: Boolean
)

data class ParsedCsvCard(
  val setCode: String,
  val cardNumber: String,
  val name: String,
  val rarity: String,
  val quantity: Int,
  val isRegistered: Boolean
)

class InventoryRepository(
  private val inventoryDao: InventoryDao,
  private val savedDeckDao: SavedDeckDao,
  private val userCardDao: UserCardDao
) {

  val inventoryFlow: Flow<List<CardWithInventory>> = inventoryDao.getAllCardsFlow().map { entities ->
    val entityMap = entities.associateBy { it.cardId.uppercase() }

    // Merge static and dynamic registered cards
    val allKnownCards = (CardCatalog.ALL_CARDS + entities.map { entity ->
      CardCatalog.getCardById(entity.cardId) ?: CardCatalog.registerCard(
        id = entity.cardId,
        name = entity.cardName,
        raritySymbol = entity.rarity
      )
    }).distinctBy { it.id.uppercase() }

    allKnownCards.map { card ->
      val ent = entityMap[card.id.uppercase()]
      CardWithInventory(
        card = card,
        ownedCount = ent?.quantity ?: 0,
        isWishlist = ent?.isWishlist ?: false
      )
    }
  }

  val savedDecksFlow: Flow<List<SavedDeckEntity>> = savedDeckDao.getAllSavedDecksFlow()

  suspend fun saveDeck(deck: SavedDeckEntity): Long {
    return savedDeckDao.insertDeck(deck)
  }

  suspend fun deleteDeck(deckId: Long) {
    savedDeckDao.deleteDeckById(deckId)
  }

  suspend fun toggleWishlist(cardId: String) {
    val existing = inventoryDao.getCardById(cardId)
    val card = CardCatalog.getCardById(cardId) ?: return
    if (existing == null) {
      inventoryDao.insertCard(
        InventoryCardEntity(
          cardId = card.id,
          cardName = card.name,
          packName = card.pack.displayName,
          rarity = card.rarity.displayName,
          quantity = 0,
          isWishlist = true
        )
      )
    } else {
      inventoryDao.updateCard(existing.copy(isWishlist = !existing.isWishlist))
    }
  }

  suspend fun processParsedCsvCards(csvCards: List<ParsedCsvCard>) {
    val inventoryEntities = mutableListOf<InventoryCardEntity>()
    val userCardEntities = mutableListOf<UserCardEntity>()

    csvCards.forEach { parsed ->
      val formattedId = "${parsed.setCode}-${parsed.cardNumber}"

      // Register with CardCatalog with actual CSV name!
      val registeredCard = CardCatalog.registerCard(
        id = formattedId,
        name = parsed.name,
        raritySymbol = parsed.rarity
      )

      val existing = inventoryDao.getCardById(registeredCard.id)

      inventoryEntities.add(
        InventoryCardEntity(
          cardId = registeredCard.id,
          cardName = registeredCard.name,
          packName = registeredCard.pack.displayName,
          rarity = registeredCard.rarity.displayName,
          quantity = parsed.quantity,
          isWishlist = existing?.isWishlist ?: false
        )
      )

      userCardEntities.add(
        UserCardEntity(
          cardId = registeredCard.id,
          setCode = parsed.setCode,
          cardNumber = parsed.cardNumber,
          name = registeredCard.name,
          rarity = parsed.rarity,
          quantity = parsed.quantity,
          isRegistered = parsed.isRegistered,
          isFavorite = existing?.isWishlist ?: false
        )
      )
    }

    if (inventoryEntities.isNotEmpty()) {
      inventoryDao.insertCards(inventoryEntities)
    }
    if (userCardEntities.isNotEmpty()) {
      userCardDao.insertUserCards(userCardEntities)
    }
  }

  companion object {
    fun fromDatabase(db: AppDatabase): InventoryRepository {
      return InventoryRepository(
        inventoryDao = db.inventoryDao(),
        savedDeckDao = db.savedDeckDao(),
        userCardDao = db.userCardDao()
      )
    }
  }
}
