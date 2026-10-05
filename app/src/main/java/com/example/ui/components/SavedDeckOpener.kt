package com.example.ui.components

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.local.SavedDeckEntity
import com.example.ui.viewmodel.TcgViewModel

/** Guard explicit deck replacement while keeping ordinary saved-deck navigation direct. */
@Composable
fun rememberSavedDeckOpener(model: TcgViewModel, onOpened: () -> Unit): (SavedDeckEntity) -> Unit {
  val decks by model.savedDecks.collectAsStateWithLifecycle()
  var pendingId by rememberSaveable { mutableLongStateOf(0L) }
  val pending = decks.firstOrNull { it.id == pendingId }
  fun open(saved: SavedDeckEntity) {
    model.loadSavedDeck(saved)
    pendingId = 0L
    onOpened()
  }
  pending?.let { saved ->
    AlertDialog(onDismissRequest = { pendingId = 0L },
      title = { Text("Hay cambios sin guardar") },
      text = { Text("Abrir «${saved.name}» reemplazará el borrador actual. Guarda tus cambios desde el editor si quieres conservarlos.",
        modifier = Modifier.verticalScroll(rememberScrollState())) },
      confirmButton = { TextButton(onClick = { open(saved) }) { Text("Abrir y descartar cambios") } },
      dismissButton = { TextButton(onClick = { pendingId = 0L }) { Text("Conservar borrador") } })
  }
  return { saved -> if (model.hasUnsavedDeckChanges()) pendingId = saved.id else open(saved) }
}
