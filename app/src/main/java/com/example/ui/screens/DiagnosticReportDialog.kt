package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.data.util.ErrorLogManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
fun DiagnosticReportDialog(onDismiss: () -> Unit) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  var report by remember { mutableStateOf<String?>(null) }
  var message by remember { mutableStateOf<String?>(null) }
  var saving by remember { mutableStateOf(false) }
  LaunchedEffect(Unit) {
    try { report = ErrorLogManager.briefReport(context) }
    catch (e: CancellationException) { throw e }
    catch (_: Exception) { message = "No se pudo preparar el resumen. Puedes guardar el TXT completo desde Ajustes." }
  }
  val save = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
    val text = report
    if (uri != null && text != null) scope.launch {
      saving = true
      try { ErrorLogManager.saveBriefReport(context, uri, text); message = "Resumen TXT guardado." }
      catch (e: CancellationException) { throw e }
      catch (_: Exception) { message = "No se pudo guardar. Prueba otra carpeta o copia el texto." }
      finally { saving = false }
    }
  }
  val saveZip = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
    if (uri != null) scope.launch {
      saving = true
      try { ErrorLogManager.saveDiagnosticZip(context, uri); message = "Diagnóstico ZIP guardado." }
      catch (e: CancellationException) { throw e }
      catch (_: Exception) { message = "No se pudo guardar el ZIP. Prueba otra carpeta o copia el resumen." }
      finally { saving = false }
    }
  }
  AlertDialog(onDismissRequest = onDismiss, title = { Text("Diagnóstico para copiar") },
    text = {
      Column(Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Copia este resumen y pégalo como mensaje aquí. No necesitas adjuntar un archivo. Añade qué estabas haciendo cuando falló.")
        if (report == null && message == null) LinearProgressIndicator(Modifier.fillMaxWidth())
        report?.let { text ->
          SelectionContainer { LazyColumn(Modifier.heightIn(max = 240.dp)) { item { Text(text, style = MaterialTheme.typography.bodySmall) } } }
          OutlinedButton(enabled = !saving, onClick = {
            try { context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
              type = "text/plain"; putExtra(Intent.EXTRA_TEXT, text)
            }, "Compartir resumen como texto")) }
            catch (_: Exception) { message = "No se pudo compartir. Usa Copiar resumen." }
          }, shape = MaterialTheme.shapes.medium) { Text("Compartir texto sin archivo") }
          OutlinedButton(enabled = !saving, onClick = { save.launch("resumen-diagnostico-tcg-pocket.txt") }, shape = MaterialTheme.shapes.medium) { Text("Guardar resumen TXT") }
        }
        HorizontalDivider()
        Text("Registro completo", style = MaterialTheme.typography.titleSmall)
        Text("El ZIP incluye el registro técnico y el resumen. Úsalo si el chat no acepta el TXT.", style = MaterialTheme.typography.bodySmall)
        OutlinedButton(enabled = !saving, onClick = { saveZip.launch("diagnostico-tcg-pocket.zip") }, shape = MaterialTheme.shapes.medium) { Text("Guardar diagnóstico ZIP") }
        OutlinedButton(enabled = !saving, onClick = { ErrorLogManager.exportErrorLogs(context, compressed = true) }, shape = MaterialTheme.shapes.medium) { Text("Compartir diagnóstico ZIP") }
        if (saving) LinearProgressIndicator(Modifier.fillMaxWidth())
        message?.let { Text(it) }
      }
    }, confirmButton = {
      TextButton(enabled = report != null, onClick = {
        try {
          val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
          clipboard.setPrimaryClip(ClipData.newPlainText("Diagnóstico breve", report!!))
          Toast.makeText(context, "Resumen copiado. Pégalo en el chat.", Toast.LENGTH_LONG).show()
        } catch (_: Exception) { message = "No se pudo copiar. Selecciona el texto del resumen." }
      }) { Text("Copiar resumen") }
    }, dismissButton = { TextButton(onClick = onDismiss) { Text("Cerrar") } })
}
