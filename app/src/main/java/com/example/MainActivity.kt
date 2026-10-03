package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.Style
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.screens.CollectionScreen
import com.example.ui.screens.DeckBuilderScreen
import com.example.ui.screens.MetaDeckAnalyzerScreen
import com.example.ui.screens.PackRecommenderScreen
import com.example.ui.screens.ProbabilityCalculatorScreen
import com.example.ui.theme.PocketAppTheme
import com.example.ui.theme.PocketBluePrimary
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
        var selectedTabIndex by rememberSaveable { mutableIntStateOf(0) }

        val labels = listOf("Colección", "Mazos", "Meta", "IA", "Más")
        val icons = listOf(Icons.Filled.Collections, Icons.Filled.Style,
          Icons.Filled.Insights, Icons.Filled.AutoAwesome, Icons.Filled.Menu)
        Scaffold(
          modifier = Modifier.fillMaxSize(),
          bottomBar = {
            NavigationBar(containerColor = PocketSurface, modifier = Modifier.testTag("main_bottom_nav")) {
              labels.forEachIndexed { index, label ->
                NavigationBarItem(
                  selected = selectedTabIndex == index,
                  onClick = { selectedTabIndex = index },
                  icon = { Icon(icons[index], contentDescription = label) },
                  label = { Text(label, fontSize = 12.sp) },
                  colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = androidx.compose.material3.MaterialTheme.colorScheme.onSecondaryContainer,
                    selectedTextColor = androidx.compose.material3.MaterialTheme.colorScheme.secondary,
                    indicatorColor = androidx.compose.material3.MaterialTheme.colorScheme.secondaryContainer,
                    unselectedIconColor = PocketTextSecondary,
                    unselectedTextColor = PocketTextSecondary
                  ),
                  modifier = Modifier.testTag("nav_item_$index")
                )
              }
            }
          }
        ) { innerPadding ->
          val screenModifier = Modifier.padding(innerPadding)
          when (selectedTabIndex) {
            0 -> CollectionScreen(viewModel, screenModifier)
            1 -> com.example.ui.screens.DeckMenuScreen(viewModel, screenModifier, onAskAi = { selectedTabIndex = 3 })
            2 -> com.example.ui.screens.LiveMetaScreen(viewModel, advancedViewModel, { selectedTabIndex = 3 }, screenModifier)
            3 -> com.example.ui.screens.AIAssistantScreen(viewModel, advancedViewModel, { selectedTabIndex = 1 }, screenModifier)
            else -> com.example.ui.screens.MoreScreen(viewModel, advancedViewModel, screenModifier)

          }
        }
      }
    }
  }
}
