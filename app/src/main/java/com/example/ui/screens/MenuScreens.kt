package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.example.data.util.CollectionCsv
import com.example.data.util.TradePlanner
import com.example.data.util.readBytesBounded
import com.example.data.repository.ParsedCsvCard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CancellationException
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.FilterAlt
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.Alignment

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
import com.example.ui.viewmodel.TcgViewModel

@Composable
private fun MenuChoices(labels: List<String>, selected: Int, onSelect: (Int) -> Unit) {
  LazyRow(contentPadding = PaddingValues(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
    items(labels.size) { index ->
      FilterChip(selected = selected == index, onClick = { onSelect(index) },
        label = { Text(labels[index]) })
    }
  }
}

@Composable
fun DeckMenuScreen(viewModel: TcgViewModel, modifier: Modifier = Modifier, onAskAi: () -> Unit = {}, editorRequest: Int = 0) {
  var section by rememberSaveable { mutableIntStateOf(if (editorRequest > 0) 1 else 0) }
  var appliedEditorRequest by rememberSaveable { mutableIntStateOf(editorRequest) }
  LaunchedEffect(editorRequest) {
    if (appliedEditorRequest != editorRequest) {
      section = if (editorRequest > 0) 1 else 0
      appliedEditorRequest = editorRequest
    }
  }
  BackHandler(enabled = section != 0) { section = 0 }
  Column(modifier.fillMaxSize()) {
    if (section == 1) {
      TextButton(onClick = { section = 0 }) { Text("← Mis mazos") }
    } else {
      MenuChoices(listOf("Mis mazos", "Plantillas A1"), if (section == 0) 0 else 1) {
        section = if (it == 0) 0 else 2
      }
    }
    when (section) {
      0 -> DeckLibraryScreen(viewModel, Modifier.weight(1f), onEdit = { section = 1 })
      1 -> ManualDeckScreen(viewModel, Modifier.weight(1f), onAskAi)
      else -> DeckBuilderScreen(viewModel, Modifier.weight(1f), onOpenSavedDeck = { section = 1 })
    }
  }
}

@Composable
fun AnalysisMenuScreen(viewModel: TcgViewModel, advanced: com.example.ui.viewmodel.AdvancedViewModel, onOpenDeck: (String) -> Unit,
  onOpenAiDeck: () -> Unit,
  modifier: Modifier = Modifier, initialSection: Int = 0) {
  var section by rememberSaveable(initialSection) { mutableIntStateOf(initialSection) }
  Column(modifier.fillMaxSize()) {
    MenuChoices(listOf("Plantillas", "Sobres", "Calculadora", "IA", "Sandbox", "Efectos"), section) { section = it }
    val contentModifier = Modifier.weight(1f)
    when (section) {
      0 -> MetaDeckAnalyzerScreen(viewModel, onOpenDeck, contentModifier)
      1 -> PackRecommenderScreen(viewModel, contentModifier)
      2 -> ProbabilityCalculatorScreen(contentModifier)
      3 -> AIAssistantScreen(viewModel, advanced, onOpenAiDeck, contentModifier)
      4 -> SandboxScreen(viewModel, advanced, contentModifier)
      else -> EffectFiltersScreen(viewModel, advanced, contentModifier)
    }
  }
}

@Composable
fun TradeMenuScreen(viewModel: TcgViewModel, modifier: Modifier = Modifier) {
  val inventory by viewModel.inventoryList.collectAsStateWithLifecycle()
  var section by rememberSaveable { mutableIntStateOf(0) }
  BackHandler(enabled = section != 0) { section = 0 }
  var reserveTwo by rememberSaveable { mutableStateOf(true) }
  var copied by remember { mutableStateOf(false) }
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  var peer by remember { mutableStateOf<List<ParsedCsvCard>?>(null) }
  var comparisonError by remember { mutableStateOf<String?>(null) }
  val peerPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
    if (uri != null) scope.launch {
      try {
        peer = withContext(Dispatchers.IO) {
          val text = context.contentResolver.openInputStream(uri)?.use { it.readBytesBounded(8_000_000).toString(Charsets.UTF_8) }
            ?: error("No se pudo abrir el CSV.")
          CollectionCsv.parse(text)
        }
        comparisonError = null
      } catch (e: CancellationException) { throw e }
      catch (e: Exception) { comparisonError = "No se pudo comparar: ${e.localizedMessage}" }
    }
  }
  val reserve = if (reserveTwo) 2 else 1
  val proposals = remember(inventory, peer, reserve) { peer?.let { TradePlanner.compare(inventory, it, reserve) } ?: emptyList() }
  val cards = if (section == 0) inventory.filter { it.ownedCount > reserve }
    else inventory.filter { it.isWishlist }
  val title = if (section == 0) "Disponibles para ofrecer" else "Cartas que busco"
  Column(modifier.fillMaxSize()) {
    MenuChoices(listOf("Para ofrecer", "Deseos"), section) { section = it; copied = false }
    LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(16.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp)) {
      item {
        Text(title, style = MaterialTheme.typography.titleLarge)
        HelpButton("canjes")
        Text("Organiza tus propuestas. Comprueba en el juego si cada carta se puede canjear.",
          color = MaterialTheme.colorScheme.onSurfaceVariant)
      }
      if (section == 0) item {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
          Text("Reservar dos copias", modifier = Modifier.weight(1f).padding(top = 12.dp))
          Switch(checked = reserveTwo, onCheckedChange = { reserveTwo = it; copied = false })
        }
        Text("Se muestran las copias que sobran después de reservar $reserve. No se descuenta ninguna carta.")
      }
      item {
        OutlinedButton(enabled = cards.isNotEmpty(), onClick = {
          val text = title + "\n" + cards.joinToString("\n") { item ->
            val quantity = if (section == 0) " x${item.ownedCount - reserve}" else ""
            "${item.card.id} · ${item.card.name}$quantity"
          }
          (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
            .setPrimaryClip(ClipData.newPlainText(title, text))
          copied = true
        }) { Text(if (copied) "Lista copiada" else "Copiar lista") }
      }
      if (section == 0) item {
        OutlinedButton(onClick = { peerPicker.launch("*/*") }) { Text("Comparar otro CSV") }
        comparisonError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        peer?.let { other ->
          Text("Comparación de ${other.size} registros. El CSV ajeno no se guarda en tu colección.")
          Text("Se proponen cartas de la misma rareza. Solo se considera faltante del otro usuario una cantidad cero explícita. Comprueba la elegibilidad y coste en el juego.")
          if (proposals.isEmpty()) Text("No hay propuestas recíprocas con esta reserva.")
        }
      }
      if (section == 0) items(proposals, key = { it.rarity.name }) { proposal ->
        Card(Modifier.fillMaxWidth()) {
          Column(Modifier.padding(14.dp)) {
            Text("Rareza ${proposal.rarity.symbol}", style = MaterialTheme.typography.titleMedium)
            Text("Puedes ofrecer: " + proposal.offer.take(15).joinToString { "${it.card.id} ${it.card.name}" })
            Text("Puedes pedir: " + proposal.request.take(15).joinToString { "${it.card.id} ${it.card.name}" })
            if (proposal.offer.size > 15 || proposal.request.size > 15) Text("Se muestran hasta 15 cartas por lado.")
          }
        }
      }
      if (cards.isEmpty()) item {
        Text(if (section == 0) "No tienes copias sobrantes con esta reserva."
          else "Marca cartas en Deseos desde Colección para verlas aquí.")
      }
      items(cards, key = { it.card.id }) { item ->
        Card(Modifier.fillMaxWidth()) {
          Column(Modifier.padding(14.dp)) {
            Text(item.card.name, style = MaterialTheme.typography.titleMedium)
            Text("${item.card.id} · ${item.card.rarity.displayName}")
            Text(if (section == 0) "${item.ownedCount - reserve} disponibles · ${item.ownedCount} en colección"
              else "${item.ownedCount} en colección")
          }
        }
      }
    }
  }
}

