package com.example.data.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/** A data relay, independent of model providers. Never forwards API credentials. */
class PocketConnector {
  private val client = OkHttpClient.Builder().followRedirects(false).followSslRedirects(false)
    .callTimeout(40, TimeUnit.SECONDS).build()
  companion object {
    fun validate(endpoint: String, token: String) {
      val url = endpoint.toHttpUrl()
      require(url.scheme == "https" && url.username.isEmpty() && url.password.isEmpty() &&
        url.encodedPath == "/" && url.query == null && url.fragment == null) { "Introduce la dirección HTTPS del servidor, sin rutas." }
      require(token.length in 32..4096 && token.all { it.code in 33..126 }) { "Introduce la clave de dispositivo del conector." }
    }
  }
  suspend fun request(endpoint: String, token: String, path: String, method: String, payload: JSONObject? = null): JSONObject = withContext(Dispatchers.IO) {
    validate(endpoint, token)
    require(path in listOf("/app/snapshot", "/app/proposals", "/app/data"))
    val body = payload?.toString()?.toRequestBody("application/json".toMediaType())
    require(body == null || body.contentLength() <= 8_000_000) { "La colección supera el límite del conector." }
    val request = Request.Builder().url(endpoint.toHttpUrl().resolve(path)!!).header("Authorization", "Bearer $token")
      .method(method, body).build()
    try {
      client.newCall(request).execute().use { response ->
        require(response.isSuccessful) { when (response.code) {
          401 -> "La clave del dispositivo no es válida."
          429 -> "El conector recibió demasiadas solicitudes. Reintenta en un minuto."
          else -> "El servidor del conector no completó la solicitud (${response.code})."
        } }
        val source = response.body?.source() ?: error("El conector no devolvió datos.")
        require(!source.request(2_000_001)) { "La respuesta del conector es demasiado grande." }
        JSONObject(source.readUtf8())
      }
    } catch (e: IllegalArgumentException) { throw e }
    catch (_: java.io.IOException) { throw IllegalArgumentException("No se pudo conectar con el servidor. Revisa la dirección y tu conexión.") }
  }
}
