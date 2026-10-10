package com.example.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
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
  surfaceTint = Color.Transparent,
  primary = Color(0xFFFF8A40),
  onPrimary = Color(0xFF211A15),
  primaryContainer = Color(0xFF493021),
  onPrimaryContainer = Color(0xFFFFDBC8),
  secondary = Color(0xFFFFB88A),
  secondaryContainer = Color(0xFF30353D),
  onSecondaryContainer = Color(0xFFECEFF3),
  background = Color(0xFF141619),
  surface = Color(0xFF20242A),
  surfaceVariant = Color(0xFF272D35),
  surfaceContainer = Color(0xFF20242A),
  surfaceContainerLow = Color(0xFF1B1F25),
  surfaceContainerHigh = Color(0xFF272D35),
  onBackground = Color(0xFFECEFF3),
  onSurface = Color(0xFFECEFF3),
  onSurfaceVariant = Color(0xFFA8B0BA),
  outline = Color(0xFF89929F),
  outlineVariant = Color(0xFF333942)
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
    shapes = Shapes(extraSmall = RoundedCornerShape(6.dp), small = RoundedCornerShape(8.dp),
      medium = RoundedCornerShape(10.dp), large = RoundedCornerShape(10.dp), extraLarge = RoundedCornerShape(12.dp)),
    content = content
  )
}
