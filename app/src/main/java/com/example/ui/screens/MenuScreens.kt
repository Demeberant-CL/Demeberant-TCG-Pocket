package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
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
import androidx.compose.ui.Alignment

import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
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
fun DeckMenuScreen(viewModel: TcgViewModel, advanced: com.example.ui.viewmodel.AdvancedViewModel, modifier: Modifier = Modifier, onAskAi: () -> Unit = {}, editorRequest: Int = 0) {
  var section by rememberSaveable { mutableIntStateOf(when { editorRequest < 0 -> 3; editorRequest > 0 -> 1; else -> 0 }) }
  var appliedEditorRequest by rememberSaveable { mutableIntStateOf(editorRequest) }
  LaunchedEffect(editorRequest) {
    if (appliedEditorRequest != editorRequest) {
      section = when { editorRequest < 0 -> 3; editorRequest > 0 -> 1; else -> 0 }
      appliedEditorRequest = editorRequest
    }
  }
  BackHandler(enabled = section != 0) { section = when (section) { 4, 5 -> 3; else -> 0 } }
  LaunchedEffect(section) { com.example.data.util.AppDiagnostics.screen("DECKS_$section") }
  Column(modifier.fillMaxSize()) {
    if (section == 3 || section == 5) {
      TextButton(onClick = { section = if (section == 5) 3 else 0 }) { Text(if (section == 5) "← Crear con ChatGPT" else "← Mis mazos") }
    }
    when (section) {
      0 -> DeckLibraryScreen(viewModel, Modifier.weight(1f), onEdit = { section = 1 }, onCreate = { section = 3 })
      1 -> ManualDeckScreen(viewModel, advanced, Modifier.weight(1f), onBack = { section = 0 })
      3 -> CreateDeckScreen(
        main = viewModel,
        advanced = advanced,
        onAi = {
          viewModel.generatedDeck.value?.let { advanced.startDeckChat(it, force = true) }
          section = 4
        },
        onContinue = {
          viewModel.generatedDeck.value?.let { advanced.startDeckChat(it, force = true) }
          section = 4
        },
        onConnect = { section = 5 },
        modifier = Modifier.weight(1f),
      )
      4 -> DeckChatScreen(
        viewModel,
        advanced,
        onBack = { section = if (viewModel.generatedDeck.value?.cards?.isNotEmpty() == true) 1 else 3 },
        onConnections = { section = 5 },
        modifier = Modifier.weight(1f),
        creating = viewModel.generatedDeck.value?.cards?.isNotEmpty() != true,
      )
      5 -> AIAssistantScreen(viewModel, advanced, { section = 3 }, Modifier.weight(1f), initialConfigure = true, configurationOnly = true)
      else -> DeckLibraryScreen(viewModel, Modifier.weight(1f), onEdit = { section = 1 }, onCreate = { section = 3 })
    }
  }
}

@Composable
fun MoreScreen(main: TcgViewModel, advanced: com.example.ui.viewmodel.AdvancedViewModel, modifier: Modifier = Modifier,
  initialSection: Int = -1, onMeta: () -> Unit = {}, settingsMenu: Boolean = false) {
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
  var calculation by rememberSaveable { mutableIntStateOf(0) }
  val prefs by main.userPreferences.collectAsStateWithLifecycle()
  LaunchedEffect(section, showSettings) { com.example.data.util.AppDiagnostics.screen(if (showSettings) "SETTINGS" else if (section < 0) "MORE_MENU" else "MORE_$section") }
  if (showSettings) {
    CollectionScreen(main, modifier, settingsOnly = true, onSettingsBack = { showSettings = false },
      onAiConnections = { section = 9; showSettings = false }, advanced = advanced)
    return
  }
  if (showDiagnostic) DiagnosticReportDialog { showDiagnostic = false }
  val labels = listOf("Tutoriales", "Sobres", "Intercambios", "Tapete de práctica", "Calculadora", "Filtros por efectos", "Meta de torneos", "Diagnóstico", "Apariencia y datos", "Conexión ChatGPT", "Cartas sin conexión")
  val descriptions = listOf("Aprende paso a paso", "Busca tus cartas faltantes", "Descubre qué ofrecer y qué conseguir", "Organiza cartas en un tablero manual", "Calcula probabilidades", "Busca mecánicas", "Consulta la muestra pública", "Copia el resumen o envía un ZIP", "Tema, colección, respaldos y sincronización", "Vincula ChatGPT y elige un modelo", "Descarga imágenes en español")
  val icons = listOf(Icons.AutoMirrored.Filled.MenuBook, Icons.Filled.CardGiftcard, Icons.Filled.SwapHoriz,
    Icons.Filled.SportsEsports, Icons.Filled.Calculate, Icons.Filled.FilterAlt, Icons.Filled.Insights, Icons.Filled.BugReport,
    Icons.Filled.Settings, Icons.Filled.Settings, Icons.Filled.CardGiftcard)
  Column(modifier.fillMaxSize()) {
    if (section < 0) LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
      item { Text(if (settingsMenu) "Ajustes" else "Herramientas del juego", style = MaterialTheme.typography.headlineMedium) }
      item { Text(if (settingsMenu) "Personaliza la app y administra tus datos." else "Todo para coleccionar, construir y practicar.", style = MaterialTheme.typography.bodyMedium) }
      (if (settingsMenu) listOf("Personalización y datos" to listOf(8), "Servicios y almacenamiento" to listOf(9, 10), "Soporte" to listOf(7))
        else listOf("Colección e intercambios" to listOf(2, 1, 5), "Partidas y aprendizaje" to listOf(3, 4, 0), "Competición" to listOf(6))).forEach { (group, indices) ->
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
                    Icon(icons[n], null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
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
      TextButton(onClick = { section = -1 }) { Text(if (settingsMenu) "← Ajustes" else "← Herramientas del juego") }
      when (section) {
        0 -> HelpScreen(Modifier.weight(1f))
        1 -> PackRecommenderScreen(main, Modifier.weight(1f), onTrade = { section = 2 })
        2 -> TradeAdvisorScreen(main, Modifier.weight(1f))
        3 -> SandboxScreen(main, advanced, Modifier.weight(1f))
        4 -> Column(Modifier.weight(1f)) {
          MenuChoices(listOf("Sobres", "Robo del mazo"), calculation) { calculation = it }
          ProbabilityCalculatorScreen(Modifier.weight(1f), deckOnly = calculation == 1, packsOnly = calculation == 0)
        }
        10 -> OfflineImagesScreen(Modifier.weight(1f))
        9 -> AIAssistantScreen(main, advanced, {}, Modifier.weight(1f), initialConfigure = true, configurationOnly = true)
        else -> EffectFiltersScreen(main, advanced, Modifier.weight(1f))
      }
    }
  }
}
