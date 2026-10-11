package com.example.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.Alignment
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.platform.testTag
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.data.api.CardDetails
import com.example.data.api.CardDetailsClient
import com.example.data.repository.CardWithInventory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext

@Composable
fun CardDetailsDialog(item: CardWithInventory, language: String, onDismiss: () -> Unit,
  onSave: (Int) -> Unit = {}, onWishlist: () -> Unit = {}, readOnly: Boolean = false) {
  val context = LocalContext.current
  val focusManager = LocalFocusManager.current
  val imageHeight = (LocalConfiguration.current.screenHeightDp * 0.65f).coerceIn(280f, 620f).dp
  var zoomed by remember(item.card.id) { mutableStateOf(false) }
  var local by remember(item.card.id) { mutableStateOf<com.example.domain.CombatData?>(null) }
  var quantity by rememberSaveable(item.card.id, item.ownedCount) { mutableStateOf(item.ownedCount.toString()) }
  var details by remember(item.card.id, language) { mutableStateOf<CardDetails?>(null) }
  var loading by remember { mutableStateOf(true) }
  var error by remember(item.card.id, language) { mutableStateOf<String?>(null) }
  var retry by remember(item.card.id, language) { mutableStateOf(0) }
  LaunchedEffect(item.card.id, language, retry) {
    loading = true
    details = null
    error = null
    local = withContext(Dispatchers.IO) { runCatching { com.example.data.repository.LocalCombatRepository.card(context, item.card.id, language) }.getOrNull() }
    try { details = withContext(Dispatchers.IO) { CardDetailsClient.load(context, item.card.id, language) } }
    catch (e: CancellationException) { throw e }
    catch (e: Exception) { error = "No se pudieron actualizar los detalles. Los datos locales disponibles se muestran debajo." }
    finally { loading = false }
  }
  if (zoomed) CardImageZoom(item.card.id, item.card.name, language) { zoomed = false }
  Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
    Surface(Modifier.fillMaxSize().testTag("card_reader"), color = MaterialTheme.colorScheme.background) {
    Column(Modifier.fillMaxSize().systemBarsPadding().padding(horizontal = 16.dp)) {
      Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(item.card.name, Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
        TextButton(onClick = onDismiss) { Text("Cerrar carta") }
      }
    Column(Modifier.weight(1f).verticalScroll(rememberScrollState()),
      verticalArrangement = Arrangement.spacedBy(10.dp)) {
      com.example.ui.components.PocketCardImage(id = item.card.id, name = item.card.name, language = language,
        highResolution = true, modifier = Modifier.fillMaxWidth().height(imageHeight).clickable { zoomed = true })
      OutlinedButton(onClick = { zoomed = true }, modifier = Modifier.fillMaxWidth()) { Text("Ampliar imagen · zoom con dos dedos") }
      Text("Texto de la carta", style = MaterialTheme.typography.titleLarge)
      Text("${item.card.id} · ${item.card.rarity.displayName}")
      Text(item.card.packNames.joinToString(", ").ifBlank { "Sin datos de sobre" })
      if (!readOnly) {
      OutlinedTextField(value = quantity, onValueChange = { if (it.length <= 5 && it.all(Char::isDigit)) quantity = it },
        label = { Text("Copias en mi colección") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }))
      TextButton(onClick = onWishlist) { Text(if (item.isWishlist) "Quitar de Deseos" else "Añadir a Deseos") }
      }
      if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
      error?.let {
        Text(it)
        OutlinedButton(enabled = !loading, onClick = { loading = true; retry++ }, shape = MaterialTheme.shapes.medium) {
          Text("Reintentar detalles")
        }
      }
      if (details == null) local?.let { value ->
        Text("Datos incluidos en la app · algunos textos pueden estar en inglés", style = MaterialTheme.typography.labelMedium)
        value.hp?.let { Text("PS: $it") }
        value.retreat?.let { Text("Retirada: $it") }
        if (value.text.isNotBlank()) Text(value.text)
        value.attacks.forEach { attack ->
          Text(attack.name.ifBlank { "Ataque" }, style = MaterialTheme.typography.titleMedium)
          Text("Daño: ${attack.damage.ifBlank { "Según efecto" }} · Coste: " + (attack.cost?.joinToString { com.example.domain.LocalDeckPlanner.energy(it) ?: it } ?: "Sin datos"))
          if (attack.effect.isNotBlank()) Text(attack.effect)
        }
      }
      details?.let { value ->
        Text(value.source, style = MaterialTheme.typography.labelSmall)
        value.hp?.let { Text("PS: $it") }
        Text(listOf(value.category, value.stage).filter { it.isNotBlank() }.joinToString(" · "))
        value.retreat?.let { Text("Retirada: $it") }
        if (value.abilityText.isNotBlank()) Text(value.abilityText)
        if (value.description.isNotBlank()) Text(value.description)
        value.attacks.forEach { Text(it) }
      }
      Spacer(Modifier.height(24.dp))
    }
    if (!readOnly) Button(enabled = quantity.toIntOrNull()?.let { it in 0..99999 } == true,
      onClick = { onSave(quantity.toInt()); onDismiss() }, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) { Text("Guardar cantidad") }
    }
    }
  }
}

@Composable
private fun CardImageZoom(id: String, name: String, language: String, onDismiss: () -> Unit) {
  var scale by remember(id) { mutableFloatStateOf(1f) }
  var offset by remember(id) { mutableStateOf(Offset.Zero) }
  Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
      Column(Modifier.fillMaxSize().systemBarsPadding()) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
          TextButton(onClick = { scale = 1f; offset = Offset.Zero }) { Text("Restablecer zoom") }
          TextButton(onClick = onDismiss) { Text("Volver a la carta") }
        }
        Box(Modifier.weight(1f).fillMaxWidth().pointerInput(id) {
          detectTransformGestures { _, pan, zoom, _ ->
            scale = (scale * zoom).coerceIn(1f, 5f)
            val limitX = size.width * (scale - 1f) / 2f
            val limitY = size.height * (scale - 1f) / 2f
            offset = Offset((offset.x + pan.x).coerceIn(-limitX, limitX), (offset.y + pan.y).coerceIn(-limitY, limitY))
          }
        }.pointerInput(id) { detectTapGestures(onDoubleTap = {
          scale = if (scale > 1f) 1f else 2.5f; offset = Offset.Zero
        }) }, contentAlignment = Alignment.Center) {
          com.example.ui.components.PocketCardImage(id, name, language = language, highResolution = true,
            modifier = Modifier.fillMaxSize().graphicsLayer(scaleX = scale, scaleY = scale, translationX = offset.x, translationY = offset.y))
        }
        Text("Pellizca para ampliar · doble toque para acercar", Modifier.padding(16.dp), style = MaterialTheme.typography.bodySmall)
      }
    }
  }
}
