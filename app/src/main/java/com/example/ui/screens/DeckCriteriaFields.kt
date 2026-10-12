package com.example.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.domain.DeckCriteria

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DeckCriteriaFields(expansions: List<String>, enabled: Boolean = true, onChange: (DeckCriteria) -> Unit) {
  var ownedOnly by rememberSaveable { mutableStateOf(true) }
  var chosen by rememberSaveable { mutableStateOf(listOf<String>()) }
  var required by rememberSaveable { mutableStateOf("") }
  var excluded by rememberSaveable { mutableStateOf("") }
  var expanded by rememberSaveable { mutableStateOf(false) }
  var error by remember { mutableStateOf<String?>(null) }
  LaunchedEffect(ownedOnly, chosen, required, excluded) {
    runCatching { DeckCriteria(chosen.toSet(), DeckCriteria.codes(required), DeckCriteria.codes(excluded), ownedOnly) }
      .onSuccess { error = null; onChange(it) }.onFailure { error = "Revisa los códigos de carta."; onChange(DeckCriteria(inputValid = false)) }
  }
  FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
    FilterChip(enabled = enabled, selected = ownedOnly, onClick = { ownedOnly = true }, label = { Text("Mi colección") })
    FilterChip(enabled = enabled, selected = !ownedOnly, onClick = { ownedOnly = false }, label = { Text("Catálogo") })
  }
  TextButton(enabled = enabled, onClick = { expanded = !expanded }) { Text(if (expanded) "Ocultar criterios" else "Expansiones y cartas") }
  if (expanded) {
    Text("Expansiones", style = MaterialTheme.typography.titleSmall)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
      FilterChip(enabled = enabled, selected = chosen.isEmpty(), onClick = { chosen = emptyList() }, label = { Text("Todas") })
      expansions.forEach { code -> FilterChip(enabled = enabled, selected = code in chosen, onClick = { chosen = if (code in chosen) chosen - code else chosen + code }, label = { Text(code) }) }
    }
    OutlinedTextField(enabled = enabled, value = required, onValueChange = { required = it.take(500) }, label = { Text("Cartas obligatorias · códigos") }, placeholder = { Text("A1-001, A1-002") }, modifier = Modifier.fillMaxWidth())
    OutlinedTextField(enabled = enabled, value = excluded, onValueChange = { excluded = it.take(500) }, label = { Text("Cartas excluidas · códigos") }, modifier = Modifier.fillMaxWidth())
  } else if (chosen.isNotEmpty()) Text(chosen.joinToString(), style = MaterialTheme.typography.bodySmall)
  error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
}
