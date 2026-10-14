package com.example.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.components.DexPanel
import com.example.ui.viewmodel.AdvancedViewModel
import com.example.ui.viewmodel.TcgViewModel

@Composable
fun CreateDeckScreen(
  main: TcgViewModel,
  advanced: AdvancedViewModel,
  onAi: () -> Unit,
  onContinue: () -> Unit,
  onConnect: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val draft by main.generatedDeck.collectAsStateWithLifecycle()
  val inventory by main.inventoryList.collectAsStateWithLifecycle()
  val account by advanced.chatGptPlanAccount.collectAsStateWithLifecycle()
  val connected = account?.canUsePlan == true
  var confirmNew by remember { mutableStateOf(false) }

  Column(modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
    Text("Crear un mazo", style = MaterialTheme.typography.headlineMedium)
    Text("ChatGPT analizará tu colección, propondrá 20 cartas y explicará la estrategia. Revisa la propuesta antes de guardarla.", style = MaterialTheme.typography.bodyMedium)

    DexPanel(Modifier.fillMaxWidth()) {
      Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Colección disponible", style = MaterialTheme.typography.titleMedium)
        val available = inventory.count { it.ownedCount > 0 }
        val copies = inventory.sumOf { it.ownedCount }
        Text("$available cartas distintas · $copies copias")
        LinearProgressIndicator(progress = { if (inventory.isEmpty()) 0f else available.toFloat() / inventory.size }, modifier = Modifier.fillMaxWidth())
        Text(
          if (connected) "ChatGPT está conectado" + (account?.email?.takeIf { it.isNotBlank() }?.let { " · $it" } ?: "")
          else "Conecta ChatGPT para crear y revisar propuestas.",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }

    DexPanel(Modifier.fillMaxWidth()) {
      Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Constructor con ChatGPT", style = MaterialTheme.typography.titleLarge)
        Text("Indica energía, estilo y cartas que quieras priorizar. ChatGPT usa esos criterios junto a las cartas disponibles.", style = MaterialTheme.typography.bodyMedium)
        Button(
          onClick = {
            if (!connected) onConnect()
            else if (draft?.cards?.isNotEmpty() == true) onContinue()
            else {
              main.newManualDeck()
              onAi()
            }
          },
          modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
          shape = MaterialTheme.shapes.medium,
        ) {
          Text(when {
            !connected -> "Conectar ChatGPT"
            draft?.cards?.isNotEmpty() == true -> "Continuar con ChatGPT"
            else -> "Diseñar mazo con ChatGPT"
          })
        }
        if (connected && draft?.cards?.isNotEmpty() == true) {
          OutlinedButton(onClick = { confirmNew = true }, modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium) {
            Text("Empezar otro mazo con ChatGPT")
          }
        }
      }
    }

    Text("El mazo no cambia hasta que revises y apliques la propuesta de ChatGPT.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
  }

  if (confirmNew) AlertDialog(
    onDismissRequest = { confirmNew = false },
    title = { Text("Empezar otro mazo") },
    text = { Text("El borrador actual se reemplazará. Los mazos guardados y la colección se conservarán.") },
    confirmButton = {
      TextButton(onClick = {
        confirmNew = false
        main.newManualDeck()
        onAi()
      }) { Text("Crear nuevo con ChatGPT") }
    },
    dismissButton = { TextButton(onClick = { confirmNew = false }) { Text("Cancelar") } },
  )
}
