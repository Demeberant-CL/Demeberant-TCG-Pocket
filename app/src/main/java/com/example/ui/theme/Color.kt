package com.example.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.material3.MaterialTheme

val PocketBackground: Color
  @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.background
val PocketSurface: Color
  @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.surface
val PocketBorder: Color
  @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.outlineVariant

val PocketBluePrimary: Color
  @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.primary
val PocketBlueDark = Color(0xFF0369A1)
val PocketBlueLight = Color(0xFFE0F2FE)

val PocketGold = Color(0xFFF59E0B)
val PocketGoldLight = Color(0xFFFEF3C7)

val PocketRed = Color(0xFFEF4444)
val PocketTextPrimary: Color
  @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.onSurface
val PocketTextSecondary: Color
  @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.onSurfaceVariant
val PocketTextMuted: Color
  @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.outline
