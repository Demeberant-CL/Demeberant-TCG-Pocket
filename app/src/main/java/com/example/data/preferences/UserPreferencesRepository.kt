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

data class UserPreferences(val themeMode: ThemeMode = ThemeMode.SYSTEM)

private val themeKey = stringPreferencesKey("theme_name")

internal object SettingsMigration : DataMigration<Preferences> {
  private val obsoleteKeys = setOf("app_language", "is_dark_mode")
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
  }.map { UserPreferences(ThemeMode.fromStored(it[themeKey])) }

  suspend fun restore(value: UserPreferences) = setThemeMode(value.themeMode)

  suspend fun setThemeMode(mode: ThemeMode) {
    context.dataStore.edit { it[themeKey] = mode.storedValue }
  }
}
