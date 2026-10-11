package com.example

import com.example.data.meta.MetaRefreshPolicy
import com.example.data.util.ImageAvailability
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant

class RefreshAvailabilityTest {
  @Test fun missingImagesAreSkippedUntilExpiryAndManualRetryRestoresThem() {
    var now = 0L
    val cache = ImageAvailability { now }
    val urls = listOf("spanish", "english", "community")
    cache.recordMissing("spanish")
    assertEquals(listOf("english", "community"), cache.candidates(urls))
    cache.reset(urls)
    assertEquals(urls, cache.candidates(urls))
    urls.forEach(cache::recordMissing)
    assertTrue(cache.candidates(urls).isEmpty())
    now = 86_400_000L
    assertEquals(urls, cache.candidates(urls))
  }
  @Test fun previouslyWorkingFallbackIsPreferredAndStillExpiresWhenMissing() {
    val cache = ImageAvailability { 0L }
    val urls = listOf("spanish", "english", "community")
    cache.recordSuccess("community")
    assertEquals(listOf("community", "spanish", "english"), cache.candidates(urls))
    assertEquals(urls, cache.candidates(urls, preferSuccessful = false))
    cache.recordMissing("community")
    assertEquals(listOf("spanish", "english"), cache.candidates(urls))
  }
  @Test fun automaticMetaRefreshUsesFreshnessAndFailureCooldown() {
    val now = Instant.parse("2026-10-04T12:00:00Z").toEpochMilli()
    assertFalse(MetaRefreshPolicy.shouldRefresh("2026-10-04T10:00:00Z", 0, now))
    assertTrue(MetaRefreshPolicy.shouldRefresh("2026-10-04T06:00:00Z", 0, now))
    assertFalse(MetaRefreshPolicy.shouldRefresh(null, now - 899_999, now))
    assertTrue(MetaRefreshPolicy.shouldRefresh(null, now - 900_000, now))
    assertTrue(MetaRefreshPolicy.shouldRefresh("bad-date", 0, now))
    assertTrue(MetaRefreshPolicy.shouldRefresh("2026-10-04T13:00:00Z", 0, now))
  }
}
