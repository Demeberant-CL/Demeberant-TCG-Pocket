package com.example.data.util

import android.content.Context
import android.util.AtomicFile
import com.example.data.repository.GeneratedDeck
import com.example.data.repository.DeckBuilderEngine
import org.json.JSONObject
import java.io.File

data class DeckDraft(val deck: GeneratedDeck, val editingId: Long, val automaticEnergies: Boolean)

/** One local recovery draft; atomic replacement never modifies the saved library. */
class DeckDraftStore(context: Context) {
  private val file = AtomicFile(File(context.filesDir, "deck-draft.json"))
  fun read(): DeckDraft? {
    if (!file.baseFile.exists() && !File(file.baseFile.path + ".bak").exists()) return null
    val root = JSONObject(file.openRead().use { it.readBytesBounded(100_000).toString(Charsets.UTF_8) })
    require(root.getInt("version") == 1)
    val content = root.getString("cards")
    val cards = DeckCodec.decode(content)
    val deck = GeneratedDeck(root.getString("name"), root.getString("archetype"), root.getString("strategy"),
      cards, cards.sumOf { it.count }, DeckBuilderEngine.validate(cards), DeckCodec.energies(content))
    return DeckDraft(deck, root.getLong("editingId"), root.getBoolean("automaticEnergies"))
  }
  fun write(draft: DeckDraft) {
    val deck = draft.deck
    val bytes = JSONObject().put("version", 1).put("name", deck.name).put("archetype", deck.archetype)
      .put("strategy", deck.strategy).put("cards", DeckCodec.encode(deck.cards, deck.energyTypes))
      .put("editingId", draft.editingId).put("automaticEnergies", draft.automaticEnergies).toString().toByteArray(Charsets.UTF_8)
    require(bytes.size <= 100_000)
    val stream = file.startWrite()
    try { stream.write(bytes); file.finishWrite(stream) } catch (e: Exception) { file.failWrite(stream); throw e }
  }
}
