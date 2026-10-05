package com.example

import com.example.data.meta.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MetaCacheRecoveryTest {
  @Test fun reopeningRepositoryRecoversInterruptedCacheWrite() = runBlocking {
    val context = RuntimeEnvironment.getApplication()
    val base = File(context.filesDir, "pocket-meta.json")
    val backup = File(base.path + ".bak")
    base.delete(); backup.delete()
    val saved = MetaSnapshot("2026-10-04T12:00:00Z", 1, 1,
      listOf(MetaDeck("Example", 1, 2, 1, 0, mapOf("A1-001" to 2), listOf("Planta"), "test123")), 0)
    try {
      backup.writeText(TournamentRepository.encode(saved).toString())
      assertEquals(saved, TournamentRepository(context).cached())
      assertTrue(base.exists())
    } finally { base.delete(); backup.delete() }
  }
}
