package com.example.ui.screens

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.saveable.listSaver
import com.example.domain.DeckCardFilter
import com.example.ui.viewmodel.AdvancedViewModel
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.util.DeckCodec
import com.example.ui.viewmodel.TcgViewModel

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ManualDeckScreen(viewModel: TcgViewModel, advanced: AdvancedViewModel, modifier: Modifier = Modifier, onBack: () -> Unit = {}) {
  val deck by viewModel.generatedDeck.collectAsStateWithLifecycle()
  val inventory by viewModel.inventoryList.collectAsStateWithLifecycle()
  val message by viewModel.csvStatusMessage.collectAsStateWithLifecycle()
  val generating by viewModel.isGeneratingDeck.collectAsStateWithLifecycle()
  val automatic by viewModel.automaticEnergies.collectAsStateWithLifecycle()
  var filter by rememberSaveable(stateSaver = listSaver<DeckCardFilter, Any>(
    save = { listOf(it.query, it.category, it.stage, it.element, it.expansion, it.rarity, it.onlyEx, it.availability, it.effect, it.sort) },
    restore = { DeckCardFilter(it[0] as String, it[1] as String, it[2] as String, it[3] as String,
      it[4] as String, it[5] as String, it[6] as Boolean, it[7] as String, it[8] as String, it[9] as String) }
  )) { mutableStateOf(DeckCardFilter()) }
  var showFilters by rememberSaveable { mutableStateOf(false) }
  var chatPage by rememberSaveable { mutableIntStateOf(0) }
  var chatAction by rememberSaveable { mutableStateOf("chat") }
  var visibleLimit by rememberSaveable { mutableIntStateOf(80) }
  val rulesList by advanced.deckRules.collectAsStateWithLifecycle()
  val aiBusy by advanced.busy.collectAsStateWithLifecycle()
  val rules = remember(rulesList) { rulesList.associateBy { it.cardId } }
  var adding by rememberSaveable { mutableStateOf(false) }
  var tab by rememberSaveable { mutableIntStateOf(0) }
  var leaving by remember { mutableStateOf(false) }
  fun leave() { if (viewModel.hasUnsavedDeckChanges()) leaving = true else onBack() }
  var showActions by remember { mutableStateOf(false) }
  var confirmNew by remember { mutableStateOf(false) }
  var create by remember { mutableStateOf(false) }
  var chosenType by rememberSaveable { mutableStateOf("Planta") }
  val context = LocalContext.current
  LaunchedEffect(Unit) { if (deck == null) viewModel.newManualDeck() }
  val current = deck ?: return
  val owned = remember(inventory) { inventory.associate { it.card.id to it.ownedCount } }
  val counts = remember(current.cards) { current.cards.associate { it.card.id to it.count } }
  val candidates = remember(inventory, filter, counts, rules) { filter.select(inventory, counts, rules) }
  val expansions = remember(inventory) { inventory.map { it.card.id.substringBeforeLast('-') }.distinct().sorted() }
  LaunchedEffect(filter) { visibleLimit = 80 }
  if (chatPage == 5) {
    BackHandler { chatPage = 0 }
    Column(modifier.fillMaxSize()) {
      TextButton(onClick = { chatPage = 0 }) { Text("← Editor del mazo") }
      LocalDeckPlannerScreen(viewModel, { chatPage = 0 }, Modifier.weight(1f), reference = current)
    }
    return
  }
  if (chatPage == 3 || chatPage == 4) {
    BackHandler { chatPage = 0 }
    Column(modifier.fillMaxSize()) {
      TextButton(onClick = { chatPage = 0 }) { Text("← Editor del mazo") }
      if (chatPage == 3) SandboxScreen(viewModel, advanced, Modifier.weight(1f))
      else ProbabilityCalculatorScreen(Modifier.weight(1f), deckOnly = true, deck = current)
    }
    return
  }
  if (chatPage == 1) {
    DeckChatScreen(viewModel, advanced, { chatPage = 0 }, { chatPage = 2 }, modifier, initialAction = chatAction)
    return
  }
  if (chatPage == 2) {
    BackHandler { chatPage = 1 }
    Column(modifier.fillMaxSize()) {
      TextButton(onClick = { chatPage = 1 }) { Text("← Chat del mazo") }
      AIAssistantScreen(viewModel, advanced, { chatPage = 0 }, Modifier.weight(1f), initialConfigure = true, configurationOnly = true)
    }
    return
  }
  BackHandler(enabled = !showFilters && !adding) { leave() }
  BackHandler(enabled = showFilters || adding) { if (showFilters) showFilters = false else adding = false }
  if (showFilters) {
    DeckCardFiltersScreen(filter, expansions, candidates.size, { filter = it }, { showFilters = false }, modifier)
    return
  }
  Column(modifier.fillMaxSize()) {
    TextButton(onClick = { leave() }) { Text("← Mis mazos") }
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
              DropdownMenuItem(text = { Text("Proponer mejoras sin IA") }, enabled = !generating,
                onClick = { showActions = false; chatPage = 5 })
              DropdownMenuItem(text = { Text("Crear con mis cartas") }, enabled = !generating,
                onClick = { showActions = false; create = true })
              DropdownMenuItem(text = { Text("Nuevo vacío") }, enabled = !generating,
                onClick = { showActions = false; confirmNew = true })
              DropdownMenuItem(text = { Text("Probabilidad de robo") }, onClick = { showActions = false; chatPage = 4 })
              DropdownMenuItem(text = { Text("Tapete de práctica") }, enabled = current.totalCardCount == 20, onClick = { showActions = false; chatPage = 3 })
              DropdownMenuItem(text = { Text("Consultar mi IA") }, enabled = !generating,
                onClick = { showActions = false; chatAction = "chat"; chatPage = 1 })
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
    LazyColumn(Modifier.weight(1f).fillMaxWidth().testTag("manual_deck_list"), contentPadding = PaddingValues(16.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp)) {
      message?.let { item {
        Card(Modifier.fillMaxWidth()) { Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
          Text(it, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
          TextButton(onClick = viewModel::clearCsvStatusMessage) { Text("Cerrar") }
        } }
      } }
      if (tab == 0) {
        item {
          FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            FilterChip(selected = !adding, onClick = { adding = false }, label = { Text("Mi mazo (${current.totalCardCount})") })
            FilterChip(selected = adding, onClick = { adding = true }, label = { Text("Añadir cartas") })
          }
          if (adding) {
            OutlinedTextField(filter.query, { filter = filter.copy(query = it) }, label = { Text("Buscar nombre o código") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              OutlinedButton(onClick = { showFilters = true }, shape = MaterialTheme.shapes.medium) { Text("Filtrar · ${filter.activeCount}") }
              OutlinedButton(onClick = { adding = false }, shape = MaterialTheme.shapes.medium) { Text("Listo · ${current.totalCardCount}/20") }
            }
            DeckChoice("Disponibilidad", filter.availability, listOf("all" to "Todas", "owned" to "Mis cartas", "sufficient" to "Puedo añadir otra copia", "missing" to "No las tengo")) { filter = filter.copy(availability = it) }
            DeckChoice("Ordenar", filter.sort, listOf("code" to "Código", "name" to "Nombre", "copies" to "Copias", "rarity" to "Rareza")) { filter = filter.copy(sort = it) }
            Text("${candidates.size} resultados", style = MaterialTheme.typography.bodySmall)
            if (filter.effect.isNotBlank()) {
              Text("Los efectos solo filtran cartas con datos cargados.", style = MaterialTheme.typography.bodySmall)
              TextButton(enabled = !aiBusy, onClick = { advanced.indexCards(filter.copy(effect = "").select(inventory, counts, rules).map { it.card.id }, "es") }) { Text("Cargar efectos de esta búsqueda") }
              val status by advanced.message.collectAsStateWithLifecycle()
              status?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            }
            if (candidates.isEmpty()) Text("No hay coincidencias. Revisa los filtros o carga los efectos que faltan.")
          } else if (current.cards.isEmpty()) {
            Text("Prepara tu primera baraja", style = MaterialTheme.typography.titleMedium)
            Button(onClick = { create = true }, enabled = !generating, modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium) { Text("Crear con mis cartas") }
            OutlinedButton(onClick = { chatAction = "create"; chatPage = 1 }, enabled = !generating, modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium) { Text("Crear con mi IA") }
            TextButton(onClick = { adding = true }) { Text("Elegir cartas") }
          }
          if (!adding && current.cards.isNotEmpty()) {
            OutlinedButton(onClick = { chatAction = "chat"; chatPage = 1 }, modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium) { Text("Hablar con IA") }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              TextButton(onClick = { chatAction = "improve"; chatPage = 1 }) { Text("Mejorar con IA") }
              TextButton(onClick = { chatAction = "complete"; chatPage = 1 }) { Text("Completar faltantes") }
            }
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
        } else items(candidates.take(visibleLimit), key = { it.card.id }) { item ->
          val count = counts[item.card.id] ?: 0
          Card(Modifier.fillMaxWidth()) {
            Row(Modifier.padding(10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
              DeckThumbnail(item.card.id, item.card.name)
              Column(Modifier.weight(1f)) {
                Text(item.card.name, style = MaterialTheme.typography.titleSmall)
                Text("${item.ownedCount} disponibles · $count en mazo", style = MaterialTheme.typography.bodySmall)
              }
              IconButton(enabled = !generating && count > 0, onClick = { viewModel.editDeckQuantity(item.card.id, count - 1) }) {
                Icon(Icons.Filled.Remove, "Quitar ${item.card.name}")
              }
              Text("$count")
              IconButton(enabled = !generating && count < 2 && current.totalCardCount < 20 &&
                (filter.availability !in listOf("owned", "sufficient") || count < item.ownedCount),
                onClick = { viewModel.editDeckQuantity(item.card.id, count+1) }) {
                Icon(Icons.Filled.Add, "Añadir ${item.card.name}")
              }
            }
          }
        }
        if (adding && candidates.size > visibleLimit) item {
          OutlinedButton(onClick = { visibleLimit += 80 }, modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium) { Text("Mostrar más cartas") }
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
        if (!automatic) item { OutlinedButton(onClick = viewModel::useAutomaticEnergies, enabled = !generating, shape = MaterialTheme.shapes.medium) { Text("Sugerir por tipo") } }
      } else {
        item {
          OutlinedTextField(current.name, viewModel::editDeckName, label = { Text("Nombre del mazo") }, singleLine = true, modifier = Modifier.fillMaxWidth())
          OutlinedTextField(current.strategy, viewModel::editDeckStrategy, label = { Text("Notas de estrategia") }, modifier = Modifier.fillMaxWidth())
          OutlinedButton(enabled = current.cards.isNotEmpty(), onClick = {
            context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, current.toExportText()) }, "Compartir mazo"))
          }, shape = MaterialTheme.shapes.medium) { Text("Compartir lista") }

        }
      }
      if (current.validationWarnings.isNotEmpty()) item {
        Text("Revisar antes de jugar", style = MaterialTheme.typography.titleSmall)
        current.validationWarnings.forEach { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
      }
    }
    Surface(color = MaterialTheme.colorScheme.background) {
      Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Button(onClick = { viewModel.saveCurrentDeck(allowDraft = true) },
          enabled = current.cards.isNotEmpty() && current.name.isNotBlank() && !generating,
          modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium) {
          Text(if (current.totalCardCount == 20) "Guardar mazo · 20/20" else "Guardar borrador · ${current.totalCardCount}/20")
        }
        if (current.totalCardCount == 20 && current.energyTypes.isNotEmpty() && current.validationWarnings.isEmpty()) {
          com.example.ui.components.DeckQrExportButton(current, Modifier.fillMaxWidth(), onPrepare = { ready ->
            viewModel.saveCurrentDeck(allowDraft = false, onSaved = ready)
          })
        }
      }
    }
  }
  if (leaving) AlertDialog(onDismissRequest = { leaving = false }, title = { Text("Guardar los cambios") },
    text = { Text("Puedes guardar el mazo antes de volver a tu biblioteca.") },
    confirmButton = { TextButton(onClick = { viewModel.saveCurrentDeck(allowDraft = true, onSaved = { leaving = false; onBack() }) },
      enabled = current.cards.isNotEmpty() && current.name.isNotBlank()) { Text("Guardar y volver") } },
    dismissButton = { TextButton(onClick = { leaving = false; onBack() }) { Text("Volver sin guardar") } })

  if (confirmNew) AlertDialog(onDismissRequest = { confirmNew = false }, title = { Text("Empezar un mazo vacío") },
    text = { Text("Reemplaza el borrador abierto. Los mazos guardados y la colección se conservan.", modifier = Modifier.verticalScroll(rememberScrollState())) },
    confirmButton = { TextButton(onClick = { viewModel.newManualDeck(); adding = true; confirmNew = false }) { Text("Crear vacío") } },
    dismissButton = { TextButton(onClick = { confirmNew = false }) { Text("Cancelar") } })
  if (create) AlertDialog(onDismissRequest = { create = false }, title = { Text("Crear con mis cartas") },
    text = { Column(Modifier.verticalScroll(rememberScrollState())) {
      Text("Preparar una propuesta con tu colección. Reemplaza el borrador abierto.")
      DeckCodec.energyNames.forEach { type -> FilterChip(selected = type == chosenType, onClick = { chosenType = type }, label = { Text(type) }) }
      Text("Sin conexión ni cuotas. Si faltan cartas quedará incompleto.")
    } }, confirmButton = { TextButton(onClick = { viewModel.createWithMyCards(chosenType); adding = false; create = false }) { Text("Crear propuesta") } },
    dismissButton = { TextButton(onClick = { create = false }) { Text("Cancelar") } })
}

@Composable
fun DeckThumbnail(id: String, name: String) {
  com.example.ui.components.PocketCardImage(id = id, name = name,
    modifier = Modifier.width(72.dp).height(102.dp).background(MaterialTheme.colorScheme.surfaceContainerHigh),
    unavailable = { Text(name, modifier = Modifier.padding(4.dp), fontSize = 10.sp, textAlign = TextAlign.Center, maxLines = 4) })
}
