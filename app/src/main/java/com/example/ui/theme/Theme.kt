package com.example.ui.theme

import android.app.Activity
import com.example.data.preferences.ThemeMode
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
  surfaceContainerHighest = Color(0xFFE2DDD6),
  surfaceContainerLowest = Color(0xFFFFFDFA),
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
  surfaceContainerHighest = Color(0xFF30353D),
  surfaceContainerLowest = Color(0xFF111316),
  surfaceDim = Color(0xFF141619),
  surfaceBright = Color(0xFF30353D),
  onBackground = Color(0xFFECEFF3),
  onSurface = Color(0xFFECEFF3),
  onSurfaceVariant = Color(0xFFC1C8D1),
  outline = Color(0xFF89929F),
  outlineVariant = Color(0xFF505967)
)

fun pocketColorScheme(mode: ThemeMode, systemDark: Boolean = true): androidx.compose.material3.ColorScheme {
  val dark = mode.isDark(systemDark)
  val base = if (dark) DarkColorScheme else LightColorScheme
  val accent = when (mode) {
    ThemeMode.TEAL -> Color(0xFF26D7CE)
    ThemeMode.AMOLED -> Color(0xFFB6C5E0)
    ThemeMode.NIGHT -> Color(0xFF81BCFF)
    ThemeMode.VIOLET -> Color(0xFFD2A9FF)
    ThemeMode.FOREST -> Color(0xFF83D9AA)
    ThemeMode.SAND -> Color(0xFF795332)
    ThemeMode.LIGHT -> Color(0xFFAC3D24)
    else -> base.primary
  }
  val background = when (mode) {
    ThemeMode.TEAL -> Color(0xFF111B24)
    ThemeMode.AMOLED -> Color.Black
    ThemeMode.NIGHT -> Color(0xFF0E192B)
    ThemeMode.VIOLET -> Color(0xFF1B1526)
    ThemeMode.FOREST -> Color(0xFF101D18)
    ThemeMode.SAND -> Color(0xFFF4EDE1)
    ThemeMode.LIGHT -> Color(0xFFFAF7F2)
    else -> base.background
  }
  val surface = when (mode) {
    ThemeMode.TEAL -> Color(0xFF1B2A35)
    ThemeMode.AMOLED -> Color(0xFF121416)
    ThemeMode.NIGHT -> Color(0xFF1A2C43)
    ThemeMode.VIOLET -> Color(0xFF2B2238)
    ThemeMode.FOREST -> Color(0xFF20372B)
    ThemeMode.SAND -> Color(0xFFEAE0D0)
    ThemeMode.LIGHT -> Color(0xFFF0EBE4)
    else -> base.surface
  }
  val container = if (dark) androidx.compose.ui.graphics.lerp(surface, accent, 0.15f)
    else androidx.compose.ui.graphics.lerp(surface, accent, 0.12f)
  return base.copy(primary = accent, onPrimary = if (dark) Color(0xFF101820) else Color.White,
    primaryContainer = container, onPrimaryContainer = if (dark) accent else Color(0xFF30251E),
    secondary = accent, secondaryContainer = container, onSecondaryContainer = base.onSurface,
    background = background, surface = surface, surfaceTint = Color.Transparent,
    surfaceContainerLowest = background, surfaceContainerLow = androidx.compose.ui.graphics.lerp(background, surface, 0.65f),
    surfaceContainer = surface, surfaceContainerHigh = androidx.compose.ui.graphics.lerp(surface, base.onSurface, 0.04f),
    surfaceContainerHighest = androidx.compose.ui.graphics.lerp(surface, base.onSurface, 0.08f),
    surfaceVariant = surface, surfaceDim = background, surfaceBright = surface,
    outlineVariant = androidx.compose.ui.graphics.lerp(surface, base.onSurface, 0.22f))
}

@Composable
fun PocketAppTheme(
  darkTheme: Boolean = isSystemInDarkTheme(),
  themeMode: ThemeMode = ThemeMode.SYSTEM,
  content: @Composable () -> Unit
) {
  val colorScheme = pocketColorScheme(themeMode, darkTheme)
  val view = LocalView.current
  if (!view.isInEditMode) {
    SideEffect {
      val window = (view.context as? Activity)?.window
      if (window != null) {
        val controller = WindowCompat.getInsetsController(window, view)
        controller.isAppearanceLightStatusBars = colorScheme.background.luminance() > 0.5f
        controller.isAppearanceLightNavigationBars = colorScheme.background.luminance() > 0.5f
      }
    }
  }
  MaterialTheme(colorScheme = colorScheme, typography = Typography,
    shapes = Shapes(extraSmall = RoundedCornerShape(8.dp), small = RoundedCornerShape(12.dp),
      medium = RoundedCornerShape(16.dp), large = RoundedCornerShape(20.dp), extraLarge = RoundedCornerShape(24.dp)),
    content = content)
}
