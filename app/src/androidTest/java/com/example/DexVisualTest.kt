package com.example

import android.graphics.Bitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.data.local.AppDatabase
import com.example.data.local.InventoryCardEntity
import com.example.data.local.SavedDeckEntity
import com.example.data.repository.CardCatalog
import com.example.data.util.DeckCodec
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Preview fixtures are written only into the disposable emulator, never a user's installation. */
@RunWith(AndroidJUnit4::class)
class DexVisualTest {
  @get:Rule val compose = createAndroidComposeRule<MainActivity>()
  @Test fun phoneScreensKeepTheApprovedThemeAndUsableNavigation() = runBlocking {
    val context = InstrumentationRegistry.getInstrumentation().targetContext
    CardCatalog.loadBundled(context)
    val db = AppDatabase.getDatabase(context)
    val catalog = CardCatalog.ALL_CARDS
    db.inventoryDao().insertCards(catalog.take(1343).mapIndexed { index, card ->
      InventoryCardEntity(card.id,card.name,card.pack.name,card.rarity.symbol,if (index < 1097) 2 else 1,false)
    })
    val sample = listOf("Pikachu", "Charizard").map { name ->
      catalog.first { it.name.startsWith(name,true) && it.isEx && it.isFullArt }
    }
    sample.forEach { card ->
      db.savedDeckDao().insertDeck(SavedDeckEntity(name=card.name,archetype="Prueba visual",strategy="",totalCards=2,
        cardListSerialized=DeckCodec.encode(listOf(com.example.data.repository.DeckCardEntry(card,2)),listOf("Rayo"),card.id)))
    }
    context.getSharedPreferences("zone-import-test",0).edit()
      .putString("player","0000000000000001").putString("profile_account","0000000000000001")
      .putString("profile_nickname","Demeberant").putInt("profile_level",50)
      .putLong("synced_at",System.currentTimeMillis()).putInt("new_cards",15).putLong("added_copies",26).commit()
    compose.waitUntil(20_000) { compose.onAllNodesWithText("Demeberant").fetchSemanticsNodes().isNotEmpty() }
    compose.onNodeWithText("Demeberant").assertIsDisplayed()
    compose.onNodeWithText("Nivel 50").assertIsDisplayed()
    compose.onNodeWithText("Sincronizar colección").assertIsDisplayed()
    capture("inicio")
    compose.onNode(hasScrollAction()).performScrollToNode(hasText("Mazos recientes"))
    compose.waitForIdle()
    capture("inicio-mazos")
    for ((index, name) in listOf(1 to "coleccion",2 to "mazos",3 to "ia",4 to "ajustes")) {
      compose.onNodeWithTag("nav_item_$index").performClick()
      compose.waitForIdle()
      capture(name)
    }
  }
  private fun capture(name: String) {
    val instrumentation = InstrumentationRegistry.getInstrumentation()
    val dir = File(instrumentation.targetContext.getExternalFilesDir(null),"design-preview").apply { mkdirs() }
    val image = instrumentation.uiAutomation.takeScreenshot()
    File(dir,"$name.png").outputStream().use { image.compress(Bitmap.CompressFormat.PNG,100,it) }
    image.recycle()
  }
}
