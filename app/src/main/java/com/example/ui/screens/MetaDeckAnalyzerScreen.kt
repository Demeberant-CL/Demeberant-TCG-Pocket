package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import com.example.ui.theme.PocketBluePrimary
import com.example.ui.theme.PocketBorder
import com.example.ui.theme.PocketGold
import com.example.ui.theme.PocketSurface
import com.example.ui.theme.PocketTextPrimary
import com.example.ui.theme.PocketTextSecondary
import com.example.ui.viewmodel.TcgViewModel

@Composable
fun MetaDeckAnalyzerScreen(
  viewModel: TcgViewModel,
  onNavigateToDeckBuilder: (String) -> Unit,
  modifier: Modifier = Modifier
) {
  val metaAnalysis by viewModel.metaAnalysis.collectAsStateWithLifecycle()

  LazyColumn(
    modifier = modifier
      .fillMaxSize()
      .background(PocketBackground)
      .padding(14.dp),
    verticalArrangement = Arrangement.spacedBy(14.dp)
  ) {
    // Header Overview Card
    item {
      Card(
        modifier = Modifier
          .fillMaxWidth()
          .shadow(2.dp, RoundedCornerShape(16.dp), clip = false)
          .testTag("meta_overview_card"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = PocketSurface),
        border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(PocketBorder))
      ) {
        Column(modifier = Modifier.padding(16.dp)) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Insights, contentDescription = null, tint = PocketBluePrimary, modifier = Modifier.size(22.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(
              text = "Plantillas A1 y colección",
              style = MaterialTheme.typography.titleMedium,
              fontWeight = FontWeight.Black,
              color = PocketTextPrimary
            )
          }

          Spacer(modifier = Modifier.height(8.dp))

          Text(
            text = metaAnalysis?.overview ?: "Cargando el análisis local de plantillas A1 y tu colección.",
            fontSize = 12.sp,
            color = PocketTextSecondary,
            lineHeight = 17.sp
          )

          Spacer(modifier = Modifier.height(12.dp))

          // Best Pack Recommendation Banner
          metaAnalysis?.let { analysis ->
            Box(
              modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.secondaryContainer)
                .border(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
                .padding(12.dp)
            ) {
              Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                  Icon(Icons.Filled.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(16.dp))
                  Spacer(modifier = Modifier.width(6.dp))
                  Text(
                    text = "Sobre para plantillas A1: ${analysis.bestPackToOpenNext}",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                  )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                  text = analysis.packReasoning,
                  fontSize = 11.sp,
                  color = MaterialTheme.colorScheme.onSecondaryContainer,
                  lineHeight = 15.sp
                )
              }
            }
          }
        }
      }
    }

    item {
      Text(
        text = "Plantillas históricas A1 · cobertura de colección",
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        color = PocketTextPrimary
      )
    }

    item {
      val context = androidx.compose.ui.platform.LocalContext.current
      Text("Estas plantillas no representan el meta actual.")
      androidx.compose.material3.OutlinedButton(onClick = {
        context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW,
          android.net.Uri.parse("https://play.limitlesstcg.com/tournaments?game=POCKET")))
      }) { Text("Consultar torneos actuales") }
    }
    // Deck Insight Cards
    items(metaAnalysis?.deckInsights ?: emptyList()) { deckInsight ->
      MetaDeckInsightCard(
        insight = deckInsight,
        onBuildDeckClick = { onNavigateToDeckBuilder(deckInsight.deckName) }
      )
    }
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MetaDeckInsightCard(
  insight: MetaDeckInsight,
  onBuildDeckClick: () -> Unit
) {
  val tierColor = MaterialTheme.colorScheme.secondary

  Card(
    modifier = Modifier
      .fillMaxWidth()
      .shadow(2.dp, RoundedCornerShape(14.dp), clip = false)
      .testTag("meta_deck_card_${insight.deckName}"),
    shape = RoundedCornerShape(14.dp),
    colors = CardDefaults.cardColors(containerColor = PocketSurface),
    border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(PocketBorder))
  ) {
    Column(modifier = Modifier.padding(14.dp)) {
      // Header: Name & Tier Badge
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = insight.deckName,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Black,
            color = PocketTextPrimary
          )
          Text(
            text = insight.archetype,
            fontSize = 11.sp,
            color = PocketTextSecondary
          )
        }

        Box(
          modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(tierColor.copy(alpha = 0.15f))
            .border(1.dp, tierColor.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp, vertical = 3.dp)
        ) {
          Text(
            text = "Plantilla A1",
            fontSize = 11.sp,
            fontWeight = FontWeight.Black,
            color = tierColor
          )
        }
      }

      Spacer(modifier = Modifier.height(10.dp))

      // Let the percentage wrap when system text is enlarged.
      FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        Text(
          text = "Cartas clave en tu colección:",
          fontSize = 11.sp,
          fontWeight = FontWeight.Medium,
          color = PocketTextSecondary
        )
        Text(
          text = "${insight.completionRatePercent}%",
          fontSize = 12.sp,
          fontWeight = FontWeight.Black,
          color = PocketBluePrimary
        )
      }

      Spacer(modifier = Modifier.height(4.dp))

      LinearProgressIndicator(
        progress = { insight.completionRatePercent / 100f },
        modifier = Modifier
          .fillMaxWidth()
          .height(6.dp)
          .clip(RoundedCornerShape(3.dp)),
        color = PocketBluePrimary,
        trackColor = MaterialTheme.colorScheme.surfaceContainerHigh
      )

      Spacer(modifier = Modifier.height(8.dp))

      // Key cards breakdown
      Text(
        text = "Piezas Requeridas: ${insight.keyCardsNeeded.joinToString(", ")}",
        fontSize = 10.sp,
        fontWeight = FontWeight.SemiBold,
        color = PocketTextPrimary
      )
      Text(
        text = "Poseídas: ${insight.ownedCardsSummary}",
        fontSize = 10.sp,
        color = MaterialTheme.colorScheme.primary
      )

      Spacer(modifier = Modifier.height(8.dp))

      // AI Recommendation
      Text(
        text = "💡 ${insight.aiRecommendation}",
        fontSize = 11.sp,
        color = PocketTextSecondary,
        lineHeight = 15.sp
      )

      Spacer(modifier = Modifier.height(10.dp))

      Button(
        onClick = onBuildDeckClick,
        modifier = Modifier
          .fillMaxWidth()
          .heightIn(min = 48.dp),
        shape = RoundedCornerShape(8.dp),
        colors = ButtonDefaults.buttonColors(containerColor = PocketBluePrimary)
      ) {
        Icon(Icons.Filled.Build, contentDescription = null, modifier = Modifier.size(14.dp))
        Spacer(modifier = Modifier.width(6.dp))
        Text("Cargar en Creador de Mazos", fontSize = 11.sp, fontWeight = FontWeight.Bold)
      }
    }
  }
}
