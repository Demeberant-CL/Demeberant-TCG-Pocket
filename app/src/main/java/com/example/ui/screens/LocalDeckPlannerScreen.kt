package com.example.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.domain.*
import com.example.data.util.DeckCodec
import com.example.ui.components.DexPanel
import com.example.ui.viewmodel.TcgViewModel

@Composable
fun LocalDeckPlannerScreen(main: TcgViewModel, onOpen: () -> Unit, modifier: Modifier = Modifier, reference: com.example.data.repository.GeneratedDeck? = null) {
  val inventory by main.inventoryList.collectAsStateWithLifecycle()
  val plans by main.localDeckPlans.collectAsStateWithLifecycle()
  val busy by main.isGeneratingDeck.collectAsStateWithLifecycle()
  val message by main.localPlannerMessage.collectAsStateWithLifecycle()
  var type by rememberSaveable { mutableStateOf<String?>(null) }
  var style by rememberSaveable { mutableStateOf(DeckStyle.BALANCED.name) }
  var query by rememberSaveable { mutableStateOf("") }
  var anchorId by rememberSaveable { mutableStateOf<String?>(null) }
  var pending by remember { mutableStateOf<DeckPlan?>(null) }
  val anchor = inventory.firstOrNull { it.card.id == anchorId }?.card
  val found = remember(inventory, query) {
    if (query.trim().length < 2) emptyList() else inventory.filter { it.ownedCount > 0 &&
      it.card.type != "Entrenador" && (it.card.name.contains(query.trim(), true) || it.card.id.contains(query.trim(), true)) }
      .sortedBy { it.card.id }.take(6)
  }
  LazyColumn(modifier.fillMaxSize().testTag("local_planner_list"), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
    item {
      Text("Constructor de mazos", style = MaterialTheme.typography.headlineSmall)
      Text("Con tus cartas · Sin conexión ni cuotas", style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary)
    }
    item {
      Text("Energía", style = MaterialTheme.typography.titleMedium)
      LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        item { FilterChip(selected = type == null, onClick = { type = null }, label = { Text("Automática") }) }
        items(DeckCodec.energyNames) { name -> FilterChip(selected = type == name, onClick = { type = name }, label = { Text(name) }) }
      }
      Text("Estilo", style = MaterialTheme.typography.titleMedium)
      LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(DeckStyle.entries) { value -> FilterChip(selected = style == value.name,
          onClick = { style = value.name }, label = { Text(value.label) }) }
      }
    }
    item {
      OutlinedTextField(query, { query = it; anchorId = null }, modifier = Modifier.fillMaxWidth(),
        label = { Text("Pokémon principal (opcional)") }, singleLine = true)
      anchor?.let { Text("Elegido: ${it.name} · ${it.id}", style = MaterialTheme.typography.labelMedium) }
      found.forEach { item -> TextButton(onClick = { anchorId = item.card.id; query = "" }, modifier = Modifier.fillMaxWidth()) {
        Text("${item.card.name} · ${item.card.id} · ${item.ownedCount} copias")
      } }
      Button(onClick = { main.recommendLocalDecks(PlannerOptions(type, DeckStyle.valueOf(style), anchorId)) },
        enabled = !busy, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
        Text(if (busy) "Analizando colección…" else "Crear propuestas")
      }
      if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
      message?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    }
    items(plans, key = { it.deck.cards.joinToString { e -> "${e.card.id}:${e.count}" } }) { plan ->
      var expanded by remember(plan) { mutableStateOf(false) }
      DexPanel(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
          Text(plan.deck.name, style = MaterialTheme.typography.titleLarge)
          com.example.ui.components.EnergyBadges(plan.deck.energyTypes)
          Text("${plan.deck.totalCardCount}/20 cartas · ${plan.basics} básicos · ${plan.trainers} entrenadores",
            style = MaterialTheme.typography.labelMedium)
          Text("Datos de combate: ${plan.knownCopies}/${plan.deck.totalCardCount}", style = MaterialTheme.typography.bodySmall)
          Text(plan.reasons.first(), style = MaterialTheme.typography.bodyMedium)
          plan.cautions.forEach { Text(it, style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant) }
          if (reference != null) {
            val changes = com.example.domain.DeckChat.changes(reference, plan.deck)
            Text("Reemplazos propuestos", style = MaterialTheme.typography.titleSmall)
            val names = inventory.associate { it.card.id to it.card.name }
            if (changes.first.isEmpty() && changes.second.isEmpty()) Text("La lista coincide con tu mazo.")
            changes.first.forEach { Text("Quitar ${it.count}× ${names[it.id] ?: it.id}", style = MaterialTheme.typography.bodySmall) }
            changes.second.forEach { Text("Añadir ${it.count}× ${names[it.id] ?: it.id}", style = MaterialTheme.typography.bodySmall) }
            Text("La propuesta compara ritmo, soporte y líneas de evolución. Revisa las diferencias antes de abrirla.", style = MaterialTheme.typography.bodySmall)
          }
          TextButton(onClick = { expanded = !expanded }) { Text(if (expanded) "Ocultar cartas" else "Ver cartas") }
          if (expanded) {
            plan.reasons.drop(2).forEach { Text(it, style = MaterialTheme.typography.bodySmall) }
            plan.deck.cards.forEach { entry -> Text("${entry.count}× ${entry.card.name} · ${entry.card.id}",
              style = MaterialTheme.typography.bodySmall) }
          }
          Button(onClick = {
            if (main.hasUnsavedDeckChanges()) pending = plan else main.openLocalDeckPlan(plan, onOpen)
          }, modifier = Modifier.fillMaxWidth()) { Text("Abrir en editor") }
        }
      }
    }
    if (plans.isNotEmpty()) item {
      Text("Valoración por reglas y datos disponibles. No es una predicción de victorias.",
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
  }
  pending?.let { plan -> AlertDialog(onDismissRequest = { pending = null }, title = { Text("Reemplazar borrador") },
    text = { Text("Hay cambios sin guardar. La propuesta reemplazará ese borrador; los mazos guardados se conservan.",
      modifier = Modifier.verticalScroll(rememberScrollState())) },
    confirmButton = { TextButton(onClick = { pending = null; main.openLocalDeckPlan(plan, onOpen) }) { Text("Reemplazar") } },
    dismissButton = { TextButton(onClick = { pending = null }) { Text("Volver") } }) }
}
