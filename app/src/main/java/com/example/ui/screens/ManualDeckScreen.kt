package com.example.ui.screens

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import coil.network.HttpException
import androidx.compose.ui.Alignment
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.MoreVert
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
  var tab by rememberSaveable { mutableIntStateOf(0) }
  var showActions by remember { mutableStateOf(false) }
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
  Column(modifier.fillMaxSize()) {
    Surface(tonalElevation = 2.dp) {
      Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Column(Modifier.weight(1f)) {
            Text(current.name, style = MaterialTheme.typography.titleLarge, maxLines = 2)
            Text("${current.totalCardCount}/20 cartas · " + current.energyTypes.joinToString().ifBlank { "Sin energías" },
              style = MaterialTheme.typography.bodySmall)
          }
          Box {
            IconButton(onClick = { showActions = true }) {
              Icon(Icons.Filled.MoreVert, "Opciones del mazo")
            }
            DropdownMenu(expanded = showActions, onDismissRequest = { showActions = false }) {
              DropdownMenuItem(text = { Text("Crear con mis cartas") }, enabled = !generating,
                onClick = { showActions = false; create = true })
              DropdownMenuItem(text = { Text("Nuevo vacío") }, enabled = !generating,
                onClick = { showActions = false; confirmNew = true })
              DropdownMenuItem(text = { Text("Consultar mi IA") }, enabled = !generating,
                onClick = { showActions = false; onAskAi() })
            }
          }
        }
        LinearProgressIndicator(progress = { (current.totalCardCount / 20f).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
        if (generating) LinearProgressIndicator(Modifier.fillMaxWidth())
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          items(3) { index -> FilterChip(selected = tab == index, onClick = { tab = index },
            label = { Text(listOf("Cartas", "Energías", "Notas")[index]) }) }
        }
      }
    }
    LazyColumn(Modifier.weight(1f).fillMaxWidth(), contentPadding = PaddingValues(16.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp)) {
      message?.let { item {
        Card(Modifier.fillMaxWidth()) { Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
          Text(it, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
          TextButton(onClick = viewModel::clearCsvStatusMessage) { Text("Cerrar") }
        } }
      } }
      if (tab == 0) {
        item {
          Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = !adding, onClick = { adding = false }, label = { Text("Mi mazo (${current.totalCardCount})") })
            FilterChip(selected = adding, onClick = { adding = true }, label = { Text("Añadir cartas") })
          }
          if (adding) {
            OutlinedTextField(query, { query = it }, label = { Text("Buscar nombre o código") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Row(verticalAlignment = Alignment.CenterVertically) {
              Text("Solo mis cartas", Modifier.weight(1f)); Switch(onlyOwned, { onlyOwned = it })
            }
            Text("Hasta 60 coincidencias. Busca para ver otras cartas.", style = MaterialTheme.typography.bodySmall)
            if (candidates.isEmpty()) Text("No hay coincidencias. Importa tu CSV o desactiva Solo mis cartas.")
          } else if (current.cards.isEmpty()) {
            Text("Prepara tu primera baraja", style = MaterialTheme.typography.titleMedium)
            Button(onClick = { create = true }, enabled = !generating, modifier = Modifier.fillMaxWidth()) { Text("Crear con mis cartas") }
            OutlinedButton(onClick = onAskAi, enabled = !generating, modifier = Modifier.fillMaxWidth()) { Text("Crear con mi IA") }
            TextButton(onClick = { adding = true }) { Text("Elegir cartas") }
          }
        }
        if (!adding) items(current.cards, key = { it.card.id }) { entry ->
          val available = owned[entry.card.id] ?: 0
          Card(Modifier.fillMaxWidth()) {
            Row(Modifier.padding(10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
              DeckThumbnail(entry.card.id, entry.card.name)
              Column(Modifier.weight(1f)) {
                Text(entry.card.name, style = MaterialTheme.typography.titleSmall)
                Text("$available disponibles", style = MaterialTheme.typography.bodySmall)
                val missing = maxOf(0, entry.count - available)
                if (missing > 0) Text("Faltan $missing copias", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                Row(verticalAlignment = Alignment.CenterVertically) {
                  IconButton(enabled = !generating, onClick = { viewModel.editDeckQuantity(entry.card.id, entry.count-1) }) {
                    Icon(Icons.Filled.Remove, "Quitar una copia de ${entry.card.name}")
                  }
                  Text("${entry.count}", style = MaterialTheme.typography.titleMedium)
                  IconButton(enabled = !generating && entry.count < 2 && current.totalCardCount < 20,
                    onClick = { viewModel.editDeckQuantity(entry.card.id, entry.count+1) }) {
                    Icon(Icons.Filled.Add, "Añadir una copia de ${entry.card.name}")
                  }
                }
              }
            }
          }
        } else items(candidates, key = { it.card.id }) { item ->
          val count = counts[item.card.id] ?: 0
          Card(Modifier.fillMaxWidth()) {
            Row(Modifier.padding(10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
              DeckThumbnail(item.card.id, item.card.name)
              Column(Modifier.weight(1f)) {
                Text(item.card.name, style = MaterialTheme.typography.titleSmall)
                Text("${item.ownedCount} disponibles · $count en mazo", style = MaterialTheme.typography.bodySmall)
              }
              IconButton(enabled = !generating && count < 2 && current.totalCardCount < 20 && (!onlyOwned || count < item.ownedCount),
                onClick = { viewModel.editDeckQuantity(item.card.id, count+1) }) {
                Icon(Icons.Filled.Add, "Añadir ${item.card.name}")
              }
            }
          }
        }
      } else if (tab == 1) {
        item {
          Text("Energías del mazo", style = MaterialTheme.typography.titleMedium)
          Text(if (automatic) "Sugeridas por tipo. Revisa los costes de los ataques." else "Selección personalizada conservada.")
          Text("Elige de una a tres energías. Dragón y datos incompletos requieren revisión.", style = MaterialTheme.typography.bodySmall)
        }
        items(DeckCodec.energyNames) { name ->
          FilterChip(selected = name in current.energyTypes, enabled = !generating,
            onClick = { viewModel.toggleDeckEnergy(name) }, label = { Text(name) })
        }
        if (!automatic) item { OutlinedButton(onClick = viewModel::useAutomaticEnergies, enabled = !generating) { Text("Sugerir por tipo") } }
      } else {
        item {
          OutlinedTextField(current.name, viewModel::editDeckName, label = { Text("Nombre del mazo") }, singleLine = true, modifier = Modifier.fillMaxWidth())
          OutlinedTextField(current.strategy, viewModel::editDeckStrategy, label = { Text("Notas de estrategia") }, modifier = Modifier.fillMaxWidth())
          OutlinedButton(enabled = current.cards.isNotEmpty(), onClick = {
            context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, current.toExportText()) }, "Compartir mazo"))
          }) { Text("Compartir lista") }
          if (current.totalCardCount == 20 && current.energyTypes.isNotEmpty()) com.example.ui.components.DeckQrExportButton(current)
          else Text("Exportar al juego requiere 20 cartas y energías seleccionadas.", style = MaterialTheme.typography.bodySmall)
          HelpButton("mazos")
        }
      }
      if (current.validationWarnings.isNotEmpty()) item {
        Text("Revisar antes de jugar", style = MaterialTheme.typography.titleSmall)
        current.validationWarnings.forEach { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
      }
    }
    Surface(tonalElevation = 3.dp) {
      Button(onClick = { viewModel.saveCurrentDeck(allowDraft = true) },
        enabled = current.cards.isNotEmpty() && current.name.isNotBlank() && !generating,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(if (current.totalCardCount == 20) "Guardar mazo · 20/20" else "Guardar borrador · ${current.totalCardCount}/20")
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
  var language by remember(id) { mutableStateOf("es") }
  var failed by remember(id) { mutableStateOf(false) }
  Box(Modifier.width(64.dp).height(90.dp).background(MaterialTheme.colorScheme.surfaceContainerHigh), contentAlignment = Alignment.Center) {
    if (failed) Text(name, modifier = Modifier.padding(4.dp), fontSize = 10.sp, textAlign = TextAlign.Center, maxLines = 4)
    else AsyncImage(model = TcgdexHelper.getCardImageUrl(id, language), contentDescription = name,
      contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize(),
      onError = { state ->
        if ((state.result.throwable as? HttpException)?.response?.code == 404 && language != "en") language = "en"
        else failed = true
      })
  }
}
