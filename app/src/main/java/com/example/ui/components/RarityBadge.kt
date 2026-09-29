package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.CardRarity
import com.example.ui.theme.PocketCyan
import com.example.ui.theme.PocketGold
import com.example.ui.theme.PocketPurple

@Composable
fun RarityBadge(
  rarity: CardRarity,
  modifier: Modifier = Modifier
) {
  val (bgColor, textColor, borderColor) = when (rarity) {
    CardRarity.CROWN -> Triple(Color(0xFFFEF3C7), PocketGold, Color(0xFFF59E0B))
    CardRarity.STAR_3 -> Triple(Color(0xFFF3E8FF), PocketPurple, Color(0xFFA855F7))
    CardRarity.STAR_2 -> Triple(Color(0xFFFFFBEB), Color(0xFFD97706), Color(0xFFFBBF24))
    CardRarity.STAR_1 -> Triple(Color(0xFFFEF9C3), Color(0xFFCA8A04), Color(0xFFFACC15))
    CardRarity.DIAMOND_4 -> Triple(Color(0xFFE0F2FE), Color(0xFF0284C7), PocketCyan)
    CardRarity.DIAMOND_3 -> Triple(Color(0xFFE0F7FA), Color(0xFF0097A7), Color(0xFF80DEEA))
    CardRarity.DIAMOND_2 -> Triple(Color(0xFFF1F5F9), Color(0xFF475569), Color(0xFF94A3B8))
    CardRarity.DIAMOND_1 -> Triple(Color(0xFFF8FAFC), Color(0xFF64748B), Color(0xFFCBD5E1))
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
