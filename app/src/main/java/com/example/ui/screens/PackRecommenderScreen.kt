package com.example.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.repository.CardCatalog
import com.example.data.util.CardId
import com.example.data.util.CollectionInsights
import com.example.ui.viewmodel.TcgViewModel

@Composable
fun PackRecommenderScreen(viewModel: TcgViewModel, modifier: Modifier = Modifier) {
  val inventory by viewModel.inventoryList.collectAsStateWithLifecycle()
  var query by rememberSaveable { mutableStateOf("") }
  var showCodes by rememberSaveable { mutableStateOf(false) }
  var codes by rememberSaveable { mutableStateOf("") }
  val tokens = codes.split(Regex("[,;\\s]+")).filter { it.isNotBlank() }
  val invalid = tokens.filter { token -> runCatching { CardCatalog.getCardById(CardId.normalize(token)) }.getOrNull() == null }
  val targets = tokens.mapNotNull { runCatching { CardId.normalize(it) }.getOrNull() }.toSet()
  val searchResults = remember(inventory, query) { if (query.isBlank()) emptyList() else inventory.filter { it.card.name.contains(query, true) || it.card.id.contains(query, true) }.take(20) }
  val coverage = remember(inventory, targets) { CollectionInsights.coverage(inventory, targets).filter { it.score > 0 } }
  LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
    item {
      Text("Sobres para completar tu colección", style = MaterialTheme.typography.titleLarge)
      OutlinedTextField(query, { query = it }, label = { Text("Buscar carta objetivo") }, modifier = Modifier.fillMaxWidth())
      TextButton(onClick = { showCodes = !showCodes }) { Text("Introducir códigos manualmente") }
      if (showCodes) OutlinedTextField(codes, { codes = it }, label = { Text("Objetivos por código (opcional)") },
        placeholder = { Text("A1-036, A2-001") }, isError = invalid.isNotEmpty(), modifier = Modifier.fillMaxWidth())
      if (invalid.isNotEmpty()) Text("Códigos no disponibles: ${invalid.joinToString()}", color = MaterialTheme.colorScheme.error)
      Text("Orden por cobertura · no es probabilidad de apertura")
      if (coverage.isEmpty()) Text("No hay cartas faltantes con datos de sobre.")
    }
    if (query.isNotBlank()) items(searchResults, key = { "target-${it.card.id}" }) { item ->
      OutlinedCard(onClick = {
        val next = targets.toMutableSet()
        if (!next.add(item.card.id)) next.remove(item.card.id)
        codes = next.joinToString(", ")
      }, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
          DeckThumbnail(item.card.id, item.card.name)
          Column { Text(item.card.name); Text(if (item.card.id in targets) "Objetivo seleccionado ✓" else item.card.id) }
        }
      }
    }
    if (targets.isNotEmpty()) item {
      Text("Objetivos: " + targets.joinToString { id -> CardCatalog.getCardById(id)?.name ?: id })
      TextButton(onClick = { codes = "" }) { Text("Limpiar objetivos") }
    }
    items(coverage, key = { "${it.set}:${it.pack}" }) { pack ->
      Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
          Text("${pack.set} · ${pack.pack}", style = MaterialTheme.typography.titleMedium)
          Text("${pack.missing} faltantes · ${pack.wishes} deseos · ${pack.score} puntos")
          pack.targets.forEach { Text(it) }
        }
      }
    }
  }
}
