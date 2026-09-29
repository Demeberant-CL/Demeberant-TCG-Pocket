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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import com.example.data.model.CardRarity
import com.example.ui.components.RarityBadge
import com.example.ui.theme.PocketBackground
import com.example.ui.theme.PocketBorder
import com.example.ui.theme.PocketCyan
import com.example.ui.theme.PocketCyanLight
import com.example.ui.theme.PocketGold
import com.example.ui.theme.PocketSurface
import com.example.ui.theme.PocketSurfaceSubtle
import com.example.ui.theme.PocketTextPrimary
import com.example.ui.theme.PocketTextSecondary
import com.example.ui.viewmodel.TcgViewModel
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun ProbabilityCalculatorScreen(
  viewModel: TcgViewModel,
  modifier: Modifier = Modifier
) {
  val selectedRarity by viewModel.calcSelectedRarity.collectAsStateWithLifecycle()
  val packsToOpen by viewModel.calcPacksToOpen.collectAsStateWithLifecycle()
  val result by viewModel.probabilityResult.collectAsStateWithLifecycle()

  val rarities = listOf(
    CardRarity.CROWN,
    CardRarity.STAR_3,
    CardRarity.STAR_2,
    CardRarity.STAR_1,
    CardRarity.DIAMOND_4,
    CardRarity.DIAMOND_3,
    CardRarity.DIAMOND_2
  )

  Column(
    modifier = modifier
      .fillMaxSize()
      .background(PocketBackground)
      .verticalScroll(rememberScrollState())
      .padding(horizontal = 18.dp, vertical = 14.dp)
  ) {
    // Header
    Text(
      text = "Calculadora de Probabilidad",
      fontSize = 20.sp,
      fontWeight = FontWeight.Bold,
      color = PocketTextPrimary
    )
    Text(
      text = "Tasas oficiales de apertura para Pokémon TCG Pocket",
      fontSize = 12.sp,
      color = PocketTextSecondary
    )

    Spacer(modifier = Modifier.height(18.dp))

    // Rarity selector label
    Text(
      text = "Rareza que buscas:",
      fontSize = 13.sp,
      fontWeight = FontWeight.Bold,
      color = PocketTextPrimary
    )

    Spacer(modifier = Modifier.height(8.dp))

    // Rarity Selector Pills
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      items(rarities) { rarity ->
        val isSelected = selectedRarity == rarity
        val pillBg = if (isSelected) PocketCyan else PocketSurface
        val pillBorder = if (isSelected) PocketCyan else PocketBorder
        val textColor = if (isSelected) Color.White else PocketTextPrimary

        Box(
          modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .shadow(if (isSelected) 3.dp else 1.dp, RoundedCornerShape(20.dp), clip = false)
            .background(pillBg)
            .border(1.dp, pillBorder, RoundedCornerShape(20.dp))
            .clickable { viewModel.setCalcRarity(rarity) }
            .padding(horizontal = 14.dp, vertical = 8.dp)
            .testTag("rarity_pill_${rarity.name}")
        ) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
              text = rarity.symbol,
              fontSize = 13.sp,
              fontWeight = FontWeight.Bold,
              color = if (isSelected) Color.White else PocketCyan
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
              text = rarity.displayName.substringBefore(" "),
              fontSize = 12.sp,
              fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
              color = textColor
            )
          }
        }
      }
    }

    Spacer(modifier = Modifier.height(20.dp))

    // Main Probability Showcase Card (Clean White Surface)
    Card(
      modifier = Modifier
        .fillMaxWidth()
        .shadow(4.dp, RoundedCornerShape(18.dp), clip = false)
        .testTag("probability_results_card"),
      shape = RoundedCornerShape(18.dp),
      colors = CardDefaults.cardColors(containerColor = PocketSurface),
      border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(PocketBorder))
    ) {
      Column(modifier = Modifier.padding(18.dp)) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Column {
            Text(
              text = "PROBABILIDAD TOTAL",
              fontSize = 11.sp,
              fontWeight = FontWeight.Black,
              letterSpacing = 1.sp,
              color = PocketCyan
            )
            Text(
              text = selectedRarity.displayName,
              fontSize = 16.sp,
              fontWeight = FontWeight.Bold,
              color = PocketTextPrimary
            )
          }

          RarityBadge(rarity = selectedRarity)
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Big Percentage Number
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.Bottom
        ) {
          Text(
            text = String.format(Locale.US, "%.3f%%", result.cumulativePercent),
            fontSize = 38.sp,
            fontWeight = FontWeight.Black,
            color = PocketTextPrimary
          )

          Column(horizontalAlignment = Alignment.End) {
            Text(
              text = "Por cada sobre",
              fontSize = 11.sp,
              color = PocketTextSecondary
            )
            Text(
              text = String.format(Locale.US, "%.3f%%", result.ratePerPack),
              fontSize = 15.sp,
              fontWeight = FontWeight.Bold,
              color = PocketCyan
            )
          }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Clean Progress Bar
        val progress = (result.cumulativePercent / 100.0).toFloat().coerceIn(0f, 1f)
        LinearProgressIndicator(
          progress = { progress },
          modifier = Modifier
            .fillMaxWidth()
            .height(8.dp)
            .clip(RoundedCornerShape(4.dp)),
          color = PocketCyan,
          trackColor = Color(0xFFE2E8F0)
        )

        Spacer(modifier = Modifier.height(14.dp))

        // Slot probabilities in minimal chips
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
          Box(
            modifier = Modifier
              .weight(1f)
              .clip(RoundedCornerShape(12.dp))
              .background(Color(0xFFF8FAFC))
              .border(1.dp, PocketBorder, RoundedCornerShape(12.dp))
              .padding(12.dp)
          ) {
            Column {
              Text("Carta 4 (Slot 4)", fontSize = 11.sp, color = PocketTextSecondary)
              Spacer(modifier = Modifier.height(2.dp))
              Text(
                text = "${result.slot4Rate}%",
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = PocketTextPrimary
              )
            }
          }

          Box(
            modifier = Modifier
              .weight(1f)
              .clip(RoundedCornerShape(12.dp))
              .background(PocketCyanLight)
              .border(1.dp, PocketCyan.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
              .padding(12.dp)
          ) {
            Column {
              Text("Carta 5 (Slot 5)", fontSize = 11.sp, color = PocketCyan)
              Spacer(modifier = Modifier.height(2.dp))
              Text(
                text = "${result.slot5Rate}%",
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = PocketCyan
              )
            }
          }
        }
      }
    }

    Spacer(modifier = Modifier.height(20.dp))

    // Number of packs selector
    Text(
      text = "Cantidad de sobres calculados:",
      fontSize = 13.sp,
      fontWeight = FontWeight.Bold,
      color = PocketTextPrimary
    )

    Spacer(modifier = Modifier.height(8.dp))

    // Stepper
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .shadow(2.dp, RoundedCornerShape(14.dp), clip = false)
        .clip(RoundedCornerShape(14.dp))
        .background(PocketSurface)
        .border(1.dp, PocketBorder, RoundedCornerShape(14.dp))
        .padding(horizontal = 8.dp, vertical = 6.dp),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically
    ) {
      IconButton(
        onClick = { viewModel.setCalcPacksToOpen(packsToOpen - 1) },
        modifier = Modifier
          .size(40.dp)
          .clip(CircleShape)
          .background(PocketSurfaceSubtle)
      ) {
        Icon(Icons.Filled.Remove, contentDescription = "Menos", tint = PocketTextPrimary, modifier = Modifier.size(18.dp))
      }

      Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
          text = "$packsToOpen",
          fontSize = 24.sp,
          fontWeight = FontWeight.Black,
          color = PocketTextPrimary
        )
        Text(
          text = if (packsToOpen == 1) "sobre" else "sobres",
          fontSize = 11.sp,
          color = PocketTextSecondary
        )
      }

      IconButton(
        onClick = { viewModel.setCalcPacksToOpen(packsToOpen + 1) },
        modifier = Modifier
          .size(40.dp)
          .clip(CircleShape)
          .background(PocketSurfaceSubtle)
      ) {
        Icon(Icons.Filled.Add, contentDescription = "Más", tint = PocketTextPrimary, modifier = Modifier.size(18.dp))
      }
    }

    Spacer(modifier = Modifier.height(8.dp))

    Slider(
      value = packsToOpen.toFloat(),
      onValueChange = { viewModel.setCalcPacksToOpen(it.roundToInt()) },
      valueRange = 1f..100f,
      steps = 98,
      modifier = Modifier
        .fillMaxWidth()
        .testTag("packs_slider"),
      colors = SliderDefaults.colors(
        thumbColor = PocketCyan,
        activeTrackColor = PocketCyan,
        inactiveTrackColor = Color(0xFFCBD5E1)
      )
    )

    // Quick presets
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween
    ) {
      listOf(1, 5, 10, 20, 50, 100).forEach { count ->
        val isPresetSelected = packsToOpen == count
        Box(
          modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (isPresetSelected) PocketCyan else PocketSurface)
            .border(1.dp, if (isPresetSelected) PocketCyan else PocketBorder, RoundedCornerShape(10.dp))
            .clickable { viewModel.setCalcPacksToOpen(count) }
            .padding(horizontal = 14.dp, vertical = 7.dp)
            .testTag("quick_packs_$count")
        ) {
          Text(
            text = "$count",
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = if (isPresetSelected) Color.White else PocketTextPrimary
          )
        }
      }
    }

    Spacer(modifier = Modifier.height(20.dp))

    // Estimation milestones card
    Card(
      modifier = Modifier
        .fillMaxWidth()
        .shadow(2.dp, RoundedCornerShape(14.dp), clip = false),
      shape = RoundedCornerShape(14.dp),
      colors = CardDefaults.cardColors(containerColor = PocketSurface),
      border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(PocketBorder))
    ) {
      Column(modifier = Modifier.padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Icon(Icons.Filled.Info, contentDescription = null, tint = PocketCyan, modifier = Modifier.size(18.dp))
          Spacer(modifier = Modifier.width(8.dp))
          Text(
            text = "Estimación de sobres necesarios",
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = PocketTextPrimary
          )
        }

        Spacer(modifier = Modifier.height(10.dp))

        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween
        ) {
          Column {
            Text("50% de probabilidad:", fontSize = 12.sp, color = PocketTextSecondary)
            Text(
              text = "~${result.packsFor50} sobres",
              fontSize = 15.sp,
              fontWeight = FontWeight.Bold,
              color = PocketTextPrimary
            )
          }

          Column(horizontalAlignment = Alignment.End) {
            Text("90% de probabilidad:", fontSize = 12.sp, color = PocketTextSecondary)
            Text(
              text = "~${result.packsFor90} sobres",
              fontSize = 15.sp,
              fontWeight = FontWeight.Bold,
              color = PocketCyan
            )
          }
        }

        Spacer(modifier = Modifier.height(10.dp))

        Text(
          text = result.explanation,
          fontSize = 12.sp,
          color = PocketTextSecondary,
          lineHeight = 17.sp
        )
      }
    }

    Spacer(modifier = Modifier.height(40.dp))
  }
}
