package com.example.data.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatGptPlanRepositoryTest {
  @Test fun pkceChallengeMatchesTheS256ProtocolVector() {
    assertEquals(
      "E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM",
      ChatGptPlanProtocol.pkceChallenge("dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk"),
    )
  }

  @Test fun chatGptPlanRequiresBothGrantedApiScopes() {
    assertTrue(ChatGptPlanProtocol.grantsPlanUsage("openid resource.invoke chatgpt.tokens.use.direct"))
    assertFalse(ChatGptPlanProtocol.grantsPlanUsage("openid resource.invoke"))
    assertFalse(ChatGptPlanProtocol.grantsPlanUsage("openid chatgpt.tokens.use.direct"))
  }

  @Test fun accountDisplayNeverIncludesOAuthCredentials() {
    val account = ChatGptPlanAccount(
      email = "deme@example.com",
      name = "Deme",
      clientId = "oaiapp_test",
      accessToken = "access-secret",
      refreshToken = "refresh-secret",
      idToken = "identity-secret",
      scopes = setOf("resource.invoke", "chatgpt.tokens.use.direct"),
      expiresAtMillis = 123456L,
    )
    val display = account.toString()
    assertTrue(display.contains("deme@example.com"))
    assertFalse(display.contains("access-secret"))
    assertFalse(display.contains("refresh-secret"))
    assertFalse(display.contains("identity-secret"))
  }
}
