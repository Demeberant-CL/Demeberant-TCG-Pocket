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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import com.example.data.model.BoosterPack
import com.example.ui.components.CardItemView
import com.example.ui.components.PackProbabilitiesSheet
import com.example.ui.theme.PocketBackground
import com.example.ui.theme.PocketBluePrimary
import com.example.ui.theme.PocketBorder
import com.example.ui.theme.PocketGold
import com.example.ui.theme.PocketSurface
import com.example.ui.theme.PocketTextPrimary
import com.example.ui.theme.PocketTextSecondary
import com.example.ui.viewmodel.TcgViewModel

@Composable
fun PackSimulatorScreen(
  viewModel: TcgViewModel,
  modifier: Modifier = Modifier
) {
  val openedCards by viewModel.openedPackCards.collectAsStateWithLifecycle()
  val isOpening by viewModel.isOpeningPack.collectAsStateWithLifecycle()
  val currentPack by viewModel.currentPackType.collectAsStateWithLifecycle()
  var showProbSheet by remember { mutableStateOf(false) }

  LazyColumn(
    modifier = modifier
      .fillMaxSize()
      .background(PocketBackground)
      .padding(14.dp),
    verticalArrangement = Arrangement.spacedBy(14.dp)
  ) {
    // Pack Selector & Open Action Card
    item {
      Card(
        modifier = Modifier
          .fillMaxWidth()
          .shadow(2.dp, RoundedCornerShape(16.dp), clip = false)
          .testTag("pack_simulator_card"),
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
              text = "Apertura de Sobres Oficial (5 Cartas)",
              style = MaterialTheme.typography.titleMedium,
              fontWeight = FontWeight.Bold,
              color = PocketTextPrimary
            )
            Row(
              modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .clickable { showProbSheet = true }
                .padding(4.dp),
              verticalAlignment = Alignment.CenterVertically
            ) {
              Icon(Icons.Filled.Info, contentDescription = "Ver probabilidades", tint = PocketBluePrimary, modifier = Modifier.size(16.dp))
              Text(" Probabilidades", fontSize = 11.sp, color = PocketBluePrimary, fontWeight = FontWeight.Bold)
            }
          }

          Spacer(modifier = Modifier.height(12.dp))

          // 3 Packs selector
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
          ) {
            BoosterPack.entries.forEach { pack ->
              val isSelected = currentPack == pack
              val packColor = when (pack) {
                BoosterPack.CHARIZARD -> Color(0xFFEA580C)
                BoosterPack.MEWTWO -> Color(0xFF9333EA)
                BoosterPack.PIKACHU -> Color(0xFFCA8A04)
              }

              Box(
                modifier = Modifier
                  .weight(1f)
                  .clip(RoundedCornerShape(12.dp))
                  .background(if (isSelected) packColor.copy(alpha = 0.12f) else Color(0xFFF8FAFC))
                  .border(if (isSelected) 2.dp else 1.dp, if (isSelected) packColor else PocketBorder, RoundedCornerShape(12.dp))
                  .clickable { viewModel.selectPackToOpen(pack) }
                  .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center
              ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                  Text(
                    text = pack.displayName.replace("Sobre ", ""),
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    color = if (isSelected) packColor else PocketTextPrimary
                  )
                  Text(
                    text = "Genética A1",
                    fontSize = 9.sp,
                    color = PocketTextSecondary
                  )
                }
              }
            }
          }

          Spacer(modifier = Modifier.height(14.dp))

          Button(
            onClick = { viewModel.openPack(currentPack) },
            modifier = Modifier
              .fillMaxWidth()
              .height(44.dp)
              .testTag("open_pack_btn"),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = PocketBluePrimary),
            enabled = !isOpening
          ) {
            if (isOpening) {
              CircularProgressIndicator(color = Color.White, modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
            } else {
              Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
              Text(" Abrir ${currentPack.displayName}", fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
          }
        }
      }
    }

    // Opened Cards Section
    if (openedCards.isNotEmpty()) {
      item {
        Text(
          text = "¡Resultado del Sobre! (Agregadas al Inventario)",
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.Black,
          color = PocketTextPrimary
        )
      }

      items(openedCards) { card ->
        CardItemView(
          card = card,
          ownedCount = 1,
          isWishlist = false,
          onToggleWishlist = { viewModel.toggleWishlist(card.id) }
        )
      }
    }
  }

  if (showProbSheet) {
    PackProbabilitiesSheet(onDismiss = { showProbSheet = false })
  }
}
