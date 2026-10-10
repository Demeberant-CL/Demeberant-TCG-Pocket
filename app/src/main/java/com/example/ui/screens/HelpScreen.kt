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
    "coleccion" to HelpTopic("Colección y filtros", listOf("Toca el avatar del perfil, elige un entrenador y pulsa Usar avatar. Puedes cambiarlo cuando quieras.", "Importa tu CSV desde Ajustes → Importar CSV.", "Busca un nombre o código. Todas muestra el catálogo; Tengo muestra cantidades mayores que cero; Faltan muestra cero; Deseos muestra tus favoritos.", "Toca una carta para cambiar su cantidad o marcar Deseos. Repetidas muestra más de una copia."), "Los filtros solo cambian lo que ves; no eliminan cartas."),
    "inicio" to HelpTopic("Tu pantalla de inicio", listOf("Inicio resume cartas registradas, copias y mazos guardados; toca el avatar para cambiarlo.", "Inicio muestra los mazos recientes; toca uno para abrirlo. Continuar editando retoma el borrador abierto.", "La IA se abre desde Crear mazo o el editor. Configura tu proveedor desde Ajustes → Conexiones IA; abrirlo no consulta la API.", "Ver meta y actualizar abre resultados guardados. Solo Actualizar meta descarga datos públicos. También está en Más."), "La barra principal es Inicio, Colección, Mazos y Más. La guía está en Más y no se consulta IA automáticamente."),
    "mazos" to HelpTopic("Crear y editar un mazo", listOf("Abre Mazos → + Nuevo mazo. Elige Crear manualmente, Crear con IA o Empezar con una plantilla. Las plantillas son puntos de partida históricos.", "En Cartas → Añadir cartas busca y pulsa +. En Mi mazo usa − y +. El contador debe llegar a 20.", "En Energías revisa la sugerencia por tipo y selecciona de una a tres. En Notas cambia el nombre, escribe tu estrategia o comparte la lista.", "Guardar permanece al pie del editor. Mis mazos muestra portada, energías y copias disponibles; pulsa Editar para reabrirlo. Eliminar pide confirmación."), "Máximo dos copias por nombre, incluso con arte distinto. Se pueden guardar borradores incompletos. Crear un mazo nunca descuenta cartas de la colección."),
    "ia" to HelpTopic("Conectar tu asistente IA", listOf("Abre Ajustes → Conexiones IA para seleccionar un perfil, cambiar modelo o añadir Gemini, OpenAI u otra API compatible.", "Obtén tu propia clave en la página del proveedor, introduce clave y modelo y pulsa Guardar conexión. ChatGPT Plus no incluye crédito API.", "Desde Crear con IA escribe tu objetivo y elige un tipo. En el editor puedes usar Hablar con IA, Completar faltantes o Mejorar con IA.", "Completar faltantes conserva las cartas disponibles del borrador. Mejorar con IA usa el mazo abierto como referencia. Ver cambios muestra una propuesta antes de modificar el borrador.", "Envía la consulta, revisa Ver cambios y pulsa Aplicar cambios solo si te sirve. Guarda el mazo en el editor. Si falla, Reintentar consulta conserva la solicitud; no necesitas escribirla otra vez."), "Los errores temporales pueden tener hasta dos reintentos automáticos. La cuota gratuita depende de tu cuenta y modelo; si falla no se cambia a otro proveedor. Cada conexión conserva su clave cifrada y modelo. Cambia entre perfiles sin eliminarlos. Fuera de respaldos y diagnósticos. No envía perfil ni Friend ID. No hay pruebas reales del proveedor sin tu clave."),
    "respaldo" to HelpTopic("CSV y respaldos", listOf("CSV sirve para cantidades y Deseos. En Ajustes pulsa Importar CSV o Pegar CSV para el archivo de Pokémon Zone.", "Exportar colección CSV guarda esas cantidades. Abre el archivo para revisar que tenga filas.", "Guardar respaldo completo guarda también mazos y ajustes en JSON.", "Restaurar respaldo completo muestra una confirmación antes de aplicar los registros."), "Las cartas ausentes se conservan. Las cantidades incluidas se reemplazan; los mazos idénticos no se duplican. CSV y respaldo JSON son archivos diferentes."),
    "qr" to HelpTopic("Pasar un mazo al juego", listOf("Abre un mazo con exactamente 20 cartas y revisa sus energías.", "Pulsa Exportar QR para el juego → Guardar PNG.", "En Pokémon TCG Pocket abre la opción de escanear código de mazo y selecciona el PNG original.", "Si falla, prueba QR alternativo y guarda esa imagen. No uses una captura recortada o comprimida."), "El juego decide disponibilidad y reglas. No se garantiza que todos los códigos sean aceptados. No transfiere cartas y las variantes de arte pueden cambiar."),
    "sandbox" to HelpTopic("Tapete de práctica", listOf("Abre un mazo de 20 cartas y ve a Notas → Tapete de práctica. También está en Más.", "Pulsa Iniciar con el mazo abierto. Mueve cartas de la mano a Activo o Banca.", "Usa Robar, Siguiente turno, Energía y Daño para practicar manualmente.", "Deshacer recupera el estado anterior. Nueva práctica reinicia solo el tablero."), "La mano inicial intercambia una carta para garantizar un básico si hace falta; es una aproximación. No ejecuta ataques ni todas las reglas. No modifica la colección ni los mazos guardados."),
    "canjes" to HelpTopic("Organizar canjes", listOf("Para ofrecer muestra copias sobrantes después de la reserva de una o dos.", "Deseos muestra las cartas que has marcado en Colección.", "Comparar otro CSV propone intercambios por rareza. Copiar lista prepara el texto para compartir."), "No realiza canjes ni descuenta cartas. Comprueba requisitos y costes dentro del juego."),
    "analisis" to HelpTopic("Análisis, sobres y efectos", listOf("Sobres ordena opciones por faltantes, Deseos y objetivos, sin inventar tasas de apertura.", "En Calculadora introduce la tasa total por sobre que muestra el juego, no la tasa de una sola ranura, y el número de intentos. Para robar cartas usa Notas → Probabilidad de robo.", "Colección → Buscar por efectos consulta los datos descargados. Puedes cargar los datos de una búsqueda y cancelar la descarga.", "Crear mazo → Empezar con una plantilla ofrece puntos de partida históricos, no el meta actual."), "Sobres suma 1 punto por faltante, 3 adicionales por Deseo y 5 por objetivo. Excluye promociones. La cobertura no garantiza una carta. Datos incompletos limitan las sugerencias; las etiquetas por efectos son estimadas."),
    "diagnostico" to HelpTopic("Enviar diagnóstico sin adjuntos", listOf("Abre Ajustes → Diagnóstico y ayuda.", "Pulsa Copiar resumen y pégalo como mensaje aquí. Añade pantalla, acción y qué ocurrió.", "Compartir texto sin archivo envía el mismo resumen a una aplicación que elijas.", "Guardar o Compartir diagnóstico ZIP incluye el registro completo y el resumen, sin comprimirlo manualmente. Guardar resumen TXT crea un archivo breve. Guardar/Compartir diagnóstico TXT mantiene el registro completo si lo necesitas."), "El resumen tiene como máximo 6000 caracteres, versión y fallos agrupados por servicio y categoría, sin consultas IA, URLs ni mensajes privados. El ZIP conserva las trazas y las causas, los cambios de pantalla y las etapas de las consultas. Un bloqueo superior a 8 segundos en primer plano deja una muestra de la pila, sin asegurar que Android lo declare ANR. Android 11 o posterior también aporta motivos de cierres anteriores cuando están disponibles. No incluye todos los eventos ni prueba que un flujo funcione. Las consultas SQL internas ya no se registran una por una. No puede corregir el envío de adjuntos de ChatGPT ni registrar errores internos del juego."),
    "temas" to HelpTopic("Tema de la app", listOf("Abre Ajustes y elige Claro, Oscuro o Automático.", "Automático sigue el tema del teléfono. La elección se conserva al volver a abrir la app."), "La interfaz está en español fijo.")
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
