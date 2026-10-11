package com.example

import com.example.data.preferences.ThemeMode
import com.example.ui.theme.pocketColorScheme
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.*
import org.junit.Test

class AppearanceContrastTest {
  @Test fun eightThemesRemainReadableAndPersistThroughStoredValues() {
    val modes = ThemeMode.entries.filter { it != ThemeMode.SYSTEM }
    assertEquals(8, modes.size)
    for (mode in modes) {
      assertEquals(mode, ThemeMode.fromStored(mode.storedValue))
      val colors = pocketColorScheme(mode)
      fun contrast(a: androidx.compose.ui.graphics.Color, b: androidx.compose.ui.graphics.Color): Float {
        val x = a.luminance(); val y = b.luminance()
        return (maxOf(x,y) + 0.05f) / (minOf(x,y) + 0.05f)
      }
      assertTrue("Body contrast: $mode", contrast(colors.onSurface, colors.surface) >= 4.5f)
      assertTrue("Button contrast: $mode", contrast(colors.onPrimary, colors.primary) >= 4.5f)
      assertTrue("Secondary text contrast: $mode", contrast(colors.onSurfaceVariant, colors.background) >= 4.5f)
    }
  }
}
