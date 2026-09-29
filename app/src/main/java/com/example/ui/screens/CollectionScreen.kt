package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.Upload
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
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
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
import com.example.data.model.BoosterPack
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

@Composable
fun CollectionScreen(
  viewModel: TcgViewModel,
  modifier: Modifier = Modifier
) {
  val context = LocalContext.current
  val fullInventory by viewModel.inventoryList.collectAsStateWithLifecycle()
  val filteredCards by viewModel.filteredCards.collectAsStateWithLifecycle()
  val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
  val selectedPack by viewModel.selectedPackFilter.collectAsStateWithLifecycle()
  val onlyWishlist by viewModel.onlyWishlistFilter.collectAsStateWithLifecycle()
  val onlyMissing by viewModel.onlyMissingFilter.collectAsStateWithLifecycle()
  val csvMessage by viewModel.csvStatusMessage.collectAsStateWithLifecycle()

  var showPasteDialog by remember { mutableStateOf(false) }
  var showSettingsDialog by remember { mutableStateOf(false) }
  var pasteInputText by remember { mutableStateOf("") }

  // Preferences
  var selectedLanguage by remember { mutableStateOf("Español Neutro") }
  var selectedTheme by remember { mutableStateOf("Azul Pokémon Clásico") }

  // File Picker Launcher for CSV files
  val csvPickerLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.GetContent()
  ) { uri: Uri? ->
    if (uri != null) {
      try {
        val inputStream = context.contentResolver.openInputStream(uri)
        val text = inputStream?.bufferedReader()?.use { it.readText() }
        if (!text.isNullOrBlank()) {
          viewModel.importCsv(text)
        } else {
          Toast.makeText(context, "El archivo CSV está vacío.", Toast.LENGTH_SHORT).show()
        }
      } catch (e: Exception) {
        Toast.makeText(context, "Error al leer archivo: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
      }
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
        .padding(horizontal = 14.dp, vertical = 10.dp)
        .shadow(3.dp, shape = RoundedCornerShape(18.dp), clip = false)
        .testTag("collection_stats_card"),
      shape = RoundedCornerShape(18.dp),
      colors = CardDefaults.cardColors(containerColor = PocketSurface),
      border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(PocketBorder))
    ) {
      Column(modifier = Modifier.padding(14.dp)) {
        // User Profile & Settings
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
              modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(PocketBluePrimary)
                .border(2.dp, PocketGold, CircleShape),
              contentAlignment = Alignment.Center
            ) {
              Icon(Icons.Filled.Person, contentDescription = "Perfil", tint = Color.White, modifier = Modifier.size(22.dp))
            }
            Spacer(modifier = Modifier.width(10.dp))
            Column {
              Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                  text = "Demeberant",
                  style = MaterialTheme.typography.titleMedium,
                  fontWeight = FontWeight.Bold,
                  color = PocketTextPrimary
                )
                Spacer(modifier = Modifier.width(6.dp))
                Box(
                  modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(PocketGoldLight)
                    .border(1.dp, PocketGold.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
                    .padding(horizontal = 5.dp, vertical = 1.dp)
                ) {
                  Text(text = "Lv. 34", fontSize = 10.sp, fontWeight = FontWeight.Black, color = Color(0xFFB45309))
                }
              }
              Text(
                text = "Friend ID: 9824-5495-7457-6397",
                fontSize = 11.sp,
                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                color = PocketTextSecondary
              )
            }
          }

          IconButton(
            onClick = { showSettingsDialog = true },
            modifier = Modifier.size(36.dp)
          ) {
            Icon(Icons.Filled.Settings, contentDescription = "Ajustes", tint = PocketTextSecondary)
          }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Statistical Progress Bar
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Text(
            text = "Álbum: $totalOwned / $totalCatalog registradas",
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = PocketTextSecondary
          )
          Text(
            text = "$totalCopies copias totales • ${(completionPercent * 100).toInt()}%",
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = PocketBluePrimary
          )
        }

        Spacer(modifier = Modifier.height(4.dp))

        LinearProgressIndicator(
          progress = { completionPercent },
          modifier = Modifier
            .fillMaxWidth()
            .height(7.dp)
            .clip(RoundedCornerShape(4.dp)),
          color = PocketBluePrimary,
          trackColor = Color(0xFFE2E8F0)
        )

        Spacer(modifier = Modifier.height(12.dp))

        // CSV Action Buttons (Import & Export)
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          Button(
            onClick = {
              try {
                csvPickerLauncher.launch("*/*")
              } catch (_: Exception) {
                showPasteDialog = true
              }
            },
            modifier = Modifier
              .weight(1f)
              .height(38.dp)
              .testTag("import_csv_btn"),
            shape = RoundedCornerShape(10.dp),
            colors = ButtonDefaults.buttonColors(containerColor = PocketBluePrimary, contentColor = Color.White)
          ) {
            Icon(Icons.Filled.Upload, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text("Cargar CSV", fontSize = 11.sp, fontWeight = FontWeight.Bold)
          }

          OutlinedButton(
            onClick = { showPasteDialog = true },
            modifier = Modifier
              .height(38.dp)
              .testTag("paste_csv_btn"),
            shape = RoundedCornerShape(10.dp),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = PocketTextPrimary)
          ) {
            Text("Pegar CSV", fontSize = 11.sp, fontWeight = FontWeight.Medium)
          }

          Button(
            onClick = {
              val csvContent = viewModel.generateCsvContent()
              val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
              val clip = ClipData.newPlainText("Demeberant TCG Pocket CSV", csvContent)
              clipboard.setPrimaryClip(clip)

              val sendIntent = Intent().apply {
                action = Intent.ACTION_SEND
                putExtra(Intent.EXTRA_TEXT, csvContent)
                type = "text/csv"
              }
              try {
                context.startActivity(Intent.createChooser(sendIntent, "Exportar Colección CSV"))
              } catch (_: Exception) {
                Toast.makeText(context, "CSV copiado al portapapeles con éxito", Toast.LENGTH_SHORT).show()
              }
            },
            modifier = Modifier
              .weight(1f)
              .height(38.dp)
              .testTag("export_csv_btn"),
            shape = RoundedCornerShape(10.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0F172A), contentColor = Color.White)
          ) {
            Icon(Icons.Filled.Download, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text("Exportar CSV", fontSize = 11.sp, fontWeight = FontWeight.Bold)
          }
        }
      }
    }

    // CSV Import Success / Info Toast Banner
    csvMessage?.let { msg ->
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 14.dp, vertical = 4.dp)
          .clip(RoundedCornerShape(10.dp))
          .background(Color(0xFFECFDF5))
          .border(1.dp, Color(0xFFA7F3D0), RoundedCornerShape(10.dp))
          .padding(horizontal = 12.dp, vertical = 8.dp)
      ) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = Color(0xFF059669), modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text(text = msg, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF065F46))
          }
          IconButton(onClick = { viewModel.clearCsvStatusMessage() }, modifier = Modifier.size(20.dp)) {
            Icon(Icons.Filled.Close, contentDescription = "Cerrar", tint = Color(0xFF065F46), modifier = Modifier.size(14.dp))
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
        if (searchQuery.isNotBlank()) {
          IconButton(onClick = { viewModel.setSearchQuery("") }) {
            Icon(Icons.Filled.Clear, contentDescription = "Borrar", tint = PocketTextSecondary, modifier = Modifier.size(16.dp))
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

    // Pill Filters
    LazyRow(
      contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
      horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
      item {
        val isAllSelected = selectedPack == null && !onlyWishlist && !onlyMissing
        PocketPillChip(
          label = "Todos los Sets",
          isSelected = isAllSelected,
          activeColor = PocketBluePrimary,
          onClick = {
            viewModel.setPackFilter(null)
            if (onlyWishlist) viewModel.toggleOnlyWishlist()
            if (onlyMissing) viewModel.toggleOnlyMissing()
          }
        )
      }

      item {
        PocketPillChip(
          label = "Poseídas ($totalOwned)",
          isSelected = onlyWishlist.not() && onlyMissing.not() && selectedPack == null,
          activeColor = Color(0xFF10B981),
          onClick = {
            if (onlyMissing) viewModel.toggleOnlyMissing()
          }
        )
      }

      item {
        PocketPillChip(
          label = "Faltantes (${totalCatalog - totalOwned})",
          isSelected = onlyMissing,
          activeColor = PocketRed,
          onClick = { viewModel.toggleOnlyMissing() }
        )
      }

      item {
        PocketPillChip(
          label = "Favoritas",
          isSelected = onlyWishlist,
          activeColor = PocketGold,
          onClick = { viewModel.toggleOnlyWishlist() }
        )
      }

      item {
        PocketPillChip(
          label = "Charizard",
          isSelected = selectedPack == BoosterPack.CHARIZARD,
          activeColor = Color(0xFFEA580C),
          onClick = { viewModel.setPackFilter(if (selectedPack == BoosterPack.CHARIZARD) null else BoosterPack.CHARIZARD) }
        )
      }

      item {
        PocketPillChip(
          label = "Mewtwo",
          isSelected = selectedPack == BoosterPack.MEWTWO,
          activeColor = Color(0xFF9333EA),
          onClick = { viewModel.setPackFilter(if (selectedPack == BoosterPack.MEWTWO) null else BoosterPack.MEWTWO) }
        )
      }

      item {
        PocketPillChip(
          label = "Pikachu",
          isSelected = selectedPack == BoosterPack.PIKACHU,
          activeColor = Color(0xFFCA8A04),
          onClick = { viewModel.setPackFilter(if (selectedPack == BoosterPack.PIKACHU) null else BoosterPack.PIKACHU) }
        )
      }
    }

    Spacer(modifier = Modifier.height(4.dp))

    // -------------------------------------------------------------
    // GRID DE CARTAS: EXACTAMENTE 3 COLUMNAS
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
    } else {
      LazyVerticalGrid(
        columns = GridCells.Fixed(3),
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
            onToggleWishlist = { viewModel.toggleWishlist(item.card.id) }
          )
        }
      }
    }
  }

  // Settings & Preferences Modal Dialog
  if (showSettingsDialog) {
    AlertDialog(
      onDismissRequest = { showSettingsDialog = false },
      title = {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Icon(Icons.Filled.Settings, contentDescription = null, tint = PocketBluePrimary)
          Spacer(modifier = Modifier.width(8.dp))
          Text("Ajustes y Preferencias", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
      },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
          // Language selector
          Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Icon(Icons.Filled.Translate, contentDescription = null, tint = PocketTextSecondary, modifier = Modifier.size(16.dp))
              Spacer(modifier = Modifier.width(6.dp))
              Text("Idioma Principal", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = PocketTextPrimary)
            }
            Spacer(modifier = Modifier.height(6.dp))
            listOf("Español Neutro", "English", "日本語").forEach { lang ->
              Row(
                modifier = Modifier
                  .fillMaxWidth()
                  .clip(RoundedCornerShape(8.dp))
                  .clickable { selectedLanguage = lang }
                  .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
              ) {
                RadioButton(
                  selected = selectedLanguage == lang,
                  onClick = { selectedLanguage = lang }
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(lang, fontSize = 13.sp, color = PocketTextPrimary)
              }
            }
          }

          // Theme selector
          Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Icon(Icons.Filled.Palette, contentDescription = null, tint = PocketTextSecondary, modifier = Modifier.size(16.dp))
              Spacer(modifier = Modifier.width(6.dp))
              Text("Tema Visual", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = PocketTextPrimary)
            }
            Spacer(modifier = Modifier.height(6.dp))
            listOf("Azul Pokémon Clásico", "Neón Oscuro", "Modo Claro").forEach { theme ->
              Row(
                modifier = Modifier
                  .fillMaxWidth()
                  .clip(RoundedCornerShape(8.dp))
                  .clickable { selectedTheme = theme }
                  .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
              ) {
                RadioButton(
                  selected = selectedTheme == theme,
                  onClick = { selectedTheme = theme }
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(theme, fontSize = 13.sp, color = PocketTextPrimary)
              }
            }
          }
        }
      },
      confirmButton = {
        Button(
          onClick = {
            showSettingsDialog = false
            Toast.makeText(context, "Preferencias guardadas: $selectedLanguage • $selectedTheme", Toast.LENGTH_SHORT).show()
          },
          colors = ButtonDefaults.buttonColors(containerColor = PocketBluePrimary)
        ) {
          Text("Guardar")
        }
      },
      dismissButton = {
        TextButton(onClick = { showSettingsDialog = false }) {
          Text("Cerrar")
        }
      }
    )
  }

  // CSV Paste Modal Dialog
  if (showPasteDialog) {
    AlertDialog(
      onDismissRequest = { showPasteDialog = false },
      title = { Text("Pegar Contenido CSV", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) },
      text = {
        Column {
          Text(
            text = "Pega aquí tus líneas en formato 'Set,ID,Nombre,Rareza,Cantidad,Registrada' o 'card_id,quantity':",
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
  val bg = if (isSelected) activeColor else PocketSurface
  val border = if (isSelected) activeColor else PocketBorder
  val textColor = if (isSelected) Color.White else PocketTextPrimary

  Box(
    modifier = Modifier
      .clip(RoundedCornerShape(18.dp))
      .shadow(if (isSelected) 2.dp else 1.dp, RoundedCornerShape(18.dp), clip = false)
      .background(bg)
      .border(1.dp, border, RoundedCornerShape(18.dp))
      .clickable { onClick() }
      .padding(horizontal = 10.dp, vertical = 5.dp)
  ) {
    Text(
      text = label,
      fontSize = 11.sp,
      fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
      color = textColor
    )
  }
}
