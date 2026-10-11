package com.example.data.util

/** Session-only negative cache. HTTP 404 only; never caches transient network failures. */
class ImageAvailability(private val now: () -> Long = { android.os.SystemClock.elapsedRealtime() }) {
  private val missing = linkedMapOf<String, Long>()
  private val successful = linkedSetOf<String>()
  @Synchronized fun recordMissing(url: String) {
    missing.remove(url); missing[url] = now()
    if (missing.size > 4096) missing.remove(missing.keys.first())
  }
  @Synchronized fun candidates(urls: List<String>, preferSuccessful: Boolean = true): List<String> {
    val time = now()
    missing.entries.removeAll { time - it.value !in 0 until 86_400_000L }
    return urls.filterNot { it in missing }.let { available ->
      if (preferSuccessful) available.sortedByDescending { it in successful } else available
    }
  }
  @Synchronized fun recordSuccess(url: String) {
    successful.remove(url); successful.add(url)
    if (successful.size > 4096) successful.remove(successful.first())
  }
  @Synchronized fun reset(urls: List<String>) { urls.forEach { missing.remove(it) } }
  companion object { val session = ImageAvailability() }
}
