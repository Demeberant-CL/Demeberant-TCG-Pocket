package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.components.AvatarPickerDialog
import com.example.data.util.DeckCodec
import com.example.ui.components.ProfileAvatar
import com.example.ui.viewmodel.AdvancedViewModel
import com.example.ui.viewmodel.TcgViewModel
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@Composable
fun HomeScreen(main: TcgViewModel, advanced: AdvancedViewModel, modifier: Modifier = Modifier,
  onDecks: () -> Unit, onEditor: () -> Unit, onMeta: () -> Unit) {
  val inventory by main.inventoryList.collectAsStateWithLifecycle()
  val decks by main.savedDecks.collectAsStateWithLifecycle()
  val draft by main.generatedDeck.collectAsStateWithLifecycle()
  val prefs by main.userPreferences.collectAsStateWithLifecycle()
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
        RoundedCornerShape(20.dp)).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
      Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text("Mazos recientes", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
        TextButton(onClick = onDecks) { Text("Ver todos") }
      }
      if (decks.isEmpty()) {
        Text("Todavía no hay mazos guardados.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedButton(onClick = onEditor) { Text("Abrir editor") }
      }
    }
    decks.take(3).forEach { saved -> item(key = "recent_${saved.id}") {
      val refs = remember(saved.cardListSerialized) {
        runCatching { DeckCodec.references(saved.cardListSerialized) }.getOrNull()
      }
      val canOpen = refs != null && refs.all { ref -> inventory.any { it.card.id == ref.first } }
      ElevatedCard(onClick = { main.loadSavedDeck(saved); onEditor() }, enabled = canOpen,
        modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp),
          verticalAlignment = Alignment.CenterVertically) {
          if (androidx.compose.ui.platform.LocalDensity.current.fontScale < 1.3f) {
            refs?.firstOrNull()?.let { (id, _) -> DeckThumbnail(id, "Portada de ${saved.name}") }
          }
          Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(saved.name, style = MaterialTheme.typography.titleMedium)
            Text("${saved.totalCards}/20 cartas", style = MaterialTheme.typography.bodyMedium)
            Text(if (canOpen) "Abrir mazo" else "Lista no disponible en el catálogo actual",
              style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
          }
        }
      }
    } }
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

  }
  if (chooseAvatar) AvatarPickerDialog(prefs.avatarId, onSave = { main.setProfileAvatar(it); chooseAvatar = false }, onClose = { chooseAvatar = false })
}
