package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface UserCardDao {
  @Query("SELECT * FROM user_cards ORDER BY setCode ASC, cardNumber ASC")
  fun getAllUserCardsFlow(): Flow<List<UserCardEntity>>

  @Query("SELECT * FROM user_cards WHERE cardId = :cardId LIMIT 1")
  suspend fun getUserCardById(cardId: String): UserCardEntity?

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertUserCards(cards: List<UserCardEntity>)

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertUserCard(card: UserCardEntity)

  @Update
  suspend fun updateUserCard(card: UserCardEntity)

  @Query("DELETE FROM user_cards")
  suspend fun clearAll()
}
