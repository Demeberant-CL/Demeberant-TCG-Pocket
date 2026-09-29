package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
  modifier: Modifier = Modifier
) {
  val context = LocalContext.current
  val generatedDeck by viewModel.generatedDeck.collectAsStateWithLifecycle()
  val deckPrompt by viewModel.deckBuildPrompt.collectAsStateWithLifecycle()
  val onlyFromInventory by viewModel.onlyFromInventoryDeck.collectAsStateWithLifecycle()
  val isGenerating by viewModel.isGeneratingDeck.collectAsStateWithLifecycle()
  val metaAnalysis by viewModel.metaAnalysis.collectAsStateWithLifecycle()

  LazyColumn(
    modifier = modifier
      .fillMaxSize()
      .background(PocketBackground)
      .padding(14.dp),
    verticalArrangement = Arrangement.spacedBy(14.dp)
  ) {
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
              fontWeight = FontWeight.Bold,
              color = PocketTextPrimary
            )
          }

          Spacer(modifier = Modifier.height(10.dp))

          OutlinedTextField(
            value = deckPrompt,
            onValueChange = { viewModel.setDeckPrompt(it) },
            label = { Text("Estrategia o Arquetipo") },
            placeholder = { Text("Ej: Pikachu ex agresivo, Charizard control...") },
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

          // Filter by inventory toggle
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Column(modifier = Modifier.weight(1f)) {
              Text(
                text = "Usar solo cartas de mi colección (x1+)",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = PocketTextPrimary
              )
              Text(
                text = "Filtra estrictamente cartas con Cantidad >= 1 importadas de tu CSV",
                style = MaterialTheme.typography.bodySmall,
                color = PocketTextSecondary
              )
            }
            Switch(
              checked = onlyFromInventory,
              onCheckedChange = { viewModel.toggleOnlyFromInventoryDeck() },
              colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
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
            items(listOf("Pikachu ex Turbo", "Charizard ex Llamas", "Mewtwo ex Gardevoir", "Starmie ex Tempo", "Marowak ex Lucha")) { arch ->
              Box(
                modifier = Modifier
                  .clip(RoundedCornerShape(12.dp))
                  .background(PocketBackground)
                  .border(1.dp, PocketBorder, RoundedCornerShape(12.dp))
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
              .height(44.dp)
              .testTag("generate_deck_btn"),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = PocketBluePrimary),
            enabled = !isGenerating
          ) {
            if (isGenerating) {
              CircularProgressIndicator(color = Color.White, modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
              Spacer(modifier = Modifier.width(8.dp))
              Text("Generando baraja legal...", fontSize = 12.sp)
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
              Column {
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
                  .background(Color(0xFFFEF3C7))
                  .padding(horizontal = 8.dp, vertical = 4.dp)
              ) {
                Text(
                  text = "${deck.totalCardCount}/20 Cartas",
                  fontSize = 11.sp,
                  fontWeight = FontWeight.Black,
                  color = Color(0xFFB45309)
                )
              }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Strategy box
            Box(
              modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(Color(0xFFF8FAFC))
                .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(10.dp))
                .padding(10.dp)
            ) {
              Row(verticalAlignment = Alignment.Top) {
                Icon(Icons.AutoMirrored.Filled.HelpOutline, contentDescription = null, tint = PocketBluePrimary, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(text = deck.strategy, fontSize = 11.sp, color = PocketTextSecondary)
              }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(text = "Lista del Mazo:", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(6.dp))

            deck.cards.forEach { entry ->
              Row(
                modifier = Modifier
                  .fillMaxWidth()
                  .padding(vertical = 3.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
              ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
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

            // Export Actions
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
              OutlinedButton(
                onClick = {
                  val text = deck.toExportText()
                  val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                  val clip = ClipData.newPlainText("Mazo TCG Pocket", text)
                  clipboard.setPrimaryClip(clip)
                  Toast.makeText(context, "Mazo copiado al portapapeles", Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier
                  .weight(1f)
                  .height(38.dp)
                  .testTag("copy_deck_btn"),
                shape = RoundedCornerShape(10.dp)
              ) {
                Icon(Icons.Filled.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Copiar Lista", fontSize = 11.sp)
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
                modifier = Modifier
                  .weight(1f)
                  .height(38.dp)
                  .testTag("share_deck_btn"),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = PocketBluePrimary)
              ) {
                Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Compartir", fontSize = 11.sp)
              }
            }
          }
        }
      }
    }

    // Meta Analyzer Recommendations Card
    metaAnalysis?.let { analysis ->
      item {
        Card(
          modifier = Modifier
            .fillMaxWidth()
            .shadow(2.dp, RoundedCornerShape(16.dp), clip = false)
            .testTag("meta_analysis_card"),
          shape = RoundedCornerShape(16.dp),
          colors = CardDefaults.cardColors(containerColor = PocketSurface),
          border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(PocketBorder))
        ) {
          Column(modifier = Modifier.padding(16.dp)) {
            Text(
              text = "Análisis del Meta & Recomendación de Sobre",
              style = MaterialTheme.typography.titleSmall,
              fontWeight = FontWeight.Bold,
              color = PocketTextPrimary
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(text = analysis.overview, fontSize = 11.sp, color = PocketTextSecondary)

            Spacer(modifier = Modifier.height(10.dp))

            // Recommended pack highlighted box
            Box(
              modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(Color(0xFFFEF3C7))
                .border(1.dp, PocketGold.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
                .padding(10.dp)
            ) {
              Column {
                Text(
                  text = "Sobre Recomendado: ${analysis.bestPackToOpenNext}",
                  fontSize = 12.sp,
                  fontWeight = FontWeight.Black,
                  color = Color(0xFFB45309)
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(text = analysis.packReasoning, fontSize = 11.sp, color = Color(0xFF78350F))
              }
            }

            Spacer(modifier = Modifier.height(10.dp))

            analysis.deckInsights.forEach { deckInsight ->
              Column(
                modifier = Modifier
                  .fillMaxWidth()
                  .padding(vertical = 4.dp)
                  .clip(RoundedCornerShape(8.dp))
                  .background(Color(0xFFF8FAFC))
                  .padding(8.dp)
              ) {
                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.SpaceBetween,
                  verticalAlignment = Alignment.CenterVertically
                ) {
                  Text(
                    text = "${deckInsight.deckName} (${deckInsight.tier})",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = PocketTextPrimary
                  )
                  Text(
                    text = "${deckInsight.completionRatePercent}% poseído",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Black,
                    color = PocketBluePrimary
                  )
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(text = deckInsight.aiRecommendation, fontSize = 10.sp, color = PocketTextSecondary)
              }
            }
          }
        }
      }
    }
  }
}
