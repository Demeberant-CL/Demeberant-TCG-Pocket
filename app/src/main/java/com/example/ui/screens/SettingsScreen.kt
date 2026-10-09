package com.example.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.preferences.ThemeMode

@Composable
fun SettingsScreen(
  themeMode: ThemeMode,
  onThemeModeChange: (ThemeMode) -> Unit,
  onDismiss: () -> Unit,
  content: @Composable ColumnScope.() -> Unit = {}
) {
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(stringResource(R.string.settings_title)) },
    text = {
      Column(Modifier.heightIn(max = 450.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp)) {
        com.example.ui.components.AppVersionLabel()
        Text(stringResource(R.string.settings_theme), style = MaterialTheme.typography.titleSmall)
        Column(Modifier.selectableGroup()) {
          ThemeMode.entries.forEach { mode ->
            val label = stringResource(when (mode) {
              ThemeMode.DEX -> R.string.theme_dex
              ThemeMode.LIGHT -> R.string.theme_light
              ThemeMode.DARK -> R.string.theme_dark
              ThemeMode.SYSTEM -> R.string.theme_system
            })
            Row(Modifier.fillMaxWidth().selectable(
              selected = themeMode == mode, role = Role.RadioButton,
              onClick = { onThemeModeChange(mode) }
            ).heightIn(min = 48.dp).padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
              RadioButton(selected = themeMode == mode, onClick = null)
              Spacer(Modifier.width(8.dp))
              Text(label)
            }
          }
        }
        content()
      }
    },
    confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_done)) } },
    dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close)) } }
  )
}
