package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "saved_decks")
data class SavedDeckEntity(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  val name: String,
  val archetype: String,
  val strategy: String,
  val cardListSerialized: String,
  val totalCards: Int = 20,
  val createdAt: Long = System.currentTimeMillis()
)
