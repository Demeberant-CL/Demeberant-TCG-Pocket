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
  primary = Color(0xFFA33E00),
  onPrimary = Color.White,
  primaryContainer = Color(0xFFFFDBC8),
  onPrimaryContainer = Color(0xFF351000),
  secondary = Color(0xFF92400E),
  onSecondary = Color.White,
  secondaryContainer = PocketGoldLight,
  onSecondaryContainer = Color(0xFF78350F),
  background = Color(0xFFE6E3DF),
  surface = Color(0xFFFAF8F4),
  surfaceVariant = Color(0xFFEAE5DE),
  surfaceContainer = Color(0xFFF2EEE8),
  surfaceContainerLow = Color(0xFFF6F2EC),
  surfaceContainerHigh = Color(0xFFE8E2DA),
  onSurfaceVariant = Color(0xFF505257),
  outline = Color(0xFF77716A),
  outlineVariant = Color(0xFFB8B2A9),
  onBackground = Color(0xFF0F172A),
  onSurface = Color(0xFF0F172A)
)

private val DarkColorScheme = darkColorScheme(
  primary = Color(0xFFFF762D),
  onPrimary = Color(0xFF211A15),
  primaryContainer = Color(0xFF65371F),
  onPrimaryContainer = Color(0xFFFFDBC8),
  secondary = Color(0xFFD0C5DE),
  secondaryContainer = Color(0xFF4B445B),
  onSecondaryContainer = Color(0xFFE8DFF5),
  background = Color(0xFF111216),
  surface = Color(0xFF23262E),
  onBackground = Color(0xFFF1F0F4),
  onSurface = Color(0xFFF1F0F4),
  onSurfaceVariant = Color(0xFFC5C1CC),
  outline = Color(0xFF939097),
  outlineVariant = Color(0xFF49474F)
)

@Composable
fun PocketAppTheme(
  darkTheme: Boolean = isSystemInDarkTheme(),
  content: @Composable () -> Unit
) {
  val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

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
