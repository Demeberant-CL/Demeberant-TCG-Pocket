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
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.PlayArrow
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.screens.CollectionScreen
import com.example.ui.screens.DeckBuilderScreen
import com.example.ui.screens.PackSimulatorScreen
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
      PocketAppTheme {
        var selectedTabIndex by rememberSaveable { mutableIntStateOf(0) }

        Scaffold(
          modifier = Modifier.fillMaxSize(),
          bottomBar = {
            NavigationBar(
              containerColor = PocketSurface,
              tonalElevation = 8.dp,
              modifier = Modifier.testTag("main_bottom_nav")
            ) {
              NavigationBarItem(
                selected = selectedTabIndex == 0,
                onClick = { selectedTabIndex = 0 },
                icon = { Icon(Icons.Filled.Collections, contentDescription = "Colección") },
                label = { Text("Colección", fontSize = 11.sp) },
                colors = NavigationBarItemDefaults.colors(
                  selectedIconColor = PocketBluePrimary,
                  selectedTextColor = PocketBluePrimary,
                  indicatorColor = PocketBluePrimary.copy(alpha = 0.12f),
                  unselectedIconColor = PocketTextSecondary,
                  unselectedTextColor = PocketTextSecondary
                ),
                modifier = Modifier.testTag("nav_item_collection")
              )

              NavigationBarItem(
                selected = selectedTabIndex == 1,
                onClick = { selectedTabIndex = 1 },
                icon = { Icon(Icons.Filled.AutoAwesome, contentDescription = "Mazos IA") },
                label = { Text("Mazos IA", fontSize = 11.sp) },
                colors = NavigationBarItemDefaults.colors(
                  selectedIconColor = PocketBluePrimary,
                  selectedTextColor = PocketBluePrimary,
                  indicatorColor = PocketBluePrimary.copy(alpha = 0.12f),
                  unselectedIconColor = PocketTextSecondary,
                  unselectedTextColor = PocketTextSecondary
                ),
                modifier = Modifier.testTag("nav_item_deck")
              )

              NavigationBarItem(
                selected = selectedTabIndex == 2,
                onClick = { selectedTabIndex = 2 },
                icon = { Icon(Icons.Filled.PlayArrow, contentDescription = "Sobres") },
                label = { Text("Sobres", fontSize = 11.sp) },
                colors = NavigationBarItemDefaults.colors(
                  selectedIconColor = PocketBluePrimary,
                  selectedTextColor = PocketBluePrimary,
                  indicatorColor = PocketBluePrimary.copy(alpha = 0.12f),
                  unselectedIconColor = PocketTextSecondary,
                  unselectedTextColor = PocketTextSecondary
                ),
                modifier = Modifier.testTag("nav_item_packs")
              )
            }
          }
        ) { innerPadding ->
          val screenModifier = Modifier.padding(innerPadding)
          when (selectedTabIndex) {
            0 -> CollectionScreen(viewModel = viewModel, modifier = screenModifier)
            1 -> DeckBuilderScreen(viewModel = viewModel, modifier = screenModifier)
            2 -> PackSimulatorScreen(viewModel = viewModel, modifier = screenModifier)
          }
        }
      }
    }
  }
}
