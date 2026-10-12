package com.example.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.viewmodel.AdvancedViewModel
import com.example.ui.viewmodel.TcgViewModel
import com.example.domain.AiProposal

@Composable
fun PocketConnectorScreen(main: TcgViewModel, model: AdvancedViewModel, modifier: Modifier = Modifier) {
  val config by model.connectorConfig.collectAsStateWithLifecycle()
  val proposals by model.connectorProposals.collectAsStateWithLifecycle()
  val commands by model.connectorCommands.collectAsStateWithLifecycle()
  val busy by model.busy.collectAsStateWithLifecycle()
  val message by model.message.collectAsStateWithLifecycle()
  val draft by main.generatedDeck.collectAsStateWithLifecycle()
  var url by remember { mutableStateOf(config?.endpoint.orEmpty()) }
  var key by remember { mutableStateOf(config?.apiKey.orEmpty()) }
  var confirmSync by remember { mutableStateOf(false) }
  var confirmDelete by remember { mutableStateOf(false) }
  var selected by remember { mutableStateOf<AiProposal?>(null) }
  var opened by remember { mutableStateOf<String?>(null) }
  LaunchedEffect(Unit) { model.loadConnector() }
  LaunchedEffect(config) { url = config?.endpoint.orEmpty(); key = config?.apiKey.orEmpty() }
  LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
    item { Text("Rotom AI TCG Pocket · Conector", style = MaterialTheme.typography.headlineMedium) }
    item { Text("Consulta tu colección desde un chat compatible con MCP y recibe mazos para revisarlos aquí.") }
    item { Text("Requiere un servidor HTTPS propio y habilitar el conector en el servicio de chat. Este conector no cobra consultas de IA; el alojamiento y el servicio de chat pueden tener coste.", style = MaterialTheme.typography.bodySmall) }
    item { OutlinedTextField(url, { url = it }, label = { Text("Servidor HTTPS") }, singleLine = true, enabled = !busy, modifier = Modifier.fillMaxWidth()) }
    item { OutlinedTextField(key, { key = it }, label = { Text("Clave del dispositivo") }, visualTransformation = PasswordVisualTransformation(), singleLine = true, enabled = !busy, modifier = Modifier.fillMaxWidth()) }
    item { Button(onClick = { model.saveConnector(url, key) }, enabled = !busy && url.isNotBlank() && key.isNotBlank(), modifier = Modifier.fillMaxWidth()) { Text("Guardar conector") } }
    config?.let { saved ->
      item { Text("Dirección MCP para el chat: ${saved.endpoint.trimEnd('/')}/mcp", style = MaterialTheme.typography.bodySmall) }
      item { Button(onClick = { confirmSync = true }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("Sincronizar colección y mazos") } }
      item { OutlinedButton(onClick = { opened = null; model.receiveConnector() }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("Recibir propuestas") } }
      item { OutlinedButton(onClick = { model.receiveConnectorCommands() }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("Consultar instrucciones del chat") } }
      item { Text("Instrucciones pendientes: ${commands.size}. Las acciones de edición no se ejecutan automáticamente.") }
      item { OutlinedButton(onClick = { model.runSafeConnectorCommands() }, enabled = !busy && commands.any { it.optString("action") in setOf("inspect_errors", "inspect_decks", "inspect_collection") }, modifier = Modifier.fillMaxWidth()) { Text("Responder consultas seguras") } }
      item { OutlinedButton(onClick = { model.sendConnectorDiagnostics() }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("Enviar diagnóstico básico") } }
      item { TextButton(onClick = { confirmDelete = true }, enabled = !busy) { Text("Eliminar datos del servidor") } }
    }
    if (busy) item { LinearProgressIndicator(Modifier.fillMaxWidth()); TextButton(onClick = model::cancel) { Text("Cancelar") } }
    message?.let { item { Text(it) } }
    opened?.let { item { Text(it) } }
    proposals.forEachIndexed { index, proposal ->
      item(key = index) {
        OutlinedCard(Modifier.fillMaxWidth()) {
          Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(proposal.deck.name, style = MaterialTheme.typography.titleMedium)
            Text(proposal.deck.strategy)
            Text("Energías: ${proposal.deck.energyTypes.joinToString()}")
            proposal.deck.cards.forEach { Text("${it.count} × ${it.card.name} · ${it.card.id}", style = MaterialTheme.typography.bodySmall) }
            Button(onClick = { selected = proposal }, enabled = !busy) { Text("Abrir como borrador") }
          }
        }
      }
    }
  }
  if (confirmSync) AlertDialog(onDismissRequest = { confirmSync = false }, title = { Text("Sincronizar con el chat") },
    text = { Text("Se enviarán las cantidades de tu colección, lista de deseos, catálogo, mazos guardados y el borrador abierto al servidor configurado. Las propuestas anteriores del servidor se conservarán y se validarán al recibirlas.") },
    confirmButton = { TextButton(onClick = { confirmSync = false; model.syncConnector(draft) }) { Text("Sincronizar") } },
    dismissButton = { TextButton(onClick = { confirmSync = false }) { Text("Cancelar") } })
  if (confirmDelete) AlertDialog(onDismissRequest = { confirmDelete = false }, title = { Text("Eliminar copia sincronizada") },
    text = { Text("Se eliminarán la colección compartida y las propuestas pendientes del servidor.") },
    confirmButton = { TextButton(onClick = { confirmDelete = false; model.deleteConnectorData() }) { Text("Eliminar") } },
    dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancelar") } })
  selected?.let { proposal -> AlertDialog(onDismissRequest = { selected = null }, title = { Text("Abrir propuesta") },
    text = { Text("Se reemplazará el borrador abierto. Tus mazos guardados se conservan. Revisa y guarda la propuesta desde Mazos.") },
    confirmButton = { TextButton(onClick = { selected = null; main.openAiProposal(proposal.deck,
      onOpened = { opened = "Borrador abierto. Ve a Mazos para editarlo y guardarlo." }, onRejected = { opened = it }) }) { Text("Abrir") } },
    dismissButton = { TextButton(onClick = { selected = null }) { Text("Cancelar") } }) }
}
