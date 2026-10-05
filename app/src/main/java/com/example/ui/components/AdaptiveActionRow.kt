package com.example.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp

/** Let action cards use the full width when large text leaves narrow columns. */
@Composable
fun AdaptiveActionRow(modifier: Modifier = Modifier, content: @Composable (Modifier) -> Unit) {
  val singleColumn = LocalDensity.current.fontScale >= 1.3f || LocalConfiguration.current.screenWidthDp < 360
  if (singleColumn) Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
    content(Modifier.fillMaxWidth())
  } else Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
    content(Modifier.weight(1f))
  }
}
