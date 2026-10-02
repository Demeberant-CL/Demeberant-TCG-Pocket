package com.example.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "card_rules", primaryKeys = ["cardId", "language"])
data class CardRulesEntity(
  val cardId: String, val language: String, val hp: Int?, val element: String,
  val rulesText: String, val searchText: String, val roles: String, val fetchedAt: Long,
  val source: String = "TCGdex", val category: String = "", val stage: String = "", val evolvesFrom: String = ""
)

@Dao
interface CardRulesDao {
  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun upsert(card: CardRulesEntity)
  @Query("SELECT * FROM card_rules WHERE cardId = :id AND language = :language")
  suspend fun get(id: String, language: String): CardRulesEntity?
  @Query("SELECT * FROM card_rules WHERE language = :language")
  suspend fun all(language: String): List<CardRulesEntity>
  @Query("""SELECT * FROM card_rules WHERE language = :language
    AND (:minHp IS NULL OR hp >= :minHp) AND (:maxHp IS NULL OR hp <= :maxHp)
    AND (:element = '' OR element = :element)
    AND (:role = '' OR instr(roles, '|' || :role || '|') > 0)
    AND (:keyword = '' OR instr(searchText, :keyword) > 0)
    ORDER BY cardId""")
  fun filter(language: String, minHp: Int?, maxHp: Int?, element: String, role: String, keyword: String): Flow<List<CardRulesEntity>>
}
