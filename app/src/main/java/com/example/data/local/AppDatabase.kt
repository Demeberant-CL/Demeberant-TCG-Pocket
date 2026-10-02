package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.data.util.CardId

@Database(entities = [InventoryCardEntity::class, SavedDeckEntity::class, UserCardEntity::class, CardRulesEntity::class],
  version = 5, exportSchema = true)
abstract class AppDatabase : RoomDatabase() {
  abstract fun inventoryDao(): InventoryDao
  abstract fun savedDeckDao(): SavedDeckDao
  abstract fun userCardDao(): UserCardDao
  abstract fun cardRulesDao(): CardRulesDao

  companion object {
    @Volatile private var INSTANCE: AppDatabase? = null

    /** Existing v3 data is retained; aliases merge by max quantity, never inflated by summing. */
    val MIGRATION_3_4 = object : Migration(3, 4) {
      override fun migrate(db: SupportSQLiteDatabase) {
        listOf("inventory_cards", "user_cards").forEach { table ->
          val ids = mutableListOf<String>()
          db.query("SELECT cardId FROM $table").use { cursor ->
            while (cursor.moveToNext()) ids.add(cursor.getString(0))
          }
          ids.forEach row@ { old ->
            val canonical = runCatching { CardId.normalize(old) }.getOrNull() ?: return@row
            if (canonical != old) {
              val flag = if (table == "inventory_cards") "isWishlist" else "isFavorite"
              // Fold an existing alias into the canonical row and retain the user flag.
              db.execSQL("""
                UPDATE $table SET quantity = MAX(quantity, (SELECT quantity FROM $table WHERE cardId = ?)),
                  $flag = MAX($flag, (SELECT $flag FROM $table WHERE cardId = ?)) WHERE cardId = ?
              """.trimIndent(), arrayOf(old, old, canonical))
              db.execSQL("UPDATE OR IGNORE $table SET cardId = ? WHERE cardId = ?", arrayOf(canonical, old))
              db.execSQL("DELETE FROM $table WHERE cardId = ?", arrayOf(old))
            }
          }
        }
        val knownIds = com.example.data.repository.CardCatalog.ALL_CARDS
          .filter { it.pack != com.example.data.model.BoosterPack.UNKNOWN }.map { it.id }.toSet()
        val importedIds = mutableListOf<String>()
        db.query("SELECT cardId FROM inventory_cards").use { cursor ->
          while (cursor.moveToNext()) importedIds.add(cursor.getString(0))
        }
        importedIds.filter { it !in knownIds }.forEach { id ->
          db.execSQL("UPDATE inventory_cards SET packName = ? WHERE cardId = ?",
            arrayOf(com.example.data.model.BoosterPack.UNKNOWN.displayName, id))
        }
        // Older imported rows stored display names, not rarity symbols; repository reads both.
        // Saved deck references are normalized when loaded, preserving their serialized contents.
      }
    }

    val MIGRATION_4_5 = object : Migration(4, 5) {
      override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("""CREATE TABLE IF NOT EXISTS card_rules (
          cardId TEXT NOT NULL, language TEXT NOT NULL, hp INTEGER,
          element TEXT NOT NULL, rulesText TEXT NOT NULL, searchText TEXT NOT NULL,
          roles TEXT NOT NULL, fetchedAt INTEGER NOT NULL, source TEXT NOT NULL,
          category TEXT NOT NULL, stage TEXT NOT NULL, evolvesFrom TEXT NOT NULL,
          PRIMARY KEY(cardId, language))""")
      }
    }

    private val queryLogger = java.util.concurrent.Executors.newSingleThreadExecutor { task ->
      Thread(task, "pocket-room-log").apply { isDaemon = true }
    }

    fun getDatabase(context: Context): AppDatabase = INSTANCE ?: synchronized(this) {
      INSTANCE ?: Room.databaseBuilder(context.applicationContext, AppDatabase::class.java,
        "tcg_pocket_inventory.db")
        .addMigrations(MIGRATION_3_4, MIGRATION_4_5)
        .setQueryCallback({ sql, _ ->
          com.example.data.util.ErrorLogManager.event("ROOM_QUERY", sql.trim().substringBefore(' ').uppercase())
        }, queryLogger)
        // An unknown earlier schema fails safely instead of deleting the collection.
        .build().also { INSTANCE = it }
    }
  }
}
