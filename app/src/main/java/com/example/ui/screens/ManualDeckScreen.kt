package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.viewmodel.AdvancedViewModel
import com.example.ui.viewmodel.TcgViewModel

@Composable
fun ManualDeckScreen(
  viewModel: TcgViewModel,
  advanced: AdvancedViewModel,
  modifier: Modifier = Modifier,
  onBack: () -> Unit = {},
) {
  val deck by viewModel.generatedDeck.collectAsStateWithLifecycle()
  val inventory by viewModel.inventoryList.collectAsStateWithLifecycle()
  val generatingDeck by viewModel.isGeneratingDeck.collectAsStateWithLifecycle()
  val savingDeck by viewModel.isSavingDeck.collectAsStateWithLifecycle()
  val message by viewModel.csvStatusMessage.collectAsStateWithLifecycle()
  val current = deck
  var reading by remember { mutableStateOf<com.example.data.model.PokemonCard?>(null) }
  var chatPage by rememberSaveable { mutableIntStateOf(0) }
  var chatAction by rememberSaveable { mutableStateOf("chat") }
  val generating = generatingDeck || savingDeck
  val owned = remember(inventory) { inventory.associate { it.card.id to it.ownedCount } }

  reading?.let { card ->
    CardDetailsDialog(
      com.example.data.repository.CardWithInventory(card, owned[card.id] ?: 0, false),
      "es",
      { reading = null },
      { viewModel.setQuantity(card.id, it) },
      { viewModel.toggleWishlist(card.id) },
    )
  }

  if (current == null) {
    Column(modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
      TextButton(onClick = onBack) { Text("← Mis mazos") }
      Text("Aún no hay una propuesta", style = MaterialTheme.typography.headlineSmall)
      Text("Diseña el mazo con ChatGPT y vuelve aquí para revisar la lista y guardarla.")
      Button(onClick = {
        viewModel.newManualDeck()
        chatAction = "create"
        chatPage = 1
      }, modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium) {
        Text("Crear con ChatGPT")
      }
    }
    return
  }

  if (chatPage == 1) {
    BackHandler { chatPage = 0 }
    DeckChatScreen(
      viewModel,
      advanced,
      onBack = { chatPage = 0 },
      onConnections = { chatPage = 2 },
      modifier = modifier,
      initialAction = chatAction,
      creating = current.cards.isEmpty(),
    )
    return
  }
  if (chatPage == 2) {
    BackHandler { chatPage = 1 }
    Column(modifier.fillMaxSize()) {
      TextButton(onClick = { chatPage = 1 }) { Text("← Chat del mazo") }
      AIAssistantScreen(viewModel, advanced, { chatPage = 0 }, Modifier.weight(1f), initialConfigure = true, configurationOnly = true)
    }
    return
  }

  var leaving by remember { mutableStateOf(false) }
  fun leave() {
    if (viewModel.hasUnsavedDeckChanges()) leaving = true else onBack()
  }
  BackHandler { leave() }

  Column(modifier.fillMaxSize()) {
    TextButton(onClick = { leave() }) { Text("← Mis mazos") }
    LazyColumn(
      Modifier.weight(1f).fillMaxWidth(),
      contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      item {
        Text(current.name, style = MaterialTheme.typography.headlineSmall)
        Text(
          "${current.totalCardCount}/20 cartas · " + current.energyTypes.joinToString().ifBlank { "Energías por elegir" },
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        LinearProgressIndicator(
          progress = { (current.totalCardCount / 20f).coerceIn(0f, 1f) },
          modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        )
      }
      item {
        Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
          Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(if (current.cards.isEmpty()) "Crea tu mazo con ChatGPT" else "Mejora el mazo con ChatGPT", style = MaterialTheme.typography.titleMedium)
            Text(
              if (current.cards.isEmpty()) "La IA analizará tu colección y propondrá cartas, energías y estrategia."
              else "Pide a ChatGPT completar cartas, mejorar la estrategia o explicar cambios.",
              style = MaterialTheme.typography.bodySmall,
            )
            Button(onClick = {
              chatAction = if (current.cards.isEmpty()) "create" else "improve"
              chatPage = 1
            }, enabled = !generating, modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium) {
              Text(if (current.cards.isEmpty()) "Diseñar con ChatGPT" else "Consultar a ChatGPT")
            }
            if (current.cards.isNotEmpty()) {
              OutlinedButton(onClick = { chatAction = "complete"; chatPage = 1 }, enabled = !generating, modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium) {
                Text("Completar faltantes con ChatGPT")
              }
            }
          }
        }
      }
      if (message != null) item {
        Text(message!!, style = MaterialTheme.typography.bodySmall)
      }
      if (current.cards.isEmpty()) item {
        Text("La lista aparecerá aquí después de revisar y aplicar la propuesta de ChatGPT.", style = MaterialTheme.typography.bodyMedium)
      }
      items(current.cards, key = { it.card.id }) { entry ->
        val available = owned[entry.card.id] ?: 0
        val missing = (entry.count - available).coerceAtLeast(0)
        OutlinedCard(onClick = { reading = entry.card }, modifier = Modifier.fillMaxWidth()) {
          Row(Modifier.fillMaxWidth().padding(10.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            DeckThumbnail(entry.card.id, entry.card.name)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
              Text(entry.card.name, style = MaterialTheme.typography.titleSmall)
              Text("${entry.count} en el mazo · $available disponibles", style = MaterialTheme.typography.bodySmall)
              if (missing > 0) Text("Faltan $missing copias", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
              TextButton(onClick = { reading = entry.card }, contentPadding = PaddingValues(0.dp)) { Text("Ver carta") }
            }
          }
        }
      }
      if (current.strategy.isNotBlank()) item {
        Card(Modifier.fillMaxWidth()) {
          Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Estrategia propuesta por ChatGPT", style = MaterialTheme.typography.titleMedium)
            Text(current.strategy)
          }
        }
      }
      if (current.validationWarnings.isNotEmpty()) item {
        Text("Revisar antes de jugar", style = MaterialTheme.typography.titleSmall)
        current.validationWarnings.forEach { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
      }
    }

    Surface(color = MaterialTheme.colorScheme.background) {
      Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Button(
          onClick = { viewModel.saveCurrentDeck(allowDraft = true) },
          enabled = current.cards.isNotEmpty() && current.name.isNotBlank() && !generating,
          modifier = Modifier.fillMaxWidth(),
          shape = MaterialTheme.shapes.medium,
        ) {
          Text(if (current.totalCardCount == 20) "Guardar mazo · 20/20" else "Guardar borrador · ${current.totalCardCount}/20")
        }
        if (current.totalCardCount == 20 && current.energyTypes.isNotEmpty() && current.validationWarnings.isEmpty()) {
          com.example.ui.components.DeckQrExportButton(current, Modifier.fillMaxWidth(), enabled = !generating, onPrepare = { ready ->
            viewModel.saveCurrentDeck(allowDraft = false, onSaved = ready)
          })
        }
      }
    }
  }

  if (leaving) AlertDialog(
    onDismissRequest = { leaving = false },
    title = { Text("Guardar los cambios") },
    text = { Text("Puedes guardar el mazo antes de volver a tu biblioteca.") },
    confirmButton = {
      TextButton(
        onClick = { viewModel.saveCurrentDeck(allowDraft = true, onSaved = { leaving = false; onBack() }) },
        enabled = current.cards.isNotEmpty() && current.name.isNotBlank(),
      ) { Text("Guardar y volver") }
    },
    dismissButton = { TextButton(onClick = { leaving = false; onBack() }) { Text("Volver sin guardar") } },
  )
}
