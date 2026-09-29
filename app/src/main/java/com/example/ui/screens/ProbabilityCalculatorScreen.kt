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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
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
import com.example.data.engine.TcgProbabilityEngine
import com.example.data.model.CardRarity
import com.example.ui.theme.PocketBackground
import com.example.ui.theme.PocketBluePrimary
import com.example.ui.theme.PocketBorder
import com.example.ui.theme.PocketGold
import com.example.ui.theme.PocketSurface
import com.example.ui.theme.PocketTextPrimary
import com.example.ui.theme.PocketTextSecondary

@Composable
fun ProbabilityCalculatorScreen(
  modifier: Modifier = Modifier
) {
  var selectedRarity by remember { mutableStateOf(CardRarity.CROWN) }
  var packsSliderValue by remember { mutableFloatStateOf(10f) }

  val packsCount = packsSliderValue.toInt()
  val cumulativeProb = TcgProbabilityEngine.calculateCumulativeProbability(selectedRarity, packsCount)
  val expectedPacks = TcgProbabilityEngine.getExpectedPacks(selectedRarity)
  val rarityData = TcgProbabilityEngine.RARITY_RATES[selectedRarity]

  LazyColumn(
    modifier = modifier
      .fillMaxSize()
      .background(PocketBackground)
      .padding(14.dp),
    verticalArrangement = Arrangement.spacedBy(14.dp)
  ) {
    // Header
    item {
      Card(
        modifier = Modifier
          .fillMaxWidth()
          .shadow(2.dp, RoundedCornerShape(16.dp), clip = false)
          .testTag("probability_calculator_card"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = PocketSurface),
        border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(PocketBorder))
      ) {
        Column(modifier = Modifier.padding(16.dp)) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Calculate, contentDescription = null, tint = PocketBluePrimary, modifier = Modifier.size(22.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(
              text = "Calculadora Oficial de Probabilidades",
              style = MaterialTheme.typography.titleMedium,
              fontWeight = FontWeight.Black,
              color = PocketTextPrimary
            )
          }

          Spacer(modifier = Modifier.height(6.dp))

          Text(
            text = "Basada en los índices de aparición oficiales de Pokémon TCG Pocket (Exactamente 5 cartas por sobre de expansión).",
            fontSize = 11.sp,
            color = PocketTextSecondary
          )
        }
      }
    }

    // Selector de Rareza
    item {
      Text(
        text = "Selecciona la Rareza Objetivo:",
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        color = PocketTextPrimary
      )

      Spacer(modifier = Modifier.height(6.dp))

      Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        val rarities = listOf(
          CardRarity.CROWN to "Corona (♛) - 0.04% Slot 5",
          CardRarity.THREE_STARS to "3 Estrellas (★★★ Inmersiva) - 0.222% Slot 5",
          CardRarity.TWO_STARS to "2 Estrellas (★★ Súper Rara) - 1.714% Slot 5",
          CardRarity.ONE_STAR to "1 Estrella (★ Arte Alternativo) - 5.14%",
          CardRarity.FOUR_DIAMONDS to "4 Diamantes (♦♦♦♦ ex) - 10.73%",
          CardRarity.THREE_DIAMONDS to "3 Diamantes (♦♦♦ Rara) - 24.0%",
          CardRarity.TWO_DIAMONDS to "2 Diamantes (♦♦ Poco Común) - 96.0%"
        )

        rarities.forEach { (rarity, label) ->
          val isSelected = selectedRarity == rarity
          Box(
            modifier = Modifier
              .fillMaxWidth()
              .clip(RoundedCornerShape(10.dp))
              .background(if (isSelected) PocketBluePrimary.copy(alpha = 0.12f) else PocketSurface)
              .border(
                1.dp,
                if (isSelected) PocketBluePrimary else PocketBorder,
                RoundedCornerShape(10.dp)
              )
              .clickable { selectedRarity = rarity }
              .padding(horizontal = 12.dp, vertical = 9.dp)
          ) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              Text(
                text = label,
                fontSize = 11.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                color = if (isSelected) PocketBluePrimary else PocketTextPrimary
              )
              if (isSelected) {
                Box(
                  modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(PocketBluePrimary)
                    .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                  Text("Activo", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }
              }
            }
          }
        }
      }
    }

    // Slider de Sobres a Abrir
    item {
      Card(
        modifier = Modifier
          .fillMaxWidth()
          .shadow(2.dp, RoundedCornerShape(16.dp), clip = false),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = PocketSurface),
        border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(PocketBorder))
      ) {
        Column(modifier = Modifier.padding(16.dp)) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Text(
              text = "Número de Sobres a Abrir:",
              fontSize = 12.sp,
              fontWeight = FontWeight.Bold,
              color = PocketTextPrimary
            )
            Text(
              text = "$packsCount sobres",
              fontSize = 14.sp,
              fontWeight = FontWeight.Black,
              color = PocketBluePrimary
            )
          }

          Slider(
            value = packsSliderValue,
            onValueChange = { packsSliderValue = it },
            valueRange = 1f..150f,
            steps = 149,
            colors = SliderDefaults.colors(
              thumbColor = PocketBluePrimary,
              activeTrackColor = PocketBluePrimary
            )
          )

          Spacer(modifier = Modifier.height(10.dp))

          // Result calculation boxes
          Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(
              modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFFFEF3C7))
                .border(1.dp, PocketGold.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                .padding(12.dp)
            ) {
              Column {
                Text("Probabilidad Acumulada", fontSize = 10.sp, color = Color(0xFF92400E))
                Text(
                  text = String.format("%.2f%%", cumulativeProb * 100),
                  fontSize = 20.sp,
                  fontWeight = FontWeight.Black,
                  color = Color(0xFFB45309)
                )
                Text("En $packsCount sobres", fontSize = 9.sp, color = Color(0xFF78350F))
              }
            }

            Box(
              modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFFF1F5F9))
                .padding(12.dp)
            ) {
              Column {
                Text("Promedio Esperado", fontSize = 10.sp, color = PocketTextSecondary)
                Text(
                  text = "${expectedPacks.toInt()} sobres",
                  fontSize = 20.sp,
                  fontWeight = FontWeight.Black,
                  color = PocketBluePrimary
                )
                Text("Para 1 garantizada", fontSize = 9.sp, color = PocketTextSecondary)
              }
            }
          }

          Spacer(modifier = Modifier.height(12.dp))

          rarityData?.let { data ->
            Box(
              modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFFF8FAFC))
                .padding(10.dp)
            ) {
              Column {
                Text("Desglose Oficial por Ranura:", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = PocketTextPrimary)
                Spacer(modifier = Modifier.height(4.dp))
                Text("• Ranura 4: ${String.format("%.3f%%", data.slot4Probability * 100)}", fontSize = 10.sp, color = PocketTextSecondary)
                Text("• Ranura 5: ${String.format("%.3f%%", data.slot5Probability * 100)}", fontSize = 10.sp, color = PocketTextSecondary)
                Text("• Tasa total por sobre: ${String.format("%.3f%%", data.totalProbabilityPerPack * 100)}", fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = PocketBluePrimary)
              }
            }
          }
        }
      }
    }
  }
}
