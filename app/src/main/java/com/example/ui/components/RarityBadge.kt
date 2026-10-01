package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.CardRarity
import com.example.ui.theme.PocketBluePrimary
import com.example.ui.theme.PocketGold
import com.example.ui.theme.PocketBlueDark

@Composable
fun RarityBadge(
  rarity: CardRarity,
  modifier: Modifier = Modifier
) {
  val (bgColor, textColor, borderColor) = when (rarity) {
    CardRarity.SHINY_ONE, CardRarity.SHINY_TWO -> Triple(Color(0xFFE0F2FE), Color(0xFF0284C7), PocketBluePrimary)
    CardRarity.CROWN -> Triple(Color(0xFFFEF3C7), PocketGold, Color(0xFFF59E0B))
    CardRarity.THREE_STARS -> Triple(Color(0xFFF3E8FF), PocketBlueDark, Color(0xFFA855F7))
    CardRarity.TWO_STARS -> Triple(Color(0xFFFFFBEB), Color(0xFFD97706), Color(0xFFFBBF24))
    CardRarity.ONE_STAR -> Triple(Color(0xFFFEF9C3), Color(0xFFCA8A04), Color(0xFFFACC15))
    CardRarity.FOUR_DIAMONDS -> Triple(Color(0xFFE0F2FE), Color(0xFF0284C7), PocketBluePrimary)
    CardRarity.THREE_DIAMONDS -> Triple(Color(0xFFE0F7FA), Color(0xFF0097A7), Color(0xFF80DEEA))
    CardRarity.TWO_DIAMONDS -> Triple(MaterialTheme.colorScheme.surfaceContainer, Color(0xFF475569), Color(0xFF94A3B8))
    CardRarity.ONE_DIAMOND -> Triple(MaterialTheme.colorScheme.surfaceContainerLow, Color(0xFF64748B), MaterialTheme.colorScheme.outlineVariant)
  }

  Box(
    modifier = modifier
      .clip(RoundedCornerShape(6.dp))
      .background(bgColor)
      .border(1.dp, borderColor.copy(alpha = 0.7f), RoundedCornerShape(6.dp))
      .padding(horizontal = 6.dp, vertical = 2.dp),
    contentAlignment = Alignment.Center
  ) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      Text(
        text = rarity.symbol,
        fontSize = 11.sp,
        fontWeight = FontWeight.Black,
        color = textColor
      )
    }
  }
}
