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
    "coleccion" to HelpTopic("Colección y filtros", listOf("Toca el avatar del perfil, elige un entrenador y pulsa Usar avatar. Puedes cambiarlo cuando quieras.", "Importa tu CSV desde Ajustes → Importar CSV.", "Busca un nombre o código. Todas muestra el catálogo; Tengo muestra cantidades mayores que cero; Faltan muestra cero; Deseos muestra tus favoritos.", "Toca una carta para cambiar su cantidad o marcar Deseos. Ver repetidas muestra más de una copia."), "Los filtros solo cambian lo que ves; no eliminan cartas."),
    "mazos" to HelpTopic("Crear y editar un mazo", listOf("Pulsa Crear con mis cartas y elige un tipo para obtener un borrador local. También puedes empezar con Nuevo vacío.", "En Añadir cartas busca y pulsa Añadir. En Mi mazo usa Quitar/Añadir copia. El contador debe llegar a 20.", "Las energías se sugieren por tipo. Revisa los costes de los ataques; puedes cambiar la selección.", "Pulsa Guardar mazo. En Mis mazos puedes reabrirlo, editar y guardar otra vez."), "Máximo dos copias por nombre, incluso con arte distinto. Se pueden guardar borradores incompletos. Crear un mazo nunca descuenta cartas de la colección."),
    "ia" to HelpTopic("Conectar tu asistente IA", listOf("Abre Configurar IA y elige Gemini, OpenAI u otra API compatible.", "Obtén tu propia clave en la página del proveedor, introduce clave y modelo y pulsa Guardar conexión. ChatGPT Plus no incluye crédito API.", "Escribe qué mazo buscas, selecciona un tipo si hay demasiadas cartas y pulsa Crear con mi IA.", "Para Completar mazo abre un objetivo de 20 cartas; conserva las disponibles y sustituye únicamente las faltantes.", "Confirma el envío, revisa las cartas y energías y pulsa Usar este mazo. Guarda el borrador en Mazos."), "Una consulta por pulsación. La cuota gratuita depende de tu cuenta y modelo; si falla no se cambia a otro proveedor. Cada conexión conserva su clave cifrada y modelo. Cambia entre perfiles sin eliminarlos. Fuera de respaldos y diagnósticos. No envía perfil ni Friend ID. No hay pruebas reales del proveedor sin tu clave."),
    "respaldo" to HelpTopic("CSV y respaldos", listOf("CSV sirve para cantidades y Deseos. En Ajustes pulsa Importar CSV o Pegar CSV para el archivo de Pokémon Zone.", "Exportar colección CSV guarda esas cantidades. Abre el archivo para revisar que tenga filas.", "Guardar respaldo completo guarda también mazos y ajustes en JSON.", "Restaurar respaldo completo muestra una confirmación antes de aplicar los registros."), "Las cartas ausentes se conservan. Las cantidades incluidas se reemplazan; los mazos idénticos no se duplican. CSV y respaldo JSON son archivos diferentes."),
    "qr" to HelpTopic("Pasar un mazo al juego", listOf("Abre un mazo con exactamente 20 cartas y revisa sus energías.", "Pulsa Exportar QR para el juego → Guardar PNG.", "En Pokémon TCG Pocket abre la opción de escanear código de mazo y selecciona el PNG original.", "Si falla, prueba QR alternativo y guarda esa imagen. No uses una captura recortada o comprimida."), "El juego decide disponibilidad y reglas. No se garantiza que todos los códigos sean aceptados. No transfiere cartas y las variantes de arte pueden cambiar."),
    "sandbox" to HelpTopic("Tapete de práctica", listOf("Abre un mazo de 20 cartas y ve a Más → Simulador.", "Pulsa Iniciar con el mazo abierto. Mueve cartas de la mano a Activo o Banca.", "Usa Robar, Siguiente turno, Energía y Daño para practicar manualmente.", "Deshacer recupera el estado anterior. Nueva práctica reinicia solo el tablero."), "No ejecuta ataques ni todas las reglas. No modifica la colección ni los mazos guardados."),
    "canjes" to HelpTopic("Organizar canjes", listOf("Para ofrecer muestra copias sobrantes después de la reserva de una o dos.", "Deseos muestra las cartas que has marcado en Colección.", "Comparar otro CSV propone intercambios por rareza. Copiar lista prepara el texto para compartir."), "No realiza canjes ni descuenta cartas. Comprueba requisitos y costes dentro del juego."),
    "analisis" to HelpTopic("Análisis, sobres y efectos", listOf("Sobres ordena opciones por faltantes, Deseos y objetivos, sin inventar tasas de apertura.", "En Calculadora introduce la tasa total por sobre y el número de intentos.", "Efectos busca en reglas descargadas. Abre los detalles de cartas o indexa hasta 25 de una búsqueda.", "Plantillas A1 son puntos de partida históricos, no el meta actual."), "La cobertura no garantiza una carta. Datos incompletos limitan las sugerencias; las etiquetas por efectos son estimadas."),
    "diagnostico" to HelpTopic("Enviar diagnóstico sin adjuntos", listOf("Abre Ajustes → Diagnóstico → Copiar diagnóstico para el chat.", "Pulsa Copiar resumen y pégalo como mensaje aquí. Añade pantalla, acción y qué ocurrió.", "Compartir texto sin archivo envía el mismo resumen a una aplicación que elijas.", "Guardar resumen TXT crea un archivo breve. Guardar/Compartir diagnóstico TXT mantiene el registro completo si lo necesitas."), "El resumen tiene como máximo 6000 caracteres, versión y fallos agrupados, sin consultas IA, URLs ni mensajes privados. No incluye todos los eventos ni prueba que un flujo funcione. Las consultas SQL internas ya no se registran una por una. No puede corregir el envío de adjuntos de ChatGPT ni registrar errores internos del juego."),
    "temas" to HelpTopic("Tema de la app", listOf("Abre Ajustes y elige Claro, Oscuro o Automático.", "Automático sigue el tema del teléfono. La elección se conserva al volver a abrir la app."), "La interfaz está en español fijo.")
  )
}

