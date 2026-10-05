package com.example

import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.xmlpull.v1.XmlPullParser

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BackupRulesTest {
  @Test fun backupAndTransferExcludeRegenerableLogsButKeepUserDataDomains() {
    val resources = RuntimeEnvironment.getApplication().resources
    val expected = setOf("diagnostics/", "pocket-meta.json", "pocket-meta.json.bak", "pocket-meta.json.new")
    for (resource in listOf(R.xml.backup_rules, R.xml.data_extraction_rules)) {
      val exclusions = mutableMapOf<String, MutableSet<String>>()
      var group = "legacy"
      resources.getXml(resource).use { parser ->
        while (parser.eventType != XmlPullParser.END_DOCUMENT) {
          if (parser.eventType == XmlPullParser.START_TAG) when (parser.name) {
            "cloud-backup", "device-transfer" -> group = parser.name
            "include" -> fail("An allowlist would change which existing user data is preserved")
            "exclude" -> {
              assertEquals("file", parser.getAttributeValue(null, "domain"))
              exclusions.getOrPut(group) { mutableSetOf() }.add(parser.getAttributeValue(null, "path"))
            }
          }
          parser.next()
        }
      }
      assertEquals(if (resource == R.xml.backup_rules) setOf("legacy") else setOf("cloud-backup", "device-transfer"), exclusions.keys)
      exclusions.values.forEach { assertEquals(expected, it) }
    }
  }
}
