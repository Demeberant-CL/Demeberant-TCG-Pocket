package com.example.ui.components

import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext

/** Read the installed APK metadata; never use a manually maintained version string. */
@Composable
fun AppVersionLabel(modifier: Modifier = Modifier) {
  val context = LocalContext.current
  val version = remember(context) {
    @Suppress("DEPRECATION")
    val installed = runCatching { context.packageManager.getPackageInfo(context.packageName, 0) }.getOrNull()
    installed?.versionName?.takeIf { it.isNotBlank() } ?: "no disponible"
  }
  SelectionContainer(modifier) {
    Text("Versión $version", style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant)
  }
}
