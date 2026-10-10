package com.example.data.ai

/** Contains only a safe category, numeric status and app-authored message. */
class AiHttpFailure(val status: Int, message: String) : IllegalStateException(message)

object AiFailure {
  fun category(error: Throwable, stage: String = "REQUEST"): String = when {
    error is AiHttpFailure -> when (error.status) {
      401, 403 -> "AUTH"
      429 -> "QUOTA"
      400, 404 -> "MODEL"
      in 500..599 -> "SERVICE"
      else -> "HTTP"
    }
    error is java.io.InterruptedIOException -> "TIMEOUT"
    error is java.io.IOException -> "NETWORK"
    stage == "VALIDATE" -> "INVALID_DECK"
    error is org.json.JSONException -> "RESPONSE"
    error.message?.startsWith("La respuesta quedó") == true -> "INCOMPLETE"
    error.message?.startsWith("Introduce tu") == true || error is IllegalArgumentException -> "CONFIG"
    else -> "RESPONSE"
  }
}
