package com.example.ui.screens

import androidx.compose.foundation.layout.*
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
  onSave: (Int) -> Unit, onWishlist: () -> Unit) {
  val context = LocalContext.current
  val focusManager = LocalFocusManager.current
  val imageHeight = (LocalConfiguration.current.screenHeightDp * 0.35f).coerceIn(120f, 240f).dp
  var quantity by rememberSaveable(item.card.id, item.ownedCount) { mutableStateOf(item.ownedCount.toString()) }
  var details by remember(item.card.id, language) { mutableStateOf<CardDetails?>(null) }
  var loading by remember { mutableStateOf(true) }
  var error by remember(item.card.id, language) { mutableStateOf<String?>(null) }
  var retry by remember(item.card.id, language) { mutableStateOf(0) }
  LaunchedEffect(item.card.id, language, retry) {
    loading = true
    details = null
    error = null
    try { details = withContext(Dispatchers.IO) { CardDetailsClient.load(context, item.card.id, language) } }
    catch (e: CancellationException) { throw e }
    catch (e: Exception) { error = "Detalles sin conexión o no disponibles. Puedes editar la colección." }
    finally { loading = false }
  }
  AlertDialog(onDismissRequest = onDismiss, title = { Text(item.card.name) }, text = {
    Column(Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState()),
      verticalArrangement = Arrangement.spacedBy(10.dp)) {
      com.example.ui.components.PocketCardImage(id = item.card.id, name = item.card.name, language = language,
        highResolution = true, modifier = Modifier.fillMaxWidth().height(imageHeight))
      Text("${item.card.id} · ${item.card.rarity.displayName}")
      Text(item.card.packNames.joinToString(", ").ifBlank { "Sin datos de sobre" })
      OutlinedTextField(value = quantity, onValueChange = { if (it.length <= 5 && it.all(Char::isDigit)) quantity = it },
        label = { Text("Copias en mi colección") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }))
      TextButton(onClick = onWishlist) { Text(if (item.isWishlist) "Quitar de Deseos" else "Añadir a Deseos") }
      if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
      error?.let {
        Text(it)
        OutlinedButton(enabled = !loading, onClick = { loading = true; retry++ }, shape = MaterialTheme.shapes.medium) {
          Text("Reintentar detalles")
        }
      }
      details?.let { value ->
        Text(value.source, style = MaterialTheme.typography.labelSmall)
        value.hp?.let { Text("PS: $it") }
        Text(listOf(value.category, value.stage).filter { it.isNotBlank() }.joinToString(" · "))
        value.retreat?.let { Text("Retirada: $it") }
        if (value.description.isNotBlank()) Text(value.description)
        value.attacks.forEach { Text(it) }
      }
    }
  }, confirmButton = {
    TextButton(enabled = quantity.toIntOrNull()?.let { it in 0..99999 } == true,
      onClick = { onSave(quantity.toInt()); onDismiss() }) { Text("Guardar cantidad") }
  }, dismissButton = { TextButton(onClick = onDismiss) { Text("Cerrar") } })
}
