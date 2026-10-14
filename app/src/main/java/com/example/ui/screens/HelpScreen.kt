package com.example.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

data class HelpTopic(val title: String, val steps: List<String>, val note: String)
object PocketHelp {
  val topics = linkedMapOf(
    "inicio" to HelpTopic("Pantalla principal", listOf(
      "TCG Lab reúne el resumen de tu colección, cartas destacadas, mazos recientes y el acceso a ChatGPT.",
      "Sincronizar importa o actualiza datos disponibles en Pokémon Zone. Las misiones aparecen cuando esa fuente las incluye.",
      "Abre Ajustes para conectar tu cuenta de ChatGPT y elegir un modelo.",
      "Las secciones se abren desde la pantalla principal; usa la flecha superior para volver a TCG Lab."
    ), "La colección y los mazos se guardan en este dispositivo."),
    "coleccion" to HelpTopic("Colección y filtros", listOf(
      "Abre Colección desde el resumen de TCG Lab o desde sus cartas destacadas.",
      "Sincronizar importa o actualiza las cantidades disponibles de Pokémon Zone.",
      "Busca por nombre o código y filtra por expansión, rareza, cartas que tienes, faltantes o Deseos.",
      "Abre una carta para cambiar su cantidad o marcarla como Deseo."
    ), "Los filtros solo cambian lo que ves; no eliminan cartas."),
    "mazos" to HelpTopic("Crear y editar mazos", listOf(
      "En TCG Lab toca Crear un mazo o abre Mazos para ver los guardados.",
      "Puedes crear a mano o pedir a ChatGPT una propuesta basada en las cartas registradas.",
      "Revisa las cartas y energías, y completa el mazo con 20 cartas.",
      "Guarda el mazo cuando esté listo. Editarlo no descuenta cartas de tu colección."
    ), "Las propuestas de ChatGPT son sugerencias: revisa las reglas y disponibilidad dentro del juego."),
    "ia" to HelpTopic("Conectar ChatGPT", listOf(
      "Abre Ajustes → Conexión ChatGPT e inicia sesión con tu cuenta de ChatGPT.",
      "Carga la lista de modelos y selecciona el que quieras usar.",
      "Vuelve a TCG Lab y crea un mazo con ChatGPT. Puedes revisar y editar la propuesta antes de guardarla."
    ), "La app usa los límites y condiciones de tu cuenta ChatGPT. No necesitas una clave API para esta conexión."),
    "respaldo" to HelpTopic("CSV y respaldos", listOf(
      "En Ajustes → Apariencia y datos puedes importar o exportar la colección en CSV.",
      "El respaldo completo guarda también mazos y ajustes.",
      "Restaurar un respaldo solicita confirmación antes de aplicar los datos."
    ), "CSV y respaldo completo son formatos distintos. Revisa el archivo antes de restaurar."),
    "qr" to HelpTopic("Pasar un mazo al juego", listOf(
      "Abre un mazo de 20 cartas y revisa sus energías.",
      "Usa Exportar QR para el juego y guarda la imagen.",
      "En Pokémon TCG Pocket escanea el QR o abre la imagen desde el juego."
    ), "El juego decide disponibilidad y reglas. El QR no transfiere cartas."),
    "sandbox" to HelpTopic("Tapete de práctica", listOf(
      "Abre Herramientas del juego → Tapete de práctica.",
      "Elige un mazo y mueve cartas manualmente entre mano, Activo y Banca.",
      "Usa Robar, Siguiente turno, Energía y Daño para practicar."
    ), "Es una herramienta manual; no ejecuta todas las reglas y no modifica colección ni mazos guardados."),
    "canjes" to HelpTopic("Organizar intercambios", listOf(
      "Abre Herramientas del juego → Intercambios.",
      "Revisa sugerencias según tus copias y cartas marcadas como Deseo.",
      "Puedes comparar un CSV y copiar una propuesta para compartirla."
    ), "La app no realiza canjes ni descuenta cartas. Comprueba requisitos y costes dentro del juego."),
    "analisis" to HelpTopic("Sobres, probabilidades y efectos", listOf(
      "Herramientas del juego reúne el recomendador de sobres, la calculadora y los filtros por efectos.",
      "Para calcular probabilidades, introduce las tasas que muestra el juego.",
      "Las sugerencias dependen de los datos disponibles y no garantizan una carta."
    ), "Comprueba las tasas y reglas actuales en Pokémon TCG Pocket."),
    "diagnostico" to HelpTopic("Diagnóstico y ayuda", listOf(
      "En Ajustes abre Diagnóstico y ayuda.",
      "Copia el resumen y compártelo junto con la pantalla y los pasos que causaron el problema."
    ), "El diagnóstico ayuda a describir fallos; no garantiza que una función esté operativa."),
    "temas" to HelpTopic("Tema de la app", listOf(
      "Abre Ajustes → Apariencia y datos para cambiar entre tema claro, oscuro o automático.",
      "La selección se conserva al volver a abrir la app."
    ), "TCG Lab usa una sola distribución de pantalla.")
  )
}

@Composable
fun HelpScreen(modifier: Modifier = Modifier) {
  var query by rememberSaveable { mutableStateOf("") }
  var selected by rememberSaveable { mutableStateOf<String?>(null) }
  val topic = selected?.let { PocketHelp.topics[it] }
  val listState = remember(selected) { androidx.compose.foundation.lazy.LazyListState() }
  androidx.activity.compose.BackHandler(enabled = selected != null) { selected = null }
  val groups = remember { linkedMapOf(
    "Primeros pasos" to listOf("inicio", "coleccion", "temas"),
    "Mazos y juego" to listOf("mazos", "ia", "qr", "sandbox"),
    "Herramientas" to listOf("canjes", "analisis"),
    "Datos y soporte" to listOf("respaldo", "diagnostico")
  ) }
  val visible = remember(query) { PocketHelp.topics.filter { (_, value) ->
    (value.title + value.steps.joinToString() + value.note).contains(query, ignoreCase = true)
  } }
  LazyColumn(modifier.fillMaxSize(), state = listState, contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
    item {
      if (topic != null) TextButton(onClick = { selected = null }) { Text("← Tutoriales") }
      Text(topic?.title ?: "Tutoriales", style = MaterialTheme.typography.headlineSmall)
    }
    if (topic != null) {
      topic.steps.forEachIndexed { index, step -> item(key = "step-$index") {
        OutlinedCard(Modifier.fillMaxWidth()) {
          Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Paso ${index + 1}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            Text(step)
          }
        }
      } }
      item { Text(topic.note, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    } else {
      item { OutlinedTextField(query, { query = it }, label = { Text("Buscar tutorial") }, modifier = Modifier.fillMaxWidth(), singleLine = true) }
      if (visible.isEmpty()) item { Text("No hay tutoriales con esa búsqueda.") }
      groups.forEach { (title, keys) ->
        val matching = keys.filter { it in visible }
        if (matching.isNotEmpty()) {
          item(key = title) { Text(title, style = MaterialTheme.typography.titleMedium) }
          matching.forEach { key -> item(key = key) {
            OutlinedCard(onClick = { selected = key }, modifier = Modifier.fillMaxWidth()) {
              Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(PocketHelp.topics.getValue(key).title, modifier = Modifier.weight(1f))
                Text("›", color = MaterialTheme.colorScheme.primary)
              }
            }
          } }
        }
      }
    }
  }
}
