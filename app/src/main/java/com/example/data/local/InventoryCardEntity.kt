package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "inventory_cards")
data class InventoryCardEntity(
  @PrimaryKey val cardId: String,
  val cardName: String,
  val packName: String,
  val rarity: String,
  val quantity: Int = 1,
  val isWishlist: Boolean = false,
  val acquisitionDate: Long = System.currentTimeMillis()
)
