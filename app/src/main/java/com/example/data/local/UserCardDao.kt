package com.example.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.model.UserCardEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface UserCardDao {

  @Query("SELECT * FROM user_pokemon_cards ORDER BY acquisitionDate DESC")
  fun getAllCards(): Flow<List<UserCardEntity>>

  @Query("SELECT * FROM user_pokemon_cards WHERE cardId = :cardId LIMIT 1")
  suspend fun getCardById(cardId: String): UserCardEntity?

  @Query("SELECT * FROM user_pokemon_cards WHERE rarity = :rarity ORDER BY acquisitionDate DESC")
  fun getCardsByRarity(rarity: String): Flow<List<UserCardEntity>>

  @Query("SELECT * FROM user_pokemon_cards WHERE isWishlist = 1 ORDER BY acquisitionDate DESC")
  fun getWishlistCards(): Flow<List<UserCardEntity>>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertCard(card: UserCardEntity)

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertCards(cards: List<UserCardEntity>)

  @Update
  suspend fun updateCard(card: UserCardEntity)

  @Query("UPDATE user_pokemon_cards SET ownedCount = :count, acquisitionDate = :updatedAt WHERE cardId = :cardId")
  suspend fun updateCount(cardId: String, count: Int, updatedAt: Long = System.currentTimeMillis())

  @Query("UPDATE user_pokemon_cards SET isWishlist = :isWishlist WHERE cardId = :cardId")
  suspend fun updateWishlist(cardId: String, isWishlist: Boolean)

  @Delete
  suspend fun deleteCard(card: UserCardEntity)

  @Query("DELETE FROM user_pokemon_cards WHERE cardId = :cardId")
  suspend fun deleteCardById(cardId: String)

  @Query("DELETE FROM user_pokemon_cards")
  suspend fun clearAll()
}
