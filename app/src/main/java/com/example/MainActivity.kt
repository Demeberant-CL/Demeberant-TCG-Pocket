package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.Collections
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

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()

    setContent {
      val userPrefs by viewModel.userPreferences.collectAsStateWithLifecycle()

      PocketAppTheme(
        isDarkMode = userPrefs.isDarkMode,
        themeName = userPrefs.themeName
      ) {
        var selectedTabIndex by rememberSaveable { mutableIntStateOf(0) }

        Scaffold(
          modifier = Modifier.fillMaxSize(),
          bottomBar = {
            NavigationBar(
              containerColor = PocketSurface,
              tonalElevation = 6.dp,
              modifier = Modifier.testTag("main_bottom_nav")
            ) {
              NavigationBarItem(
                selected = selectedTabIndex == 0,
                onClick = { selectedTabIndex = 0 },
                icon = { Icon(Icons.Filled.Collections, contentDescription = "Colección") },
                label = { Text("Colección", fontSize = 10.sp, fontWeight = if (selectedTabIndex == 0) FontWeight.Bold else FontWeight.Normal) },
                colors = NavigationBarItemDefaults.colors(
                  selectedIconColor = androidx.compose.material3.MaterialTheme.colorScheme.onSecondaryContainer,
                  selectedTextColor = androidx.compose.material3.MaterialTheme.colorScheme.secondary,
                  indicatorColor = androidx.compose.material3.MaterialTheme.colorScheme.secondaryContainer,
                  unselectedIconColor = PocketTextSecondary,
                  unselectedTextColor = PocketTextSecondary
                ),
                modifier = Modifier.testTag("nav_item_collection")
              )

              NavigationBarItem(
                selected = selectedTabIndex == 1,
                onClick = { selectedTabIndex = 1 },
                icon = { Icon(Icons.Filled.AutoAwesome, contentDescription = "Mazos") },
                label = { Text("Mazos", fontSize = 10.sp, fontWeight = if (selectedTabIndex == 1) FontWeight.Bold else FontWeight.Normal) },
                colors = NavigationBarItemDefaults.colors(
                  selectedIconColor = androidx.compose.material3.MaterialTheme.colorScheme.onSecondaryContainer,
                  selectedTextColor = androidx.compose.material3.MaterialTheme.colorScheme.secondary,
                  indicatorColor = androidx.compose.material3.MaterialTheme.colorScheme.secondaryContainer,
                  unselectedIconColor = PocketTextSecondary,
                  unselectedTextColor = PocketTextSecondary
                ),
                modifier = Modifier.testTag("nav_item_deck")
              )

              NavigationBarItem(
                selected = selectedTabIndex == 2,
                onClick = { selectedTabIndex = 2 },
                icon = { Icon(Icons.Filled.Insights, contentDescription = "Meta") },
                label = { Text("Meta", fontSize = 10.sp, fontWeight = if (selectedTabIndex == 2) FontWeight.Bold else FontWeight.Normal) },
                colors = NavigationBarItemDefaults.colors(
                  selectedIconColor = androidx.compose.material3.MaterialTheme.colorScheme.onSecondaryContainer,
                  selectedTextColor = androidx.compose.material3.MaterialTheme.colorScheme.secondary,
                  indicatorColor = androidx.compose.material3.MaterialTheme.colorScheme.secondaryContainer,
                  unselectedIconColor = PocketTextSecondary,
                  unselectedTextColor = PocketTextSecondary
                ),
                modifier = Modifier.testTag("nav_item_meta")
              )

              NavigationBarItem(
                selected = selectedTabIndex == 3,
                onClick = { selectedTabIndex = 3 },
                icon = { Icon(Icons.Filled.CardGiftcard, contentDescription = "Sobres") },
                label = { Text("Sobres", fontSize = 10.sp, fontWeight = if (selectedTabIndex == 3) FontWeight.Bold else FontWeight.Normal) },
                colors = NavigationBarItemDefaults.colors(
                  selectedIconColor = androidx.compose.material3.MaterialTheme.colorScheme.onSecondaryContainer,
                  selectedTextColor = androidx.compose.material3.MaterialTheme.colorScheme.secondary,
                  indicatorColor = androidx.compose.material3.MaterialTheme.colorScheme.secondaryContainer,
                  unselectedIconColor = PocketTextSecondary,
                  unselectedTextColor = PocketTextSecondary
                ),
                modifier = Modifier.testTag("nav_item_recommender")
              )

              NavigationBarItem(
                selected = selectedTabIndex == 4,
                onClick = { selectedTabIndex = 4 },
                icon = { Icon(Icons.Filled.Calculate, contentDescription = "Probabilidad") },
                label = { Text("Cálculo", fontSize = 10.sp, fontWeight = if (selectedTabIndex == 4) FontWeight.Bold else FontWeight.Normal) },
                colors = NavigationBarItemDefaults.colors(
                  selectedIconColor = androidx.compose.material3.MaterialTheme.colorScheme.onSecondaryContainer,
                  selectedTextColor = androidx.compose.material3.MaterialTheme.colorScheme.secondary,
                  indicatorColor = androidx.compose.material3.MaterialTheme.colorScheme.secondaryContainer,
                  unselectedIconColor = PocketTextSecondary,
                  unselectedTextColor = PocketTextSecondary
                ),
                modifier = Modifier.testTag("nav_item_probability")
              )
            }
          }
        ) { innerPadding ->
          val screenModifier = Modifier.padding(innerPadding)
          when (selectedTabIndex) {
            0 -> CollectionScreen(viewModel = viewModel, modifier = screenModifier)
            1 -> DeckBuilderScreen(viewModel = viewModel, modifier = screenModifier)
            2 -> MetaDeckAnalyzerScreen(
              viewModel = viewModel,
              onNavigateToDeckBuilder = { deckName ->
                viewModel.setDeckPrompt(deckName)
                viewModel.generateDeck(deckName)
                selectedTabIndex = 1
              },
              modifier = screenModifier
            )
            3 -> PackRecommenderScreen(viewModel = viewModel, modifier = screenModifier)
            4 -> ProbabilityCalculatorScreen(modifier = screenModifier)
          }
        }
      }
    }
  }
}
