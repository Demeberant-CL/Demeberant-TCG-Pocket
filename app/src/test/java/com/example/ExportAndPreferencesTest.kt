package com.example

import android.content.Intent
import androidx.core.content.FileProvider
import com.example.data.preferences.*
import com.example.data.util.ErrorLogManager
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows
import org.robolectric.shadows.ShadowLooper
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExportAndPreferencesTest {
  @Before fun isolateFileProviderPaths() {
    // Robolectric creates a new application/cache directory per test, while AndroidX
    // keeps resolved provider roots in a process-wide cache for the same authority.
    val cache = FileProvider::class.java.getDeclaredField("sCache").apply { isAccessible = true }
    (cache.get(null) as MutableMap<*, *>).clear()
  }

  @Test fun selectedThemeIsPersistedAndReadByAnotherRepository() = runBlocking {
    val context = RuntimeEnvironment.getApplication()
    val repository = UserPreferencesRepository(context)
    for (mode in ThemeMode.entries) {
      repository.setThemeMode(mode)
      assertEquals(mode, UserPreferencesRepository(context).userPreferencesFlow.first().themeMode)
      assertFalse(context.dataStore.data.first().asMap().keys.any { it.name in setOf("app_language", "is_dark_mode") })
    }
  }

  @Test fun fileProviderServesQrPngAndRejectsUnrelatedPrivateFile() {
    val context = RuntimeEnvironment.getApplication()
    val file = File(context.cacheDir, "deck_qr/test.png").apply { parentFile!!.mkdirs(); writeBytes(byteArrayOf(1,2,3)) }
    val uri = FileProvider.getUriForFile(context, context.packageName + ".fileprovider", file)
    assertEquals("content", uri.scheme)
    assertArrayEquals(byteArrayOf(1,2,3), context.contentResolver.openInputStream(uri)!!.use { it.readBytes() })
    val private = File(context.filesDir, "private-test.txt").apply { writeText("PRIVATE") }
    assertTrue(runCatching { FileProvider.getUriForFile(context, context.packageName + ".fileprovider", private) }.isFailure)
  }

  @Test fun diagnosticCanBeSavedAsUtf8TxtWithoutPrivatePayload() = runBlocking {
    val context = RuntimeEnvironment.getApplication()
    ErrorLogManager.init(context)
    ErrorLogManager.event("AUDIT_SAVE", "Safe saved event", IllegalArgumentException("PRIVATE_QUERY password=SECRET"))
    val destination = File(context.cacheDir, "saved-diagnostic.txt")
    ErrorLogManager.saveLogs(context, android.net.Uri.fromFile(destination))
    val text = destination.readText(Charsets.UTF_8)
    assertTrue(text.contains("AUDIT_SAVE"))
    assertFalse(text.contains("PRIVATE_QUERY"))
    assertFalse(text.contains("password=SECRET"))
  }

  @Test fun diagnosticTxtExportHasReadGrantAndNoExceptionPayload() = runBlocking {
    val context = RuntimeEnvironment.getApplication()
    ErrorLogManager.init(context)
    ErrorLogManager.event("AUDIT_EXPORT", "Safe operation", IllegalArgumentException("PRIVATE_AI_JSON token=SECRET_API_TOKEN"))
    ErrorLogManager.exportErrorLogs(context)
    val shadow = Shadows.shadowOf(context)
    var chooser: Intent? = null
    repeat(200) {
      ShadowLooper.idleMainLooper()
      if (chooser == null) chooser = shadow.nextStartedActivity
      if (chooser == null) Thread.sleep(10)
    }
    assertNotNull("Diagnostic export did not open the chooser", chooser)
    @Suppress("DEPRECATION")
    val send = chooser!!.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)!!
    assertEquals(Intent.ACTION_SEND, send.action)
    assertEquals("text/plain", send.type)
    assertTrue(send.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
    @Suppress("DEPRECATION")
    val uri = send.getParcelableExtra<android.net.Uri>(Intent.EXTRA_STREAM)!!
    val content = context.contentResolver.openInputStream(uri)!!.bufferedReader().use { it.readText() }
    assertTrue(content.contains("AUDIT_EXPORT"))
    assertFalse(content.contains("PRIVATE_AI_JSON"))
    assertFalse(content.contains("SECRET_API_TOKEN"))
    assertNotNull(send.clipData)
  }
}