@Composable
fun HelpButton(topic: String? = null) {
  var shown by remember { mutableStateOf(false) }
  TextButton(onClick = { shown = true }) {
    Icon(Icons.AutoMirrored.Filled.MenuBook, contentDescription = null, modifier = Modifier.size(20.dp))
    Spacer(Modifier.width(6.dp))
    Text(if (topic == null) "Ayuda y tutoriales" else "Guía")
  }
  if (shown) HelpDialog(topic) { shown = false }
}

@Composable
fun HelpDialog(initial: String? = null, onClose: () -> Unit) {
  var selected by rememberSaveable(initial) { mutableStateOf(initial) }
  AlertDialog(onDismissRequest = onClose, title = { Text(selected?.let { PocketHelp.topics[it]?.title } ?: "Ayuda y tutoriales") },
    text = {
      LazyColumn(Modifier.fillMaxWidth().heightIn(max = 440.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        val topic = selected?.let { PocketHelp.topics[it] }
        if (topic == null) {
          PocketHelp.topics.forEach { (key, value) -> item(key) {
            OutlinedButton(onClick = { selected = key }, modifier = Modifier.fillMaxWidth()) { Text(value.title) }
          } }
        } else {
          topic.steps.forEachIndexed { index, step -> item { Text("${index + 1}. $step") } }
          item { Text(topic.note, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
      }
    }, confirmButton = { TextButton(onClick = onClose) { Text("Cerrar") } },
    dismissButton = { if (selected != null) TextButton(onClick = { selected = null }) { Text("Todos los tutoriales") } })
}

@Composable
fun HelpScreen(modifier: Modifier = Modifier) {
  var query by rememberSaveable { mutableStateOf("") }
  var selected by rememberSaveable { mutableStateOf<String?>(null) }
  LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
    item { Text("Ayuda y tutoriales", style = MaterialTheme.typography.headlineSmall)
      OutlinedTextField(query, { query = it }, label = { Text("Buscar ayuda") }, modifier = Modifier.fillMaxWidth()) }
    PocketHelp.topics.filter { (_, v) -> (v.title + v.steps.joinToString()).contains(query, ignoreCase = true) }.forEach { (key, topic) ->
      item(key) { OutlinedButton(onClick = { selected = key }, modifier = Modifier.fillMaxWidth()) { Text(topic.title) } }
    }
  }
  selected?.let { HelpDialog(it) { selected = null } }
}
