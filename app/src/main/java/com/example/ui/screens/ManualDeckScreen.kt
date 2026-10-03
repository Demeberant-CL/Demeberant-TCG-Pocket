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
import com.example.data.util.TcgdexHelper
import coil.compose.AsyncImage

@Composable
fun ManualDeckScreen(viewModel: TcgViewModel, modifier: Modifier = Modifier, onAskAi: () -> Unit = {}) {
  val deck by viewModel.generatedDeck.collectAsStateWithLifecycle()
  val inventory by viewModel.inventoryList.collectAsStateWithLifecycle()
  val message by viewModel.csvStatusMessage.collectAsStateWithLifecycle()
  val generating by viewModel.isGeneratingDeck.collectAsStateWithLifecycle()
  val automatic by viewModel.automaticEnergies.collectAsStateWithLifecycle()
  var query by rememberSaveable { mutableStateOf("") }
  var onlyOwned by rememberSaveable { mutableStateOf(true) }
  var adding by rememberSaveable { mutableStateOf(false) }
  var showEnergy by rememberSaveable { mutableStateOf(false) }
  var showNotes by rememberSaveable { mutableStateOf(false) }
  var confirmNew by remember { mutableStateOf(false) }
  var create by remember { mutableStateOf(false) }
  var chosenType by rememberSaveable { mutableStateOf("Planta") }
  val context = LocalContext.current
  LaunchedEffect(Unit) { if (deck == null) viewModel.newManualDeck() }
  val current = deck ?: return
  val owned = remember(inventory) { inventory.associate { it.card.id to it.ownedCount } }
  val counts = remember(current.cards) { current.cards.associate { it.card.id to it.count } }
  val candidates = remember(inventory, onlyOwned, query) { inventory.filter { (!onlyOwned || it.ownedCount > 0) &&
    (query.isBlank() || it.card.name.contains(query, true) || it.card.id.contains(query, true)) }.take(60) }
  LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
    item {
      Text("Tu mazo · ${current.totalCardCount}/20 cartas", style = MaterialTheme.typography.titleLarge)
      HelpButton("mazos")
      if (generating) LinearProgressIndicator(Modifier.fillMaxWidth())
      Button(onClick = { create = true }, enabled = !generating, modifier = Modifier.fillMaxWidth()) { Text("Crear con mis cartas") }
      Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = { confirmNew = true }, enabled = !generating, modifier = Modifier.weight(1f)) { Text("Nuevo vacío") }
        OutlinedButton(onClick = onAskAi, modifier = Modifier.weight(1f)) { Text("Ayuda de mi IA") }
      }
      message?.let { Text(it); TextButton(onClick = viewModel::clearCsvStatusMessage) { Text("Cerrar aviso") } }
      OutlinedTextField(current.name, viewModel::editDeckName, label = { Text("Nombre del mazo") }, modifier = Modifier.fillMaxWidth())
      Text(if (current.totalCardCount == 20) "20 cartas añadidas. Revisa los avisos antes de exportar." else "Faltan ${20-current.totalCardCount} cartas para completar el mazo.")
      Text("Energías: " + current.energyTypes.joinToString().ifBlank { "Se sugerirán al añadir Pokémon de tipo conocido." })
      Text(if (automatic) "Sugeridas por tipo. Revisa los costes de ataques; Dragón y datos incompletos requieren selección manual." else "Selección personalizada conservada.", style = MaterialTheme.typography.bodySmall)
      Row {
        TextButton(onClick = { showEnergy = !showEnergy }) { Text(if (showEnergy) "Ocultar energías" else "Cambiar energías") }
        if (!automatic) TextButton(onClick = viewModel::useAutomaticEnergies) { Text("Sugerir energías") }
      }
      if (showEnergy) LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        items(DeckCodec.energyNames) { name -> FilterChip(selected = name in current.energyTypes,
          onClick = { viewModel.toggleDeckEnergy(name) }, label = { Text(name) }) }
      }
      Button(onClick = { viewModel.saveCurrentDeck(allowDraft = true) }, enabled = current.cards.isNotEmpty() && current.name.isNotBlank() && !generating,
        modifier = Modifier.fillMaxWidth()) { Text(if (current.totalCardCount == 20) "Guardar mazo" else "Guardar borrador") }
      if (current.totalCardCount == 20 && current.energyTypes.isNotEmpty()) com.example.ui.components.DeckQrExportButton(current)
      else Text("Exportar al juego estará disponible con 20 cartas y energías seleccionadas.", style = MaterialTheme.typography.bodySmall)
      current.validationWarnings.forEach { Text(it, color = MaterialTheme.colorScheme.error) }
      TextButton(onClick = { showNotes = !showNotes }) { Text(if (showNotes) "Ocultar notas" else "Notas y compartir lista") }
      if (showNotes) {
        OutlinedTextField(current.strategy, viewModel::editDeckStrategy, label = { Text("Notas de estrategia") }, modifier = Modifier.fillMaxWidth())
        OutlinedButton(enabled = current.cards.isNotEmpty(), onClick = {
          context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, current.toExportText()) }, "Compartir mazo"))
        }) { Text("Compartir lista") }
      }
      Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(selected = !adding, onClick = { adding = false }, label = { Text("Mi mazo (${current.totalCardCount})") })
        FilterChip(selected = adding, onClick = { adding = true }, label = { Text("Añadir cartas") })
      }
      if (adding) {
        OutlinedTextField(query, { query = it }, label = { Text("Buscar nombre o código") }, modifier = Modifier.fillMaxWidth())
        Row { Text("Solo mis cartas", Modifier.weight(1f).padding(top = 12.dp)); Switch(onlyOwned, { onlyOwned = it }) }
        Text("Hasta 60 coincidencias. Busca para ver otras cartas.", style = MaterialTheme.typography.bodySmall)
        if (candidates.isEmpty()) Text("No hay coincidencias. Importa tu CSV o desactiva Solo mis cartas.")
      } else if (current.cards.isEmpty()) {
        Text("Empieza con Crear con mis cartas o pulsa Añadir cartas.")
        OutlinedButton(onClick = { adding = true }) { Text("Añadir cartas") }
      }
    }
    if (!adding) items(current.cards, key = { it.card.id }) { entry ->
      Card(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
          DeckThumbnail(entry.card.id, entry.card.name)
          Column(Modifier.weight(1f)) {
            Text(entry.card.name, style = MaterialTheme.typography.titleSmall)
            Text("${entry.count} en mazo · ${owned[entry.card.id] ?: 0} disponibles", style = MaterialTheme.typography.bodySmall)
            val missing = maxOf(0, entry.count - (owned[entry.card.id] ?: 0))
            if (missing > 0) Text("Te faltan $missing copias", color = MaterialTheme.colorScheme.error)
            Row {
              TextButton(onClick = { viewModel.editDeckQuantity(entry.card.id, entry.count-1) }) { Text("Quitar") }
              TextButton(enabled = entry.count < 2 && current.totalCardCount < 20,
                onClick = { viewModel.editDeckQuantity(entry.card.id, entry.count+1) }) { Text("Añadir copia") }
            }
          }
        }
      }
    }
    else items(candidates, key = { it.card.id }) { item ->
      val count = counts[item.card.id] ?: 0
      Card(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
          DeckThumbnail(item.card.id, item.card.name)
          Column(Modifier.weight(1f)) { Text(item.card.name); Text("${item.ownedCount} disponibles · $count en mazo", style = MaterialTheme.typography.bodySmall) }
          TextButton(enabled = count < 2 && current.totalCardCount < 20 && (!onlyOwned || count < item.ownedCount),
            onClick = { viewModel.editDeckQuantity(item.card.id, count+1) }) { Text("Añadir") }
        }
      }
    }
  }
  if (confirmNew) AlertDialog(onDismissRequest = { confirmNew = false }, title = { Text("Empezar un mazo vacío") },
    text = { Text("Reemplaza el borrador abierto. Los mazos guardados y la colección se conservan.") },
    confirmButton = { TextButton(onClick = { viewModel.newManualDeck(); adding = true; confirmNew = false }) { Text("Crear vacío") } },
    dismissButton = { TextButton(onClick = { confirmNew = false }) { Text("Cancelar") } })
  if (create) AlertDialog(onDismissRequest = { create = false }, title = { Text("Crear con mis cartas") },
    text = { Column {
      Text("Elige un tipo. Se usará tu colección actual, con hasta dos copias por nombre y las preevoluciones conocidas. Reemplaza el borrador abierto, no los mazos guardados.")
      LazyColumn(Modifier.heightIn(max = 280.dp)) {
        items(DeckCodec.energyNames) { type -> FilterChip(selected = type == chosenType, onClick = { chosenType = type }, label = { Text(type) }) }
      }
      Text("Es un punto de partida local, no IA. Si faltan cartas quedará incompleto.")
    } }, confirmButton = { TextButton(onClick = { viewModel.createWithMyCards(chosenType); adding = false; create = false }) { Text("Preparar borrador") } },
    dismissButton = { TextButton(onClick = { create = false }) { Text("Cancelar") } })
}

@Composable
fun DeckThumbnail(id: String, name: String) {
  AsyncImage(model = TcgdexHelper.getCardImageUrl(id, "es"), contentDescription = name, modifier = Modifier.width(64.dp).height(90.dp))
}
