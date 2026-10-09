package com.example.data.preferences

import android.content.Context
import androidx.datastore.core.DataMigration
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

enum class ThemeMode(val storedValue: String) {
  LIGHT("light"), DARK("dark"), SYSTEM("system");

  fun isDark(systemDark: Boolean): Boolean = when (this) {
    LIGHT -> false
    DARK -> true
    SYSTEM -> systemDark
  }

  companion object {
    fun fromStored(value: String?): ThemeMode = when (value) {
      "light", "blue" -> LIGHT
      "dark" -> DARK
      else -> SYSTEM
    }
  }
}

object ProfileAvatars {
  val ids = listOf("trainer_red", "trainer_teal", "trainer_orange", "trainer_violet", "trainer_green", "trainer_blue")
  val names = listOf("Entrenador rojo", "Entrenadora turquesa", "Entrenador naranja", "Entrenadora violeta", "Entrenador verde", "Entrenadora azul")
  fun normalize(id: String?): String = id?.takeIf { it in ids } ?: ids.first()
}

data class UserPreferences(val themeMode: ThemeMode = ThemeMode.SYSTEM, val avatarId: String = ProfileAvatars.ids.first())

private val avatarKey = stringPreferencesKey("profile_avatar")
private val themeKey = stringPreferencesKey("theme_name")

internal object SettingsMigration : DataMigration<Preferences> {
  private val obsoleteKeys = setOf("app_language", "is_dark_mode", "dex_theme_applied")
  override suspend fun shouldMigrate(currentData: Preferences): Boolean =
    currentData.asMap().keys.any { it.name in obsoleteKeys } ||
      currentData[themeKey]?.let { it !in ThemeMode.entries.map { mode -> mode.storedValue } } == true

  override suspend fun migrate(currentData: Preferences): Preferences {
    val preferences = currentData.toMutablePreferences()
    preferences.asMap().keys.filter { it.name in obsoleteKeys }.forEach { preferences.remove(it) }
    preferences[themeKey] = ThemeMode.fromStored(currentData[themeKey]).storedValue
    return preferences.toPreferences()
  }

  override suspend fun cleanUp() = Unit
}

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(
  name = "user_preferences",
  produceMigrations = { listOf(SettingsMigration) }
)

class UserPreferencesRepository(private val context: Context) {
  val userPreferencesFlow: Flow<UserPreferences> = context.dataStore.data.catch { error ->
    if (error is IOException) emit(emptyPreferences()) else throw error
  }.map { UserPreferences(ThemeMode.fromStored(it[themeKey]), ProfileAvatars.normalize(it[avatarKey])) }

  suspend fun restore(value: UserPreferences, restoreAvatar: Boolean = true) {
    context.dataStore.edit {
      it[themeKey] = value.themeMode.storedValue
      if (restoreAvatar) it[avatarKey] = ProfileAvatars.normalize(value.avatarId)
    }
  }

  suspend fun setAvatar(id: String) {
    require(id in ProfileAvatars.ids) { "Avatar no válido." }
    context.dataStore.edit { it[avatarKey] = id }
  }

  suspend fun setThemeMode(mode: ThemeMode) {
    context.dataStore.edit { it[themeKey] = mode.storedValue }
  }
}
