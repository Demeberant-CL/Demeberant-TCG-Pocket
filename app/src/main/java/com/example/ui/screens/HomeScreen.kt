package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Style
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.components.AvatarPickerDialog
import com.example.ui.components.AdaptiveActionRow
import com.example.ui.components.ProfileAvatar
import com.example.ui.viewmodel.AdvancedViewModel
import com.example.ui.viewmodel.TcgViewModel
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@Composable
fun HomeScreen(main: TcgViewModel, advanced: AdvancedViewModel, modifier: Modifier = Modifier,
  onCollection: () -> Unit, onDecks: () -> Unit, onEditor: () -> Unit,
  onAi: () -> Unit, onMeta: () -> Unit, onGuide: () -> Unit) {
  val inventory by main.inventoryList.collectAsStateWithLifecycle()
  val decks by main.savedDecks.collectAsStateWithLifecycle()
  val draft by main.generatedDeck.collectAsStateWithLifecycle()
  val prefs by main.userPreferences.collectAsStateWithLifecycle()
  val connection by advanced.connection.collectAsStateWithLifecycle()
  val ready by advanced.connectionReady.collectAsStateWithLifecycle()
  val snapshot by advanced.metaSnapshot.collectAsStateWithLifecycle()
  val message by main.csvStatusMessage.collectAsStateWithLifecycle()
  var chooseAvatar by remember { mutableStateOf(false) }
  val owned = remember(inventory) { inventory.count { it.ownedCount > 0 } }
  val copies = remember(inventory) { inventory.sumOf { it.ownedCount } }
  val progress = if (inventory.isEmpty()) 0f else owned.toFloat() / inventory.size
  LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
    item {
      Column(Modifier.fillMaxWidth().background(Brush.horizontalGradient(listOf(
        MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.secondaryContainer)),
        RoundedCornerShape(24.dp)).padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
          ProfileAvatar(prefs.avatarId, Modifier.size(60.dp).clip(CircleShape).clickable { chooseAvatar = true }, "Cambiar avatar")
          Column(Modifier.weight(1f)) {
            Text("Tu espacio Pocket", style = MaterialTheme.typography.headlineSmall)
            Text("Colección · Mazos · IA", style = MaterialTheme.typography.bodyMedium)
          }
        }
        Text(if (inventory.isEmpty()) "Preparando tu colección…" else "$owned / ${inventory.size} cartas · $copies copias",
          style = MaterialTheme.typography.titleSmall)
        LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
        Text("${decks.size} mazos guardados · ${(progress * 100).toInt()}% del catálogo registrado",
          style = MaterialTheme.typography.bodySmall)
      }
    }
    message?.let { status -> item {
      Card(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
          Text(status, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
          TextButton(onClick = main::clearCsvStatusMessage) { Text("Cerrar") }
        }
      }
    } }
    item {
      Text("Accesos rápidos", style = MaterialTheme.typography.titleMedium)
      Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        AdaptiveActionRow { cardModifier ->
          HomeAction("Colección", "$owned cartas registradas", Icons.Filled.Collections, cardModifier, onCollection)
          HomeAction("Mis mazos", "${decks.size} guardados", Icons.Filled.Style, cardModifier, onDecks)
        }
        AdaptiveActionRow { cardModifier ->
          HomeAction("Crear / editar", "Prepara tu baraja", Icons.Filled.Edit, cardModifier, onEditor)
          HomeAction("Mi IA", if (ready && connection.apiKey.isNotBlank()) connection.provider.label else "Configura tu conexión",
            Icons.Filled.AutoAwesome, cardModifier, onAi)
        }
      }
    }
    draft?.takeIf { it.cards.isNotEmpty() }?.let { deck -> item {
      ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
          Text("Mazo abierto", style = MaterialTheme.typography.labelLarge)
          Text(deck.name, style = MaterialTheme.typography.titleMedium)
          Text("${deck.totalCardCount}/20 cartas · " + deck.energyTypes.joinToString().ifBlank { "Energías por revisar" }, style = MaterialTheme.typography.bodySmall)
          Button(onClick = onEditor) { Text("Continuar editando") }
        }
      }
    } }
    item {
      ElevatedCard(onClick = onAi, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
          Icon(Icons.Filled.AutoAwesome, null, tint = MaterialTheme.colorScheme.primary)
          Column(Modifier.weight(1f)) {
            Text("Tu asistente", style = MaterialTheme.typography.titleMedium)
            Text(if (!ready) "Cargando conexión guardada…" else if (connection.apiKey.isBlank()) "Conecta Gemini, OpenAI u otra API compatible"
              else "${connection.label}\n${connection.provider.label} · ${connection.model}", style = MaterialTheme.typography.bodySmall)
          }
        }
      }
    }
    item {
      ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
          Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Insights, null, tint = MaterialTheme.colorScheme.primary)
            Text("Meta de torneos", style = MaterialTheme.typography.titleMedium)
          }
          snapshot?.let { data ->
            val date = remember(data.updated) { runCatching {
              DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT).withZone(ZoneId.systemDefault()).format(Instant.parse(data.updated))
            }.getOrDefault(data.updated) }
            Text("Limitless · $date\n${data.tournaments} torneos · ${data.players} listas", style = MaterialTheme.typography.bodySmall)
            Text("Muestra guardada. Consulta la fecha antes de usarla.", style = MaterialTheme.typography.bodySmall)
          } ?: Text("Consulta resultados públicos y mazos de ejemplo.", style = MaterialTheme.typography.bodySmall)
          OutlinedButton(onClick = onMeta) { Text("Ver meta y actualizar") }
        }
      }
    }
    item {
      ElevatedCard(onClick = onGuide, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
          Icon(Icons.AutoMirrored.Filled.MenuBook, null, tint = MaterialTheme.colorScheme.primary)
          Column(Modifier.weight(1f)) {
            Text("Guía y tutoriales", style = MaterialTheme.typography.titleMedium)
            Text("Aprende a usar cada función", style = MaterialTheme.typography.bodySmall)
          }
        }
      }
    }
  }
  if (chooseAvatar) AvatarPickerDialog(prefs.avatarId, onSave = { main.setProfileAvatar(it); chooseAvatar = false }, onClose = { chooseAvatar = false })
}

@Composable
private fun HomeAction(title: String, description: String, icon: ImageVector, modifier: Modifier, onClick: () -> Unit) {
  ElevatedCard(onClick = onClick, modifier = modifier, shape = RoundedCornerShape(24.dp)) {
    Column(Modifier.fillMaxWidth().padding(16.dp).heightIn(min = 100.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
      Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(32.dp))
      Text(title, style = MaterialTheme.typography.titleMedium)
      Text(description, style = MaterialTheme.typography.bodySmall)
    }
  }
}
