package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.CardRarity
import com.example.data.preferences.PlayerProfiles
import com.example.data.util.DeckCodec
import com.example.ui.components.AvatarPickerDialog
import com.example.ui.components.ProfileAvatar
import com.example.ui.viewmodel.AdvancedViewModel
import com.example.ui.viewmodel.TcgViewModel
import java.text.NumberFormat
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HomeScreen(main: TcgViewModel, advanced: AdvancedViewModel, modifier: Modifier = Modifier,
  onDecks: () -> Unit, onEditor: () -> Unit, onMeta: () -> Unit,
  onSync: () -> Unit = {}, onCollection: () -> Unit = {}) {
  val context = LocalContext.current
  val profileFlow = remember(context) { PlayerProfiles.observe(context.applicationContext) }
  val player by profileFlow.collectAsStateWithLifecycle(initialValue = PlayerProfiles.read(context))
  val openSavedDeck = com.example.ui.components.rememberSavedDeckOpener(main, onEditor)
  val inventory by main.inventoryList.collectAsStateWithLifecycle()
  val decks by main.savedDecks.collectAsStateWithLifecycle()
  val draft by main.generatedDeck.collectAsStateWithLifecycle()
  val prefs by main.userPreferences.collectAsStateWithLifecycle()
  val message by main.csvStatusMessage.collectAsStateWithLifecycle()
  var chooseAvatar by remember { mutableStateOf(false) }
  var editProfile by remember { mutableStateOf(false) }
  val numbers = remember { NumberFormat.getIntegerInstance(Locale.forLanguageTag("es-CL")) }
  val owned = remember(inventory) { inventory.count { it.ownedCount > 0 } }
  val catalogIds = remember(inventory) { inventory.mapTo(hashSetOf()) { it.card.id } }
  val copies = remember(inventory) { inventory.sumOf { it.ownedCount.toLong() } }
  val progress = if (inventory.isEmpty()) 0f else owned.toFloat() / inventory.size
  val rarityGroups = remember(inventory) {
    listOf(
      Triple("♦", "Diamantes", CardRarity.entries.filter { it.name.endsWith("DIAMOND") || it.name.endsWith("DIAMONDS") }),
      Triple("★", "Estrellas", listOf(CardRarity.ONE_STAR, CardRarity.TWO_STARS, CardRarity.THREE_STARS)),
      Triple("♛", "Coronas", listOf(CardRarity.CROWN)),
      Triple("✷", "Brillos", listOf(CardRarity.SHINY_ONE, CardRarity.SHINY_TWO))
    ).map { (symbol, name, rarities) -> Triple(symbol, name, inventory.filter { it.card.rarity in rarities && it.ownedCount > 0 }.size) }
  }
  val updated = remember(player.syncedAt) {
    if (player.syncedAt == 0L) "Fecha de última sincronización no disponible" else
      "Última sincronización · " + DateTimeFormatter.ofPattern("dd/MM · HH:mm")
        .withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(player.syncedAt))
  }
  LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
    item {
      Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        ProfileAvatar(prefs.avatarId, Modifier.size(44.dp).clip(CircleShape).clickable { chooseAvatar = true }, "Cambiar avatar")
        Column(Modifier.weight(1f)) {
          Text(player.nickname.ifBlank { "Completar perfil" }, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
          player.level?.let { level -> Text("Nivel $level", style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        IconButton(onClick = { editProfile = true }) { Icon(Icons.Default.Edit, "Editar perfil del jugador") }
      }
      if (player.friendId.matches(Regex("[0-9]{16}"))) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Text("Friend ID: " + player.friendId.chunked(4).joinToString(" "), modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
          IconButton(onClick = {
            (context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager)
              .setPrimaryClip(android.content.ClipData.newPlainText("Friend ID", player.friendId))
            android.widget.Toast.makeText(context, "Friend ID copiado", android.widget.Toast.LENGTH_SHORT).show()
          }) { Icon(Icons.Default.ContentCopy, "Copiar Friend ID", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp)) }
        }
      } else Text("Conecta tu cuenta para mostrar el Friend ID", style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    item {
      OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.background(Brush.linearGradient(listOf(MaterialTheme.colorScheme.primaryContainer.copy(alpha = .35f),
          MaterialTheme.colorScheme.surface))).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
          Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            Column(Modifier.weight(1f)) {
              Text(numbers.format(owned), style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
              Text("cartas", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Column(Modifier.weight(1f)) {
              Text(numbers.format(copies), style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
              Text("copias", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
          }
          Text("${(progress * 100).toInt()} % del catálogo · ${numbers.format(inventory.size)} cartas", style = MaterialTheme.typography.bodySmall)
          LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth().height(4.dp).clip(CircleShape))
          Button(onClick = onSync, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
            Icon(Icons.Default.Sync, null); Spacer(Modifier.width(8.dp)); Text("Sincronizar colección")
          }
          Text(updated, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
      }
    }
    message?.let { status -> item {
      Card(Modifier.fillMaxWidth()) { Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(status, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
        TextButton(onClick = main::clearCsvStatusMessage) { Text("Cerrar") }
      } }
    } }
    if (player.syncedAt > 0L) item {
      OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
          Text("Últimas novedades", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
          if (player.syncedAt > 0L) FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            DexMetric("+${numbers.format(player.newCards)}", "cartas nuevas", false)
            DexMetric("+${numbers.format(player.addedCopies)}", "copias añadidas", true)
          } else Text("Las novedades aparecerán después de sincronizar.", color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall)
        }
      }
    }
    item {
      OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
          Text("Resumen por rareza", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
          rarityGroups.chunked(2).forEach { pair ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              pair.forEachIndexed { index, (_, name, count) ->
                Surface(modifier = Modifier.weight(1f), shape = MaterialTheme.shapes.small,
                  color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                  Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Column(Modifier.weight(1f)) {
                      Text(name, style = MaterialTheme.typography.labelMedium,
                        color = if (index == 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary)
                      Text(numbers.format(count), style = MaterialTheme.typography.titleMedium)
                    }
                  }
                }
              }
            }
          }
          TextButton(onClick = onCollection) { Text("Ver colección") }
        }
      }
    }
    item {
      Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text("Mazos recientes", Modifier.weight(1f), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        TextButton(onClick = onDecks) { Text("Ver todos") }
      }
      if (decks.isEmpty()) {
        Text("Todavía no hay mazos guardados.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedButton(onClick = onEditor) { Text("Crear mazo") }
      }
    }
    decks.take(4).chunked(2).forEach { pair -> item(key = "recent_${pair.first().id}") {
      BoxWithConstraints(Modifier.fillMaxWidth()) {
        val compact = maxWidth >= 330.dp && androidx.compose.ui.platform.LocalDensity.current.fontScale < 1.3f
        if (compact) Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
          pair.forEach { saved -> RecentDeckTile(saved, catalogIds, Modifier.weight(1f)) { openSavedDeck(saved) } }
          if (pair.size == 1) Spacer(Modifier.weight(1f))
        } else Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
          pair.forEach { saved -> RecentDeckTile(saved, catalogIds, Modifier.fillMaxWidth()) { openSavedDeck(saved) } }
        }
      }
    } }
    draft?.takeIf { it.cards.isNotEmpty() }?.let { deck -> item {
      OutlinedCard(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Continuar edición", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        Text(deck.name, style = MaterialTheme.typography.titleMedium)
        Text("${deck.totalCardCount}/20 cartas", style = MaterialTheme.typography.bodySmall)
        Button(onClick = onEditor) { Text("Continuar editando") }
      } }
    } }
    item { TextButton(onClick = onMeta) { Text("Meta de torneos →") } }
  }
  if (chooseAvatar) AvatarPickerDialog(prefs.avatarId, onSave = { main.setProfileAvatar(it); chooseAvatar = false }, onClose = { chooseAvatar = false })
  if (editProfile) {
    var nickname by rememberSaveable(player.friendId) { mutableStateOf(player.nickname) }
    var level by rememberSaveable(player.friendId) { mutableStateOf(player.level?.toString().orEmpty()) }
    val valid = nickname.trim().length in 1..60 && nickname.none { it.isISOControl() } &&
      (level.isBlank() || level.toIntOrNull()?.let { it in 1..999 } == true)
    AlertDialog(onDismissRequest = { editProfile = false }, title = { Text("Perfil del jugador") },
      text = { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Puedes completar tu perfil si Pokémon Zone todavía no muestra estos datos.", style = MaterialTheme.typography.bodySmall)
        OutlinedTextField(nickname, { nickname = it.take(60) }, label = { Text("Nick") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(level, { level = it.take(3) }, label = { Text("Nivel") }, singleLine = true,
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
      } }, confirmButton = { TextButton(enabled = valid, onClick = { PlayerProfiles.edit(context, nickname, level.toIntOrNull()); editProfile = false }) { Text("Guardar") } },
      dismissButton = { TextButton(onClick = { editProfile = false }) { Text("Cancelar") } })
  }
}

@Composable
private fun RecentDeckTile(saved: com.example.data.local.SavedDeckEntity, catalogIds: Set<String>,
  modifier: Modifier, onOpen: () -> Unit) {
  val refs = remember(saved.cardListSerialized) { runCatching { DeckCodec.references(saved.cardListSerialized) }.getOrNull() }
  val cover = remember(saved.cardListSerialized) { runCatching { DeckCodec.cover(saved.cardListSerialized) }.getOrNull() }
  val canOpen = refs != null && refs.all { it.first in catalogIds }
  OutlinedCard(onClick = onOpen, enabled = canOpen, modifier = modifier) {
    DeckCover(cover, saved.name, Modifier.fillMaxWidth().height(104.dp))
    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
      Text(saved.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold,
        minLines = 2, maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
      Text("${saved.totalCards}/20 cartas", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
      Text(if (canOpen) "Abrir →" else "Por revisar", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
    }
  }
}

@Composable
private fun DexMetric(value: String, label: String, violet: Boolean) {
  Surface(shape = RoundedCornerShape(16.dp), color = if (violet) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.primaryContainer) {
    Column(Modifier.widthIn(min = 136.dp).padding(16.dp)) {
      Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold,
        color = if (violet) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onPrimaryContainer)
      Text(label, style = MaterialTheme.typography.bodySmall)
    }
  }
}

@Composable
fun DeckCover(id: String?, name: String, modifier: Modifier = Modifier) {
  Box(modifier.background(Brush.linearGradient(listOf(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.secondaryContainer))),
    contentAlignment = Alignment.Center) {
    if (id != null) com.example.ui.components.PocketCardImage(id, name, highResolution = true, modifier = Modifier.fillMaxSize(),
      contentScale = androidx.compose.ui.layout.ContentScale.Fit, alignment = Alignment.Center,
      unavailable = { Text(name, Modifier.padding(16.dp), style = MaterialTheme.typography.titleMedium) })
    else Text(name, Modifier.padding(16.dp), style = MaterialTheme.typography.titleMedium)
  }
}
