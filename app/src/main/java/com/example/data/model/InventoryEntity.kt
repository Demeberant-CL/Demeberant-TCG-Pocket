package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "card_inventory")
data class InventoryEntity(
  @PrimaryKey val cardId: String,
  val ownedCount: Int = 0,
  val isWishlist: Boolean = false,
  val updatedAt: Long = System.currentTimeMillis()
)
