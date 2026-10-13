package com.example.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.viewmodel.AdvancedViewModel

/** Selecting a saved profile only changes local settings; a request remains explicit. */
@Composable
fun QuickAiSwitch(
  model: AdvancedViewModel,
  chatGptConnected: Boolean = false,
  chatGptSelected: Boolean = false,
  onSelectChatGpt: () -> Unit = {},
) {
  val profiles by model.profiles.collectAsStateWithLifecycle()
  val active by model.connection.collectAsStateWithLifecycle()
  val busy by model.busy.collectAsStateWithLifecycle()
  val ready by model.connectionReady.collectAsStateWithLifecycle()
  var expanded by remember { mutableStateOf(false) }
  if (profiles.entries.size < 2 && !chatGptConnected) return
  Column {
    OutlinedButton(enabled = ready && !busy, onClick = { expanded = true },
      modifier = Modifier.testTag("quick_ai_switch"), shape = MaterialTheme.shapes.medium) {
      Text(if (chatGptSelected) "ChatGPT" else "Cambiar IA")
    }
    DropdownMenu(expanded = expanded && !busy, onDismissRequest = { expanded = false }) {
      profiles.entries.forEach { saved ->
        DropdownMenuItem(text = { Column {
          Text((if (!chatGptSelected && saved.id == active.id) "✓ " else "") + saved.label)
          Text("${saved.provider.label} · ${saved.model}", style = MaterialTheme.typography.bodySmall)
        } }, onClick = {
          expanded = false
          model.setChatGptPlanPreferred(false)
          if (saved.id != active.id) model.selectConnection(saved.id)
        }, modifier = Modifier.testTag("ai_profile_${saved.id}"))
      }
      if (chatGptConnected) {
        HorizontalDivider()
        DropdownMenuItem(text = { Column {
          Text((if (chatGptSelected) "✓ " else "") + "ChatGPT")
          Text("Usar el plan conectado", style = MaterialTheme.typography.bodySmall)
        } }, onClick = {
          expanded = false
          onSelectChatGpt()
        }, modifier = Modifier.testTag("ai_profile_chatgpt_plan"))
      }
    }
  }
}
