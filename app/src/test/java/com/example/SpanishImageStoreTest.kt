package com.example

import com.example.data.util.CardId
import com.example.data.util.SpanishImageStore
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SpanishImageStoreTest {
  @Test fun publishedIndexContainsOnlyCanonicalSpanishCardPaths() {
    val index = SpanishImageStore.index(RuntimeEnvironment.getApplication())
    assertEquals(4317, index.size)
    index.forEach { (id, path) ->
      assertEquals(id, CardId.normalize(id))
      assertTrue(path.startsWith("/cards/"))
      assertTrue(path.contains("/es_ES/"))
      assertTrue(path.endsWith(".png"))
    }
    assertTrue(index.containsKey("PROMO-A-001"))
    assertTrue(index.containsKey("PROMO-B-001"))
  }
  @Test fun downloadsRejectNonSpanishPathsBeforeAnyNetworkCall() = runBlocking {
    val context = RuntimeEnvironment.getApplication()
    assertTrue(runCatching { SpanishImageStore.download(context, "A1-001", "/cards/A1/en_US/001.png") }.isFailure)
    assertFalse(SpanishImageStore.file(context, "A1-001").exists())
  }
}
