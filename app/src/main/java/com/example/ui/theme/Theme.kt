package com.example.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val LightColorScheme = lightColorScheme(
  primary = Color(0xFF0284C7),
  onPrimary = Color.White,
  primaryContainer = Color(0xFFE0F2FE),
  onPrimaryContainer = Color(0xFF0369A1),
  secondary = PocketGold,
  background = Color(0xFFF8FAFC),
  surface = Color.White,
  onBackground = Color(0xFF0F172A),
  onSurface = Color(0xFF0F172A)
)

private val DarkColorScheme = darkColorScheme(
  primary = Color(0xFF38BDF8),
  onPrimary = Color(0xFF0F172A),
  primaryContainer = Color(0xFF0369A1),
  onPrimaryContainer = Color(0xFFE0F2FE),
  secondary = PocketGold,
  background = Color(0xFF020617), // Slate 950
  surface = Color(0xFF0F172A),    // Slate 900
  onBackground = Color(0xFFF8FAFC),
  onSurface = Color(0xFFF8FAFC)
)

private val ClassicBlueColorScheme = lightColorScheme(
  primary = Color(0xFF0284C7),
  onPrimary = Color.White,
  primaryContainer = Color(0xFFE0F2FE),
  onPrimaryContainer = Color(0xFF0369A1),
  secondary = Color(0xFFF59E0B),
  background = Color(0xFFF0F9FF),
  surface = Color.White,
  onBackground = Color(0xFF0C4A6E),
  onSurface = Color(0xFF0C4A6E)
)

@Composable
fun PocketAppTheme(
  isDarkMode: Boolean = false,
  themeName: String = "dark",
  content: @Composable () -> Unit
) {
  val colorScheme = when {
    themeName == "light" -> LightColorScheme
    themeName == "blue" -> ClassicBlueColorScheme
    isDarkMode || themeName == "dark" -> DarkColorScheme
    else -> LightColorScheme
  }

  val view = LocalView.current
  if (!view.isInEditMode) {
    SideEffect {
      val window = (view.context as? Activity)?.window
      if (window != null) {
        val windowInsetsController = WindowCompat.getInsetsController(window, view)
        windowInsetsController.isAppearanceLightStatusBars = colorScheme.surface.luminance() > 0.5f
        windowInsetsController.isAppearanceLightNavigationBars = colorScheme.surface.luminance() > 0.5f
      }
    }
  }

  MaterialTheme(
    colorScheme = colorScheme,
    content = content
  )
}
