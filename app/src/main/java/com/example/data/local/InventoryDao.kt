package com.example.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface InventoryDao {
  @Query("SELECT * FROM inventory_cards ORDER BY acquisitionDate DESC")
  fun getAllCardsFlow(): Flow<List<InventoryCardEntity>>

  @Query("SELECT * FROM inventory_cards WHERE cardId = :cardId LIMIT 1")
  suspend fun getCardById(cardId: String): InventoryCardEntity?

  @Query("SELECT * FROM inventory_cards")
  suspend fun getAllCards(): List<InventoryCardEntity>

  @Query("SELECT COUNT(*) FROM inventory_cards")
  suspend fun getCardCount(): Int

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertCard(card: InventoryCardEntity)

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertCards(cards: List<InventoryCardEntity>)

  @Update
  suspend fun updateCard(card: InventoryCardEntity)

  @Delete
  suspend fun deleteCard(card: InventoryCardEntity)

  @Query("DELETE FROM inventory_cards WHERE cardId = :cardId")
  suspend fun deleteCardById(cardId: String)

  @Query("DELETE FROM inventory_cards")
  suspend fun clearAll()
}
