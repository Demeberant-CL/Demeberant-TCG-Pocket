package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
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
import com.example.data.api.MetaDeckInsight
import com.example.ui.theme.PocketBackground
import com.example.ui.theme.PocketBorder
import com.example.ui.theme.PocketCyan
import com.example.ui.theme.PocketCyanLight
import com.example.ui.theme.PocketGold
import com.example.ui.theme.PocketGoldLight
import com.example.ui.theme.PocketPurple
import com.example.ui.theme.PocketSurface
import com.example.ui.theme.PocketTextPrimary
import com.example.ui.theme.PocketTextSecondary
import com.example.ui.viewmodel.TcgViewModel

@Composable
fun MetaDeckAnalyzerScreen(
  viewModel: TcgViewModel,
  modifier: Modifier = Modifier
) {
  val metaResult by viewModel.metaAnalysisResult.collectAsStateWithLifecycle()
  val isAnalyzing by viewModel.isAnalyzingMeta.collectAsStateWithLifecycle()

  Column(
    modifier = modifier
      .fillMaxSize()
      .background(PocketBackground)
      .verticalScroll(rememberScrollState())
      .padding(horizontal = 18.dp, vertical = 14.dp)
  ) {
    // Header
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically
    ) {
      Column(modifier = Modifier.weight(1f)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Icon(Icons.Filled.AutoAwesome, contentDescription = null, tint = PocketCyan, modifier = Modifier.size(20.dp))
          Spacer(modifier = Modifier.width(6.dp))
          Text(
            text = "Análisis Meta por Gemini",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = PocketTextPrimary
          )
        }
        Spacer(modifier = Modifier.height(2.dp))
        Text(
          text = "Recomendaciones de mazos y mejoras según tu colección",
          fontSize = 12.sp,
          color = PocketTextSecondary
        )
      }

      Button(
        onClick = { viewModel.runGeminiMetaAnalysis() },
        enabled = !isAnalyzing,
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(containerColor = PocketCyan, contentColor = Color.White),
        modifier = Modifier.testTag("analyze_meta_btn")
      ) {
        if (isAnalyzing) {
          CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
        } else {
          Icon(Icons.Filled.Refresh, contentDescription = "Actualizar", modifier = Modifier.size(16.dp))
          Spacer(modifier = Modifier.width(4.dp))
          Text("Analizar", fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
      }
    }

    Spacer(modifier = Modifier.height(16.dp))

    // Top Summary Banner
    metaResult?.let { result ->
      Card(
        modifier = Modifier
          .fillMaxWidth()
          .shadow(4.dp, RoundedCornerShape(16.dp), clip = false)
          .testTag("meta_summary_card"),
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
              text = "RECOMENDACIÓN PRINCIPAL",
              fontSize = 10.sp,
              fontWeight = FontWeight.Black,
              letterSpacing = 1.sp,
              color = PocketCyan
            )

            Box(
              modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(PocketCyanLight)
                .padding(horizontal = 8.dp, vertical = 3.dp)
            ) {
              Text(
                text = "Gemini 3.5 Flash",
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                color = PocketCyan
              )
            }
          }

          Spacer(modifier = Modifier.height(6.dp))

          Text(
            text = result.recommendedArchetype,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = PocketTextPrimary
          )

          Spacer(modifier = Modifier.height(8.dp))

          Text(
            text = result.generalSummary,
            fontSize = 12.sp,
            color = PocketTextSecondary,
            lineHeight = 17.sp
          )
        }
      }

      Spacer(modifier = Modifier.height(18.dp))

      Text(
        text = "Arquetipos del Metajuego Actual:",
        fontSize = 14.sp,
        fontWeight = FontWeight.Bold,
        color = PocketTextPrimary
      )

      Spacer(modifier = Modifier.height(10.dp))

      result.deckInsights.forEach { deck ->
        MetaDeckCard(deck = deck)
        Spacer(modifier = Modifier.height(12.dp))
      }
    } ?: run {
      // Loading State
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .height(180.dp)
          .shadow(2.dp, RoundedCornerShape(16.dp), clip = false)
          .clip(RoundedCornerShape(16.dp))
          .background(PocketSurface),
        contentAlignment = Alignment.Center
      ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
          CircularProgressIndicator(color = PocketCyan, modifier = Modifier.size(36.dp))
          Spacer(modifier = Modifier.height(12.dp))
          Text(
            text = "Analizando tu colección contra el metajuego...",
            fontSize = 13.sp,
            color = PocketTextSecondary
          )
        }
      }
    }

    Spacer(modifier = Modifier.height(40.dp))
  }
}

