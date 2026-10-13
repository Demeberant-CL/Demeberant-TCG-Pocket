package com.example

import androidx.compose.ui.test.assertDoesNotExist
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import androidx.test.ext.junit.runners.AndroidJUnit4

@RunWith(AndroidJUnit4::class)
class DexVisualTest {
  @get:Rule
  val compose = createAndroidComposeRule<MainActivity>()

  @Test
  fun tcgLabUsesCurrentNavigationAndChatGptSettings() {
    compose.onNodeWithText("TCG Lab").assertIsDisplayed()
    compose.onNodeWithText("Diseño de la app").assertDoesNotExist()
    compose.onNodeWithText("Conexiones IA").assertDoesNotExist()
    compose.onNodeWithText("Rotom AI · Conector").assertDoesNotExist()

    compose.onNodeWithText("Abrir colección  ›").performClick()
    compose.onNodeWithText("← TCG Lab").performClick()
    compose.onNodeWithText("Ajustes").performClick()
    compose.onNodeWithText("Conexión ChatGPT").performClick()
    compose.onNodeWithText("ChatGPT").assertIsDisplayed()
  }
}
