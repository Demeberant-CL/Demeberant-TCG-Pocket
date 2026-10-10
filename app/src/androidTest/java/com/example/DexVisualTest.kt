package com.example

import android.graphics.Bitmap
import androidx.lifecycle.ViewModelProvider
import com.example.ui.viewmodel.TcgViewModel
import com.example.ui.viewmodel.AdvancedViewModel
import com.example.domain.*
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
        cardListSerialized=DeckCodec.encode(listOf(com.example.data.repository.DeckCardEntry(card,2)),listOf("Rayo"))))
    }
    compose.waitUntil(20_000) { compose.onAllNodesWithText("TCG Dex").fetchSemanticsNodes().isNotEmpty() }
    compose.onNodeWithText("Sincronizar colección").assertDoesNotExist()
    Thread.sleep(6000) // Wait for real card artwork on a fresh emulator installation.
    capture("inicio")
    compose.onNode(hasScrollAction() and !hasTestTag("main_bottom_nav")).performScrollToNode(hasText("Mazos recientes"))
    compose.waitForIdle()
    Thread.sleep(5000) // Let the real catalog artwork load before the screenshot.
    capture("inicio-mazos")
    for ((index, name) in listOf(1 to "coleccion",2 to "mazos",3 to "ia",4 to "ajustes")) {
      compose.onNodeWithTag("nav_item_$index").performClick()
      compose.waitForIdle()
      capture(name)
      if (index == 1) {
        compose.onNodeWithText("Filtrar").performClick()
        compose.onNodeWithText("Filtros avanzados").assertIsDisplayed()
        capture("coleccion-filtros")
        compose.onNodeWithText("Ver cartas").performClick()
        compose.onNodeWithText("Ajustes de colección").performClick()
        compose.onNodeWithText("Listo").assertIsDisplayed()
        capture("ajustes-dialogo")
        compose.onNodeWithText("Listo").performClick()
      }
      if (index == 3) {
        compose.waitUntil(20_000) { compose.onNodeWithText("Conexiones").fetchSemanticsNode().config.contains(androidx.compose.ui.semantics.SemanticsProperties.Disabled).not() }
        compose.onNodeWithText("Conexiones").performClick()
        capture("ia-conexiones")
        compose.onNodeWithText("Cerrar").performClick()
      }
      if (index == 4) {
        compose.onNode(hasScrollAction() and !hasTestTag("main_bottom_nav")).performScrollToNode(hasText("Ajustes"))
        compose.onNodeWithText("Ajustes").performClick()
        compose.onNodeWithText("Sincronizar colección").assertIsDisplayed()
        capture("mas-ajustes")
        compose.onNodeWithText("Listo").performClick()
        for ((label, file) in listOf("Sobres" to "sobres", "Canjes" to "canjes", "Simulador" to "simulador", "Calculadora" to "calculadora", "Filtros por efectos" to "efectos")) {
          compose.onNode(hasScrollAction() and !hasTestTag("main_bottom_nav")).performScrollToNode(hasText(label))
          compose.onNodeWithText(label).performClick()
          compose.waitForIdle()
          capture(file)
          compose.onNodeWithText("← Más herramientas").performClick()
        }
      }
      if (index == 2) {
        compose.onNodeWithText("+ Nuevo mazo").performClick()
        compose.waitForIdle()
        capture("mazos-crear")
        compose.onNodeWithText("Empezar").performClick()
        compose.waitForIdle()
        capture("mazos-editor")
        compose.onNodeWithText("Añadir cartas").performClick()
        compose.onNodeWithText("Filtrar · 0").performClick()
        compose.onNodeWithText("Objeto").performClick()
        capture("mazos-filtros")
        compose.onNode(hasScrollAction() and !hasTestTag("main_bottom_nav")).performScrollToNode(hasText("Efectos"))
        capture("mazos-filtros-efectos")
        compose.onNode(hasText("Ver ", substring = true) and hasText(" cartas", substring = true)).performClick()
        val manualItem = catalog.take(1343).first { it.category == "item" }
        compose.onNodeWithText("Buscar nombre o código").performTextInput(manualItem.name)
        capture("mazos-anadir")
        val main = ViewModelProvider(compose.activity)[TcgViewModel::class.java]
        compose.onAllNodesWithContentDescription("Añadir ${manualItem.name}").onFirst().performScrollTo().performClick()
        compose.waitUntil(10_000) { main.generatedDeck.value?.totalCardCount == 1 }
        compose.onAllNodesWithContentDescription("Quitar ${manualItem.name}").onFirst().performScrollTo().performClick()
        compose.waitUntil(10_000) { main.generatedDeck.value?.totalCardCount == 0 }
        val advanced = ViewModelProvider(compose.activity)[AdvancedViewModel::class.java]
        val basic = catalog.first { it.category == "pokemon" && it.stage == "basic" && it.type == "Fuego" }
        compose.runOnIdle { main.editDeckQuantity(basic.id, 2) }
        compose.onNodeWithText("Mi mazo (2)").performClick()
        capture("mazos-editor-con-cartas")
        compose.onNodeWithText("Hablar con IA").performClick()
        compose.waitForIdle()
        val base = main.generatedDeck.value!!
        val proposed = DeckAutomation.build(main.inventoryList.value, "Fuego").copy(name = base.name)
        check(proposed.totalCardCount == 20 && proposed.validationWarnings.isEmpty())
        compose.runOnIdle {
          advanced.deckChatMessages.value = listOf(DeckChatMessage(true, "Completa el mazo con mis cartas."),
            DeckChatMessage(false, "Esta propuesta de prueba conserva el tipo Fuego. Revisa las cartas y cantidades antes de aplicarla."))
          advanced.deckChatSuggestion.value = DeckChatSuggestion(base, AiProposal(proposed, emptyList()))
        }
        compose.waitForIdle()
        capture("mazos-chat")
        compose.onNodeWithText("Ver cambios").performScrollTo().performClick()
        capture("mazos-revisar")
        check(main.generatedDeck.value == base) { "Preview must not change the draft" }
        compose.onNodeWithText("Aplicar cambios").performClick()
        compose.waitUntil(20_000) { main.generatedDeck.value?.totalCardCount == 20 }
        compose.onNodeWithText("Guardar mazo · 20/20").assertIsDisplayed()
        // Re-enter the hub to capture the AI creation form without making a billable API request.
        compose.onNodeWithText("← Mis mazos").performClick()
        compose.onNodeWithText("+ Nuevo mazo").performClick()
        compose.onNode(hasText("Crear con IA") and hasClickAction()).performClick()
        compose.onNodeWithText("Crear nuevo").performClick()
        compose.waitForIdle()
        capture("mazos-crear-ia")
      }
    }
  }
  private fun capture(name: String) {
    compose.runOnIdle {
      val view = compose.activity.window.decorView
      (compose.activity.getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager)
        .hideSoftInputFromWindow(view.windowToken, 0)
    }
    compose.waitForIdle()
    Thread.sleep(1200) // Semantics can settle before the Android surface renders its final frame.
    val instrumentation = InstrumentationRegistry.getInstrumentation()
    val dir = File(instrumentation.targetContext.filesDir,"design-preview").apply { mkdirs() }
    val image = instrumentation.uiAutomation.takeScreenshot()
    File(dir,"$name.png").outputStream().use { image.compress(Bitmap.CompressFormat.PNG,100,it) }
    image.recycle()
  }
}
