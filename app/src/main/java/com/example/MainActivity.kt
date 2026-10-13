package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.screens.CollectionScreen
import com.example.ui.theme.PocketAppTheme
import com.example.ui.theme.PocketSurface
import com.example.ui.viewmodel.TcgViewModel

class MainActivity : ComponentActivity() {

  private val viewModel: TcgViewModel by viewModels()
  private val advancedViewModel: com.example.ui.viewmodel.AdvancedViewModel by viewModels()

  @OptIn(ExperimentalMaterial3Api::class)
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()

    setContent {
      val userPrefs by viewModel.userPreferences.collectAsStateWithLifecycle()

      PocketAppTheme(
        themeMode = userPrefs.themeMode,
        visualStyle = com.example.data.preferences.VisualStyle.GALLERY,
      ) {
        var navigation by rememberSaveable { mutableStateOf(listOf(0)) }
        val selectedPanel = navigation.last()
        val screenStates = rememberSaveableStateHolder()
        var showExitConfirmation by rememberSaveable { mutableStateOf(false) }
        var deckEditorRequest by rememberSaveable { mutableIntStateOf(0) }
        var moreSection by rememberSaveable { mutableIntStateOf(-1) }
        var metaReturnTab by rememberSaveable { mutableIntStateOf(0) }

        fun navigateTo(destination: Int) {
          showExitConfirmation = false
          navigation = com.example.ui.AppNavigation.open(navigation, destination)
        }
        fun returnToHome() { navigation = listOf(0) }

        LaunchedEffect(selectedPanel) {
          com.example.data.util.AppDiagnostics.screen(when (selectedPanel) {
            0 -> "HOME"
            1 -> "COLLECTION"
            2 -> "DECKS"
            3 -> "CHATGPT"
            4 -> "GAME_TOOLS"
            6 -> "SETTINGS"
            else -> "META"
          })
        }

        BackHandler(enabled = selectedPanel == 0) { showExitConfirmation = true }
        if (showExitConfirmation) AlertDialog(
          onDismissRequest = { showExitConfirmation = false },
          title = { Text("¿Salir de la app?") },
          text = {
            Text(
              "Puedes seguir usando TCG Lab o salir. Tu colección, mazos y conexión ChatGPT se conservan.",
              modifier = Modifier.verticalScroll(rememberScrollState()),
            )
          },
          confirmButton = {
            TextButton(onClick = {
              showExitConfirmation = false
              this@MainActivity.finish()
            }) { Text("Salir") }
          },
          dismissButton = {
            TextButton(onClick = { showExitConfirmation = false }) { Text("Continuar") }
          },
        )

        Scaffold(Modifier.fillMaxSize()) { innerPadding ->
          screenStates.SaveableStateProvider(0) {
            com.example.ui.screens.HomeScreen(
              viewModel,
              advancedViewModel,
              Modifier.padding(innerPadding).consumeWindowInsets(innerPadding).fillMaxSize(),
              onDecks = {
                screenStates.removeState(2)
                deckEditorRequest = 0
                navigateTo(2)
              },
              onEditor = {
                deckEditorRequest = kotlin.math.abs(deckEditorRequest) + 1
                navigateTo(2)
              },
              onCreate = {
                deckEditorRequest = -kotlin.math.abs(deckEditorRequest) - 1
                navigateTo(2)
              },
              onMeta = {
                metaReturnTab = 0
                navigateTo(5)
              },
              onTools = {
                screenStates.removeState(4)
                moreSection = -1
                navigateTo(4)
              },
              onCollection = { expansion ->
                viewModel.setExpansionFilter(expansion)
                screenStates.removeState(1)
                navigateTo(1)
              },
              onZoneSync = {
                startActivity(android.content.Intent(this@MainActivity, com.example.zonebrowser.ZoneSyncActivity::class.java))
              },
              onAiSettings = { navigateTo(3) },
              onSettings = { navigateTo(6) },
            )
          }
        }

        if (selectedPanel != 0) ModalBottomSheet(
          onDismissRequest = { returnToHome() },
          sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
          modifier = Modifier.fillMaxHeight(0.94f),
          containerColor = PocketSurface,
        ) {
          Column(Modifier.fillMaxWidth().fillMaxHeight().systemBarsPadding()) {
            Row(
              Modifier.fillMaxWidth().background(PocketSurface),
              verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            ) {
              TextButton(onClick = { returnToHome() }) { Text("← TCG Lab") }
              Text(when (selectedPanel) {
                1 -> "Colección"
                2 -> "Mazos"
                3 -> "ChatGPT"
                4 -> "Herramientas"
                5 -> "Meta"
                6 -> "Ajustes"
                else -> "TCG Lab"
              }, color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant)
            }
            screenStates.SaveableStateProvider(selectedPanel) {
              val panelModifier = Modifier.weight(1f)
              when (selectedPanel) {
                1 -> CollectionScreen(
                  viewModel,
                  panelModifier,
                  onAiConnections = { navigateTo(3) },
                  advanced = advancedViewModel,
                )
                2 -> com.example.ui.screens.DeckMenuScreen(
                  viewModel,
                  advancedViewModel,
                  panelModifier,
                  onAskAi = { navigateTo(3) },
                  editorRequest = deckEditorRequest,
                )
                3 -> com.example.ui.screens.AIAssistantScreen(
                  viewModel,
                  advancedViewModel,
                  {
                    deckEditorRequest = kotlin.math.abs(deckEditorRequest) + 1
                    navigateTo(2)
                  },
                  panelModifier,
                  initialConfigure = true,
                  configurationOnly = true,
                )
                5 -> Column(panelModifier.fillMaxSize()) {
                  TextButton(onClick = { navigateTo(metaReturnTab) }) {
                    Text(if (metaReturnTab == 4) "← Herramientas del juego" else "← TCG Lab")
                  }
                  com.example.ui.screens.LiveMetaScreen(
                    viewModel,
                    advancedViewModel,
                    {
                      deckEditorRequest = kotlin.math.abs(deckEditorRequest) + 1
                      navigateTo(2)
                    },
                    Modifier.weight(1f),
                  )
                }
                6 -> com.example.ui.screens.MoreScreen(
                  viewModel,
                  advancedViewModel,
                  panelModifier,
                  settingsMenu = true,
                )
                else -> com.example.ui.screens.MoreScreen(
                  viewModel,
                  advancedViewModel,
                  panelModifier,
                  initialSection = moreSection,
                  onMeta = {
                    moreSection = -1
                    metaReturnTab = 4
                    navigateTo(5)
                  },
                )
              }
            }
          }
        }
      }
    }
  }
}
