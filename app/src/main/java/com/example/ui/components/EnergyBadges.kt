package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** Names remain visible: color and symbols are supplementary, never the only cue. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EnergyBadges(energies: List<String>, modifier: Modifier = Modifier) {
  FlowRow(modifier, horizontalArrangement = Arrangement.spacedBy(10.dp),
    verticalArrangement = Arrangement.spacedBy(6.dp)) {
    energies.distinct().forEach { name ->
      val (color, icon) = when (name.lowercase()) {
        "agua" -> Color(0xFF66C8FF) to Icons.Filled.WaterDrop
        "fuego" -> Color(0xFFFF956B) to Icons.Filled.LocalFireDepartment
        "rayo" -> Color(0xFFFFD85A) to Icons.Filled.Bolt
        "planta" -> Color(0xFF93D879) to Icons.Filled.Grass
        "psíquico", "psiquico" -> Color(0xFFD5A2F5) to Icons.Filled.Visibility
        "lucha" -> Color(0xFFDDAC7A) to Icons.Filled.SportsMma
        "oscuridad" -> Color(0xFFA0B4CC) to Icons.Filled.DarkMode
        "metal" -> Color(0xFFCCD3DA) to Icons.Filled.Shield
        else -> Color(0xFFDFE3E8) to Icons.Filled.Star
      }
      Row(verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(Modifier.size(28.dp).clip(CircleShape).background(color),
          contentAlignment = Alignment.Center) {
          Icon(icon, null, tint = Color(0xFF182029), modifier = Modifier.size(20.dp))
        }
        Text(name, style = MaterialTheme.typography.labelMedium)
      }
    }
  }
}
