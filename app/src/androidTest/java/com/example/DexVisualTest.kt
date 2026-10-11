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
      val partner = catalog.first { it.type == card.type && it.stage == "basic" && it.id != card.id }
      db.savedDeckDao().insertDeck(SavedDeckEntity(name=card.name,archetype="Prueba visual",strategy="",totalCards=4,
        cardListSerialized=DeckCodec.encode(listOf(com.example.data.repository.DeckCardEntry(card,2),
          com.example.data.repository.DeckCardEntry(partner,2)),listOf(card.type))))
    }
    compose.waitUntil(20_000) { compose.onAllNodesWithText("POCKET ATLAS").fetchSemanticsNodes().isNotEmpty() }
    val connectionsModel = ViewModelProvider(compose.activity)[AdvancedViewModel::class.java]
    compose.waitUntil(20_000) { connectionsModel.connectionReady.value && !connectionsModel.busy.value }
    compose.runOnIdle { connectionsModel.saveConnection(com.example.data.ai.AiConnection(apiKey="fixture-only", id="fixture-gemini", label="Gemini prueba")) }
    compose.waitUntil(10_000) { connectionsModel.profiles.value.entries.any { it.id == "fixture-gemini" } && !connectionsModel.busy.value }
    compose.runOnIdle { connectionsModel.saveConnection(com.example.data.ai.AiConnection(com.example.data.ai.AiProvider.GROQ, "llama-3.3-70b-versatile", "fixture-only", id="fixture-groq", label="Groq prueba")) }
    compose.waitUntil(10_000) { connectionsModel.profiles.value.entries.any { it.id == "fixture-groq" } && !connectionsModel.busy.value }
    compose.onNodeWithText("Sincronizar colección").assertDoesNotExist()
    Thread.sleep(6000) // Wait for real card artwork on a fresh emulator installation.
    capture("inicio")
    compose.onNode(hasScrollAction() and !hasTestTag("main_bottom_nav")).performScrollToNode(hasText("Mazos recientes"))
    compose.waitForIdle()
    Thread.sleep(5000) // Let the real catalog artwork load before the screenshot.
    capture("inicio-mazos")
    for ((index, name) in listOf(1 to "coleccion",2 to "mazos",4 to "ajustes")) {
      compose.onNodeWithTag("nav_item_$index").performClick()
      compose.waitForIdle()
      capture(name)
      if (index == 1) {
        compose.onNodeWithText("Lista").performClick()
        capture("coleccion-lista")
        compose.onNodeWithText("Cuadrícula").performScrollTo().performClick()
        capture("coleccion-grande")
        compose.onNodeWithText("Compacta").performScrollTo().performClick()
        compose.onNodeWithText("Filtrar").performClick()
        compose.onNodeWithText("Filtros avanzados").assertIsDisplayed()
        capture("coleccion-filtros")
        compose.onNodeWithText("Ver cartas").performClick()
        compose.onNodeWithContentDescription("Ajustes").performClick()
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
        compose.onNode(hasScrollAction() and !hasTestTag("main_bottom_nav")).performScrollToNode(hasText("Tutoriales"))
        compose.onNodeWithText("Tutoriales").performClick()
        capture("tutoriales")
        compose.onNodeWithText("Crear y editar un mazo").performScrollTo().performClick()
        compose.onNodeWithText("Paso 1").assertIsDisplayed()
        capture("tutorial-mazos")
        compose.onNodeWithText("← Tutoriales").performClick()
        compose.onNodeWithText("← Más herramientas").performClick()
        compose.onNode(hasScrollAction() and !hasTestTag("main_bottom_nav")).performScrollToNode(hasText("Ajustes"))
        compose.onNodeWithText("Ajustes").performClick()
        compose.onNodeWithText("Sincronizar colección").assertIsDisplayed()
        capture("mas-ajustes")
        compose.onNodeWithText("Listo").performClick()
        compose.onNode(hasScrollAction() and !hasTestTag("main_bottom_nav")).performScrollToNode(hasText("Conexiones IA"))
        compose.onNodeWithText("Conexiones IA").performClick()
        capture("ia-conexiones")
        compose.onNodeWithText("Cerrar").performClick()
        compose.onNodeWithText("← Más herramientas").performClick()
        for ((label, file) in listOf("Sobres" to "sobres", "Canjes" to "canjes", "Tapete de práctica" to "simulador", "Calculadora" to "calculadora")) {
          compose.onNode(hasScrollAction() and !hasTestTag("main_bottom_nav")).performScrollToNode(hasText(label))
          compose.onNodeWithText(label).performClick()
          compose.waitForIdle()
          capture(file)
          compose.onNodeWithText("← Más herramientas").performClick()
        }
      }
      if (index == 2) {
        val themeModel = ViewModelProvider(compose.activity)[TcgViewModel::class.java]
        for (mode in com.example.data.preferences.ThemeMode.entries.filter { it != com.example.data.preferences.ThemeMode.SYSTEM }) {
          compose.runOnIdle { themeModel.setThemeMode(mode) }
          compose.waitUntil(10_000) { themeModel.userPreferences.value.themeMode == mode }
          capture("tema-${mode.storedValue}")
        }
        compose.runOnIdle { themeModel.setThemeMode(com.example.data.preferences.ThemeMode.DARK) }
        compose.waitUntil(10_000) { themeModel.userPreferences.value.themeMode == com.example.data.preferences.ThemeMode.DARK }
        compose.onNodeWithText("Abrir").assertDoesNotExist()
        compose.onAllNodes(hasText(sample[0].name) and hasClickAction()).onFirst().performScrollTo().performClick()
        compose.onNodeWithTag("manual_deck_list").performScrollToNode(hasText("Añadir cartas"))
        compose.onNodeWithText("Añadir cartas").assertIsDisplayed()
        backToLibrary()
        compose.onNodeWithText("+ Crear mazo").performClick()
        compose.waitForIdle()
        capture("mazos-crear")
        compose.onNodeWithText("Crear propuestas").performScrollTo().performClick()
        compose.onNodeWithText("Crear propuestas").performScrollTo().performClick()
        val plannerModel = ViewModelProvider(compose.activity)[TcgViewModel::class.java]
        compose.waitUntil(45_000) { !plannerModel.isGeneratingDeck.value }
        check(plannerModel.localDeckPlans.value.isNotEmpty()) { plannerModel.localPlannerMessage.value ?: "No local proposals" }
        compose.onNodeWithTag("local_planner_list").performScrollToNode(hasText("Abrir en editor"))
        capture("constructor-local")
        compose.onAllNodesWithText("Ver cartas").onFirst().performScrollTo().performClick()
        capture("constructor-cartas")
        compose.onAllNodesWithText("Abrir en editor").onFirst().performScrollTo().performClick()
        if (compose.onAllNodesWithText("Reemplazar").fetchSemanticsNodes().isNotEmpty()) compose.onNodeWithText("Reemplazar").performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("manual_deck_list").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("manual_deck_list").performScrollToNode(hasText("Añadir cartas"))
        compose.onNodeWithText("Añadir cartas").assertIsDisplayed()
        backToLibrary()
        compose.onNodeWithText("+ Crear mazo").performClick()
        compose.onNodeWithText("Empezar").performScrollTo().performClick()
        if (compose.onAllNodesWithText("Crear nuevo").fetchSemanticsNodes().isNotEmpty()) {
          compose.onNodeWithText("Crear nuevo").performClick()
        }
        compose.waitForIdle()
        capture("mazos-editor")
        compose.onNodeWithText("Guía").assertDoesNotExist()
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
        compose.onNodeWithTag("deck_chat_input").performTextInput("Conserva esta pregunta")
        compose.onNodeWithTag("deck_chat_list").performScrollToNode(hasTestTag("quick_ai_switch"))
        compose.onNodeWithTag("quick_ai_switch").performClick()
        compose.onNodeWithTag("ai_profile_fixture-gemini").performClick()
        compose.waitUntil(10_000) { connectionsModel.connection.value.id == "fixture-gemini" && !connectionsModel.busy.value }
        compose.onNodeWithTag("deck_chat_input").assertTextContains("Conserva esta pregunta")
        compose.onNodeWithTag("quick_ai_switch").performClick()
        compose.onNodeWithTag("ai_profile_fixture-groq").performClick()
        compose.waitUntil(10_000) { connectionsModel.connection.value.id == "fixture-groq" && !connectionsModel.busy.value }
        compose.onNodeWithTag("deck_chat_input").assertTextContains("Conserva esta pregunta")
        capture("ia-cambio-rapido")
        compose.onNodeWithTag("deck_chat_input").performTextClearance()
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
        compose.onNodeWithTag("deck_to_game").performClick()
        compose.waitUntil(20_000) { compose.onAllNodesWithTag("deck_qr_image").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("QR del mazo").assertIsDisplayed()
        capture("mazos-qr")
        compose.onNodeWithText("Probar QR alternativo").assertDoesNotExist()
        compose.onNodeWithTag("deck_qr_close").performClick()
        // Re-enter the hub to capture the AI creation form without making a billable API request.
        backToLibrary()
        compose.onNodeWithText("+ Crear mazo").performClick()
        compose.onNode(hasText("Crear con IA") and hasClickAction()).performScrollTo().performClick()
        compose.onNodeWithText("Crear nuevo").performClick()
        compose.waitForIdle()
        capture("mazos-crear-ia")
        compose.onNodeWithText("← Crear mazo").performClick()
        compose.onNodeWithText("← Mis mazos").performClick()
        compose.onNodeWithTag("nav_item_4").assertExists()
      }
    }
  }
  private fun backToLibrary() {
    compose.onNodeWithText("← Mis mazos").performClick()
    if (compose.onAllNodesWithText("Volver sin guardar").fetchSemanticsNodes().isNotEmpty())
      compose.onNodeWithText("Volver sin guardar").performClick()
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
