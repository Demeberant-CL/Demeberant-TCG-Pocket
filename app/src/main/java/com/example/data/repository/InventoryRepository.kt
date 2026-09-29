package com.example.data.repository

import com.example.data.local.InventoryCardEntity
import com.example.data.local.InventoryDao
import com.example.data.model.PokemonCard
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

data class CardWithInventory(
  val card: PokemonCard,
  val ownedCount: Int,
  val isWishlist: Boolean
)

class InventoryRepository(private val dao: InventoryDao) {

  val inventoryFlow: Flow<List<CardWithInventory>> = dao.getAllCardsFlow().map { entities ->
    val entityMap = entities.associateBy { it.cardId.uppercase() }
    CardCatalog.ALL_CARDS.map { card ->
      val ent = entityMap[card.id.uppercase()]
      CardWithInventory(
        card = card,
        ownedCount = ent?.quantity ?: 0,
        isWishlist = ent?.isWishlist ?: false
      )
    }
  }

  suspend fun toggleWishlist(cardId: String) {
    val existing = dao.getCardById(cardId)
    val card = CardCatalog.getCardById(cardId) ?: return
    if (existing == null) {
      dao.insertCard(
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
      dao.updateCard(existing.copy(isWishlist = !existing.isWishlist))
    }
  }

  suspend fun setQuantitiesBatch(quantities: Map<String, Int>) {
    val entities = mutableListOf<InventoryCardEntity>()
    quantities.forEach { (rawId, qty) ->
      val card = CardCatalog.getCardById(rawId)
        ?: CardCatalog.ALL_CARDS.find { it.id.endsWith(rawId, ignoreCase = true) }
        ?: CardCatalog.ALL_CARDS.find { it.name.equals(rawId, ignoreCase = true) }

      if (card != null) {
        val existing = dao.getCardById(card.id)
        entities.add(
          InventoryCardEntity(
            cardId = card.id,
            cardName = card.name,
            packName = card.pack.displayName,
            rarity = card.rarity.displayName,
            quantity = qty,
            isWishlist = existing?.isWishlist ?: false
          )
        )
      } else {
        val existing = dao.getCardById(rawId)
        entities.add(
          InventoryCardEntity(
            cardId = rawId,
            cardName = "Carta $rawId",
            packName = "Expansión Genética",
            rarity = "Común",
            quantity = qty,
            isWishlist = existing?.isWishlist ?: false
          )
        )
      }
    }
    if (entities.isNotEmpty()) {
      dao.insertCards(entities)
    }
  }
}
