package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.engine.PackRecommendation
import com.example.data.engine.TcgProbabilityEngine
import com.example.data.model.BoosterPack
import com.example.ui.theme.PocketBackground
import com.example.ui.theme.PocketBluePrimary
import com.example.ui.theme.PocketBorder
import com.example.ui.theme.PocketGold
import com.example.ui.theme.PocketSurface
import com.example.ui.theme.PocketTextPrimary
import com.example.ui.theme.PocketTextSecondary
import com.example.ui.viewmodel.TcgViewModel

@Composable
fun PackRecommenderScreen(
  viewModel: TcgViewModel,
  modifier: Modifier = Modifier
) {
  val inventory by viewModel.inventoryList.collectAsStateWithLifecycle()
  var targetCardsInput by remember { mutableStateOf("A1-036, A1-047, A1-096") }
  var recommendationResult by remember { mutableStateOf<PackRecommendation?>(null) }

  // Initial calculation based on inventory
  remember(inventory) {
    val initialTargets = targetCardsInput.split(",").map { it.trim() }.filter { it.isNotBlank() }
    recommendationResult = TcgProbabilityEngine.recommendPack(initialTargets, inventory)
    true
  }

  LazyColumn(
    modifier = modifier
      .fillMaxSize()
      .background(PocketBackground)
      .padding(14.dp),
    verticalArrangement = Arrangement.spacedBy(14.dp)
  ) {
    // Header Input Card
    item {
      Card(
        modifier = Modifier
          .fillMaxWidth()
          .shadow(2.dp, RoundedCornerShape(16.dp), clip = false)
          .testTag("pack_recommender_card"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = PocketSurface),
        border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(PocketBorder))
      ) {
        Column(modifier = Modifier.padding(16.dp)) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.CardGiftcard, contentDescription = null, tint = PocketBluePrimary, modifier = Modifier.size(22.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(
              text = "Recomendador de Sobres Óptimo",
              style = MaterialTheme.typography.titleMedium,
              fontWeight = FontWeight.Black,
              color = PocketTextPrimary
            )
          }

          Spacer(modifier = Modifier.height(8.dp))

          Text(
            text = "Calcula matemáticamente qué sobre abrir para maximizar la probabilidad de conseguir cartas objetivo o completar tu colección.",
            fontSize = 11.sp,
            color = PocketTextSecondary,
            lineHeight = 16.sp
          )

          Spacer(modifier = Modifier.height(12.dp))

          OutlinedTextField(
            value = targetCardsInput,
            onValueChange = { targetCardsInput = it },
            label = { Text("Códigos de Cartas Objetivo (separados por coma)") },
            placeholder = { Text("Ej: A1-036, A1-096, A1-129") },
            modifier = Modifier
              .fillMaxWidth()
              .testTag("target_cards_input"),
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
              focusedBorderColor = PocketBluePrimary,
              unfocusedBorderColor = PocketBorder
            ),
            singleLine = true
          )

          Spacer(modifier = Modifier.height(8.dp))

          // Quick Preset Targets
          Text(text = "Objetivos Populares:", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = PocketTextSecondary)
          Spacer(modifier = Modifier.height(6.dp))
          LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            val presets = listOf(
              "Meta Rayo" to "A1-096, A1-101",
              "Meta Fuego" to "A1-036, A1-047",
              "Meta Psíquico" to "A1-129, A1-130",
              "Meta Agua" to "A1-076, A1-056"
            )
            items(presets) { (label, codes) ->
              Box(
                modifier = Modifier
                  .clip(RoundedCornerShape(12.dp))
                  .background(PocketBackground)
                  .border(1.dp, PocketBorder, RoundedCornerShape(12.dp))
                  .clickable {
                    targetCardsInput = codes
                    val targets = codes.split(",").map { it.trim() }.filter { it.isNotBlank() }
                    recommendationResult = TcgProbabilityEngine.recommendPack(targets, inventory)
                  }
                  .padding(horizontal = 8.dp, vertical = 4.dp)
              ) {
                Text(label, fontSize = 11.sp, color = PocketBluePrimary, fontWeight = FontWeight.Medium)
              }
            }
          }

          Spacer(modifier = Modifier.height(12.dp))

          Button(
            onClick = {
              val targets = targetCardsInput.split(",").map { it.trim() }.filter { it.isNotBlank() }
              recommendationResult = TcgProbabilityEngine.recommendPack(targets, inventory)
            },
            modifier = Modifier
              .fillMaxWidth()
              .height(42.dp)
              .testTag("calculate_recommendation_btn"),
            shape = RoundedCornerShape(10.dp),
            colors = ButtonDefaults.buttonColors(containerColor = PocketBluePrimary)
          ) {
            Icon(Icons.Filled.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("Calcular Recomendación Óptima", fontSize = 12.sp, fontWeight = FontWeight.Bold)
          }
        }
      }
    }

    // Recommendation Result Card
    recommendationResult?.let { rec ->
      item {
        val packColor = when (rec.recommendedPack) {
          BoosterPack.CHARIZARD -> Color(0xFFEA580C)
          BoosterPack.MEWTWO -> Color(0xFF9333EA)
          BoosterPack.PIKACHU -> Color(0xFFCA8A04)
        }

        Card(
          modifier = Modifier
            .fillMaxWidth()
            .shadow(3.dp, RoundedCornerShape(16.dp), clip = false)
            .testTag("recommendation_result_card"),
          shape = RoundedCornerShape(16.dp),
          colors = CardDefaults.cardColors(containerColor = PocketSurface),
          border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(packColor))
        ) {
          Column(modifier = Modifier.padding(16.dp)) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              Column {
                Text(
                  text = "Sobre Recomendado",
                  fontSize = 11.sp,
                  fontWeight = FontWeight.Bold,
                  color = PocketTextSecondary
                )
                Text(
                  text = rec.recommendedPack.displayName,
                  style = MaterialTheme.typography.titleLarge,
                  fontWeight = FontWeight.Black,
                  color = packColor
                )
              }

              Box(
                modifier = Modifier
                  .clip(RoundedCornerShape(10.dp))
                  .background(packColor.copy(alpha = 0.12f))
                  .border(1.dp, packColor.copy(alpha = 0.4f), RoundedCornerShape(10.dp))
                  .padding(horizontal = 10.dp, vertical = 6.dp)
              ) {
                Text(
                  text = "Score: ${rec.score.toInt()}",
                  fontWeight = FontWeight.Black,
                  color = packColor,
                  fontSize = 12.sp
                )
              }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Reasoning Box
            Box(
              modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(Color(0xFFF8FAFC))
                .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(10.dp))
                .padding(10.dp)
            ) {
              Row(verticalAlignment = Alignment.Top) {
                Icon(Icons.Filled.Info, contentDescription = null, tint = PocketBluePrimary, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                  text = rec.reasoning,
                  fontSize = 11.sp,
                  color = PocketTextPrimary,
                  lineHeight = 16.sp
                )
              }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Stats grid
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              Box(
                modifier = Modifier
                  .weight(1f)
                  .clip(RoundedCornerShape(10.dp))
                  .background(Color(0xFFF1F5F9))
                  .padding(10.dp)
              ) {
                Column {
                  Text("Probabilidad Slot 5", fontSize = 10.sp, color = PocketTextSecondary)
                  Text(
                    text = "${(rec.successProbabilitySlot5 * 100).toInt()}%",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Black,
                    color = PocketBluePrimary
                  )
                  Text("Rarezas altas", fontSize = 9.sp, color = PocketTextSecondary)
                }
              }

              Box(
                modifier = Modifier
                  .weight(1f)
                  .clip(RoundedCornerShape(10.dp))
                  .background(Color(0xFFF1F5F9))
                  .padding(10.dp)
              ) {
                Column {
                  Text("Cartas Faltantes", fontSize = 10.sp, color = PocketTextSecondary)
                  Text(
                    text = "${rec.missingCardsCount}",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Black,
                    color = PocketGold
                  )
                  Text("En este sobre", fontSize = 9.sp, color = PocketTextSecondary)
                }
              }
            }

            if (rec.targetCardsFound.isNotEmpty()) {
              Spacer(modifier = Modifier.height(12.dp))
              Text(
                text = "Cartas Objetivo Encontradas en este Sobre:",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = PocketTextPrimary
              )
              Spacer(modifier = Modifier.height(4.dp))
              rec.targetCardsFound.forEach { targetStr ->
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 2.dp)) {
                  Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = Color(0xFF10B981), modifier = Modifier.size(14.dp))
                  Spacer(modifier = Modifier.width(6.dp))
                  Text(targetStr, fontSize = 11.sp, color = PocketTextPrimary)
                }
              }
            }
          }
        }
      }
    }
  }
}
