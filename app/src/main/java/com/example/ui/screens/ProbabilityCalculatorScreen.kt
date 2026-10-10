package com.example.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.data.util.CollectionInsights

@Composable
fun ProbabilityCalculatorScreen(modifier: Modifier = Modifier, deckOnly: Boolean = false, packsOnly: Boolean = false) {
  var rate by rememberSaveable { mutableStateOf("") }
  var attempts by rememberSaveable { mutableFloatStateOf(10f) }
  var targets by rememberSaveable { mutableFloatStateOf(2f) }
  var draws by rememberSaveable { mutableFloatStateOf(5f) }
  val parsed = rate.replace(',', '.').toDoubleOrNull()?.takeIf { it.isFinite() && it in 0.0..100.0 }
  LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
    if (!deckOnly) item {
      Text("Probabilidad de conseguir una carta", style = MaterialTheme.typography.titleLarge)
      Text("Introduce la probabilidad total por sobre que muestra el juego para tu objetivo. No uses la tasa de una sola ranura.")
      OutlinedTextField(rate, { rate = it }, label = { Text("Probabilidad por sobre (%)") },
        isError = rate.isNotBlank() && parsed == null, singleLine = true, modifier = Modifier.fillMaxWidth())
      Text("${attempts.toInt()} sobres")
      Slider(attempts, { attempts = it }, valueRange = 1f..500f, steps = 498)
      if (parsed != null) {
        Text("Al menos una: ${"%.2f".format(CollectionInsights.cumulativeChance(parsed / 100, attempts.toInt()) * 100)} %")
        Text(if (parsed > 0) "Promedio: ${"%.1f".format(100 / parsed)} sobres." else "No se puede obtener con una tasa de cero.")
      }
      Text("Modelo de aperturas independientes con tasa constante. Un promedio no garantiza el resultado.")
    }
    if (!packsOnly) item {
      HorizontalDivider()
      Text("Robar una carta objetivo", style = MaterialTheme.typography.titleLarge)
      Text("Mazo de 20 cartas; robo aleatorio sin reemplazo.")
      Text("${targets.toInt()} copias objetivo"); Slider(targets, { targets = it }, valueRange = 0f..20f, steps = 19)
      Text("${draws.toInt()} cartas robadas"); Slider(draws, { draws = it }, valueRange = 0f..20f, steps = 19)
      Text("Al menos una: ${"%.2f".format(CollectionInsights.drawChance(20, targets.toInt(), draws.toInt()) * 100)} %")
      Text("No simula la garantía de Pokémon básico en la mano inicial ni habilidades, búsqueda o efectos del juego.")
    }
  }
}
