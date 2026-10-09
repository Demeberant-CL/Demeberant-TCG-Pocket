package com.example.zoneimport

import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.example.data.local.AppDatabase
import com.example.data.repository.CardCatalog
import com.example.ui.theme.PocketAppTheme
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction

data class ZoneImportState(val busy: Boolean = false, val plan: ZoneImportPlan? = null,
  val message: String = "Elige el JSON guardado por Pocket Zone. Revisa el resumen antes de importar.",
  val lastResult: String = "")

class ZoneImportViewModel(app: Application) : AndroidViewModel(app) {
  private val prefs = app.getSharedPreferences("zone-import-test", Context.MODE_PRIVATE)
  private val mutable = MutableStateFlow(ZoneImportState(lastResult = prefs.getString("result", "") ?: ""))
  val state = mutable.asStateFlow()
  private var job: kotlinx.coroutines.Job? = null

  fun select(uri: Uri) {
    if (mutable.value.busy) return
    mutable.value = mutable.value.copy(busy = true, plan = null, message = "Validando todas las cartas…")
    job = viewModelScope.launch {
      try {
        val plan = withContext(Dispatchers.IO) {
          val app = getApplication<Application>()
          val bytes = app.contentResolver.openInputStream(uri)?.use { stream ->
            val buffer = java.io.ByteArrayOutputStream()
            val chunk = ByteArray(8192)
            while (true) {
              kotlinx.coroutines.currentCoroutineContext().ensureActive()
              val n = stream.read(chunk)
              if (n < 0) break
              require(buffer.size() + n <= ZoneCollectionImport.MAX_BYTES) { "Archivo demasiado grande (máximo 3 MB)." }
              buffer.write(chunk, 0, n)
            }
            buffer.toByteArray()
          } ?: error("No se pudo abrir el archivo.")
          require(bytes.isNotEmpty()) { "El archivo está vacío. Prueba abrirlo desde Descargas, no desde Recientes." }
          val text = Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString()
          CardCatalog.loadBundled(app)
          ZoneCollectionImport.parse(text)
        }
        mutable.value = mutable.value.copy(busy = false, plan = plan,
          message = "Archivo válido. Revisa el resumen antes de importar.")
      } catch (e: CancellationException) { throw e }
      catch (e: Exception) {
        mutable.value = mutable.value.copy(busy = false, plan = null,
          message = "No se importó nada: ${e.message?.take(220) ?: "archivo inválido"}")
      }
    }
  }

  fun cancel() {
    job?.cancel()
    mutable.value = mutable.value.copy(busy = false, plan = null,
      message = "Cancelado. Las cantidades de la importación se guardan juntas o se descartan juntas.")
  }

  fun apply() {
    val plan = mutable.value.plan ?: return
    if (mutable.value.busy) return
    mutable.value = mutable.value.copy(busy = true, message = "Importando y verificando las dos tablas…")
    job = viewModelScope.launch {
      try {
        val result = withContext(Dispatchers.IO) {
          val start = android.os.SystemClock.elapsedRealtime()
          ZoneCollectionImport.apply(AppDatabase.getDatabase(getApplication()), plan)
          "IMPORTACIÓN OK\n${plan.uniqueCards} cartas únicas · ${plan.totalCopies} copias · ${plan.sets} sets\n" +
            "Cantidades verificadas en ambas tablas.\nDuplicados omitidos: ${plan.duplicates}.\n" +
            "Reimportar reemplaza cantidades; no suma. Cartas ausentes, deseos y mazos se conservan.\n" +
            "Duración: ${android.os.SystemClock.elapsedRealtime() - start} ms.\n" +
            "App: com.aistudio.tcgpocket2.kxmpzq"
        }
        prefs.edit().putString("result", result).putString("player", plan.playerId).apply()
        mutable.value = mutable.value.copy(busy = false, plan = null, message = result, lastResult = result)
      } catch (e: CancellationException) { throw e }
      catch (_: Exception) {
        mutable.value = mutable.value.copy(busy = false,
          message = "No se guardó la importación. La transacción se revirtió. Puedes intentarlo de nuevo.")
      }
    }
  }
}

class ZoneImportActivity : ComponentActivity() {
  private val model: ZoneImportViewModel by viewModels()
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContent {
      val state by model.state.collectAsStateWithLifecycle()
      val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(model::select)
      }
      PocketAppTheme {
        Scaffold { padding ->
          Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Pokémon Zone · Importación", style = MaterialTheme.typography.headlineSmall)
            Text("Se actualizan las cantidades incluidas y se conservan los deseos y mazos.")
            Button(enabled = !state.busy, onClick = { picker.launch(arrayOf("*/*")) },
              modifier = Modifier.fillMaxWidth()) { Text("Elegir JSON de Pocket Zone") }
            if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            Text(state.message)
            state.plan?.let { plan ->
              Text("${plan.uniqueCards} cartas únicas · ${plan.totalCopies} copias · ${plan.sets} sets",
                style = MaterialTheme.typography.titleMedium)
              Text("Jugador: ${plan.playerId}\nLectura: ${plan.readAt}\nDuplicados omitidos: ${plan.duplicates}")
              Text(if (plan.collectionComplete) "El archivo declara una colección completa; no se comprueba contra Nintendo."
                else "El archivo no certifica que esté toda la colección. Solo se actualizan las cartas incluidas.")
              Text("Se reemplazan las cantidades incluidas. No se suman copias. Las cartas ausentes, deseos y mazos se conservan.")
              Button(enabled = !state.busy, onClick = model::apply, modifier = Modifier.fillMaxWidth()) {
                Text("Importar colección")
              }
            }
            if (state.busy || state.plan != null) OutlinedButton(onClick = model::cancel,
              modifier = Modifier.fillMaxWidth()) { Text("Cancelar") }
            if (state.lastResult.isNotEmpty()) OutlinedButton(onClick = {
              (getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
                .setPrimaryClip(ClipData.newPlainText("Resultado importación", state.lastResult))
            }, modifier = Modifier.fillMaxWidth()) { Text("Copiar resultado para el chat") }
            OutlinedButton(onClick = { finish() }, modifier = Modifier.fillMaxWidth()) { Text("Volver a la colección") }
          }
        }
      }
    }
  }
}
