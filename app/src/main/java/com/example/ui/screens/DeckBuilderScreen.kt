package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import com.example.ui.components.AdaptiveActionRow
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.theme.PocketBackground
import com.example.ui.theme.PocketBluePrimary
import com.example.ui.theme.PocketBorder
import com.example.ui.theme.PocketGold
import com.example.ui.theme.PocketSurface
import com.example.ui.theme.PocketTextPrimary
import com.example.ui.theme.PocketTextSecondary
import com.example.ui.viewmodel.TcgViewModel

@Composable
fun DeckBuilderScreen(
  viewModel: TcgViewModel,
  modifier: Modifier = Modifier,
  savedOnly: Boolean = false,
  onOpenSavedDeck: () -> Unit = {}
) {
  val context = LocalContext.current
  val generatedDeck by viewModel.generatedDeck.collectAsStateWithLifecycle()
  val deckPrompt by viewModel.deckBuildPrompt.collectAsStateWithLifecycle()
  val onlyFromInventory by viewModel.onlyFromInventoryDeck.collectAsStateWithLifecycle()
  val isGenerating by viewModel.isGeneratingDeck.collectAsStateWithLifecycle()
  val savedDecks by viewModel.savedDecks.collectAsStateWithLifecycle()

  var showSaveDialog by remember { mutableStateOf(false) }
  var customDeckNameInput by remember { mutableStateOf("") }
  var pendingDelete by remember { mutableStateOf<com.example.data.local.SavedDeckEntity?>(null) }

  LazyColumn(
    modifier = modifier
      .fillMaxSize()
      .background(PocketBackground)
      .padding(14.dp),
    verticalArrangement = Arrangement.spacedBy(14.dp)
  ) {
    if (!savedOnly) {
    // Top Prompt & Generation Card
    item {
      Card(
        modifier = Modifier
          .fillMaxWidth()
          .shadow(2.dp, RoundedCornerShape(16.dp), clip = false)
          .testTag("deck_builder_input_card"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = PocketSurface),
        border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(PocketBorder))
      ) {
        Column(modifier = Modifier.padding(16.dp)) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.AutoAwesome, contentDescription = null, tint = PocketBluePrimary, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(
              text = "Creador de Mazos Inteligente",
              style = MaterialTheme.typography.titleMedium,
              fontWeight = FontWeight.Black,
              color = PocketTextPrimary
            )
          }

          Spacer(modifier = Modifier.height(10.dp))

          OutlinedTextField(
            value = deckPrompt,
            onValueChange = { viewModel.setDeckPrompt(it) },
            label = { Text("Estrategia o Arquetipo") },
            placeholder = { Text("Ej: Pikachu ex turbo, Charizard llamas, Mewtwo control...") },
            modifier = Modifier
              .fillMaxWidth()
              .testTag("deck_prompt_input"),
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
              focusedBorderColor = PocketBluePrimary,
              unfocusedBorderColor = PocketBorder
            )
          )

          Spacer(modifier = Modifier.height(10.dp))

          // Filter strictly by inventory switch
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Column(modifier = Modifier.weight(1f)) {
              Text(
                text = "Usar solo cartas en posesión (Cantidad ≥ 1)",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = PocketTextPrimary
              )
              Text(
                text = "Filtra estrictamente cartas importadas de tu CSV",
                style = MaterialTheme.typography.bodySmall,
                color = PocketTextSecondary
              )
            }
            Switch(
              checked = onlyFromInventory,
              onCheckedChange = { viewModel.toggleOnlyFromInventoryDeck() },
              colors = SwitchDefaults.colors(
                checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                checkedTrackColor = PocketBluePrimary
              ),
              modifier = Modifier.testTag("only_inventory_switch")
            )
          }

          Spacer(modifier = Modifier.height(12.dp))

          // Quick Archetype Chips
          Text(text = "Sugerencias del Meta:", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = PocketTextSecondary)
          Spacer(modifier = Modifier.height(6.dp))
          LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            val suggestions = listOf("Pikachu ex Turbo", "Charizard ex Llamas", "Mewtwo ex Gardevoir", "Starmie ex Tempo", "Marowak ex Lucha")
            items(suggestions) { arch ->
              Box(
                modifier = Modifier
                  .clip(RoundedCornerShape(12.dp))
                  .background(PocketBackground)
                  .border(1.dp, PocketBorder, RoundedCornerShape(12.dp))
                  .clickable {
                    viewModel.setDeckPrompt(arch)
                    viewModel.generateDeck(arch)
                  }
                  .padding(horizontal = 8.dp, vertical = 4.dp)
              ) {
                Text(
                  text = arch,
                  fontSize = 11.sp,
                  color = PocketBluePrimary,
                  fontWeight = FontWeight.Medium
                )
              }
            }
          }

          Spacer(modifier = Modifier.height(14.dp))

          Button(
            onClick = { viewModel.generateDeck() },
            modifier = Modifier
              .fillMaxWidth()
              .heightIn(min = 48.dp)
              .testTag("generate_deck_btn"),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = PocketBluePrimary),
            enabled = !isGenerating
          ) {
            if (isGenerating) {
              CircularProgressIndicator(color = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
              Spacer(modifier = Modifier.width(8.dp))
              Text("Generando propuesta...", fontSize = 12.sp)
            } else {
              Icon(Icons.Filled.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
              Spacer(modifier = Modifier.width(6.dp))
              Text("Generar Mazo de 20 Cartas", fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
          }
        }
      }
    }

    // Generated Deck Result Card
    generatedDeck?.let { deck ->
      item {
        Card(
          modifier = Modifier
            .fillMaxWidth()
            .shadow(3.dp, RoundedCornerShape(16.dp), clip = false)
            .testTag("generated_deck_result_card"),
          shape = RoundedCornerShape(16.dp),
          colors = CardDefaults.cardColors(containerColor = PocketSurface),
          border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(PocketGold))
        ) {
          Column(modifier = Modifier.padding(16.dp)) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              Column(Modifier.weight(1f)) {
                Text(
                  text = deck.name,
                  style = MaterialTheme.typography.titleMedium,
                  fontWeight = FontWeight.Black,
                  color = PocketTextPrimary
                )
                Text(
                  text = "Arquetipo: ${deck.archetype}",
                  fontSize = 11.sp,
                  fontWeight = FontWeight.Bold,
                  color = PocketBluePrimary
                )
              }

              Box(
                modifier = Modifier
                  .clip(RoundedCornerShape(8.dp))
                  .background(MaterialTheme.colorScheme.secondaryContainer)
                  .padding(horizontal = 8.dp, vertical = 4.dp)
              ) {
                Text(
                  text = "${deck.totalCardCount}/20 Cartas",
                  fontSize = 11.sp,
                  fontWeight = FontWeight.Black,
                  color = MaterialTheme.colorScheme.onSecondaryContainer
                )
              }
            }

            deck.validationWarnings.forEach { warning ->
              Text(warning, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
            }
            Spacer(modifier = Modifier.height(10.dp))

            // Strategy box
            Box(
              modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerLow)
                .border(1.dp, MaterialTheme.colorScheme.surfaceContainerHigh, RoundedCornerShape(10.dp))
                .padding(10.dp)
            ) {
              Row(verticalAlignment = Alignment.Top) {
                Icon(Icons.AutoMirrored.Filled.HelpOutline, contentDescription = null, tint = PocketBluePrimary, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(text = deck.strategy, fontSize = 11.sp, color = PocketTextSecondary, lineHeight = 16.sp)
              }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(text = "Composición del Mazo (Visual):", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))

            // Visual Card Preview Row
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              items(deck.cards) { entry ->
                Box(
                  modifier = Modifier
                    .width(70.dp)
                    .aspectRatio(0.714f)
                    .clip(RoundedCornerShape(8.dp))
                    .border(1.dp, PocketBorder, RoundedCornerShape(8.dp))
                    .background(PocketSurface)
                ) {
                  com.example.ui.components.PocketCardImage(
                    id = entry.card.id, name = entry.card.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                  )
                  Box(
                    modifier = Modifier
                      .align(Alignment.TopEnd)
                      .padding(2.dp)
                      .clip(RoundedCornerShape(4.dp))
                      .background(MaterialTheme.colorScheme.secondaryContainer)
                      .padding(horizontal = 4.dp, vertical = 1.dp)
                  ) {
                    Text(
                      text = "x${entry.count}",
                      fontSize = 9.sp,
                      fontWeight = FontWeight.Black,
                      color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                  }
                }
              }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Text listing
            deck.cards.forEach { entry ->
              Row(
                modifier = Modifier
                  .fillMaxWidth()
                  .padding(vertical = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
              ) {
                Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                  Box(
                    modifier = Modifier
                      .clip(RoundedCornerShape(4.dp))
                      .background(PocketBluePrimary.copy(alpha = 0.1f))
                      .padding(horizontal = 5.dp, vertical = 1.dp)
                  ) {
                    Text(
                      text = "${entry.count}x",
                      fontSize = 10.sp,
                      fontWeight = FontWeight.Black,
                      color = PocketBluePrimary
                    )
                  }
                  Spacer(modifier = Modifier.width(8.dp))
                  Text(text = entry.card.name, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = PocketTextPrimary)
                }

                Text(text = entry.card.id, fontSize = 10.sp, color = PocketTextSecondary)
              }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Stack actions when enlarged text would squeeze their labels.
            AdaptiveActionRow { actionModifier ->
              Button(
                onClick = {
                  customDeckNameInput = deck.name
                  showSaveDialog = true
                },
                modifier = actionModifier
                  .heightIn(min = 48.dp)
                  .testTag("save_deck_db_btn"),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                enabled = deck.validationWarnings.isEmpty()
              ) {
                Icon(Icons.Filled.Save, contentDescription = null, modifier = Modifier.size(15.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Guardar", fontSize = 11.sp, fontWeight = FontWeight.Bold)
              }

              OutlinedButton(
                onClick = {
                  val text = deck.toExportText()
                  val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                  val clip = ClipData.newPlainText("Mazo TCG Pocket", text)
                  clipboard.setPrimaryClip(clip)
                  Toast.makeText(context, "Mazo copiado al portapapeles", Toast.LENGTH_SHORT).show()
                },
                modifier = actionModifier
                  .heightIn(min = 48.dp),
                shape = RoundedCornerShape(10.dp)
              ) {
                Icon(Icons.Filled.ContentCopy, contentDescription = null, modifier = Modifier.size(15.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Copiar", fontSize = 11.sp)
              }

              Button(
                onClick = {
                  val text = deck.toExportText()
                  val sendIntent = Intent().apply {
                    action = Intent.ACTION_SEND
                    putExtra(Intent.EXTRA_TEXT, text)
                    type = "text/plain"
                  }
                  context.startActivity(Intent.createChooser(sendIntent, "Compartir Mazo TCG Pocket"))
                },
                modifier = actionModifier
                  .heightIn(min = 48.dp),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = PocketBluePrimary)
              ) {
                Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(15.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Compartir", fontSize = 11.sp)
              }
            }
          }
        }
      }
    }

    }
    // Saved Decks in Room DB Section
    if (savedOnly) item {
      Card(
        modifier = Modifier
          .fillMaxWidth()
          .shadow(2.dp, RoundedCornerShape(16.dp), clip = false)
          .testTag("saved_decks_card"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = PocketSurface),
        border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(PocketBorder))
      ) {
        Column(modifier = Modifier.padding(16.dp)) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Bookmark, contentDescription = null, tint = PocketBluePrimary, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(
              text = "Mis mazos (${savedDecks.size})",
              style = MaterialTheme.typography.titleSmall,
              fontWeight = FontWeight.Bold,
              color = PocketTextPrimary
            )
          }

          Spacer(modifier = Modifier.height(10.dp))

          if (savedDecks.isEmpty()) {
            Text(
              text = "No tienes mazos guardados. Abre Crear para preparar uno y guardarlo.",
              fontSize = 11.sp,
              color = PocketTextSecondary
            )
          } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
              savedDecks.forEach { saved ->
                Row(
                  modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(PocketBackground)
                    .border(1.dp, PocketBorder, RoundedCornerShape(10.dp))
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                  horizontalArrangement = Arrangement.SpaceBetween,
                  verticalAlignment = Alignment.CenterVertically
                ) {
                  Column(modifier = Modifier.weight(1f)) {
                    Text(text = saved.name, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = PocketTextPrimary)
                    Text(text = "${saved.archetype} • ${saved.totalCards} cartas", fontSize = 10.sp, color = PocketTextSecondary)
                  }

                  Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                      onClick = { viewModel.loadSavedDeck(saved); onOpenSavedDeck() },
                      modifier = Modifier.size(48.dp)
                    ) {
                      Icon(Icons.Filled.PlayArrow, contentDescription = "Cargar mazo", tint = PocketBluePrimary, modifier = Modifier.size(18.dp))
                    }

                    IconButton(
                      onClick = { pendingDelete = saved },
                      modifier = Modifier.size(48.dp)
                    ) {
                      Icon(Icons.Filled.Delete, contentDescription = "Eliminar mazo", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                    }
                  }
                }
              }
            }
          }
        }
      }
    }
  }

  pendingDelete?.let { saved ->
    AlertDialog(onDismissRequest = { pendingDelete = null }, title = { Text("Eliminar mazo") },
      text = { Text("¿Eliminar «${saved.name}»? Las cartas de tu colección se conservan.",
        modifier = Modifier.verticalScroll(rememberScrollState())) },
      confirmButton = { TextButton(onClick = { viewModel.deleteSavedDeck(saved.id); pendingDelete = null }) { Text("Eliminar") } },
      dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("Cancelar") } })
  }

  // Save Deck Modal Dialog
  if (showSaveDialog) {
    AlertDialog(
      onDismissRequest = { showSaveDialog = false },
      title = { Text("Guardar Mazo", fontWeight = FontWeight.Bold) },
      text = {
        Column(Modifier.verticalScroll(rememberScrollState())) {
          Text("Ingresa un nombre para almacenar este mazo en tu base de datos local:", fontSize = 12.sp, color = PocketTextSecondary)
          Spacer(modifier = Modifier.height(8.dp))
          OutlinedTextField(
            value = customDeckNameInput,
            onValueChange = { customDeckNameInput = it },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
          )
        }
      },
      confirmButton = {
        Button(
          onClick = {
            if (customDeckNameInput.isNotBlank()) {
              viewModel.saveCurrentDeck(customDeckNameInput.trim())
              showSaveDialog = false
            }
          },
          colors = ButtonDefaults.buttonColors(containerColor = PocketBluePrimary)
        ) {
          Text("Guardar Mazo")
        }
      },
      dismissButton = {
        TextButton(onClick = { showSaveDialog = false }) {
          Text("Cancelar")
        }
      }
    )
  }
}
