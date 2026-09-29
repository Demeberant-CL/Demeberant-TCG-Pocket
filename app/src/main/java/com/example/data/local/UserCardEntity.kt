package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "user_cards")
data class UserCardEntity(
  @PrimaryKey val cardId: String,
  val setCode: String,
  val cardNumber: String,
  val name: String,
  val rarity: String,
  val quantity: Int = 1,
  val isRegistered: Boolean = true,
  val isFavorite: Boolean = false,
  val updatedAt: Long = System.currentTimeMillis()
)
