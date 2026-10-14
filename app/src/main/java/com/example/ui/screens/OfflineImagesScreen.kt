package com.example.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.data.util.SpanishImageStore
import kotlinx.coroutines.*

@Composable
fun OfflineImagesScreen(modifier: Modifier = Modifier) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  var index by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
  var selected by remember { mutableStateOf("Todas") }
  var menu by remember { mutableStateOf(false) }
  var job by remember { mutableStateOf<Job?>(null) }
  var busy by remember { mutableStateOf(false) }
  var done by remember { mutableIntStateOf(0) }
  var total by remember { mutableIntStateOf(0) }
  var failures by remember { mutableIntStateOf(0) }
  var stats by remember { mutableStateOf(0 to 0L) }
  var status by remember { mutableStateOf("") }
  LaunchedEffect(Unit) {
    index = withContext(Dispatchers.IO) { SpanishImageStore.index(context) }
    stats = SpanishImageStore.stats(context)
  }
  Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
    Text("Cartas sin conexión", style = MaterialTheme.typography.headlineMedium)
    Text("Guarda imágenes originales en español por expansión. Se conservan al cerrar la app y al actualizarla.")
    Text("Estas imágenes tienen resolución limitada: descargarlas no mejora el detalle al ampliar. Consulta también el texto de efectos.", style = MaterialTheme.typography.bodySmall)
    Text("${stats.first} imágenes guardadas · ${stats.second / 1_048_576} MB")
    Box {
      OutlinedButton(enabled = !busy && index.isNotEmpty(), onClick = { menu = true }) { Text("Expansión: $selected") }
      DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
        (listOf("Todas") + index.keys.map { it.substringBeforeLast('-') }.distinct().sorted()).forEach { set ->
          DropdownMenuItem(text = { Text(set) }, onClick = { selected = set; menu = false })
        }
      }
    }
    val entries = index.filterKeys { selected == "Todas" || it.substringBeforeLast('-') == selected }
    Text("${entries.size} cartas · estimación de espacio ${entries.size * 270L / 1024} MB. El tamaño real puede variar.")
    Text("Usa Wi-Fi. Mantén esta pantalla abierta; puedes cancelar y continuar después sin repetir las imágenes guardadas.", style = MaterialTheme.typography.bodySmall)
    Button(enabled = !busy && entries.isNotEmpty(), modifier = Modifier.fillMaxWidth(), onClick = {
      busy = true; done = 0; total = entries.size; failures = 0
      job = scope.launch {
        try {
          for ((id, path) in entries) {
            ensureActive()
            try { SpanishImageStore.download(context, id, path) }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) {
              if (e.message?.contains("espacio") == true) throw e
              failures++
            }
            done++
          }
          status = "Comprobadas $done imágenes. No disponibles: $failures."
        } catch (e: CancellationException) { status = "Descarga detenida. Las imágenes guardadas se conservan." }
        catch (e: Exception) { status = e.message ?: "No se pudo completar la descarga." }
        finally { busy = false; withContext(NonCancellable) { stats = SpanishImageStore.stats(context) } }
      }
    }) { Text("Descargar en español") }
    if (busy) {
      LinearProgressIndicator(progress = { done.toFloat() / total.coerceAtLeast(1) }, modifier = Modifier.fillMaxWidth())
      Text("$done/$total · $failures no disponibles")
      TextButton(onClick = { job?.cancel() }) { Text("Cancelar descarga") }
    }
    if (status.isNotBlank()) Text(status)
  }
}
