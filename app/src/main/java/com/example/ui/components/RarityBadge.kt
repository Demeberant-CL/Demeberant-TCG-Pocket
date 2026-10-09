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

@Composable
fun RarityBadge(
  rarity: CardRarity,
  modifier: Modifier = Modifier
) {
  val color = when (rarity) {
    CardRarity.CROWN -> MaterialTheme.colorScheme.tertiary
    CardRarity.ONE_STAR, CardRarity.TWO_STARS, CardRarity.THREE_STARS -> MaterialTheme.colorScheme.secondary
    else -> MaterialTheme.colorScheme.primary
  }
  val bgColor = MaterialTheme.colorScheme.surfaceContainerHigh
  val textColor = color
  val borderColor = color

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
