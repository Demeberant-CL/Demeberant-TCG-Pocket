package com.example.data.ai

import org.json.JSONObject

/** Conservative catalogue filter, not proof of quota or successful generation. */
object AiModelCompatibility {
  private val validId = Regex("[a-zA-Z0-9._:/-]{1,120}")
  private val geminiText = Regex("gemini-(2\\.5|3\\.[0-9]+)-(flash(-lite)?|pro)(-preview(-[0-9-]+)?|-[0-9]{3})?")
  private val openAiText = Regex("gpt-(4o(-mini)?|4\\.1(-mini|-nano)?|5(\\.[0-9]+)?(-mini|-nano)?)(-[0-9]{4}-[0-9]{2}-[0-9]{2})?")
  private val excluded = Regex("image|audio|tts|embedding|embed-|realtime|live-|transcrib|moderation|vision-only|deep-research|codex|computer-use|search-preview", RegexOption.IGNORE_CASE)
  fun accepts(provider: AiProvider, row: JSONObject, id: String): Boolean {
    if (!validId.matches(id) || excluded.containsMatchIn(id)) return false
    return when (provider) {
      AiProvider.GEMINI -> {
        val methods = row.optJSONArray("supportedGenerationMethods") ?: return false
        geminiText.matches(id) && (0 until methods.length()).any { methods.optString(it) == "generateContent" } &&
          row.optInt("outputTokenLimit", 4096) >= 4096
      }
      AiProvider.OPENAI -> openAiText.matches(id) || id == "chat-latest"
      AiProvider.COMPATIBLE -> {
        // /models alone does not establish Chat Completions support. Require explicit metadata.
        val endpoints = row.optJSONArray("supported_endpoints") ?: return false
        val modalities = row.optJSONObject("architecture")
        val output = modalities?.optJSONArray("output_modalities") ?: return false
        (0 until endpoints.length()).any { endpoints.optString(it) in setOf("chat/completions", "/v1/chat/completions") } &&
          (0 until output.length()).any { output.optString(it) == "text" }
      }
    }
  }
}
