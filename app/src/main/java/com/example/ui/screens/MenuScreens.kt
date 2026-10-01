package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.viewmodel.TcgViewModel

@Composable
private fun MenuChoices(labels: List<String>, selected: Int, onSelect: (Int) -> Unit) {
  LazyRow(contentPadding = PaddingValues(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
    items(labels.size) { index ->
      FilterChip(selected = selected == index, onClick = { onSelect(index) },
        label = { Text(labels[index]) })
    }
  }
}

@Composable
fun DeckMenuScreen(viewModel: TcgViewModel, modifier: Modifier = Modifier) {
  var section by rememberSaveable { mutableIntStateOf(0) }
  Column(modifier.fillMaxSize()) {
    MenuChoices(listOf("Crear", "Mis mazos"), section) { section = it }
    DeckBuilderScreen(viewModel, Modifier.weight(1f), savedOnly = section == 1,
      onOpenSavedDeck = { section = 0 })
  }
}

@Composable
fun AnalysisMenuScreen(viewModel: TcgViewModel, onOpenDeck: (String) -> Unit,
  modifier: Modifier = Modifier) {
  var section by rememberSaveable { mutableIntStateOf(0) }
  Column(modifier.fillMaxSize()) {
    MenuChoices(listOf("Meta", "Recomendaciones", "Calculadora"), section) { section = it }
    val contentModifier = Modifier.weight(1f)
    when (section) {
      0 -> MetaDeckAnalyzerScreen(viewModel, onOpenDeck, contentModifier)
      1 -> PackRecommenderScreen(viewModel, contentModifier)
      else -> ProbabilityCalculatorScreen(contentModifier)
    }
  }
}

@Composable
fun TradeMenuScreen(viewModel: TcgViewModel, modifier: Modifier = Modifier) {
  val inventory by viewModel.inventoryList.collectAsStateWithLifecycle()
  var section by rememberSaveable { mutableIntStateOf(0) }
  var reserveTwo by rememberSaveable { mutableStateOf(true) }
  var copied by remember { mutableStateOf(false) }
  val context = LocalContext.current
  val reserve = if (reserveTwo) 2 else 1
  val cards = if (section == 0) inventory.filter { it.ownedCount > reserve }
    else inventory.filter { it.isWishlist }
  val title = if (section == 0) "Disponibles para ofrecer" else "Cartas que busco"
  Column(modifier.fillMaxSize()) {
    MenuChoices(listOf("Para ofrecer", "Deseos"), section) { section = it; copied = false }
    LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(16.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp)) {
      item {
        Text(title, style = MaterialTheme.typography.titleLarge)
        Text("Organiza tus propuestas. Comprueba en el juego si cada carta se puede canjear.",
          color = MaterialTheme.colorScheme.onSurfaceVariant)
      }
      if (section == 0) item {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
          Text("Reservar dos copias", modifier = Modifier.weight(1f).padding(top = 12.dp))
          Switch(checked = reserveTwo, onCheckedChange = { reserveTwo = it; copied = false })
        }
        Text("Se muestran las copias que sobran después de reservar $reserve. No se descuenta ninguna carta.")
      }
      item {
        OutlinedButton(enabled = cards.isNotEmpty(), onClick = {
          val text = title + "\n" + cards.joinToString("\n") { item ->
            val quantity = if (section == 0) " x${item.ownedCount - reserve}" else ""
            "${item.card.id} · ${item.card.name}$quantity"
          }
          (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
            .setPrimaryClip(ClipData.newPlainText(title, text))
          copied = true
        }) { Text(if (copied) "Lista copiada" else "Copiar lista") }
      }
      if (cards.isEmpty()) item {
        Text(if (section == 0) "No tienes copias sobrantes con esta reserva."
          else "Marca cartas en Deseos desde Colección para verlas aquí.")
      }
      items(cards, key = { it.card.id }) { item ->
        Card(Modifier.fillMaxWidth()) {
          Column(Modifier.padding(14.dp)) {
            Text(item.card.name, style = MaterialTheme.typography.titleMedium)
            Text("${item.card.id} · ${item.card.rarity.displayName}")
            Text(if (section == 0) "${item.ownedCount - reserve} disponibles · ${item.ownedCount} en colección"
              else "${item.ownedCount} en colección")
          }
        }
      }
    }
  }
}
