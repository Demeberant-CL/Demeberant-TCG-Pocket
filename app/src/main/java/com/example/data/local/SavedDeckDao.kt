package com.example.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.data.model.SavedDeckEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SavedDeckDao {
  @Query("SELECT * FROM saved_decks ORDER BY createdAt DESC")
  fun getAllSavedDecks(): Flow<List<SavedDeckEntity>>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertDeck(deck: SavedDeckEntity)

  @Delete
  suspend fun deleteDeck(deck: SavedDeckEntity)

  @Query("DELETE FROM saved_decks WHERE id = :deckId")
  suspend fun deleteDeckById(deckId: String)
}
