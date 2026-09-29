package com.example.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface SavedDeckDao {
  @Query("SELECT * FROM saved_decks ORDER BY createdAt DESC")
  fun getAllSavedDecksFlow(): Flow<List<SavedDeckEntity>>

  @Query("SELECT * FROM saved_decks WHERE id = :id LIMIT 1")
  suspend fun getDeckById(id: Long): SavedDeckEntity?

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertDeck(deck: SavedDeckEntity): Long

  @Update
  suspend fun updateDeck(deck: SavedDeckEntity)

  @Delete
  suspend fun deleteDeck(deck: SavedDeckEntity)

  @Query("DELETE FROM saved_decks WHERE id = :id")
  suspend fun deleteDeckById(id: Long)
}
