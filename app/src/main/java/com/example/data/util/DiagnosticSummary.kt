package com.example.data.util

/** Compact allowlisted report: never copies event messages, URLs, bodies or exception messages. */
object DiagnosticSummary {
  const val MAX_CHARS = 6000
  private val header = Regex("^\\[([0-9TZ:.+\\-]{10,40})] \\[([A-Z_]{1,80})]")
  private val statusPattern = Regex("\\bstatus=([0-9]{3})\\b")
  private val failureTags = setOf("FATAL_CRASH", "SETTINGS_WRITE", "INVENTORY_WRITE", "BACKUP_RESTORE", "DECK_BUILD", "SAVE_DECK", "LOAD_DECK", "DELETE_DECK", "DECK_CHAT", "UI_STALL", "DIAGNOSTIC_HEALTH", "DETAILS", "RULES_WRITE", "RULES_INDEX", "HTTP_FAIL", "ROOM_FAIL", "ROOM_READ", "RULES_READ",
    "RULES_FETCH", "QR_EXPORT", "LOG_SAVE", "LOG_EXPORT", "AI_CONNECTED", "AI_ASSIST", "AI_EXTERNAL_IMPORT", "INVENTORY_READ", "DECKS_READ", "AI_OPEN", "META_FETCH", "AI_MODELS")
  private val exception = Regex("^[A-Za-z][A-Za-z0-9_$]{0,79}(Exception|Error)$")
  private val frame = Regex("^  at (com\\.example\\.[A-Za-z0-9_.$]+\\((?:[A-Za-z0-9_.-]{1,100}:[0-9]{1,8}|Unknown Source|Native Method)\\))$")
  private data class Failure(val timestamp: String, val tag: String, var type: String = "Sin detalle privado",
    var origin: String = "", var count: Int = 1, var exceptionSeen: Boolean = false)

  fun create(lines: Sequence<String>, version: String, androidVersion: Int, dropped: Long = 0): String {
    var first = ""; var last = ""; var events = 0; var httpOk = 0; var roomRoutine = 0; var failures = 0
    var pending: Failure? = null
    val groups = linkedMapOf<String, Failure>()
    fun finish() {
      val value = pending ?: return
      val key = "${value.tag}|${value.type}|${value.origin}"
      val existing = groups.remove(key)
      if (existing != null) value.count += existing.count
      groups[key] = value
      if (groups.size > 100) groups.remove(groups.keys.first())
      pending = null
    }
    lines.forEach { line ->
      val match = header.find(line)
      if (match != null) {
        finish(); events++
        val timestamp = match.groupValues[1]; val tag = match.groupValues[2]
        if (first.isBlank()) first = timestamp
        last = timestamp
        if (tag == "HTTP_END") {
          val status = statusPattern.find(line)?.groupValues?.get(1)?.toIntOrNull()
          if (status != null && status in 200..299) httpOk++
          else if (status != null && status >= 400) { failures++; pending = Failure(timestamp, "HTTP_ERROR", "HTTP $status · " + when {
            line.contains("assets.tcgdex.net") || line.contains("source=IMAGES") -> "Imágenes"
            line.contains("api.tcgdex.net") || line.contains("source=CARD_DATA") -> "Datos de cartas"
            else -> "Otro servicio"
          }) }
        }
        if (tag == "ROOM_QUERY") roomRoutine++
        if (tag == "PROCESS_EXIT" && Regex("reason=(4|5|6)\\b").containsMatchIn(line)) {
          failures++; pending = Failure(timestamp, tag, "Cierre registrado por Android")
        }
        if (tag in failureTags) { failures++; pending = Failure(timestamp, tag)
          val category = Regex("category=(NETWORK|QUOTA|AUTH|MODEL|INCOMPLETE|INVALID_DECK|TIMEOUT|SERVICE|HTTP|RESPONSE|CONFIG)\\b").find(line)?.groupValues?.get(1)
          if (category != null) {
            val status = Regex("status=([0-9]{3})\\b").find(line)?.groupValues?.get(1)
            pending?.type = category + (status?.let { " · HTTP $it" } ?: "")
          }
        }
      } else pending?.let { value ->
        if (exception.matches(line) && !value.exceptionSeen) {
          value.type = if (value.type == "Sin detalle privado") line else "${value.type} · $line"
          value.exceptionSeen = true
        }
        if (value.origin.isEmpty()) frame.matchEntire(line)?.let { value.origin = it.groupValues[1] }
      }
    }
    finish()
    val safeVersion = version.replace(Regex("[^A-Za-z0-9._-]"), "").take(40)
    return buildString {
      appendLine("DIAGNÓSTICO BREVE · Pokémon TCG Pocket")
      appendLine("App: $safeVersion · Android API: $androidVersion")
      appendLine("Periodo UTC: ${first.ifBlank { "sin registros" }} → ${last.ifBlank { "sin registros" }}")
      appendLine("Eventos conservados (histórico, puede incluir versiones anteriores): $events · HTTP correctos: $httpOk · fallos registrados: $failures")
      if (dropped > 0) appendLine("Eventos omitidos por saturación en esta sesión: $dropped")
      if (roomRoutine > 0) appendLine("Operaciones SQL históricas resumidas: $roomRoutine. DROP no prueba borrado de colección.")
      appendLine("No incluye consultas IA, cuerpos, URLs, credenciales ni mensajes privados.")
      appendLine()
      if (groups.isEmpty()) appendLine("No hay fallos reconocidos en los registros conservados; esto no confirma todos los flujos.")
      else {
        appendLine("Últimos tipos de fallo (máximo 12; agrupados):")
        groups.values.toList().takeLast(12).forEach { value ->
          appendLine("${value.timestamp} · ${value.tag} · ${value.type} · ${value.count} vez/veces")
          if (value.origin.isNotBlank()) appendLine("  ${value.origin}")
        }
        if (groups.size > 12) appendLine("Se omitieron otros tipos de fallo. El TXT completo conserva más detalle técnico.")
      }
      appendLine()
      appendLine("Añade: pantalla, acción realizada, qué esperabas y qué ocurrió.")
      appendLine("El error de escaneo dentro del juego no queda registrado por esta app.")
    }.take(MAX_CHARS)
  }
}
