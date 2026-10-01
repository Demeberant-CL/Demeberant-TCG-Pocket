package com.example.data.util

import com.example.data.repository.ParsedCsvCard
import com.example.data.repository.CardWithInventory
import java.text.Normalizer
import java.util.Locale

/** Quoted delimiters, doubled quotes, CRLF, BOM and multiline fields are supported. */
object CollectionCsv {
  fun parse(text: String): List<ParsedCsvCard> {
    val input = text.removePrefix("\uFEFF")
    require(input.isNotBlank()) { "El archivo CSV está vacío." }
    val header = input.lineSequence().first()
    val delimiter = if (header.count { it == ';' } > header.count { it == ',' }) ';' else ','
    val rows = readRows(input, delimiter).filter { row -> row.any { it.isNotBlank() } }
    require(rows.isNotEmpty()) { "El archivo CSV está vacío." }
    fun normalize(value: String) = Normalizer.normalize(value.trim().lowercase(Locale.ROOT), Normalizer.Form.NFD)
      .replace(Regex("\\p{M}"), "")
    val headers = rows.first().map(::normalize)
    fun column(vararg aliases: String) = headers.indexOfFirst { it in aliases }
    val set = column("set", "expansion", "coleccion")
    val id = column("id", "card_id", "codigo", "numero")
    val name = column("nombre", "name")
    val rarity = column("rareza", "rarity")
    val qty = column("cantidad", "quantity", "count", "copias")
    require(listOf(set, id, name, rarity, qty).all { it >= 0 }) {
      "Faltan columnas: Set, ID, Nombre, Rareza y Cantidad."
    }
    val seen = mutableSetOf<String>()
    val parsed = rows.drop(1).mapIndexed { index, row ->
      require(row.size == headers.size) { "Fila ${index + 2}: número de campos incorrecto." }
      val canonicalId = CardId.normalize("${row[set].trim()}-${row[id].trim()}")
      require(seen.add(canonicalId)) { "Fila ${index + 2}: carta duplicada $canonicalId." }
      val (setCode, number) = CardId.split(canonicalId)
      val quantity = row[qty].trim().toIntOrNull()
      require(quantity != null && quantity >= 0) { "Fila ${index + 2}: cantidad no válida." }
      require(row[name].isNotBlank()) { "Fila ${index + 2}: nombre vacío." }
      com.example.data.model.CardRarity.fromSymbol(row[rarity])
      ParsedCsvCard(setCode, number, row[name].trim(), row[rarity].trim(), quantity, quantity > 0)
    }
    require(parsed.isNotEmpty()) { "El CSV no contiene cartas." }
    return parsed
  }

  fun export(cards: List<CardWithInventory>): String {
    fun quote(value: String) = "\"${value.replace("\"", "\"\"")}\""
    return buildString {
      appendLine("\"Set\",\"ID\",\"Nombre\",\"Rareza\",\"Cantidad\",\"Registrada\"")
      cards.sortedBy { it.card.id }.forEach { item ->
        val (set, number) = CardId.split(item.card.id)
        appendLine(listOf(set, number.toInt().toString(), item.card.name, item.card.rarity.symbol,
          item.ownedCount.toString(), if (item.ownedCount > 0) "sí" else "no").joinToString(",", transform = ::quote))
      }
    }.trimEnd()
  }

  private fun readRows(text: String, delimiter: Char): List<List<String>> {
    val rows = mutableListOf<List<String>>()
    val row = mutableListOf<String>()
    val field = StringBuilder()
    var quoted = false
    var closedQuote = false
    var i = 0
    fun finishField() { row.add(field.toString()); field.setLength(0); closedQuote = false }
    fun finishRow() { finishField(); rows.add(row.toList()); row.clear() }
    while (i < text.length) {
      val c = text[i]
      if (quoted) {
        if (c == '\"') {
          if (i + 1 < text.length && text[i + 1] == '\"') { field.append('"'); i++ }
          else { quoted = false; closedQuote = true }
        } else field.append(c)
      } else when (c) {
        '\"' -> { require(field.isEmpty() && !closedQuote) { "Comillas CSV no válidas." }; quoted = true }
        delimiter -> finishField()
        '\n' -> finishRow()
        '\r' -> { if (i + 1 < text.length && text[i + 1] == '\n') i++; finishRow() }
        else -> { require(!closedQuote || c.isWhitespace()) { "Contenido después de comillas CSV." }; if (!closedQuote) field.append(c) }
      }
      i++
    }
    require(!quoted) { "Campo CSV con comillas sin cerrar." }
    if (field.isNotEmpty() || row.isNotEmpty() || closedQuote) finishRow()
    return rows
  }
}
