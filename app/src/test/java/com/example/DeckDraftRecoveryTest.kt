package com.example

import com.example.data.repository.*
import com.example.data.util.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DeckDraftRecoveryTest {
  @Test fun draftRestoresNotesEnergiesCopiesAndSavedDeckIdentityWithoutChangingLibrary() {
    val context = RuntimeEnvironment.getApplication()
    CardCatalog.loadBundled(context)
    val file = File(context.filesDir, "deck-draft.json")
    file.delete()
    try {
      val store = DeckDraftStore(context)
      assertNull(store.read())
      val card = CardCatalog.getCardById("A1-001")!!
      val deck = GeneratedDeck("Mi prueba", "Manual", "Notas conservadas", listOf(DeckCardEntry(card, 2)), 2,
        DeckBuilderEngine.validate(listOf(DeckCardEntry(card, 2))), listOf("Planta"))
      val draft = DeckDraft(deck, 17L, false)
      store.write(draft)
      assertEquals(draft, DeckDraftStore(context).read())
      val changed = draft.copy(deck = deck.copy(name = "Editado", energyTypes = listOf("Planta", "Agua")))
      store.write(changed)
      assertEquals(changed, store.read())
      val original = file.readBytes()
      val invalid = draft.copy(deck = deck.copy(strategy = "x".repeat(100001)))
      assertTrue(runCatching { store.write(invalid) }.isFailure)
      assertArrayEquals(original, file.readBytes())
    } finally { file.delete(); File(file.path + ".bak").delete() }
  }
}
