package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.FilterChip
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.util.AppBackup
import com.example.data.util.BackupSnapshot
import com.example.data.util.readBytesBounded
import com.example.data.util.ErrorLogManager
import com.example.ui.components.CardItemView
import com.example.ui.theme.PocketBackground
import com.example.ui.theme.PocketBluePrimary
import com.example.ui.theme.PocketBorder
import com.example.ui.theme.PocketGold
import com.example.ui.theme.PocketGoldLight
import com.example.ui.theme.PocketRed
import com.example.ui.theme.PocketSurface
import com.example.ui.theme.PocketTextMuted
import com.example.ui.theme.PocketTextPrimary
import com.example.ui.theme.PocketTextSecondary
import com.example.ui.viewmodel.TcgViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun CollectionScreen(
  viewModel: TcgViewModel,
  modifier: Modifier = Modifier
) {
  var listView by rememberSaveable { mutableStateOf(false) }
  var largeCards by rememberSaveable { mutableStateOf(false) }
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val fullInventory by viewModel.inventoryList.collectAsStateWithLifecycle()
  val filteredCards by viewModel.filteredCards.collectAsStateWithLifecycle()
  val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
  val collectionFilter by viewModel.collectionFilter.collectAsStateWithLifecycle()
  val expansionFilter by viewModel.expansionFilter.collectAsStateWithLifecycle()
  val rarityFilter by viewModel.rarityFilter.collectAsStateWithLifecycle()
  var showFiltersDialog by remember { mutableStateOf(false) }
  var showProfileDetails by remember { mutableStateOf(false) }
  val csvMessage by viewModel.csvStatusMessage.collectAsStateWithLifecycle()

  var selectedCardId by remember { mutableStateOf<String?>(null) }
  var pendingRestore by remember { mutableStateOf<BackupSnapshot?>(null) }
  var pendingBackup by remember { mutableStateOf<String?>(null) }
  var showPasteDialog by remember { mutableStateOf(false) }
  var showSettingsDialog by remember { mutableStateOf(false) }
  var showDiagnosticReport by remember { mutableStateOf(false) }
  var pasteInputText by remember { mutableStateOf("") }

  val diagnosticSave = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
    if (uri != null) scope.launch {
      try { ErrorLogManager.saveLogs(context, uri); Toast.makeText(context, "Diagnóstico TXT guardado.", Toast.LENGTH_LONG).show() }
      catch (e: kotlinx.coroutines.CancellationException) { throw e }
      catch (e: Exception) {
        ErrorLogManager.event("LOG_SAVE", "Diagnostic save failed", e)
        Toast.makeText(context, "No se pudo guardar el diagnóstico. Prueba otra carpeta.", Toast.LENGTH_LONG).show()
      }
    }
  }

  // Preferences from DataStore
  val userPreferences by viewModel.userPreferences.collectAsStateWithLifecycle()
  var showAvatarPicker by remember { mutableStateOf(false) }
  if (showAvatarPicker) com.example.ui.components.AvatarPickerDialog(userPreferences.avatarId,
    onSave = { viewModel.setProfileAvatar(it); showAvatarPicker = false }, onClose = { showAvatarPicker = false })

  // File Picker Launcher for CSV files
  val csvPickerLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.GetContent()
  ) { uri: Uri? ->
    if (uri != null) scope.launch {
      try {
        val text = withContext(Dispatchers.IO) {
          context.contentResolver.openInputStream(uri)?.use { it.readBytesBounded(8_000_000).toString(Charsets.UTF_8) }
        }
        if (!text.isNullOrBlank()) viewModel.importCsv(text)
        else Toast.makeText(context, "El archivo CSV está vacío.", Toast.LENGTH_SHORT).show()
      } catch (e: Exception) {
        Toast.makeText(context, "Error al leer archivo: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
      }
    }
  }

  val csvExportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
    if (uri != null) scope.launch {
      try {
        val content = viewModel.generateCsvContent()
        withContext(Dispatchers.IO) {
          val output = context.contentResolver.openOutputStream(uri)
            ?: error("No se pudo abrir el destino.")
          output.bufferedWriter().use { it.write(content) }
        }
        Toast.makeText(context, "CSV guardado.", Toast.LENGTH_SHORT).show()
      } catch (e: Exception) {
        Toast.makeText(context, "Error al exportar: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
      }
    }
  }

  val backupExport = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
    if (uri == null) pendingBackup = null else scope.launch {
      try {
        val content = pendingBackup ?: error("Respaldo no preparado.")
        withContext(Dispatchers.IO) {
          val output = context.contentResolver.openOutputStream(uri, "wt") ?: error("No se pudo abrir el destino.")
          output.bufferedWriter(Charsets.UTF_8).use { it.write(content) }
        }
        Toast.makeText(context, "Respaldo completo guardado.", Toast.LENGTH_LONG).show()
      } catch (e: kotlinx.coroutines.CancellationException) { throw e }
      catch (e: Exception) { Toast.makeText(context, "Error al guardar: ${e.localizedMessage}", Toast.LENGTH_LONG).show() }
      finally { pendingBackup = null }
    }
  }
  val backupImport = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
    if (uri != null) scope.launch {
      try {
        pendingRestore = withContext(Dispatchers.IO) {
          val text = context.contentResolver.openInputStream(uri)?.use { it.readBytesBounded(8_000_000).toString(Charsets.UTF_8) }
            ?: error("No se pudo abrir el archivo.")
          AppBackup.decode(text)
        }
      } catch (e: kotlinx.coroutines.CancellationException) { throw e }
      catch (e: Exception) { Toast.makeText(context, "Respaldo rechazado: ${e.localizedMessage}", Toast.LENGTH_LONG).show() }
    }
  }

  // Statistics calculation
  val totalCatalog = fullInventory.size
  val totalOwned = fullInventory.count { it.ownedCount > 0 }
  val totalCopies = fullInventory.sumOf { it.ownedCount }
  val completionPercent = if (totalCatalog > 0) (totalOwned.toFloat() / totalCatalog.toFloat()) else 0f

  Column(
    modifier = modifier
      .fillMaxSize()
      .background(PocketBackground)
  ) {
    // -------------------------------------------------------------
    // CABECERA Y PROGRESO DE COLECCIÓN
    // -------------------------------------------------------------
    Card(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 12.dp, vertical = 8.dp)
        .shadow(3.dp, shape = RoundedCornerShape(10.dp), clip = false)
        .testTag("collection_stats_card"),
      shape = RoundedCornerShape(10.dp),
      colors = CardDefaults.cardColors(containerColor = PocketSurface),
      border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(PocketBorder))
    ) {
      Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
          com.example.ui.components.ProfileAvatar(userPreferences.avatarId,
            Modifier.size(40.dp).clip(CircleShape).clickable { showAvatarPicker = true }
              .border(2.dp, PocketGold, CircleShape), "Cambiar avatar de perfil")
          Column(Modifier.weight(1f).padding(horizontal = 10.dp).clickable { showProfileDetails = !showProfileDetails }) {
            Text("Mi colección", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text("$totalOwned / $totalCatalog · ${(completionPercent * 100).toInt()}% · $totalCopies copias",
              style = MaterialTheme.typography.labelMedium, color = PocketTextSecondary)
            if (showProfileDetails) {
              Text("Demeberant · Lv. 34", style = MaterialTheme.typography.bodySmall)
              Text("Friend ID: 9824-5495-7457-6397", style = MaterialTheme.typography.bodySmall)
            }
          }
          IconButton(onClick = { showSettingsDialog = true }) {
            Icon(Icons.Filled.Settings, contentDescription = "Ajustes", tint = PocketTextSecondary)
          }
        }
        LinearProgressIndicator(progress = { completionPercent },
          modifier = Modifier.fillMaxWidth().height(3.dp).clip(RoundedCornerShape(4.dp)),
          color = PocketBluePrimary, trackColor = MaterialTheme.colorScheme.surfaceContainerHigh)
      }
    }

    // CSV Import Success / Info Toast Banner
    csvMessage?.let { msg ->
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 14.dp, vertical = 4.dp)
          .clip(RoundedCornerShape(10.dp))
          .background(MaterialTheme.colorScheme.tertiaryContainer)
          .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(10.dp))
          .padding(horizontal = 12.dp, vertical = 8.dp)
      ) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.onTertiaryContainer, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text(text = msg, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onTertiaryContainer)
          }
          IconButton(onClick = { viewModel.clearCsvStatusMessage() }, modifier = Modifier.size(20.dp)) {
            Icon(Icons.Filled.Close, contentDescription = "Cerrar", tint = MaterialTheme.colorScheme.onTertiaryContainer, modifier = Modifier.size(14.dp))
          }
        }
      }
    }

    // Search Bar
    OutlinedTextField(
      value = searchQuery,
      onValueChange = { viewModel.setSearchQuery(it) },
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 14.dp, vertical = 4.dp)
        .shadow(2.dp, RoundedCornerShape(14.dp), clip = false)
        .testTag("search_card_input"),
      placeholder = { Text("Buscar Pokémon o código (ej. A1-036)...", fontSize = 12.sp, color = PocketTextMuted) },
      leadingIcon = {
        Icon(Icons.Filled.Search, contentDescription = "Buscar", tint = PocketTextSecondary, modifier = Modifier.size(18.dp))
      },
      trailingIcon = {
        Row {
          if (searchQuery.isNotBlank()) IconButton(onClick = { viewModel.setSearchQuery("") }) {
            Icon(Icons.Filled.Clear, contentDescription = "Borrar búsqueda")
          }
          IconButton(onClick = { showFiltersDialog = true }) {
            Icon(Icons.Filled.Tune, contentDescription = "Filtros avanzados",
              tint = if (expansionFilter != null || rarityFilter != null) PocketBluePrimary else PocketTextSecondary)
          }
        }
      },
      singleLine = true,
      shape = RoundedCornerShape(14.dp),
      colors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = PocketBluePrimary,
        unfocusedBorderColor = PocketBorder,
        focusedContainerColor = PocketSurface,
        unfocusedContainerColor = PocketSurface,
        focusedTextColor = PocketTextPrimary,
        unfocusedTextColor = PocketTextPrimary
      )
    )

    // One collection status is selected at a time.
    LazyRow(
      contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
      horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
      item {
        PocketPillChip(
          label = "Todas",
          isSelected = collectionFilter == com.example.data.util.CollectionFilter.ALL,
          activeColor = MaterialTheme.colorScheme.primaryContainer,
          onClick = { viewModel.setCollectionFilter(com.example.data.util.CollectionFilter.ALL) }
        )
      }
      item {
        PocketPillChip(
          label = "Tengo ($totalOwned)",
          isSelected = collectionFilter == com.example.data.util.CollectionFilter.OWNED,
          activeColor = MaterialTheme.colorScheme.primaryContainer,
          onClick = { viewModel.setCollectionFilter(com.example.data.util.CollectionFilter.OWNED) }
        )
      }
      item {
        PocketPillChip(
          label = "Faltan (${totalCatalog - totalOwned})",
          isSelected = collectionFilter == com.example.data.util.CollectionFilter.MISSING,
          activeColor = MaterialTheme.colorScheme.primaryContainer,
          onClick = { viewModel.setCollectionFilter(com.example.data.util.CollectionFilter.MISSING) }
        )
      }
      item {
        PocketPillChip(
          label = "Deseos",
          isSelected = collectionFilter == com.example.data.util.CollectionFilter.FAVORITES,
          activeColor = MaterialTheme.colorScheme.primaryContainer,
          onClick = { viewModel.setCollectionFilter(com.example.data.util.CollectionFilter.FAVORITES) }
        )
      }
      item {
        PocketPillChip("Repetidas", collectionFilter == com.example.data.util.CollectionFilter.REPEATED,
          MaterialTheme.colorScheme.primaryContainer) {
          viewModel.setCollectionFilter(com.example.data.util.CollectionFilter.REPEATED)
        }
      }
    }
    LazyRow(contentPadding = PaddingValues(horizontal = 14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
      item { FilterChip(selected = !listView, onClick = { listView = false }, label = { Text("Cuadrícula") }) }
      item { FilterChip(selected = listView, onClick = { listView = true }, label = { Text("Lista") }) }
      if (!listView) item { FilterChip(selected = largeCards, onClick = { largeCards = !largeCards }, label = { Text("Cartas grandes") }) }
    }
    Text("${filteredCards.size} resultados", modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp),
      style = MaterialTheme.typography.labelMedium, color = PocketTextSecondary)

    // -------------------------------------------------------------
    // GRID DE CARTAS: ADAPTADO A ANCHO Y TAMAÑO DE TEXTO
    // -------------------------------------------------------------
    if (filteredCards.isEmpty()) {
      Box(
        modifier = Modifier
          .fillMaxSize()
          .padding(32.dp),
        contentAlignment = Alignment.Center
      ) {
        Text(
          text = "No se encontraron cartas que coincidan.",
          color = PocketTextSecondary,
          fontSize = 13.sp
        )
      }
    } else if (listView) {
      LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 14.dp, end = 14.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)) {
        items(filteredCards.size, key = { filteredCards[it].card.id }) { index ->
          val item = filteredCards[index]
          Card(Modifier.fillMaxWidth().clickable { selectedCardId = item.card.id }) {
            Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp),
              verticalAlignment = Alignment.CenterVertically) {
              com.example.ui.components.PocketCardImage(item.card.id, item.card.name,
                modifier = Modifier.size(width = 48.dp, height = 68.dp))
              Column(Modifier.weight(1f)) {
                Text(item.card.name, style = MaterialTheme.typography.titleMedium)
                Text("${item.card.id} · ${item.card.type}", style = MaterialTheme.typography.bodySmall)
                Text(if (item.ownedCount > 0) "Tengo ${item.ownedCount}" else "Falta",
                  style = MaterialTheme.typography.bodyMedium)
              }
              IconButton(onClick = { viewModel.toggleWishlist(item.card.id) }) {
                Icon(if (item.isWishlist) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                  if (item.isWishlist) "Quitar de deseadas" else "Añadir a deseadas",
                  tint = if (item.isWishlist) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
              }
            }
          }
        }
      }
    } else {
      LazyVerticalGrid(
        columns = GridCells.Adaptive(((if (largeCards) 150f else 100f) * androidx.compose.ui.platform.LocalDensity.current.fontScale.coerceAtLeast(1f)).dp),
        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxSize()
      ) {
        items(filteredCards, key = { it.card.id }) { item ->
          CardItemView(
            card = item.card,
            ownedCount = item.ownedCount,
            isWishlist = item.isWishlist,
            onToggleWishlist = { viewModel.toggleWishlist(item.card.id) },
            imageLanguage = "es",
            onClick = { selectedCardId = item.card.id }
          )
        }
      }
    }
  }

  selectedCardId?.let { id -> fullInventory.find { it.card.id == id }?.let { item ->
    CardDetailsDialog(item, "es", { selectedCardId = null },
      { viewModel.setQuantity(id, it) }, { viewModel.toggleWishlist(id) })
  } }
  pendingRestore?.let { backup ->
    AlertDialog(onDismissRequest = { pendingRestore = null }, title = { Text("Restaurar respaldo") },
      text = { Text("${backup.cards.size} registros · ${backup.cards.sumOf { it.quantity }} copias · ${backup.decks.size} mazos.\n\nSe reemplazan cantidades y deseos de las cartas incluidas. Las demás cartas y los mazos existentes se conservan. También se restauran los ajustes.") },
      confirmButton = { TextButton(onClick = { viewModel.restoreBackup(backup); pendingRestore = null }) { Text("Restaurar") } },
      dismissButton = { TextButton(onClick = { pendingRestore = null }) { Text("Cancelar") } })
  }

  if (showFiltersDialog) {
    val expansions = fullInventory.map { com.example.data.util.CardId.split(it.card.id).first }.distinct().sorted()
    AlertDialog(
      onDismissRequest = { showFiltersDialog = false },
      title = { Text("Filtros avanzados") },
      text = {
        Column(Modifier.heightIn(max = 400.dp).verticalScroll(rememberScrollState())) {
          Text("Expansión", fontWeight = FontWeight.Bold)
          FilterChip(selected = expansionFilter == null, onClick = { viewModel.setExpansionFilter(null) }, label = { Text("Todas las expansiones") })
          expansions.forEach { code ->
            FilterChip(selected = expansionFilter == code, onClick = { viewModel.setExpansionFilter(code) }, label = { Text(code) })
          }
          Text("Rareza", fontWeight = FontWeight.Bold)
          FilterChip(selected = rarityFilter == null, onClick = { viewModel.setRarityFilter(null) }, label = { Text("Todas las rarezas") })
          com.example.data.model.CardRarity.entries.forEach { rarity ->
            FilterChip(selected = rarityFilter == rarity, onClick = { viewModel.setRarityFilter(rarity) }, label = { Text("${rarity.symbol} · ${rarity.displayName}") })
          }
        }
      },
      confirmButton = { TextButton(onClick = { showFiltersDialog = false }) { Text("Ver cartas") } },
      dismissButton = { TextButton(onClick = { viewModel.setExpansionFilter(null); viewModel.setRarityFilter(null) }) { Text("Limpiar filtros") } }
    )
  }

  if (showDiagnosticReport) DiagnosticReportDialog { showDiagnosticReport = false }

  if (showSettingsDialog) {
    SettingsScreen(
      themeMode = userPreferences.themeMode,
      onThemeModeChange = viewModel::setThemeMode,
      onDismiss = { showSettingsDialog = false }
    ) {
          HelpButton()
          Text("Colección: importar y exportar", fontWeight = FontWeight.Bold)
          OutlinedButton(onClick = {
            showSettingsDialog = false
            try { csvPickerLauncher.launch("*/*") } catch (_: Exception) { showPasteDialog = true }
          }, modifier = Modifier.fillMaxWidth()) { Text("Importar CSV") }
          OutlinedButton(onClick = { showSettingsDialog = false; showPasteDialog = true },
            modifier = Modifier.fillMaxWidth()) { Text("Pegar CSV") }
          OutlinedButton(onClick = { showSettingsDialog = false; csvExportLauncher.launch("coleccion-pokemon.csv") },
            modifier = Modifier.fillMaxWidth()) { Text("Exportar colección CSV") }
          Text("El CSV contiene cantidades y Deseos. El respaldo JSON incluye también mazos y ajustes.", fontSize = 12.sp)
          OutlinedButton(onClick = {
            showSettingsDialog = false
            scope.launch {
              try {
                pendingBackup = viewModel.generateBackupContent()
                backupExport.launch("respaldo-tcg-pocket.json")
              } catch (e: kotlinx.coroutines.CancellationException) { throw e }
              catch (e: Exception) { Toast.makeText(context, "No se pudo preparar el respaldo.", Toast.LENGTH_LONG).show() }
            }
          }, modifier = Modifier.fillMaxWidth()) { Text("Guardar respaldo completo") }
          OutlinedButton(onClick = { showSettingsDialog = false; backupImport.launch("*/*") },
            modifier = Modifier.fillMaxWidth()) { Text("Restaurar respaldo completo") }
          Text("Catálogo comunitario del 01-10-2026. Los PS y ataques se consultan a TCGdex al abrir una carta.", fontSize = 12.sp)
          // Error Logging Export Section
          Column {
            Text("Diagnóstico", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = PocketTextPrimary)
            Spacer(modifier = Modifier.height(6.dp))
            Button(onClick = { showSettingsDialog = false; showDiagnosticReport = true }, modifier = Modifier.fillMaxWidth()) { Text("Copiar diagnóstico para el chat") }
            Text("Resumen breve sin adjuntos. El TXT completo queda disponible abajo.", fontSize = 12.sp)
            OutlinedButton(onClick = {
              showSettingsDialog = false
              diagnosticSave.launch("diagnostico-tcg-pocket.txt")
            }, modifier = Modifier.fillMaxWidth()) { Text("Guardar diagnóstico TXT") }
            HelpButton("diagnostico")
            OutlinedButton(
              onClick = {
                ErrorLogManager.exportErrorLogs(context)
              },
              modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp),
              shape = RoundedCornerShape(8.dp)
            ) {
              Icon(Icons.Filled.Download, contentDescription = null, modifier = Modifier.size(16.dp))
              Spacer(modifier = Modifier.width(6.dp))
              Text("Compartir diagnóstico TXT", fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
          }
    }
  }

  // CSV Paste Modal Dialog
  if (showPasteDialog) {
    AlertDialog(
      onDismissRequest = { showPasteDialog = false },
      title = { Text("Pegar Contenido CSV", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) },
      text = {
        Column {
          Text(
            text = "Pega aquí tus líneas en formato 'Set,ID,Nombre,Rareza,Cantidad,Registrada' :",
            fontSize = 12.sp,
            color = PocketTextSecondary
          )
          Spacer(modifier = Modifier.height(8.dp))
          OutlinedTextField(
            value = pasteInputText,
            onValueChange = { pasteInputText = it },
            placeholder = { Text("\"A1\",\"1\",\"Bulbasaur\",\"♦\",\"1\",\"sí\"", fontSize = 11.sp, color = PocketTextMuted) },
            modifier = Modifier
              .fillMaxWidth()
              .height(160.dp),
            shape = RoundedCornerShape(10.dp)
          )
        }
      },
      confirmButton = {
        Button(
          onClick = {
            if (pasteInputText.isNotBlank()) {
              viewModel.importCsv(pasteInputText)
              showPasteDialog = false
              pasteInputText = ""
            }
          },
          colors = ButtonDefaults.buttonColors(containerColor = PocketBluePrimary)
        ) {
          Text("Procesar CSV")
        }
      },
      dismissButton = {
        TextButton(onClick = { showPasteDialog = false }) {
          Text("Cancelar")
        }
      }
    )
  }
}

@Composable
private fun PocketPillChip(
  label: String,
  isSelected: Boolean,
  activeColor: Color,
  onClick: () -> Unit
) {
  val bg = if (isSelected) activeColor else PocketBackground
  val border = if (isSelected) activeColor else PocketBorder
  val textColor = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else PocketTextSecondary

  Box(
    modifier = Modifier
      .clip(RoundedCornerShape(10.dp))
      .shadow(if (isSelected) 2.dp else 1.dp, RoundedCornerShape(10.dp), clip = false)
      .background(bg)
      .border(1.dp, border, RoundedCornerShape(10.dp))
      .clickable { onClick() }
      .heightIn(min = 48.dp)
      .padding(horizontal = 12.dp, vertical = 10.dp)
  ) {
    Text(
      text = label,
      fontSize = 11.sp,
      fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
      color = textColor
    )
  }
}
