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
  var codes by rememberSaveable { mutableStateOf("") }
  val tokens = codes.split(Regex("[,;\\s]+")).filter { it.isNotBlank() }
  val invalid = tokens.filter { token -> runCatching { CardCatalog.getCardById(CardId.normalize(token)) }.getOrNull() == null }
  val targets = tokens.mapNotNull { runCatching { CardId.normalize(it) }.getOrNull() }.toSet()
  val coverage = remember(inventory, targets) { CollectionInsights.coverage(inventory, targets).filter { it.score > 0 } }
  LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
    item {
      Text("Sobres para completar tu colección", style = MaterialTheme.typography.titleLarge)
      Text("Comparación de todas las expansiones con datos de sobre. Prioriza cartas que faltan, Deseos y tus objetivos. Las promociones se excluyen.")
      OutlinedTextField(codes, { codes = it }, label = { Text("Objetivos por código (opcional)") },
        placeholder = { Text("A1-036, A2-001") }, isError = invalid.isNotEmpty(), modifier = Modifier.fillMaxWidth())
      if (invalid.isNotEmpty()) Text("Códigos no disponibles: ${invalid.joinToString()}", color = MaterialTheme.colorScheme.error)
      Text("Puntuación: 1 por faltante, 3 adicionales por Deseo y 5 adicionales por objetivo. Mide cobertura; no probabilidades de apertura.")
      if (coverage.isEmpty()) Text("No hay cartas faltantes con datos de sobre.")
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
