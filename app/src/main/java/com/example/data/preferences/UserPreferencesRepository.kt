package com.example.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.catch
import java.io.IOException

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "user_preferences")

data class UserPreferences(
  val isDarkMode: Boolean = true,
  val language: String = "es", // "es", "en", "ja"
  val themeName: String = "dark" // "dark", "blue", "light"
)

class UserPreferencesRepository(private val context: Context) {

  private object Keys {
    val IS_DARK_MODE = booleanPreferencesKey("is_dark_mode")
    val LANGUAGE = stringPreferencesKey("app_language")
    val THEME_NAME = stringPreferencesKey("theme_name")
  }

  val userPreferencesFlow: Flow<UserPreferences> = context.dataStore.data.catch { error ->
    if (error is IOException) emit(androidx.datastore.preferences.core.emptyPreferences()) else throw error
  }.map { preferences ->
    val isDark = preferences[Keys.IS_DARK_MODE] ?: true
    val lang = preferences[Keys.LANGUAGE] ?: "es"
    val theme = preferences[Keys.THEME_NAME] ?: "dark"
    UserPreferences(isDarkMode = isDark, language = lang, themeName = theme)
  }

  suspend fun restore(value: UserPreferences) {
    require(value.language in setOf("es", "en", "ja"))
    require(value.themeName in setOf("dark", "blue", "light"))
    context.dataStore.edit {
      it[Keys.IS_DARK_MODE] = value.isDarkMode
      it[Keys.LANGUAGE] = value.language
      it[Keys.THEME_NAME] = value.themeName
    }
  }

  suspend fun setDarkMode(enabled: Boolean) {
    context.dataStore.edit { preferences ->
      preferences[Keys.IS_DARK_MODE] = enabled
    }
  }

  suspend fun setLanguage(languageCode: String) {
    context.dataStore.edit { preferences ->
      preferences[Keys.LANGUAGE] = languageCode
    }
  }

  suspend fun setThemeName(themeName: String) {
    context.dataStore.edit { preferences ->
      preferences[Keys.THEME_NAME] = themeName
      preferences[Keys.IS_DARK_MODE] = themeName == "dark"
    }
  }
}
