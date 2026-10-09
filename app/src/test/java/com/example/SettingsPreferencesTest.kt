package com.example

import androidx.datastore.preferences.core.*
import com.example.data.preferences.*
import com.example.data.util.*
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SettingsPreferencesTest {
  @Test fun automaticTracksSystemAndOverridesAreExplicit() {
    assertFalse(ThemeMode.LIGHT.isDark(true))
    assertTrue(ThemeMode.DARK.isDark(false))
    assertFalse(ThemeMode.SYSTEM.isDark(false))
    assertTrue(ThemeMode.SYSTEM.isDark(true))
    assertEquals(ThemeMode.SYSTEM, UserPreferences().themeMode)
  }

  @Test fun migrationDropsOnlyObsoleteSettingsAndPreservesModes() = runBlocking {
    val theme = stringPreferencesKey("theme_name")
    val unrelated = stringPreferencesKey("unrelated")
    for (oldMode in listOf("blue", "light", "dark", "system")) {
      val before = preferencesOf(theme to oldMode, unrelated to "preserved",
        stringPreferencesKey("app_language") to "ja", booleanPreferencesKey("is_dark_mode") to true)
      assertTrue(SettingsMigration.shouldMigrate(before))
      val after = SettingsMigration.migrate(before)
      assertEquals(if (oldMode == "blue") "light" else oldMode, after[theme])
      assertEquals("preserved", after[unrelated])
      assertFalse(after.asMap().keys.any { it.name in setOf("app_language", "is_dark_mode") })
      assertFalse(SettingsMigration.shouldMigrate(after))
    }
  }

  @Test fun avatarBackupRoundTripAndLegacyMissingFieldAreCompatible() {
    val snapshot = BackupSnapshot(emptyList(), emptyList(), UserPreferences(ThemeMode.DARK, "trainer_green"))
    val text = AppBackup.encode(snapshot)
    assertEquals(snapshot, AppBackup.decode(text))
    val legacy = JSONObject(text)
    legacy.getJSONObject("preferences").remove("avatar")
    val restored = AppBackup.decode(legacy.toString())
    assertFalse(restored.hasAvatarPreference)
    assertEquals(ThemeMode.DARK, restored.preferences.themeMode)
    legacy.getJSONObject("preferences").put("avatar", "future-avatar")
    assertEquals(ProfileAvatars.ids.first(), AppBackup.decode(legacy.toString()).preferences.avatarId)
  }

  @Test fun legacyBackupIgnoresLanguageAndMapsBlueWithoutDroppingCards() {
    val cards = listOf(com.example.data.local.InventoryCardEntity("A1-001", "Bulbasaur", "", "♦", 3, true))
    val snapshot = BackupSnapshot(cards, emptyList(), UserPreferences(ThemeMode.SYSTEM))
    val root = JSONObject(AppBackup.encode(snapshot))
    assertFalse(root.getJSONObject("preferences").has("language"))
    assertFalse(root.getJSONObject("preferences").has("dark"))
    root.put("preferences", JSONObject().put("language", "ja").put("dark", false).put("theme", "blue"))
    val restored = AppBackup.decode(root.toString())
    assertEquals(cards, restored.cards)
    assertEquals(ThemeMode.LIGHT, restored.preferences.themeMode)
    for (mode in ThemeMode.entries) {
      val state = snapshot.copy(preferences = UserPreferences(mode))
      assertEquals(state.preferences, AppBackup.decode(AppBackup.encode(state)).preferences)
    }
  }
}
