package com.example

import com.example.data.ai.*
import com.example.data.meta.*
import com.example.data.util.DiagnosticSummary
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import javax.crypto.spec.SecretKeySpec

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ProfilesMetaTest {
  @Test fun switchingAndUpdatingProfilesPreservesIndependentSecrets() {
    val store = AiConnectionStore(RuntimeEnvironment.getApplication()) { SecretKeySpec(ByteArray(32) { 8 }, "AES") }
    store.clear()
    val gemini = AiConnection(apiKey = "gemini-secret", id = "g", label = "Gemini personal")
    val other = AiConnection(AiProvider.COMPATIBLE, "model", "other-secret", "https://example.com/v1/chat/completions", "o", "Otra")
    store.save(gemini); store.save(other)
    assertEquals(2, store.loadProfiles().entries.size)
    store.saveProfiles(store.loadProfiles().copy(activeId = "g"))
    assertEquals("gemini-secret", store.load()!!.apiKey)
    store.save(gemini.copy(model = "different-model"))
    assertEquals("other-secret", store.loadProfiles().entries.first { it.id == "o" }.apiKey)
    assertFalse(store.loadProfiles().toString().contains("secret"))
    store.clear()
  }
  @Test fun metaCacheRoundTripPreservesCardsEnergyAndCounts() {
    val deck = MetaDeck("Sample", 3, 7, 2, 1, mapOf("A1-001" to 2, "P-A-005" to 2), listOf("Planta"), "abc")
    val value = MetaSnapshot("2026-10-03T12:00:00Z", 2, 3, listOf(deck), 1)
    assertEquals(value, TournamentRepository.parse(TournamentRepository.encode(value)))
    assertEquals("Psíquico", TournamentRepository.energyNames["Psychic"])
  }
  @Test fun summaryOnlyAcceptsFixedErrorCategories() {
    val text = DiagnosticSummary.create(sequenceOf("[2026-10-03T09:00:00Z] [AI_CONNECTED] category=QUOTA private-secret"), "1", 34)
    assertTrue(text.contains("QUOTA")); assertFalse(text.contains("private-secret"))
  }
}
