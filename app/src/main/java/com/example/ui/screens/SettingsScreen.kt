package com.example.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
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
  val context = LocalContext.current
  Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
      Column(Modifier.fillMaxSize().systemBarsPadding().padding(16.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
          TextButton(onClick = onDismiss) { Text("←") }
          Text(stringResource(R.string.settings_title), Modifier.weight(1f), style = MaterialTheme.typography.headlineSmall)
          TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_done)) }
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
          com.example.ui.components.DexPanel(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
              Text("Pokémon Zone", style = MaterialTheme.typography.titleMedium)
              Text("Actualiza las cantidades de tu colección.", style = MaterialTheme.typography.bodyMedium)
              Button(onClick = {
                onDismiss()
                context.startActivity(android.content.Intent(context, com.example.zonebrowser.ZoneSyncActivity::class.java))
              }, modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium) { Text("Sincronizar colección") }
            }
          }
          com.example.ui.components.DexPanel(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp)) {
              Text(stringResource(R.string.settings_theme), style = MaterialTheme.typography.titleMedium)
              Column(Modifier.selectableGroup()) {
                ThemeMode.entries.forEach { mode ->
                  val label = stringResource(when (mode) {
                    ThemeMode.LIGHT -> R.string.theme_light
                    ThemeMode.DARK -> R.string.theme_dark
                    ThemeMode.SYSTEM -> R.string.theme_system
                  })
                  Row(Modifier.fillMaxWidth().selectable(selected = themeMode == mode, role = Role.RadioButton,
                    onClick = { onThemeModeChange(mode) }).padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = themeMode == mode, onClick = null)
                    Spacer(Modifier.width(8.dp))
                    Text(label)
                  }
                }
              }
            }
          }
          content()
          com.example.ui.components.AppVersionLabel(Modifier.padding(vertical = 8.dp))
        }
      }
    }
  }
}
