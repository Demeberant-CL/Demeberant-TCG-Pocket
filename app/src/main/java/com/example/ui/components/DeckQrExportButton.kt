package com.example.ui.components

import android.content.ClipData
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.example.data.repository.GeneratedDeck
import com.example.data.util.DeckQrImage
import com.example.data.util.DeckQrImages
import com.example.data.util.ErrorLogManager
import kotlinx.coroutines.*
import java.io.File

@Composable
fun DeckQrExportButton(deck: GeneratedDeck) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  var shown by remember { mutableStateOf(false) }
  var busy by remember { mutableStateOf(false) }
  var result by remember { mutableStateOf<DeckQrImage?>(null) }
  var error by remember { mutableStateOf<String?>(null) }
  val save = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("image/png")) { uri ->
    val image = result
    if (uri != null && image != null) scope.launch {
      try {
        withContext(Dispatchers.IO) {
          val stream = context.contentResolver.openOutputStream(uri, "wt") ?: kotlin.error("Destino no disponible.")
          stream.use { it.write(image.png) }
        }
        Toast.makeText(context, "QR guardado.", Toast.LENGTH_SHORT).show()
      } catch (e: CancellationException) { throw e }
      catch (e: Exception) {
        ErrorLogManager.event("QR_SAVE", "Could not save QR", e)
        error = "No se pudo guardar la imagen."
      }
    }
  }
  OutlinedButton(enabled = !busy, onClick = {
    shown = true; busy = true; error = null; result = null
    val snapshot = deck.copy(cards = deck.cards.toList(), energyTypes = deck.energyTypes.toList())
    scope.launch {
      try { result = withContext(Dispatchers.Default) { DeckQrImages.create(context, snapshot) } }
      catch (e: CancellationException) { throw e }
      catch (e: Exception) {
        ErrorLogManager.event("QR_EXPORT", "Could not create deck QR", e)
        error = e.message ?: "No se pudo generar el QR."
      }
      finally { busy = false }
    }
  }) { Text("Exportar QR para el juego") }
  if (shown) AlertDialog(onDismissRequest = { if (!busy) shown = false }, title = { Text("QR de mazo · Pokémon TCG Pocket") },
    text = {
      Column(Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState())) {
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        result?.let { image ->
          Image(image.bitmap.asImageBitmap(), "Código QR del mazo", Modifier.fillMaxWidth().aspectRatio(1f))
          Text("Guarda el PNG y usa la opción de escanear código al crear un mazo en el juego. Si el escáner solo ofrece cámara, muestra el QR en otra pantalla.")
          Text("Comparte las cartas y energías del mazo, no tu colección. Las variantes de arte pueden cambiar según las cartas disponibles en la cuenta receptora.")
          Text("Formato comunitario compatible con códigos del juego; falta confirmar esta exportación en tu teléfono. El juego decide disponibilidad y reglas especiales.")
          OutlinedButton(onClick = { save.launch("mazo-tcg-pocket-qr.png") }) { Text("Guardar PNG") }
          TextButton(onClick = {
            scope.launch {
              try {
                val uri = withContext(Dispatchers.IO) {
                  val directory = File(context.cacheDir, "deck_qr").apply { mkdirs() }
                  // Keep only a small number of recent images in this export-only directory.
                  directory.listFiles()?.sortedByDescending { it.lastModified() }?.drop(4)?.forEach { it.delete() }
                  val file = File(directory, "deck-" + java.util.UUID.randomUUID() + ".png")
                  file.writeBytes(image.png)
                  FileProvider.getUriForFile(context, context.packageName + ".fileprovider", file)
                }
                val intent = Intent(Intent.ACTION_SEND).apply {
                  type = "image/png"; putExtra(Intent.EXTRA_STREAM, uri)
                  clipData = ClipData.newUri(context.contentResolver, "QR de mazo", uri)
                  addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(Intent.createChooser(intent, "Compartir QR del mazo"))
              } catch (e: CancellationException) { throw e }
              catch (e: Exception) { ErrorLogManager.event("QR_SHARE", "Could not share QR", e); error = "No se pudo compartir." }
            }
          }) { Text("Compartir PNG") }
        }
      }
    },
    confirmButton = { TextButton(enabled = !busy, onClick = { shown = false }) { Text("Cerrar") } })
}
