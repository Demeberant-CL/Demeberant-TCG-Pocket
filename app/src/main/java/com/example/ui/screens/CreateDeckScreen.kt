package com.example.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.components.DexPanel
import com.example.ui.viewmodel.TcgViewModel

@Composable
fun CreateDeckScreen(main: TcgViewModel, onManual: () -> Unit, onAi: () -> Unit, onContinue: () -> Unit, onTemplates: () -> Unit = {}, modifier: Modifier = Modifier) {
  val draft by main.generatedDeck.collectAsStateWithLifecycle()
  var pending by remember { mutableStateOf<String?>(null) }
  fun start(mode: String) {
    if (draft?.cards?.isNotEmpty() == true) pending = mode
    else { main.newManualDeck(); if (mode == "manual") onManual() else onAi() }
  }
  LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
    item { Text("Crear mazo", style = MaterialTheme.typography.headlineSmall); Text("Elige cómo empezar", color = MaterialTheme.colorScheme.onSurfaceVariant) }
    item { DexPanel(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
      Text("Crear manualmente", style = MaterialTheme.typography.titleLarge)
      Text("Elige cartas, usa filtros y ajusta cantidades, energías y notas.")
      Button(onClick = { start("manual") }, modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium) { Text("Empezar") }
    } } }
    item { DexPanel(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
      Text("Crear con IA", style = MaterialTheme.typography.titleLarge)
      Text("Describe tu idea. Revisa una propuesta usando tus cartas y conversa para ajustarla.")
      OutlinedButton(onClick = { start("ai") }, modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium) { Text("Crear con IA") }
    } } }
    item { OutlinedButton(onClick = onTemplates, modifier = Modifier.fillMaxWidth()) { Text("Empezar con una plantilla") } }
    draft?.takeIf { it.cards.isNotEmpty() }?.let { deck -> item { DexPanel(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp)) {
      Text("Borrador actual", style = MaterialTheme.typography.titleMedium)
      Text("${deck.name} · ${deck.totalCardCount}/20")
      TextButton(onClick = onContinue) { Text("Continuar borrador") }
    } } } }
  }
  if (pending != null) AlertDialog(onDismissRequest = { pending = null }, title = { Text("Empezar otro mazo") },
    text = { Text("El borrador abierto se reemplazará. Puedes volver y guardarlo primero.") },
    confirmButton = { TextButton(onClick = { val mode = pending; pending = null; main.newManualDeck(); if (mode == "manual") onManual() else onAi() }) { Text("Crear nuevo") } },
    dismissButton = { TextButton(onClick = { pending = null }) { Text("Volver") } })
}
