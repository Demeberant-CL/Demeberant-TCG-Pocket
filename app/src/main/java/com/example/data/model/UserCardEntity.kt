package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room database entity storing user Pokémon card collection data.
 * Includes cardId, cardName, rarity, and acquisitionDate as primary attributes.
 */
@Entity(tableName = "user_pokemon_cards")
data class UserCardEntity(
  @PrimaryKey val cardId: String,
  val cardName: String,
  val rarity: String,
  val acquisitionDate: Long = System.currentTimeMillis(),
  val packName: String = "",
  val ownedCount: Int = 1,
  val isWishlist: Boolean = false,
  val notes: String = ""
)
