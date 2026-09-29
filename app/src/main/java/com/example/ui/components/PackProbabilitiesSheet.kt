package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.PocketBluePrimary
import com.example.ui.theme.PocketGold
import com.example.ui.theme.PocketSurface
import com.example.ui.theme.PocketTextPrimary
import com.example.ui.theme.PocketTextSecondary
import kotlin.math.pow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PackProbabilitiesSheet(
  onDismiss: () -> Unit
) {
  val sheetState = rememberModalBottomSheetState()
  var packsCount by remember { mutableFloatStateOf(10f) }

  ModalBottomSheet(
    onDismissRequest = onDismiss,
    sheetState = sheetState,
    containerColor = PocketSurface
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 20.dp, vertical = 8.dp)
        .padding(bottom = 32.dp)
    ) {
      Text(
        text = "Probabilidades Reales de Sobres",
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Black,
        color = PocketTextPrimary
      )
      Text(
        text = "Exactamente 5 cartas por sobre de expansión (Genética A1)",
        style = MaterialTheme.typography.bodySmall,
        color = PocketTextSecondary
      )

      Spacer(modifier = Modifier.height(16.dp))

      Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FAFC))
      ) {
        Column(modifier = Modifier.padding(12.dp)) {
          Text("Estructura de Slots Oficial:", fontWeight = FontWeight.Bold, fontSize = 12.sp)
          Spacer(modifier = Modifier.height(6.dp))
          Text("• Slots 1 a 3: Cartas de 1 diamante (♦) (100% de probabilidad)", fontSize = 11.sp, color = PocketTextSecondary)
          Text("• Slot 4: 2 diamantes (90%), 3 diamantes (5%), 4 diamantes (4.16%), 1 estrella (0.84%)", fontSize = 11.sp, color = PocketTextSecondary)
          Text("• Slot 5: 2 diamantes (60%), 3 diamantes (20%), 4 diamantes (6.86%), 1 estrella (4.342%), 2 estrellas (1.714%), 3 estrellas inmersivas (0.222%), Corona (0.04%)", fontSize = 11.sp, color = PocketTextSecondary)
        }
      }

      Spacer(modifier = Modifier.height(16.dp))

      Text(
        text = "Simulador de Probabilidad Acumulada",
        fontWeight = FontWeight.Bold,
        fontSize = 13.sp,
        color = PocketTextPrimary
      )
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Text("Sobres a abrir:", fontSize = 12.sp, color = PocketTextSecondary)
        Text("${packsCount.toInt()} sobres", fontWeight = FontWeight.Bold, color = PocketBluePrimary)
      }

      Slider(
        value = packsCount,
        onValueChange = { packsCount = it },
        valueRange = 1f..100f,
        steps = 99,
        colors = SliderDefaults.colors(
          thumbColor = PocketBluePrimary,
          activeTrackColor = PocketBluePrimary
        )
      )

      val n = packsCount.toInt()
      val pCrown = 0.0004
      val cumCrown = (1.0 - (1.0 - pCrown).pow(n)) * 100.0

      val pEx4 = 0.0686 + 0.0416
      val cumEx = (1.0 - (1.0 - pEx4).pow(n)) * 100.0

      val pImmersive = 0.00222
      val cumImmersive = (1.0 - (1.0 - pImmersive).pow(n)) * 100.0

      Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ProbStatBox(
          title = "Carta Corona (♛)",
          perPack = "0.04%",
          cumulative = String.format("%.2f%%", cumCrown),
          modifier = Modifier.weight(1f)
        )
        ProbStatBox(
          title = "Inmersiva (★★★)",
          perPack = "0.22%",
          cumulative = String.format("%.2f%%", cumImmersive),
          modifier = Modifier.weight(1f)
        )
        ProbStatBox(
          title = "4 Diamantes (ex)",
          perPack = "11.02%",
          cumulative = String.format("%.2f%%", cumEx),
          modifier = Modifier.weight(1f)
        )
      }
    }
  }
}

@Composable
private fun ProbStatBox(
  title: String,
  perPack: String,
  cumulative: String,
  modifier: Modifier = Modifier
) {
  Column(
    modifier = modifier
      .clip(RoundedCornerShape(10.dp))
      .background(Color(0xFFF1F5F9))
      .padding(8.dp)
  ) {
    Text(title, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = PocketTextPrimary, maxLines = 1)
    Spacer(modifier = Modifier.height(4.dp))
    Text("Por sobre: $perPack", fontSize = 9.sp, color = PocketTextSecondary)
    Spacer(modifier = Modifier.height(2.dp))
    Text(cumulative, fontSize = 14.sp, fontWeight = FontWeight.Black, color = PocketGold)
    Text("acumulada", fontSize = 8.sp, color = PocketTextSecondary)
  }
}
