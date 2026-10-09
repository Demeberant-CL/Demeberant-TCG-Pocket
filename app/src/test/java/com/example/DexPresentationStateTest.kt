package com.example

import androidx.datastore.preferences.core.*
import com.example.data.preferences.*
import com.example.data.repository.CardCatalog
import com.example.data.util.*
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DexPresentationStateTest {
  @Test fun retiredDexThemeReturnsToSystemAndOtherSettingsSurvive() = runBlocking {
    val key = stringPreferencesKey("theme_name")
    val avatar = stringPreferencesKey("profile_avatar")
    val migrated = SettingsMigration.migrate(preferencesOf(key to "dex", avatar to "trainer_teal", booleanPreferencesKey("dex_theme_applied") to true))
    assertEquals("system", migrated[key])
    assertEquals("trainer_teal", migrated[avatar])
    val later = migrated.toMutablePreferences().apply { this[key] = "light" }.toPreferences()
    assertFalse(SettingsMigration.shouldMigrate(later))
    assertEquals("light", SettingsMigration.migrate(later)[key])
    assertEquals(ThemeMode.SYSTEM, ThemeMode.fromStored("dex"))
  }

  @Test fun selectedCoverSurvivesSerializationBackupAndRemovalWithoutChangingCounts() {
    val context = RuntimeEnvironment.getApplication()
    CardCatalog.loadBundled(context)
    val cards = DeckCodec.decode("A1-001:2;A1-033:1")
    val text = DeckCodec.encode(cards, listOf("Planta", "Fuego"), "A1-033")
    assertEquals("A1-033", DeckCodec.cover(text))
    assertEquals(cards, DeckCodec.decode(text))
    val saved = com.example.data.local.SavedDeckEntity(name="Portada",archetype="Manual",strategy="",cardListSerialized=text,totalCards=3)
    val backup = AppBackup.decode(AppBackup.encode(BackupSnapshot(emptyList(),listOf(saved),UserPreferences())))
    assertEquals("A1-033", DeckCodec.cover(backup.decks.single().cardListSerialized))
    val model = com.example.ui.viewmodel.TcgViewModel(context)
    val store = androidx.lifecycle.ViewModelStore().apply { put("cover",model) }
    try {
      model.loadSavedDeck(saved.copy(id=7))
      assertFalse(model.hasUnsavedDeckChanges())
      model.setDeckCover("A1-001")
      assertTrue(model.hasUnsavedDeckChanges())
      assertEquals(3, model.generatedDeck.value!!.totalCardCount)
      model.setDeckCover("A1-033")
      assertFalse(model.hasUnsavedDeckChanges())
      model.editDeckQuantity("A1-033", 0)
      assertNull(model.generatedDeck.value!!.coverCardId)
      assertEquals("A1-001",DeckCodec.cover(DeckCodec.encode(model.generatedDeck.value!!.cards,listOf("Planta"))))
    } finally { store.clear() }
  }

  @Test fun previousPlayersProfileIsNeverDisplayedAfterAccountChange() {
    val context = RuntimeEnvironment.getApplication()
    val prefs = context.getSharedPreferences("zone-import-test",0)
    prefs.edit().clear().putString("player","0000000000000001").commit()
    PlayerProfiles.edit(context,"Primer jugador",50)
    assertEquals("Primer jugador",PlayerProfiles.read(context).nickname)
    PlayerProfiles.capture(context,"0000000000000001",JSONObject().put("friendId","0000000000000002").put("nickname","Incorrecto").toString())
    assertEquals("Primer jugador",PlayerProfiles.read(context).nickname)
    prefs.edit().putString("player","0000000000000002").commit()
    assertEquals("",PlayerProfiles.read(context).nickname)
    assertNull(PlayerProfiles.read(context).level)
    PlayerProfiles.capture(context,"0000000000000002",JSONObject().put("friendId","0000000000000002").put("nickname","Segundo jugador").put("level",25).toString())
    assertEquals("Segundo jugador",PlayerProfiles.read(context).nickname)
    assertEquals(25,PlayerProfiles.read(context).level)
  }
}
