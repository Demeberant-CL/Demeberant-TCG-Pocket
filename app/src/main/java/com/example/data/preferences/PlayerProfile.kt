package com.example.data.preferences

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import org.json.JSONObject

data class PlayerProfile(val friendId: String = "", val nickname: String = "", val level: Int? = null,
  val syncedAt: Long = 0, val newCards: Int = 0, val addedCopies: Long = 0)

/** Profile fields stay local; a different account never inherits the previous player's name. */
object PlayerProfiles {
  private fun preferences(context: Context) = context.getSharedPreferences("zone-import-test", Context.MODE_PRIVATE)
  fun read(context: Context): PlayerProfile {
    val p = preferences(context)
    val account = p.getString("player", "").orEmpty()
    val matches = p.getString("profile_account", "") == account
    return PlayerProfile(account, if (matches) p.getString("profile_nickname", "").orEmpty() else "",
      if (matches) p.getInt("profile_level", 0).takeIf { it in 1..999 } else null,
      p.getLong("synced_at", 0), p.getInt("new_cards", 0), p.getLong("added_copies", 0))
  }
  fun observe(context: Context) = callbackFlow {
    val p = preferences(context)
    val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> trySend(read(context)) }
    p.registerOnSharedPreferenceChangeListener(listener)
    trySend(read(context))
    awaitClose { p.unregisterOnSharedPreferenceChangeListener(listener) }
  }.distinctUntilChanged()

  fun edit(context: Context, nickname: String, level: Int?) {
    require(nickname.trim().length in 1..60 && nickname.none { it.isISOControl() }) { "Nick no válido." }
    require(level == null || level in 1..999) { "Nivel no válido." }
    val account = read(context).friendId
    preferences(context).edit().putString("profile_account", account)
      .putString("profile_nickname", nickname.trim()).putInt("profile_level", level ?: 0).apply()
  }

  @JvmStatic fun capture(context: Context, expectedAccount: String, json: String) {
    if (!expectedAccount.matches(Regex("[0-9]{16}")) || json.length > 512) return
    val value = runCatching { JSONObject(json) }.getOrNull() ?: return
    if (value.optString("friendId") != expectedAccount) return
    val nickname = value.optString("nickname").trim()
    val level = value.optInt("level", 0)
    val p = preferences(context)
    val same = p.getString("profile_account", "") == expectedAccount
    val update = p.edit().putString("profile_account", expectedAccount)
    if (!same) update.remove("profile_nickname").remove("profile_level")
    if (nickname.length in 1..60 && nickname.none { it.isISOControl() }) update.putString("profile_nickname", nickname)
    if (level in 1..999) update.putInt("profile_level", level)
    update.apply()
  }
}
