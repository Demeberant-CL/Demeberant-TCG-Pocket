package com.example

import com.example.data.ai.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import javax.crypto.spec.SecretKeySpec
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PocketConnectorTest {
  @Test fun connectorRequiresHttpsOriginAndDeviceSecret() {
    PocketConnector.validate("https://pocket.example/", "x".repeat(32))
    for (url in listOf("http://pocket.example", "https://user:secret@pocket.example", "https://pocket.example/mcp", "https://pocket.example/?token=secret", "https://pocket.example/#secret")) {
      assertThrows(IllegalArgumentException::class.java) { PocketConnector.validate(url, "x".repeat(32)) }
    }
    assertThrows(IllegalArgumentException::class.java) { PocketConnector.validate("https://pocket.example", "short") }
    assertThrows(IllegalArgumentException::class.java) { PocketConnector.validate("https://pocket.example", "x".repeat(32) + "\n") }
  }
  @Test fun connectorCredentialsStaySeparateFromModelCredentials() {
    val context = RuntimeEnvironment.getApplication()
    val key = SecretKeySpec(ByteArray(32) { 42 }, "AES")
    val modelStore = AiConnectionStore(context) { key }
    val connectorStore = AiConnectionStore(context, fileName = "pocket-connector.enc") { key }
    modelStore.clear(); connectorStore.clear()
    try {
      modelStore.save(AiConnection(apiKey = "model-secret"))
      connectorStore.save(AiConnection(AiProvider.COMPATIBLE, "connector", "device-secret", "https://pocket.example", "connector"))
      assertEquals("model-secret", modelStore.load()!!.apiKey)
      assertEquals("device-secret", connectorStore.load()!!.apiKey)
      assertFalse(File(context.noBackupFilesDir, "pocket-connector.enc").readBytes().toString(Charsets.UTF_8).contains("device-secret"))
      connectorStore.clear()
      assertNotNull(modelStore.load())
    } finally { modelStore.clear(); connectorStore.clear() }
  }
}
