package com.example

import com.example.domain.*
import com.example.data.repository.*
import com.example.data.model.*
import com.example.data.util.*
import com.google.zxing.*
import com.google.zxing.common.HybridBinarizer
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.json.JSONArray

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DeckQrTest {
  private val known = PocketDeckPayload(
    listOf(40, 40, 1410, 990, 320, 1000, 1030, 170, 30, 30, 290),
    listOf(13290, 13290, 980, 980, 13310, 18340, 18340, 7410, 7410), listOf(4))
  private val expected = "C5iWqJiWqJicApiaXpiXwJiaaJiahpiXKpiWnpiWnpiXogkAM+oAM+oAA9QAA9QAM/4AR6QAR6QAHPIAHPIBBA=="
  private fun identities() = RuntimeEnvironment.getApplication().assets.open("pocket-qr-entities.json")
    .bufferedReader().use { PocketDeckQr.readIdentities(it.readText()) }

  private fun deck(): GeneratedDeck {
    val mapping = identities().values
    val rows = known.trainers.map { entity -> mapping.first { it.kind == "trainer" && it.entity == entity } } +
      known.pokemon.map { entity -> mapping.first { it.kind == "pokemon" && it.entity == entity } }
    val entries = rows.groupBy { it.id }.map { (_, same) ->
      val row = same.first()
      DeckCardEntry(PokemonCard(row.id, row.name, BoosterPack.UNKNOWN, CardRarity.ONE_DIAMOND,
        0, if (row.kind == "trainer") "Entrenador" else "Rayo", "", ""), same.size)
    }
    return GeneratedDeck("Prueba", "", "", entries, 20, energyTypes = listOf("Rayo"))
  }

  @Test fun binaryEncoderMatchesCapturedGameFixtureExactly() {
    assertEquals(expected, PocketDeckQr.encode(known))
    // Captured from an in-game 20-Pokémon deck with Metal energy, upstream fixture.
    assertEquals("ABQAUSIAUSIAUSwAUSwAUZoARewARewATAQATAQATA4ATA4ATBgATBgASQwASQwASO4ASO4ASQIASQIASPgBCA==",
      PocketDeckQr.encode(PocketDeckPayload(emptyList(), listOf(20770,20770,20780,20780,20890,17900,17900,
        19460,19460,19470,19470,19480,19480,18700,18700,18670,18670,18690,18690,18680), listOf(8))))
  }

  @Test fun bundledMapCoversCatalogueAndDistinguishesTrainerPokemonNamespaces() {
    CardCatalog.loadBundled(RuntimeEnvironment.getApplication())
    val map = identities()
    assertEquals(4317, map.size)
    val missing = CardCatalog.ALL_CARDS.filter { it.source.startsWith("Catálogo comunitario") && it.id !in map }
    assertTrue(missing.toString(), missing.isEmpty())
    assertEquals(980, map.getValue("A1-098").entity)
    assertEquals(980, map.getValue("B1-224").entity)
    assertEquals("pokemon", map.getValue("A1-098").kind)
    assertEquals("trainer", map.getValue("B1-224").kind)
    assertEquals(map.getValue("A1-098").entity, map.getValue("A4-218").entity)
  }

  @Test fun resolverPreservesCountsAndEnergyOrderAndRejectsUnsupportedDrafts() {
    val good = deck()
    assertEquals(expected, PocketDeckQr.encode(PocketDeckQr.resolve(good, identities())))
    val multiple = PocketDeckQr.resolve(good.copy(energyTypes = listOf("Metal","Planta","Agua")), identities())
    assertEquals(listOf(8,1,3), multiple.energies)
    assertTrue(runCatching { PocketDeckQr.resolve(good.copy(totalCardCount = 19), identities()) }.isFailure)
    assertTrue(runCatching { PocketDeckQr.resolve(good.copy(energyTypes = emptyList()), identities()) }.isFailure)
    assertTrue(runCatching { PocketDeckQr.resolve(good.copy(energyTypes = listOf("Dragón")), identities()) }.isFailure)
    assertTrue(runCatching { PocketDeckQr.resolve(good.copy(energyTypes = listOf("Rayo","Rayo")), identities()) }.isFailure)
    val unknown = good.cards.mapIndexed { i, entry -> if (i == 0) entry.copy(card = entry.card.copy(id = "B4B-999")) else entry }
    assertTrue(runCatching { PocketDeckQr.resolve(good.copy(cards = unknown), identities()) }.isFailure)
    assertTrue(runCatching { PocketDeckQr.encode(known.copy(energies = listOf(10))) }.isFailure)
    assertTrue(runCatching { PocketDeckQr.encode(known.copy(energies = listOf(11))) }.isFailure)
  }

  @Test fun resolverRejectsThreeCopiesAcrossAlternateArt() {
    val map = identities()
    val good = deck()
    val magneton = good.cards.first { it.card.id == "A1-098" }
    val rows = good.cards.map { if (it == magneton) it.copy(count = 1) else it } +
      magneton.copy(card = magneton.card.copy(id = "A4-218"), count = 2)
    assertTrue(runCatching { PocketDeckQr.resolve(good.copy(cards = rows, totalCardCount = 21), map) }.isFailure)
    val other = rows.first { it.count == 1 && it.card.id != "A1-098" }
    val twenty = rows.filter { it != other }
    assertEquals(20, twenty.sumOf { it.count })
    assertTrue(runCatching { PocketDeckQr.resolve(good.copy(cards = twenty), map) }.isFailure)
  }

  @Test fun exportedBitmapDecodesToGamePayloadAndPngHasSignature() {
    val image = DeckQrImages.create(RuntimeEnvironment.getApplication(), deck())
    assertEquals(expected, image.payload)
    assertEquals(listOf(137,80,78,71,13,10,26,10), image.png.take(8).map { it.toInt() and 255 })
    val bitmap = image.bitmap
    val pixels = IntArray(bitmap.width * bitmap.height)
    bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
    val source = RGBLuminanceSource(bitmap.width, bitmap.height, pixels)
    val decoded = MultiFormatReader().decode(BinaryBitmap(HybridBinarizer(source)))
    assertEquals(expected, decoded.text)
    assertEquals(BarcodeFormat.QR_CODE, decoded.barcodeFormat)
  }
}