@Composable
fun MoreScreen(main: TcgViewModel, advanced: com.example.ui.viewmodel.AdvancedViewModel, modifier: Modifier = Modifier,
  initialSection: Int = -1, onMeta: () -> Unit = {}) {
  var section by rememberSaveable { mutableIntStateOf(initialSection) }
  var appliedInitialSection by rememberSaveable { mutableIntStateOf(initialSection) }
  LaunchedEffect(initialSection) {
    if (appliedInitialSection != initialSection) {
      section = initialSection
      appliedInitialSection = initialSection
    }
  }
  BackHandler(enabled = section >= 0) { section = -1 }
  var showDiagnostic by remember { mutableStateOf(false) }
  var showSettings by remember { mutableStateOf(false) }
  val prefs by main.userPreferences.collectAsStateWithLifecycle()
  if (showSettings) SettingsScreen(prefs.themeMode, main::setThemeMode, { showSettings = false })
  if (showDiagnostic) DiagnosticReportDialog { showDiagnostic = false }
  val labels = listOf("Ayuda y tutoriales", "Sobres", "Canjes", "Simulador", "Calculadora", "Filtros por efectos", "Meta de torneos", "Diagnóstico", "Ajustes")
  val descriptions = listOf("Aprende paso a paso", "Busca tus cartas faltantes", "Organiza intercambios", "Prueba tu mazo", "Calcula probabilidades", "Busca mecánicas", "Consulta la muestra pública", "Copia el resumen o envía un ZIP", "Cuenta, apariencia y sincronización")
  val icons = listOf(Icons.AutoMirrored.Filled.MenuBook, Icons.Filled.CardGiftcard, Icons.Filled.SwapHoriz,
    Icons.Filled.SportsEsports, Icons.Filled.Calculate, Icons.Filled.FilterAlt, Icons.Filled.Insights, Icons.Filled.BugReport, androidx.compose.material.icons.Icons.Filled.Settings)
  Column(modifier.fillMaxSize()) {
    if (section < 0) LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
      item { Text("Herramientas", style = MaterialTheme.typography.headlineSmall) }
      listOf("Colección y juego" to listOf(1, 2, 3, 4, 5),
        "Información" to listOf(6, 0), "Mi app" to listOf(8, 7)).forEach { (group, indices) ->
        item(key = group) {
          com.example.ui.components.DexPanel(Modifier.fillMaxWidth()) {
            Column {
              Text(group, Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .padding(horizontal = 16.dp, vertical = 10.dp), style = MaterialTheme.typography.titleSmall)
              indices.forEachIndexed { index, n ->
                Surface(onClick = { if (n == 6) onMeta() else if (n == 7) showDiagnostic = true else if (n == 8) showSettings = true else section = n },
                  color = MaterialTheme.colorScheme.surface) {
                  Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(icons[n], null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                      Text(labels[n], style = MaterialTheme.typography.titleSmall)
                      Text(descriptions[n], style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text("›", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                  }
                }
                if (index < indices.lastIndex) HorizontalDivider(Modifier.padding(horizontal = 16.dp))
              }
            }
          }
        }
      }
      item { com.example.ui.components.AppVersionLabel(Modifier.padding(vertical = 8.dp)) }
    } else {
      TextButton(onClick = { section = -1 }) { Text("← Más herramientas") }
      when (section) {
        0 -> HelpScreen(Modifier.weight(1f))
        1 -> PackRecommenderScreen(main, Modifier.weight(1f))
        2 -> TradeMenuScreen(main, Modifier.weight(1f))
        3 -> SandboxScreen(main, advanced, Modifier.weight(1f))
        4 -> ProbabilityCalculatorScreen(Modifier.weight(1f))
        else -> EffectFiltersScreen(main, advanced, Modifier.weight(1f))
      }
    }
  }
}
