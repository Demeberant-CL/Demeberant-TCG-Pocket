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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import com.example.data.repository.GeneratedDeck
import com.example.data.util.DeckQrImage
import com.example.data.util.DeckQrImages
import com.example.data.util.ErrorLogManager
import kotlinx.coroutines.*
import java.io.File

@Composable
fun DeckQrExportButton(deck: GeneratedDeck, modifier: Modifier = Modifier, onPrepare: (() -> Unit) -> Unit = { it() }) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  var alternate by remember { mutableStateOf(false) }
  var shown by remember { mutableStateOf(false) }
  var busy by remember { mutableStateOf(false) }
  var help by remember { mutableStateOf(false) }
  var snapshot by remember { mutableStateOf(deck) }
  var result by remember { mutableStateOf<DeckQrImage?>(null) }
  var error by remember { mutableStateOf<String?>(null) }
  fun generate(next: Boolean) {
    busy = true; error = null
    scope.launch {
      try {
        result = withContext(Dispatchers.Default) { DeckQrImages.create(context, snapshot, next) }
        alternate = next
      } catch (e: CancellationException) { throw e }
      catch (e: Exception) {
        ErrorLogManager.event("QR_EXPORT", "Could not create deck QR", e)
        error = e.message ?: "No se pudo generar el QR."
      } finally { busy = false }
    }
  }
  val save = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("image/png")) { uri ->
    val image = result
    if (uri != null && image != null) scope.launch {
      busy = true
      try {
        withContext(Dispatchers.IO) {
          val stream = context.contentResolver.openOutputStream(uri, "wt") ?: kotlin.error("Destino no disponible.")
          stream.use { it.write(image.png) }
        }
        Toast.makeText(context, "QR guardado.", Toast.LENGTH_SHORT).show()
      } catch (e: CancellationException) { throw e }
      catch (e: Exception) { ErrorLogManager.event("QR_SAVE", "Could not save QR", e); error = "No se pudo guardar la imagen." }
      finally { busy = false }
    }
  }
  OutlinedButton(enabled = !busy, modifier = modifier.testTag("deck_to_game"), onClick = {
    onPrepare {
      snapshot = deck.copy(cards = deck.cards.toList(), energyTypes = deck.energyTypes.toList())
      alternate = false; help = false; result = null; shown = true
      generate(false)
    }
  }, shape = MaterialTheme.shapes.medium) { Text("Llevar al juego") }
  if (shown) Dialog(onDismissRequest = { if (!busy) shown = false }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
      Column(Modifier.fillMaxSize().systemBarsPadding().padding(16.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
          Text("QR del mazo", Modifier.weight(1f), style = MaterialTheme.typography.headlineSmall)
          TextButton(enabled = !busy, onClick = { shown = false }) { Text("Cerrar") }
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
          Text(snapshot.name, style = MaterialTheme.typography.titleLarge)
          Text("${snapshot.totalCardCount} cartas", style = MaterialTheme.typography.bodySmall)
          EnergyBadges(snapshot.energyTypes)
          Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            snapshot.cards.filter { it.card.type != "Entrenador" }.sortedByDescending { it.card.isEx }.take(3).forEach { entry ->
              PocketCardImage(entry.card.id, entry.card.name, modifier = Modifier.width(44.dp).height(62.dp))
            }
          }
          if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
          error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
          result?.let { image ->
            Image(image.bitmap.asImageBitmap(), "Código QR del mazo", Modifier.fillMaxWidth().aspectRatio(1f).testTag("deck_qr_image"))
            Text("En Pokémon TCG Pocket, abre la opción para importar un mazo mediante QR.", style = MaterialTheme.typography.bodyMedium)
            Text("Las variantes de arte pueden cambiar. La aceptación depende del juego.", style = MaterialTheme.typography.bodySmall)
            Button(enabled = !busy, onClick = {
              val filename = snapshot.name.replace(Regex("[^\\p{L}\\p{N}_-]+"), "-").take(60)
              save.launch("$filename-qr.png")
            }, modifier = Modifier.fillMaxWidth()) { Text("Guardar imagen") }
            OutlinedButton(enabled = !busy, modifier = Modifier.fillMaxWidth(), onClick = {
              busy = true
              scope.launch {
                try {
                  val uri = withContext(Dispatchers.IO) {
                    val directory = File(context.cacheDir, "deck_qr").apply { mkdirs() }
                    directory.listFiles()?.sortedByDescending { it.lastModified() }?.drop(4)?.forEach { it.delete() }
                    val file = File(directory, "deck-" + java.util.UUID.randomUUID() + ".png")
                    file.writeBytes(image.png)
                    FileProvider.getUriForFile(context, context.packageName + ".fileprovider", file)
                  }
                  val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "image/png"; putExtra(Intent.EXTRA_STREAM, uri)
                    putExtra(Intent.EXTRA_TEXT, snapshot.name)
                    clipData = ClipData.newUri(context.contentResolver, "QR de mazo", uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                  }
                  context.startActivity(Intent.createChooser(intent, "Compartir QR del mazo"))
                } catch (e: CancellationException) { throw e }
                catch (e: Exception) { ErrorLogManager.event("QR_SHARE", "Could not share QR", e); error = "No se pudo compartir." }
                finally { busy = false }
              }
            }) { Text("Compartir imagen") }
          }
          TextButton(onClick = { help = !help }) { Text("¿No funciona el QR?") }
          if (help) {
            Text("Usa la imagen original, sin recortarla. Mantén el margen blanco y evita reflejos. Puedes probar otra distribución con las mismas cartas.", style = MaterialTheme.typography.bodySmall)
            OutlinedButton(enabled = !busy, onClick = { generate(!alternate) }) {
              Text(if (alternate) "Volver al QR principal" else "Probar QR alternativo")
            }
          }
        }
      }
    }
  }
}
