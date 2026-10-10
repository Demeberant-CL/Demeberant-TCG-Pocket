package com.example.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.data.model.CardRarity
import com.example.data.util.DeckCodec
import com.example.domain.DeckCardFilter
import com.example.domain.CardRole
import com.example.ui.components.DexPanel

@Composable
fun DeckChoice(label: String, value: String, choices: List<Pair<String, String>>, onSelect: (String) -> Unit) {
  var open by remember { mutableStateOf(false) }
  OutlinedButton(onClick = { open = true }, modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium) {
    Text("$label: ${choices.firstOrNull { it.first == value }?.second ?: value}")
  }
  if (open) AlertDialog(onDismissRequest = { open = false }, title = { Text(label) },
    text = { LazyColumn { items(choices) { (key, name) ->
      TextButton(onClick = { onSelect(key); open = false }, modifier = Modifier.fillMaxWidth()) { Text(name) }
    } } }, confirmButton = { TextButton(onClick = { open = false }) { Text("Cerrar") } })
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DeckCardFiltersScreen(filter: DeckCardFilter, expansions: List<String>, results: Int,
  onChange: (DeckCardFilter) -> Unit, onDone: () -> Unit, modifier: Modifier = Modifier) {
  Column(modifier.fillMaxSize()) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
      TextButton(onClick = onDone) { Text("← Añadir cartas") }
      TextButton(onClick = { onChange(DeckCardFilter(query = filter.query, sort = filter.sort, availability = filter.availability)) }) { Text("Limpiar") }
    }
    LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
      item { Text("Filtros de cartas", style = MaterialTheme.typography.headlineSmall) }
      item { DexPanel(Modifier.fillMaxWidth()) { Column(Modifier.padding(12.dp)) {
        Text("Tipo de carta", style = MaterialTheme.typography.titleMedium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
          listOf("" to "Todas", "pokemon" to "Pokémon", "item" to "Objeto", "supporter" to "Partidario",
            "tool" to "Herramienta", "Fossil" to "Fósil", "trainer" to "Entrenador", "unknown" to "Sin verificar").forEach { (key, name) ->
            FilterChip(selected = filter.category == key, onClick = { onChange(filter.copy(category = key,
              stage = if (key in listOf("", "pokemon")) filter.stage else "", onlyEx = if (key in listOf("", "pokemon")) filter.onlyEx else false)) }, label = { Text(name) })
          }
        }
        Text("Evolución", style = MaterialTheme.typography.titleSmall)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
          listOf("" to "Todas", "basic" to "Básico", "1" to "Fase 1", "2" to "Fase 2").forEach { (key, name) ->
            FilterChip(selected = filter.stage == key, onClick = { onChange(filter.copy(stage = key,
              category = if (key.isNotEmpty()) "pokemon" else filter.category)) }, label = { Text(name) })
          }
        }
        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
          Text("Solo Pokémon ex", Modifier.weight(1f))
          Switch(filter.onlyEx, { onChange(filter.copy(onlyEx = it, category = if (it) "pokemon" else filter.category)) })
        }
      } } }
      item { DexPanel(Modifier.fillMaxWidth()) { Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        DeckChoice("Energía / tipo", filter.element, listOf("" to "Todas") + (DeckCodec.energyNames + listOf("Dragón", "Incoloro")).map { it to it }) { onChange(filter.copy(element = it)) }
        DeckChoice("Expansión", filter.expansion, listOf("" to "Todas") + expansions.map { it to it }) { onChange(filter.copy(expansion = it)) }
        DeckChoice("Rareza", filter.rarity, listOf("" to "Todas") + CardRarity.entries.map { it.name to "${it.symbol} · ${it.displayName}" }) { onChange(filter.copy(rarity = it)) }
        DeckChoice("Disponibilidad", filter.availability, listOf("all" to "Todas", "owned" to "Mis cartas", "sufficient" to "Puedo añadir otra copia", "missing" to "No las tengo")) { onChange(filter.copy(availability = it)) }
      } } }
      item { DexPanel(Modifier.fillMaxWidth()) { Column(Modifier.padding(12.dp)) {
        Text("Efectos", style = MaterialTheme.typography.titleMedium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
          FilterChip(selected = filter.effect.isEmpty(), onClick = { onChange(filter.copy(effect = "")) }, label = { Text("Todos") })
          CardRole.entries.forEach { role -> FilterChip(selected = filter.effect == role.key,
            onClick = { onChange(filter.copy(effect = role.key)) }, label = { Text(role.label) }) }
        }
        Text("Solo aparecen cartas con efectos cargados. Puedes cargar más desde Añadir cartas.", style = MaterialTheme.typography.bodySmall)
      } } }
      item { Text("Las cartas sin datos verificados no se clasifican por evolución ni efectos.", style = MaterialTheme.typography.bodySmall) }
    }
    Button(onClick = onDone, modifier = Modifier.fillMaxWidth().padding(16.dp), shape = MaterialTheme.shapes.medium) { Text("Ver $results cartas") }
  }
}
