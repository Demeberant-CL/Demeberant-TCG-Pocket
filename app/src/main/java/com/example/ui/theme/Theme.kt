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
  primary = Color(0xFF006879),
  onPrimary = Color.White,
  primaryContainer = Color(0xFFBAF0FA),
  onPrimaryContainer = Color(0xFF002027),
  secondary = Color(0xFF65518A),
  onSecondary = Color.White,
  secondaryContainer = Color(0xFFEADDFF),
  onSecondaryContainer = Color(0xFF211137),
  background = Color(0xFFE6EDF7),
  surface = Color(0xFFF6F9FF),
  surfaceVariant = Color(0xFFDEE7F3),
  surfaceContainer = Color(0xFFEDF2FA),
  surfaceContainerLow = Color(0xFFF2F6FC),
  surfaceContainerHigh = Color(0xFFE2EAF5),
  onSurfaceVariant = Color(0xFF505257),
  outline = Color(0xFF77716A),
  outlineVariant = Color(0xFFAABBD0),
  onBackground = Color(0xFF0F172A),
  onSurface = Color(0xFF0F172A)
)

private val DarkColorScheme = darkColorScheme(
  primary = Color(0xFF22D3EE), onPrimary = Color(0xFF041725),
  primaryContainer = Color(0xFF123C57), onPrimaryContainer = Color(0xFFBCF3FF),
  secondary = Color(0xFFB79AFF), onSecondary = Color(0xFF1C1235),
  secondaryContainer = Color(0xFF30224F), onSecondaryContainer = Color(0xFFE7DCFF),
  tertiary = Color(0xFF80AFFF), onTertiary = Color(0xFF071C39),
  tertiaryContainer = Color(0xFF1A335B), onTertiaryContainer = Color(0xFFD3E3FF),
  background = Color(0xFF091426), onBackground = Color(0xFFF3F6FF),
  surface = Color(0xFF102039), onSurface = Color(0xFFF3F6FF),
  surfaceVariant = Color(0xFF1A2D49), onSurfaceVariant = Color(0xFFB4C5DE),
  surfaceContainerLowest = Color(0xFF0B172B), surfaceContainerLow = Color(0xFF101F36),
  surfaceContainer = Color(0xFF14243E), surfaceContainerHigh = Color(0xFF1A2D49),
  surfaceContainerHighest = Color(0xFF213751),
  outline = Color(0xFF8197B6), outlineVariant = Color(0xFF2C4263),
  error = Color(0xFFFFB4AB), onError = Color(0xFF690005),
  errorContainer = Color(0xFF51232A), onErrorContainer = Color(0xFFFFDAD6)
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
    shapes = Shapes(extraSmall = RoundedCornerShape(8.dp), small = RoundedCornerShape(12.dp),
      medium = RoundedCornerShape(20.dp), large = RoundedCornerShape(24.dp), extraLarge = RoundedCornerShape(28.dp)),
    content = content
  )
}
