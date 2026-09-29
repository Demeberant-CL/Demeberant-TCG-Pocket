package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "saved_decks")
data class SavedDeckEntity(
  @PrimaryKey val id: String,
  val name: String,
  val archetype: String,
  val strategy: String,
  val cardsJson: String, // Stored as "cardId:count,cardId:count"
  val createdAt: Long = System.currentTimeMillis()
)
