package com.example.ui.screens

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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.TipsAndUpdates
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.BoosterPack
import com.example.data.repository.CardCatalog
import com.example.ui.components.RarityBadge
import com.example.ui.theme.TcgBorder
import com.example.ui.theme.TcgCyan
import com.example.ui.theme.TcgGold
import com.example.ui.theme.TcgOrange
import com.example.ui.theme.TcgPurple
import com.example.ui.theme.TcgSurfaceElevated
import com.example.ui.viewmodel.TcgViewModel
import java.util.Locale

@Composable
fun PackRecommenderScreen(
  viewModel: TcgViewModel,
  modifier: Modifier = Modifier
) {
  val targetCards by viewModel.selectedTargetCards.collectAsStateWithLifecycle()
  val result by viewModel.recommendationResult.collectAsStateWithLifecycle()

  val packColor = when (result.recommendedPack) {
    BoosterPack.CHARIZARD -> TcgOrange
    BoosterPack.MEWTWO -> TcgPurple
    BoosterPack.PIKACHU -> TcgGold
    else -> TcgCyan
  }

  val candidateCards = CardCatalog.ALL_CARDS.filter { it.rarity.isRare }

  Column(
    modifier = modifier
      .fillMaxSize()
      .background(MaterialTheme.colorScheme.background)
      .verticalScroll(rememberScrollState())
      .padding(horizontal = 20.dp, vertical = 16.dp)
  ) {
    // Header
    Text(
      text = "Recomendador de Sobres",
      fontSize = 22.sp,
      fontWeight = FontWeight.Bold,
      color = Color.White
    )
    Text(
      text = "¿Qué sobre te conviene abrir según tus cartas objetivo?",
      fontSize = 13.sp,
      color = Color(0xFF94A3B8)
    )

    Spacer(modifier = Modifier.height(20.dp))

    // Recommended Pack Hero Card
    Card(
      modifier = Modifier
        .fillMaxWidth()
        .testTag("recommended_pack_card"),
      shape = RoundedCornerShape(20.dp),
      colors = CardDefaults.cardColors(containerColor = TcgSurfaceElevated),
      border = CardDefaults.outlinedCardBorder().copy(
        brush = Brush.linearGradient(listOf(packColor, packColor.copy(alpha = 0.3f)))
      )
    ) {
      Column(modifier = Modifier.padding(20.dp)) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.AutoAwesome, contentDescription = null, tint = packColor, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text(
              text = "RECOMENDACIÓN DEL DÍA",
              fontSize = 11.sp,
              fontWeight = FontWeight.Black,
              letterSpacing = 1.sp,
              color = packColor
            )
          }

          Box(
            modifier = Modifier
              .clip(RoundedCornerShape(12.dp))
              .background(packColor.copy(alpha = 0.15f))
              .border(1.dp, packColor.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
              .padding(horizontal = 10.dp, vertical = 4.dp)
          ) {
            Text(
              text = "${targetCards.size} cartas seleccionadas",
              fontSize = 11.sp,
              fontWeight = FontWeight.SemiBold,
              color = Color.White
            )
          }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Big Pack Name
        Text(
          text = result.recommendedPack.displayName,
          fontSize = 26.sp,
          fontWeight = FontWeight.Black,
          color = Color.White
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
          text = result.reasoning,
          fontSize = 13.sp,
          color = Color(0xFFCBD5E1),
          lineHeight = 18.sp
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Metric Pill
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xFF0F1522))
            .padding(14.dp),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Column {
            Text(
              text = "Probabilidad acumulada en Slot 5",
              fontSize = 11.sp,
              color = Color(0xFF94A3B8)
            )
            Text(
              text = String.format(Locale.US, "%.3f%%", result.slot5ProbabilityPercent),
              fontSize = 22.sp,
              fontWeight = FontWeight.Black,
              color = packColor
            )
          }

          Box(
            modifier = Modifier
              .clip(RoundedCornerShape(8.dp))
              .background(packColor)
              .padding(horizontal = 10.dp, vertical = 6.dp)
          ) {
            Text(
              text = "ÓPTIMO",
              fontSize = 10.sp,
              fontWeight = FontWeight.Black,
              color = Color.Black
            )
          }
        }
      }
    }

    Spacer(modifier = Modifier.height(24.dp))

    // Target Selection Section
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically
    ) {
      Text(
        text = "Tus cartas deseadas:",
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        color = Color(0xFFE2E8F0)
      )

      OutlinedButton(
        onClick = { viewModel.setWishlistAsTargets() },
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.testTag("sync_wishlist_btn")
      ) {
        Icon(Icons.Filled.Bookmark, contentDescription = null, tint = TcgGold, modifier = Modifier.size(14.dp))
        Spacer(modifier = Modifier.width(4.dp))
        Text("Importar Wishlist", fontSize = 11.sp, color = TcgGold)
      }
    }

    Spacer(modifier = Modifier.height(12.dp))

    // Candidate Cards Selector
    LazyRow(
      horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
      items(candidateCards) { card ->
        val isTarget = targetCards.contains(card.id)
        val pillBg = if (isTarget) TcgGold.copy(alpha = 0.2f) else TcgSurfaceElevated
        val pillBorder = if (isTarget) TcgGold else TcgBorder

        Box(
          modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .background(pillBg)
            .border(1.dp, pillBorder, RoundedCornerShape(14.dp))
            .clickable { viewModel.toggleTargetCard(card.id) }
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .testTag("target_chip_${card.id}")
        ) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            if (isTarget) {
              Icon(Icons.Filled.Check, contentDescription = null, tint = TcgGold, modifier = Modifier.size(14.dp))
              Spacer(modifier = Modifier.width(4.dp))
            }
            Text(
              text = card.id,
              fontSize = 11.sp,
              fontWeight = FontWeight.Bold,
              color = if (isTarget) TcgGold else Color(0xFF94A3B8)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
              text = card.name,
              fontSize = 12.sp,
              fontWeight = if (isTarget) FontWeight.Bold else FontWeight.Normal,
              color = if (isTarget) Color.White else Color(0xFFCBD5E1)
            )
            Spacer(modifier = Modifier.width(6.dp))
            RarityBadge(rarity = card.rarity)
          }
        }
      }
    }

    Spacer(modifier = Modifier.height(24.dp))

    // Minimal Game Tip Card
    Card(
      modifier = Modifier.fillMaxWidth(),
      shape = RoundedCornerShape(16.dp),
      colors = CardDefaults.cardColors(containerColor = TcgSurfaceElevated)
    ) {
      Row(
        modifier = Modifier.padding(16.dp),
        verticalAlignment = Alignment.Top
      ) {
        Icon(Icons.Filled.TipsAndUpdates, contentDescription = null, tint = TcgGold, modifier = Modifier.size(20.dp))
        Spacer(modifier = Modifier.width(12.dp))
        Column {
          Text(
            text = "Estrategia de Apertura",
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White
          )
          Spacer(modifier = Modifier.height(4.dp))
          Text(
            text = "Las cartas de 3 Estrellas (Inmersivas) y 2 Estrellas aparecen exclusivamente en el Slot 5. Si buscas una carta en particular, concentrar tus sobres en una sola expansión maximiza la probabilidad acumulada matemática.",
            fontSize = 12.sp,
            color = Color(0xFF94A3B8),
            lineHeight = 17.sp
          )
        }
      }
    }

    Spacer(modifier = Modifier.height(40.dp))
  }
}