@Composable
fun MetaDeckCard(deck: MetaDeckInsight) {
  val (badgeBg, badgeText, badgeBorder) = when (deck.tier.uppercase()) {
    "TIER S" -> Triple(PocketGoldLight, Color(0xFFB45309), PocketGold)
    "TIER 1" -> Triple(PocketCyanLight, Color(0xFF0369A1), PocketCyan)
    else -> Triple(Color(0xFFF3E8FF), Color(0xFF6B21A8), PocketPurple)
  }

  Card(
    modifier = Modifier
      .fillMaxWidth()
      .shadow(3.dp, RoundedCornerShape(16.dp), clip = false)
      .testTag("deck_insight_${deck.deckName}"),
    shape = RoundedCornerShape(16.dp),
    colors = CardDefaults.cardColors(containerColor = PocketSurface),
    border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(PocketBorder))
  ) {
    Column(modifier = Modifier.padding(16.dp)) {
      // Deck header: Name + Tier Badge
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = deck.deckName,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = PocketTextPrimary
          )
        }

        Box(
          modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(badgeBg)
            .border(1.dp, badgeBorder, RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp, vertical = 3.dp)
        ) {
          Text(
            text = deck.tier,
            fontSize = 11.sp,
            fontWeight = FontWeight.Black,
            color = badgeText
          )
        }
      }

      Spacer(modifier = Modifier.height(10.dp))

      // Progress percentage
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Text(
          text = "Completitud del mazo:",
          fontSize = 11.sp,
          color = PocketTextSecondary
        )
        Text(
          text = "${deck.completionRatePercent}%",
          fontSize = 13.sp,
          fontWeight = FontWeight.Bold,
          color = if (deck.completionRatePercent >= 75) PocketCyan else PocketTextSecondary
        )
      }

      Spacer(modifier = Modifier.height(4.dp))

      LinearProgressIndicator(
        progress = { (deck.completionRatePercent / 100f).coerceIn(0f, 1f) },
        modifier = Modifier
          .fillMaxWidth()
          .height(6.dp)
          .clip(RoundedCornerShape(3.dp)),
        color = PocketCyan,
        trackColor = Color(0xFFE2E8F0)
      )

      Spacer(modifier = Modifier.height(12.dp))

      // Cards Owned Summary
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .clip(RoundedCornerShape(10.dp))
          .background(Color(0xFFF0FDF4))
          .border(1.dp, Color(0xFFBBF7D0), RoundedCornerShape(10.dp))
          .padding(10.dp)
      ) {
        Column {
          Text("Cartas clave que tienes:", fontSize = 10.sp, color = Color(0xFF166534), fontWeight = FontWeight.SemiBold)
          Spacer(modifier = Modifier.height(2.dp))
          Text(
            text = deck.ownedCardsSummary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = PocketTextPrimary
          )
        }
      }

      Spacer(modifier = Modifier.height(8.dp))

      // Missing & Priority
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .clip(RoundedCornerShape(10.dp))
          .background(Color(0xFFFEF2F2))
          .border(1.dp, Color(0xFFFECACA), RoundedCornerShape(10.dp))
          .padding(10.dp)
      ) {
        Column {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
          ) {
            Text("Cartas por conseguir:", fontSize = 10.sp, color = Color(0xFF991B1B), fontWeight = FontWeight.SemiBold)
            Text("Prioridad: ${deck.priorityCraftCard}", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFFB45309))
          }
          Spacer(modifier = Modifier.height(2.dp))
          Text(
            text = deck.missingCardsSummary,
            fontSize = 12.sp,
            color = PocketTextPrimary
          )
        }
      }

      Spacer(modifier = Modifier.height(10.dp))

      // Gemini Tactical Advice
      Text(
        text = deck.strategyAndAdvice,
        fontSize = 12.sp,
        color = PocketTextSecondary,
        lineHeight = 16.sp
      )
    }
  }
}
