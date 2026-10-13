package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.BackHandler
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.screens.CollectionScreen
import com.example.ui.theme.PocketAppTheme
import com.example.ui.theme.PocketSurface
import com.example.ui.viewmodel.TcgViewModel

class MainActivity : ComponentActivity() {

  private val viewModel: TcgViewModel by viewModels()

  private val advancedViewModel: com.example.ui.viewmodel.AdvancedViewModel by viewModels()

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()

    setContent {
      val userPrefs by viewModel.userPreferences.collectAsStateWithLifecycle()

      PocketAppTheme(
        themeMode = userPrefs.themeMode, visualStyle = com.example.data.preferences.VisualStyle.GALLERY
      ) {
        var navigation by rememberSaveable { mutableStateOf(listOf(0)) }
        val selectedTabIndex = navigation.last()
        LaunchedEffect(selectedTabIndex) {
          com.example.data.util.AppDiagnostics.screen(when (selectedTabIndex) {
            0 -> "HOME"; 1 -> "COLLECTION"; 2 -> "DECKS"; 3 -> "AI_CONNECTIONS"; 4 -> "GAME_TOOLS"; 6 -> "SETTINGS"; else -> "META"
          })
        }
        val screenStates = rememberSaveableStateHolder()
        var showExitConfirmation by rememberSaveable { mutableStateOf(false) }
        fun navigateTo(destination: Int) {
          showExitConfirmation = false
          navigation = com.example.ui.AppNavigation.open(navigation, destination)
        }
        var deckDetail by rememberSaveable { mutableStateOf(false) }
        var deckEditorRequest by rememberSaveable { mutableIntStateOf(0) }
        var moreSection by rememberSaveable { mutableIntStateOf(-1) }
        var metaReturnTab by rememberSaveable { mutableIntStateOf(0) }
        // Dialogs and screen-local handlers get priority over this root handler.
        BackHandler {
          if (navigation.size > 1) navigation = com.example.ui.AppNavigation.back(navigation)
          else showExitConfirmation = true
        }
        if (showExitConfirmation) androidx.compose.material3.AlertDialog(
          onDismissRequest = { showExitConfirmation = false },
          title = { Text("¿Salir de la app?") },
          text = { Text("Puedes seguir usando la app o salir. Tu colección, mazos y conexiones guardados se conservan.",
            modifier = Modifier.verticalScroll(rememberScrollState())) },
          confirmButton = { androidx.compose.material3.TextButton(onClick = {
            showExitConfirmation = false
            this@MainActivity.finish()
          }) { Text("Salir") } },
          dismissButton = { androidx.compose.material3.TextButton(onClick = { showExitConfirmation = false }) { Text("Continuar") } }
        )

        Scaffold(
          modifier = Modifier.fillMaxSize(),
          topBar = {
            if (selectedTabIndex != 0) {
              androidx.compose.foundation.layout.Row(
                Modifier.fillMaxWidth().background(PocketSurface),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
              ) {
                androidx.compose.material3.TextButton(onClick = { navigation = listOf(0) }) {
                  Text("← TCG Lab")
                }
              }
            }
          },
        ) { innerPadding ->
          val screenModifier = Modifier.padding(innerPadding).consumeWindowInsets(innerPadding)
          screenStates.SaveableStateProvider(selectedTabIndex) {
          when (selectedTabIndex) {
            0 -> com.example.ui.screens.HomeScreen(viewModel, advancedViewModel, screenModifier,
              onDecks = { screenStates.removeState(2); deckEditorRequest = 0; navigateTo(2) },
              onEditor = { deckEditorRequest = kotlin.math.abs(deckEditorRequest) + 1; navigateTo(2) },
              onCreate = { deckEditorRequest = -kotlin.math.abs(deckEditorRequest) - 1; navigateTo(2) },
              onMeta = { metaReturnTab = 0; navigateTo(5) },
              onTrade = { screenStates.removeState(4); moreSection = 2; navigateTo(4) },
              onTools = { screenStates.removeState(4); moreSection = -1; navigateTo(4) },
              onCollection = { expansion -> viewModel.setExpansionFilter(expansion); screenStates.removeState(1); navigateTo(1) },
              onZoneSync = { startActivity(android.content.Intent(this@MainActivity, com.example.zonebrowser.ZoneSyncActivity::class.java)) },
              onAiSettings = { navigateTo(3) },
              onSettings = { navigateTo(6) })
            1 -> CollectionScreen(viewModel, screenModifier, onAiConnections = { navigateTo(3) }, advanced = advancedViewModel)
            2 -> com.example.ui.screens.DeckMenuScreen(viewModel, advancedViewModel, screenModifier, onAskAi = { navigateTo(3) }, editorRequest = deckEditorRequest, onDetailChange = { deckDetail = it })
            3 -> com.example.ui.screens.AIAssistantScreen(viewModel, advancedViewModel, { deckEditorRequest = kotlin.math.abs(deckEditorRequest) + 1; navigateTo(2) }, screenModifier, initialConfigure = true, configurationOnly = true)
            5 -> androidx.compose.foundation.layout.Column(screenModifier.fillMaxSize()) {
              androidx.compose.material3.TextButton(onClick = { navigateTo(metaReturnTab) }) { Text(if (metaReturnTab == 4) "← Herramientas del juego" else "← Inicio") }
              com.example.ui.screens.LiveMetaScreen(viewModel, advancedViewModel, { deckEditorRequest = kotlin.math.abs(deckEditorRequest) + 1; navigateTo(2) }, Modifier.weight(1f))
            }
            6 -> com.example.ui.screens.MoreScreen(viewModel, advancedViewModel, screenModifier, settingsMenu = true)
            else -> com.example.ui.screens.MoreScreen(viewModel, advancedViewModel, screenModifier,
              initialSection = moreSection, onMeta = { moreSection = -1; metaReturnTab = 4; navigateTo(5) })

          }
          }
        }
      }
    }
  }
}
