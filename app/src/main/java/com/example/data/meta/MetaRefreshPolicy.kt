package com.example.data.meta

import java.time.Instant

object MetaRefreshPolicy {
  fun shouldRefresh(updated: String?, lastAttempt: Long, now: Long): Boolean {
    if (lastAttempt > 0 && now - lastAttempt in 0 until 900_000L) return false
    val saved = updated?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrNull() }
    return saved == null || now - saved >= 21_600_000L || saved > now
  }
}
