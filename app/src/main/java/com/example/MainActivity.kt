package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.BackHandler
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Collections
import androidx.compose.material.icons.outlined.Style
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.Style
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.screens.CollectionScreen
import com.example.ui.theme.PocketAppTheme
import com.example.ui.theme.PocketSurface
import com.example.ui.theme.PocketTextSecondary
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
        darkTheme = userPrefs.themeMode.isDark(androidx.compose.foundation.isSystemInDarkTheme())
      ) {
        var navigation by rememberSaveable { mutableStateOf(listOf(0)) }
        val selectedTabIndex = navigation.last()
        val screenStates = rememberSaveableStateHolder()
        var showExitConfirmation by rememberSaveable { mutableStateOf(false) }
        fun navigateTo(destination: Int) {
          showExitConfirmation = false
          navigation = com.example.ui.AppNavigation.open(navigation, destination)
        }
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

        val labels = listOf("Inicio", "Colección", "Mazos", "Mi IA", "Más")
        val icons = listOf(Icons.Outlined.Home, Icons.Outlined.Collections, Icons.Outlined.Style,
          Icons.Outlined.AutoAwesome, Icons.Outlined.MoreHoriz)
        var globalSettings by rememberSaveable { mutableStateOf(false) }
        if (globalSettings) com.example.ui.screens.SettingsScreen(userPrefs.themeMode, viewModel::setThemeMode, { globalSettings = false })
        Scaffold(
          containerColor = Color.Transparent,
          modifier = Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(
            androidx.compose.material3.MaterialTheme.colorScheme.background,
            androidx.compose.material3.MaterialTheme.colorScheme.surfaceContainerLowest))),
          topBar = {
            androidx.compose.foundation.layout.Row(Modifier.statusBarsPadding().fillMaxWidth().heightIn(min = 52.dp).padding(horizontal = 16.dp),
              verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
              com.example.ui.components.DexBall(Modifier.size(24.dp))
              Spacer(Modifier.width(8.dp))
              val brandAccent = androidx.compose.material3.MaterialTheme.colorScheme.primary
              Text(androidx.compose.ui.text.buildAnnotatedString {
                append("TCG ")
                pushStyle(androidx.compose.ui.text.SpanStyle(color = brandAccent))
                append("Dex"); pop()
              }, modifier = Modifier.weight(1f), style = androidx.compose.material3.MaterialTheme.typography.titleLarge,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
              androidx.compose.material3.IconButton(onClick = { globalSettings = true }) {
                Icon(Icons.Outlined.Settings, "Configuración")
              }
            }
          },
          bottomBar = {
            BoxWithConstraints {
            val density = LocalDensity.current
            val textMeasurer = rememberTextMeasurer()
            val widestLabel = labels.maxOf { textMeasurer.measure(it, TextStyle(fontSize = 12.sp)).size.width }
            val itemWidth = maxOf(maxWidth / labels.size, with(density) { widestLabel.toDp() } + 24.dp)
            val navigationScroll = rememberScrollState()
            val activeIndex = if (selectedTabIndex == 5) metaReturnTab else selectedTabIndex
            LaunchedEffect(activeIndex, itemWidth, maxWidth) {
              val target = with(density) { (itemWidth * activeIndex - (maxWidth - itemWidth) / 2).roundToPx() }
              navigationScroll.animateScrollTo(target.coerceAtLeast(0))
            }
            NavigationBar(containerColor = androidx.compose.material3.MaterialTheme.colorScheme.background, modifier = Modifier.horizontalScroll(navigationScroll)
              .width(itemWidth * labels.size).heightIn(min = (72f + 28f * (density.fontScale - 1f).coerceAtLeast(0f)).dp)
              .testTag("main_bottom_nav")) {
              labels.forEachIndexed { index, label ->
                NavigationBarItem(
                  selected = selectedTabIndex == index || (selectedTabIndex == 5 && index == metaReturnTab),
                  onClick = {
                    if (selectedTabIndex != index) {
                      if (index == 2) deckEditorRequest = 0
                      if (index == 4) moreSection = -1
                      navigateTo(index)
                    }
                  },
                  icon = { Icon(icons[index], contentDescription = label) },
                  label = { Text(label, fontSize = 12.sp, maxLines = 1, softWrap = false, textAlign = androidx.compose.ui.text.style.TextAlign.Center) },
                  colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = androidx.compose.material3.MaterialTheme.colorScheme.primary,
                    selectedTextColor = androidx.compose.material3.MaterialTheme.colorScheme.primary,
                    indicatorColor = Color.Transparent,
                    unselectedIconColor = PocketTextSecondary,
                    unselectedTextColor = PocketTextSecondary
                  ),
                  modifier = Modifier.testTag("nav_item_$index")
                )
              }
            }
            }
          }
        ) { innerPadding ->
          val screenModifier = Modifier.padding(innerPadding)
          screenStates.SaveableStateProvider(selectedTabIndex) {
          when (selectedTabIndex) {
            0 -> com.example.ui.screens.HomeScreen(viewModel, advancedViewModel, screenModifier,
              onDecks = { screenStates.removeState(2); deckEditorRequest = 0; navigateTo(2) },
              onEditor = { deckEditorRequest++; navigateTo(2) },
              onMeta = { metaReturnTab = 0; navigateTo(5) },
              onSync = { startActivity(android.content.Intent(this@MainActivity, com.example.zonebrowser.ZoneSyncActivity::class.java)) },
              onCollection = { navigateTo(1) })
            1 -> CollectionScreen(viewModel, screenModifier)
            2 -> com.example.ui.screens.DeckMenuScreen(viewModel, screenModifier, onAskAi = { navigateTo(3) }, editorRequest = deckEditorRequest)
            3 -> com.example.ui.screens.AIAssistantScreen(viewModel, advancedViewModel, { deckEditorRequest++; navigateTo(2) }, screenModifier)
            5 -> androidx.compose.foundation.layout.Column(screenModifier.fillMaxSize()) {
              androidx.compose.material3.TextButton(onClick = { navigateTo(metaReturnTab) }) { Text(if (metaReturnTab == 4) "← Más herramientas" else "← Inicio") }
              com.example.ui.screens.LiveMetaScreen(viewModel, advancedViewModel, { navigateTo(3) }, Modifier.weight(1f))
            }
            else -> com.example.ui.screens.MoreScreen(viewModel, advancedViewModel, screenModifier,
              initialSection = moreSection, onMeta = { moreSection = -1; metaReturnTab = 4; navigateTo(5) })

          }
          }
        }
      }
    }
  }
}
