package com.example.data.repository

import android.content.Context
import com.example.data.local.AppDatabase
import com.example.data.local.CardRulesEntity
import com.example.domain.CombatAttack
import com.example.domain.CombatData
import org.json.JSONObject

/** Bundled public card facts plus already cached details. No network or AI calls. */
object LocalCombatRepository {
  @Volatile private var bundled: Map<String, CombatData>? = null
  private fun load(context: Context): Map<String, CombatData> = bundled ?: synchronized(this) {
    bundled ?: context.assets.open("pocket-combat.json").bufferedReader().use { reader ->
      val cards = JSONObject(reader.readText()).getJSONObject("cards")
      val result = cards.keys().asSequence().associateWith { id ->
        val c = cards.getJSONObject(id); val a = c.optJSONArray("attacks")
        CombatData(if (c.isNull("hp")) null else c.getInt("hp"),
          if (c.isNull("retreat")) null else c.getInt("retreat"),
          if (a == null) emptyList() else (0 until a.length()).map { index ->
            val attack = a.getJSONObject(index); val costs = attack.optJSONArray("cost")
            CombatAttack(costs?.let { (0 until it.length()).map(it::getString) },
              attack.optString("damage"), attack.optString("effect"), attack.optString("name"))
          }, c.optString("text"))
      }.toMutableMap()
      context.assets.open("pocket-combat-extra.json").bufferedReader().use { extraReader ->
        val extra = JSONObject(extraReader.readText()).getJSONObject("cards")
        extra.keys().forEach { id ->
          val c = extra.getJSONObject(id)
          val old = result[id]
          val a = c.getJSONArray("attacks")
          val attacks = (0 until a.length()).map { index ->
            val attack = a.getJSONObject(index)
            val costs = attack.optJSONArray("cost")
            CombatAttack(costs?.let { (0 until it.length()).map(it::getString) },
              attack.optString("damage"), attack.optString("effect"), attack.optString("name"))
          }
          result[id] = CombatData(
            if (c.isNull("hp")) old?.hp else c.getInt("hp"),
            if (c.isNull("retreat")) old?.retreat else c.getInt("retreat"),
            attacks.ifEmpty { old?.attacks.orEmpty() },
            listOf(old?.text.orEmpty(), c.optString("text")).filter(String::isNotBlank).distinct().joinToString("\n"))
        }
      }
      result.toMap()
    }.also { bundled = it }
  }
  suspend fun card(context: Context, id: String, language: String): CombatData? {
    val rule = AppDatabase.getDatabase(context).cardRulesDao().get(id, language)
    return rule?.let(::fromRule) ?: load(context)[id]
  }
  suspend fun snapshot(context: Context): Map<String, CombatData> {
    val result = load(context).toMutableMap()
    val dao = AppDatabase.getDatabase(context).cardRulesDao()
    // Prefer Spanish cached effects but retain structured bundled attacks if a cost is missing.
    (dao.all("en") + dao.all("es")).forEach { rule ->
      val old = result[rule.cardId]
      val parsed = fromRule(rule)
      result[rule.cardId] = parsed.copy(attacks = parsed.attacks.takeIf { list -> list.any { it.cost != null } }
        ?: old?.attacks.orEmpty(), hp = parsed.hp ?: old?.hp, retreat = parsed.retreat ?: old?.retreat,
        text = parsed.text.ifBlank { old?.text.orEmpty() })
    }
    return result
  }
  internal fun fromRule(rule: CardRulesEntity): CombatData {
    val lines = rule.rulesText.lines()
    val attacks = lines.filter { "Coste:" in it }.map { line ->
      val before = line.substringBefore("Coste:").split('·').map(String::trim).filter(String::isNotBlank)
      val cost = line.substringAfter("Coste:").substringBefore('·').trim()
      CombatAttack(if (cost == "sin datos") null else cost.split(',').map(String::trim).filter(String::isNotBlank),
        before.lastOrNull()?.takeIf { it.firstOrNull()?.isDigit() == true }.orEmpty(), line.substringAfter(" · ", ""))
    }
    return CombatData(rule.hp, lines.firstOrNull { it.startsWith("Retirada:") }?.substringAfter(':')?.trim()?.toIntOrNull(),
      attacks, lines.filterNot { it.startsWith("PS:") || it.startsWith("Retirada:") }.joinToString("\n"))
  }
}
