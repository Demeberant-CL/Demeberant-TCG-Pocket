package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.PokemonCard
import com.example.ui.theme.PocketBackground
import com.example.ui.theme.PocketBluePrimary
import com.example.ui.theme.PocketBorder
import com.example.ui.theme.PocketGold
import com.example.ui.theme.PocketRed
import com.example.ui.theme.PocketSurface
import com.example.ui.theme.PocketTextMuted
import com.example.ui.theme.PocketTextPrimary
import com.example.ui.theme.PocketTextSecondary

@Composable
fun CardItemView(
  card: PokemonCard,
  ownedCount: Int,
  isWishlist: Boolean,
  onToggleWishlist: () -> Unit,
  modifier: Modifier = Modifier,
  imageLanguage: String = "es",
  onClick: () -> Unit = {}
) {
  val isOwned = ownedCount > 0

  val typeColor = when (card.type.lowercase()) {
    "planta" -> Color(0xFF10B981)
    "fuego" -> Color(0xFFEF4444)
    "agua" -> Color(0xFF0284C7)
    "rayo" -> Color(0xFFF59E0B)
    "psíquico" -> Color(0xFF8B5CF6)
    "lucha" -> Color(0xFFD97706)
    "oscuridad" -> Color(0xFF475569)
    "metal" -> Color(0xFF64748B)
    "dragón" -> Color(0xFFF97316)
    else -> Color(0xFF94A3B8)
  }

  Card(
    modifier = modifier.clickable(onClick = onClick)
      .fillMaxWidth()
      .aspectRatio(0.714f)
      .shadow(if (isOwned) 3.dp else 1.dp, shape = RoundedCornerShape(12.dp), clip = false)
      .testTag("card_item_${card.id}"),
    shape = RoundedCornerShape(12.dp),
    colors = CardDefaults.cardColors(
      containerColor = if (isOwned) PocketSurface else MaterialTheme.colorScheme.surfaceContainerHigh
    ),
    border = CardDefaults.outlinedCardBorder().copy(
      brush = androidx.compose.ui.graphics.SolidColor(
        if (isOwned) PocketBorder else MaterialTheme.colorScheme.outlineVariant
      )
    )
  ) {
    Box(modifier = Modifier.fillMaxSize()) {
      PocketCardImage(id = card.id, name = card.name, language = imageLanguage,
        contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize(), unavailable = {
        Column(
          modifier = Modifier
            .fillMaxSize()
            .alpha(1f)
            .background(if (isOwned) MaterialTheme.colorScheme.surfaceContainerLow else MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(6.dp),
          verticalArrangement = Arrangement.SpaceBetween
        ) {
          // Top Row: ID & Type Dot
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Text(
              text = card.id,
              fontSize = 8.sp,
              fontWeight = FontWeight.Bold,
              color = PocketTextSecondary
            )
            Box(
              modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(if (isOwned) typeColor else Color(0xFF94A3B8))
            )
          }

          // Center: Name & HP
          Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth()
          ) {
            Text(
              text = card.name,
              fontSize = 11.sp,
              fontWeight = FontWeight.Bold,
              textAlign = TextAlign.Center,
              color = PocketTextPrimary,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis
            )
            if (card.hp > 0) {
              Text(
                text = "${card.hp} PS",
                fontSize = 9.sp,
                fontWeight = FontWeight.Medium,
                color = PocketTextSecondary
              )
            }
          }

          // Bottom: Rarity
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Text(
              text = card.rarity.symbol,
              fontSize = 9.sp,
              fontWeight = FontWeight.Bold,
              color = PocketGold
            )
            Text(
              text = "Imagen no disponible",
              fontSize = 7.sp,
              color = PocketTextMuted
            )
          }
        }
      })

      // Floating Badge Top-Right: Quantity (x1, x2, x3...) or (x0)
      Box(
        modifier = Modifier
          .align(Alignment.TopEnd)
          .padding(4.dp)
          .clip(RoundedCornerShape(6.dp))
          .background(if (isOwned) Color(0xFFFEF3C7) else Color(0xFF0F172A).copy(alpha = 0.8f))
          .border(
            1.dp,
            if (isOwned) PocketGold else Color.White.copy(alpha = 0.3f),
            RoundedCornerShape(6.dp)
          )
          .padding(horizontal = 5.dp, vertical = 1.dp)
      ) {
        Text(
          text = if (isOwned) "x$ownedCount" else "Falta",
          fontSize = 10.sp,
          fontWeight = FontWeight.Black,
          color = if (isOwned) Color(0xFFB45309) else Color.White
        )
      }

      // Wishlist Heart Icon Top-Left
      Box(
        modifier = Modifier
          .align(Alignment.TopStart)
          .padding(4.dp)
          .size(22.dp)
          .clip(CircleShape)
          .background(Color.White.copy(alpha = 0.85f))
          .clickable { onToggleWishlist() }
          .testTag("wishlist_btn_${card.id}"),
        contentAlignment = Alignment.Center
      ) {
        Icon(
          imageVector = if (isWishlist) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
          contentDescription = if (isWishlist) "Quitar de deseadas" else "Añadir a deseadas",
          tint = if (isWishlist) PocketRed else PocketTextMuted,
          modifier = Modifier.size(13.dp)
        )
      }

    }
  }
}
