package com.example.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

data class HelpTopic(val title: String, val steps: List<String>, val note: String)
object PocketHelp {
  val topics = linkedMapOf(
    "coleccion" to HelpTopic("Colección y filtros", listOf("Importa tu CSV desde Ajustes → Importar CSV.", "Busca un nombre o código. Todas muestra el catálogo; Tengo muestra cantidades mayores que cero; Faltan muestra cero; Deseos muestra tus favoritos.", "Toca una carta para cambiar su cantidad o marcar Deseos. Ver repetidas muestra más de una copia."), "Los filtros solo cambian lo que ves; no eliminan cartas."),
    "mazos" to HelpTopic("Crear y editar un mazo", listOf("Pulsa Crear con mis cartas y elige un tipo para obtener un borrador local. También puedes empezar con Nuevo vacío.", "En Añadir cartas busca y pulsa Añadir. En Mi mazo usa Quitar/Añadir copia. El contador debe llegar a 20.", "Las energías se sugieren por tipo. Revisa los costes de los ataques; puedes cambiar la selección.", "Pulsa Guardar mazo. En Mis mazos puedes reabrirlo, editar y guardar otra vez."), "Máximo dos copias por nombre, incluso con arte distinto. Se pueden guardar borradores incompletos. Crear un mazo nunca descuenta cartas de la colección."),
    "ia" to HelpTopic("Usar tu asistente IA", listOf("Elige Crear mazo o Mejorar mazo abierto. Para mejorar, abre primero un mazo de 20 cartas.", "Escribe lo que buscas y pulsa Preparar consulta. Revisa los datos y pulsa Copiar consulta.", "Abre ChatGPT u otra IA y pega la consulta. Copia toda su respuesta.", "Vuelve aquí y pulsa Pegar respuesta y Revisar mazo. Si es válida verás las cartas, las energías y las sustituciones.", "Pulsa Usar este mazo y después Guardar mazo en el editor."), "No necesitas conocer JSON: es el formato que pide la consulta. Una suscripción de IA no conecta automáticamente su cuenta a esta app. Tú decides a qué servicio enviar los datos; no añadas datos personales. La app verifica cantidades actuales y no guarda automáticamente."),
    "respaldo" to HelpTopic("CSV y respaldos", listOf("CSV sirve para cantidades y Deseos. En Ajustes pulsa Importar CSV o Pegar CSV para el archivo de Pokémon Zone.", "Exportar colección CSV guarda esas cantidades. Abre el archivo para revisar que tenga filas.", "Guardar respaldo completo guarda también mazos y ajustes en JSON.", "Restaurar respaldo completo muestra una confirmación antes de aplicar los registros."), "Las cartas ausentes se conservan. Las cantidades incluidas se reemplazan; los mazos idénticos no se duplican. CSV y respaldo JSON son archivos diferentes."),
    "qr" to HelpTopic("Pasar un mazo al juego", listOf("Abre un mazo con exactamente 20 cartas y revisa sus energías.", "Pulsa Exportar QR para el juego → Guardar PNG.", "En Pokémon TCG Pocket abre la opción de escanear código de mazo y selecciona el PNG original.", "Si falla, prueba QR alternativo y guarda esa imagen. No uses una captura recortada o comprimida."), "El juego decide disponibilidad y reglas. No se garantiza que todos los códigos sean aceptados. No transfiere cartas y las variantes de arte pueden cambiar."),
    "sandbox" to HelpTopic("Tapete de práctica", listOf("Abre un mazo de 20 cartas y ve a Análisis → Sandbox.", "Pulsa Iniciar con el mazo abierto. Mueve cartas de la mano a Activo o Banca.", "Usa Robar, Siguiente turno, Energía y Daño para practicar manualmente.", "Deshacer recupera el estado anterior. Nueva práctica reinicia solo el tablero."), "No ejecuta ataques ni todas las reglas. No modifica la colección ni los mazos guardados."),
    "canjes" to HelpTopic("Organizar canjes", listOf("Para ofrecer muestra copias sobrantes después de la reserva de una o dos.", "Deseos muestra las cartas que has marcado en Colección.", "Comparar otro CSV propone intercambios por rareza. Copiar lista prepara el texto para compartir."), "No realiza canjes ni descuenta cartas. Comprueba requisitos y costes dentro del juego."),
    "analisis" to HelpTopic("Análisis, sobres y efectos", listOf("Sobres ordena opciones por faltantes, Deseos y objetivos, sin inventar tasas de apertura.", "En Calculadora introduce la tasa total por sobre y el número de intentos.", "Efectos busca en reglas descargadas. Abre los detalles de cartas o indexa hasta 25 de una búsqueda.", "Plantillas A1 son puntos de partida históricos, no el meta actual."), "La cobertura no garantiza una carta. Datos incompletos limitan las sugerencias; las etiquetas por efectos son estimadas."),
    "diagnostico" to HelpTopic("Guardar o compartir diagnóstico", listOf("Abre Ajustes → Diagnóstico.", "Guardar diagnóstico TXT permite elegir carpeta y nombre.", "Compartir diagnóstico TXT abre el selector de aplicaciones.", "Revisa el contenido antes de enviarlo cuando necesites ayuda."), "Registra eventos técnicos, no consultas IA, credenciales ni cuerpos privados. No es un respaldo de tu colección."),
    "temas" to HelpTopic("Tema de la app", listOf("Abre Ajustes y elige Claro, Oscuro o Automático.", "Automático sigue el tema del teléfono. La elección se conserva al volver a abrir la app."), "La interfaz está en español fijo.")
  )
}

@Composable
fun HelpButton(topic: String? = null) {
  var shown by remember { mutableStateOf(false) }
  TextButton(onClick = { shown = true }) { Text(if (topic == null) "Ayuda y tutoriales" else "¿Cómo se usa?") }
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
