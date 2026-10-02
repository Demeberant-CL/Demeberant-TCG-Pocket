package com.example.ui.screens

import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.util.DeckCodec
import com.example.ui.viewmodel.TcgViewModel

@Composable
fun ManualDeckScreen(viewModel: TcgViewModel, modifier: Modifier = Modifier) {
  val deck by viewModel.generatedDeck.collectAsStateWithLifecycle()
  val inventory by viewModel.inventoryList.collectAsStateWithLifecycle()
  val message by viewModel.csvStatusMessage.collectAsStateWithLifecycle()
  var query by rememberSaveable { mutableStateOf("") }
  var onlyOwned by rememberSaveable { mutableStateOf(true) }
  var confirmNew by remember { mutableStateOf(false) }
  val context = LocalContext.current
  LaunchedEffect(Unit) { if (deck == null) viewModel.newManualDeck() }
  val current = deck ?: return
  val candidates = inventory.filter { (!onlyOwned || it.ownedCount > 0) &&
    (query.isBlank() || it.card.name.contains(query, true) || it.card.id.contains(query, true)) }.take(60)
  LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp)) {
    item {
      Text("Editor de mazos · ${current.totalCardCount}/20", style = MaterialTheme.typography.titleLarge)
      Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = { confirmNew = true }) { Text("Nuevo") }
        Button(onClick = { viewModel.saveCurrentDeck(allowDraft = true) },
          enabled = current.cards.isNotEmpty() && current.name.isNotBlank()) { Text("Guardar") }
      }
      message?.let { Text(it); TextButton(onClick = viewModel::clearCsvStatusMessage) { Text("Cerrar aviso") } }
      OutlinedTextField(current.name, viewModel::editDeckName, label = { Text("Nombre") }, modifier = Modifier.fillMaxWidth())
      OutlinedTextField(current.strategy, viewModel::editDeckStrategy, label = { Text("Notas de estrategia") }, modifier = Modifier.fillMaxWidth())
      Text("Energías · máximo tres")
      LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        items(DeckCodec.energyNames) { name -> FilterChip(selected = name in current.energyTypes,
          onClick = { viewModel.toggleDeckEnergy(name) }, label = { Text(name) }) }
      }
      com.example.ui.components.DeckQrExportButton(current)
      current.validationWarnings.forEach { Text(it, color = MaterialTheme.colorScheme.error) }
      Text("Puedes guardar un borrador incompleto. Revisa en el juego las reglas especiales y los datos de cartas sin verificar.")
      OutlinedButton(enabled = current.cards.isNotEmpty(), onClick = {
        context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
          type = "text/plain"; putExtra(Intent.EXTRA_TEXT, current.toExportText())
        }, "Compartir mazo"))
      }) { Text("Compartir lista") }
    }
    items(current.cards, key = { it.card.id }) { entry ->
      val owned = inventory.find { it.card.id == entry.card.id }?.ownedCount ?: 0
      Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
          Text("${entry.card.name} · ${entry.card.id}")
          Text("${entry.count} en el mazo · $owned en colección · ${maxOf(0, entry.count - owned)} por conseguir")
          Row {
            TextButton(onClick = { viewModel.editDeckQuantity(entry.card.id, entry.count - 1) }) { Text("−") }
            TextButton(onClick = { viewModel.editDeckQuantity(entry.card.id, entry.count + 1) },
              enabled = entry.count < 2 && current.totalCardCount < 20) { Text("+") }
          }
        }
      }
    }
    item {
      Text("Añadir cartas", style = MaterialTheme.typography.titleMedium)
      OutlinedTextField(query, { query = it }, label = { Text("Buscar nombre o código") }, modifier = Modifier.fillMaxWidth())
      Row {
        Text("Solo las que tengo", modifier = Modifier.weight(1f).padding(top = 12.dp))
        Switch(checked = onlyOwned, onCheckedChange = { onlyOwned = it })
      }
      Text("Hasta 60 coincidencias. Afina la búsqueda para encontrar otra carta.")
      if (candidates.isEmpty()) Text("No hay coincidencias.")
    }
    items(candidates, key = { "add-${it.card.id}" }) { item ->
      val count = current.cards.find { it.card.id == item.card.id }?.count ?: 0
      Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Column(Modifier.weight(1f)) { Text(item.card.name); Text("${item.card.id} · ${item.ownedCount} copias") }
        TextButton(enabled = count < 2 && current.totalCardCount < 20,
          onClick = { viewModel.editDeckQuantity(item.card.id, count + 1) }) { Text("Añadir") }
      }
    }
  }
  if (confirmNew) AlertDialog(onDismissRequest = { confirmNew = false }, title = { Text("Nuevo mazo") },
    text = { Text("Los cambios sin guardar del editor se perderán. Los mazos guardados se conservan.") },
    confirmButton = { TextButton(onClick = { viewModel.newManualDeck(); confirmNew = false }) { Text("Crear") } },
    dismissButton = { TextButton(onClick = { confirmNew = false }) { Text("Cancelar") } })
}
