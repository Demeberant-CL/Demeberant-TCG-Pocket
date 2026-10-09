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
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.BorderStroke
import com.example.ui.components.DexPanel
import com.example.ui.components.DexCardFan
import com.example.ui.components.DexRarityIcon
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
        Column(Modifier.weight(1f)) {
          Text(player.nickname.ifBlank { "Completar perfil" }, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.ExtraBold, fontSize = 30.sp)
          player.level?.let { level -> Text("Nivel $level", style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        IconButton(onClick = { editProfile = true }) { Icon(Icons.Default.Edit, "Editar perfil del jugador", modifier = Modifier.size(20.dp)) }
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
      DexPanel(Modifier.fillMaxWidth()) {
        Column(Modifier.background(Brush.linearGradient(listOf(MaterialTheme.colorScheme.primaryContainer.copy(alpha = .35f),
          MaterialTheme.colorScheme.surface))).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
          Box(Modifier.fillMaxWidth()) {
            DexCardFan(Modifier.align(Alignment.CenterEnd).width(130.dp).height(64.dp))
            Row(Modifier.fillMaxWidth().padding(end = 72.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
              Column(Modifier.weight(1f)) {
                Text(numbers.format(owned), style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.ExtraBold, fontSize = 28.sp)
                Text("cartas", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
              }
              VerticalDivider(Modifier.height(48.dp), color = MaterialTheme.colorScheme.outline)
              Column(Modifier.weight(1f)) {
                Text(numbers.format(copies), style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.ExtraBold, fontSize = 28.sp)
                Text("copias", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
              }
            }
          }
          Text("${(progress * 100).toInt()} % del catálogo · ${numbers.format(inventory.size)} cartas", style = MaterialTheme.typography.bodySmall)
          LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth().height(7.dp).clip(CircleShape))
          Surface(onClick = onSync, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(28.dp), color = Color.Transparent) {
            Row(Modifier.background(Brush.verticalGradient(listOf(Color(0xFF42EBF5), MaterialTheme.colorScheme.primary)))
              .heightIn(min = 48.dp).padding(horizontal = 12.dp, vertical = 10.dp),
              horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
              Icon(Icons.Default.Sync, null, tint = MaterialTheme.colorScheme.onPrimary)
              Spacer(Modifier.width(8.dp))
              Text("Sincronizar colección", color = MaterialTheme.colorScheme.onPrimary, style = MaterialTheme.typography.labelLarge)
            }
          }
          Text(updated, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
      }
    }
    message?.let { status -> item {
      Card(Modifier.fillMaxWidth()) { Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(status, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
        TextButton(onClick = main::clearCsvStatusMessage) { Text("Cerrar") }
      } }
    } }
    item {
      DexPanel(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
          Text("Últimas novedades", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
          if (player.syncedAt > 0L) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DexMetric("+${numbers.format(player.newCards)}", "cartas nuevas", false, Modifier.weight(1f))
            DexMetric("+${numbers.format(player.addedCopies)}", "copias añadidas", true, Modifier.weight(1f))
          } else Text("Sincroniza para ver tus nuevas cartas y copias.", color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall)

        }
      }
    }
    item {
      DexPanel(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Resumen por rareza", Modifier.weight(1f), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            TextButton(onClick = onCollection, contentPadding = PaddingValues(4.dp)) { Text("Ver detalle ›", style = MaterialTheme.typography.labelSmall) }
          }
          BoxWithConstraints {
            val stack = maxWidth < 310.dp || androidx.compose.ui.platform.LocalDensity.current.fontScale >= 1.3f
            val groups = if (stack) rarityGroups.chunked(2) else listOf(rarityGroups)
            Column { groups.forEachIndexed { rowIndex, pair ->
              Column {
                if (rowIndex > 0) Spacer(Modifier.height(6.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                  pair.forEach { (_, name, count) ->
                    val index = rarityGroups.indexOfFirst { it.second == name }
                    Surface(modifier = Modifier.weight(1f), shape = RoundedCornerShape(10.dp),
                      border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = .5f)) {
                      Column(Modifier.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        DexRarityIcon(index, Modifier.size(26.dp))
                        Text(name, style = MaterialTheme.typography.labelSmall)
                        Text(numbers.format(count), style = MaterialTheme.typography.labelLarge)
                      }
                    }
                  }
                }
              }
            }
            }
          }

        }
      }
    }
    item {
      DexPanel(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
          Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Mazos recientes", Modifier.weight(1f), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            TextButton(onClick = onDecks, contentPadding = PaddingValues(4.dp)) { Text("Ver todos ›", style = MaterialTheme.typography.labelSmall) }
          }
          if (decks.isEmpty()) {
            Text("Todavía no hay mazos guardados.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedButton(onClick = onEditor) { Text("Crear mazo") }
          } else BoxWithConstraints(Modifier.fillMaxWidth()) {
            if (maxWidth >= 280.dp && androidx.compose.ui.platform.LocalDensity.current.fontScale < 1.3f) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              decks.take(2).forEach { saved -> RecentDeckTile(saved, catalogIds, Modifier.weight(1f)) { openSavedDeck(saved) } }
              if (decks.size == 1) Spacer(Modifier.weight(1f))
            } else Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
              decks.take(2).forEach { saved -> RecentDeckTile(saved, catalogIds, Modifier.fillMaxWidth()) { openSavedDeck(saved) } }
            }
          }
        }
      }
    }
    draft?.takeIf { it.cards.isNotEmpty() }?.let { deck -> item {
      DexPanel(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
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
    DeckCover(cover, saved.name, Modifier.fillMaxWidth().height(76.dp))
    Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
      Text(saved.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold,
        minLines = 2, maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
      Text("${saved.totalCards}/20 cartas", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
      if (!canOpen) Text("Por revisar", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelSmall)
    }
  }
}

@Composable
private fun DexMetric(value: String, label: String, violet: Boolean, modifier: Modifier) {
  val accent = if (violet) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary
  Surface(modifier, shape = RoundedCornerShape(10.dp), color = Color.Transparent, border = BorderStroke(1.dp, accent.copy(alpha = .3f))) {
    Row(Modifier.background(Brush.linearGradient(listOf(accent.copy(alpha = .2f), accent.copy(alpha = .06f))))
      .padding(10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      Icon(if (violet) Icons.Default.ContentCopy else Icons.Default.Sync, null, Modifier.size(24.dp), tint = accent)
      Column(Modifier.weight(1f)) {
        Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
      }
    }
  }
}

@Composable
fun DeckCover(id: String?, name: String, modifier: Modifier = Modifier) {
  Box(modifier.background(Brush.linearGradient(listOf(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.secondaryContainer))),
    contentAlignment = Alignment.Center) {
    if (id != null) com.example.ui.components.PocketCardImage(id, name, highResolution = true, modifier = Modifier.fillMaxSize(),
      artworkOnly = true, fullArt = com.example.data.repository.CardCatalog.ALL_CARDS.firstOrNull { it.id == id }?.isFullArt == true,
      contentScale = androidx.compose.ui.layout.ContentScale.Crop, alignment = Alignment.Center,
      unavailable = { Text(name, Modifier.padding(16.dp), style = MaterialTheme.typography.titleMedium) })
    else Text(name, Modifier.padding(16.dp), style = MaterialTheme.typography.titleMedium)
  }
}
