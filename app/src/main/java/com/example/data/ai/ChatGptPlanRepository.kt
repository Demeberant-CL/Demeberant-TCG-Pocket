package com.example.data.ai

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.withLock
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.MediaType.Companion.toMediaType
import org.json.JSONObject
import java.io.File
import java.io.InputStream
import java.io.ByteArrayOutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.URL
import java.security.KeyStore
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import java.util.UUID
import java.util.concurrent.TimeUnit
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.crypto.ECDSAVerifier
import com.nimbusds.jose.crypto.RSASSAVerifier
import com.nimbusds.jose.jwk.ECKey
import com.nimbusds.jose.jwk.JWKSet
import com.nimbusds.jose.jwk.RSAKey
import com.nimbusds.jwt.SignedJWT

data class ChatGptPlanAccount(
  val email: String,
  val name: String,
  val clientId: String,
  val accessToken: String,
  val refreshToken: String,
  val idToken: String,
  val scopes: Set<String>,
  val expiresAtMillis: Long,
) {
  val canUsePlan: Boolean get() = "chatgpt.tokens.use.direct" in scopes &&
    "resource.invoke" in scopes && accessToken.isNotBlank()
  override fun toString() = "ChatGptPlanAccount(email=$email, connected=true, credentials=hidden)"
}

data class ChatGptModel(val slug: String, val displayName: String)

internal object ChatGptPlanProtocol {
  fun pkceChallenge(verifier: String): String = Base64.getUrlEncoder().withoutPadding()
    .encodeToString(MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(Charsets.US_ASCII)))

  fun grantsPlanUsage(scope: String): Boolean {
    val scopes = scope.split(' ').filter(String::isNotBlank).toSet()
    return "chatgpt.tokens.use.direct" in scopes && "resource.invoke" in scopes
  }
}

private fun InputStream.readBytesBounded(maxBytes: Int): ByteArray {
  val output = ByteArrayOutputStream()
  val buffer = ByteArray(4096)
  var total = 0
  while (true) {
    val count = read(buffer)
    if (count < 0) break
    total += count
    require(total <= maxBytes) { "La respuesta supera el límite permitido." }
    output.write(buffer, 0, count)
  }
  return output.toByteArray()
}

class ChatGptPlanHttpFailure(
  val status: Int,
  val errorCode: String?,
  val requestId: String?,
  message: String,
) : IllegalStateException(message)

private fun okhttp3.Response.toChatGptPlanFailure(): ChatGptPlanHttpFailure {
  val responseText = runCatching {
    body?.byteStream()?.use { it.readBytesBounded(16_384)?.toString(Charsets.UTF_8) }.orEmpty()
  }.getOrDefault("")
  val json = runCatching { JSONObject(responseText) }.getOrNull()
  val error = json?.optJSONObject("error")
  val detail = (error?.optString("message")?.takeIf(String::isNotBlank)
    ?: json?.optString("detail")?.takeIf(String::isNotBlank))
    ?.replace(Regex("[\\r\\n\\t]+"), " ")?.take(300)
  val httpStatus = this.code
  val code = error?.optString("code")?.takeIf { it.isNotBlank() && it != "null" }
  val requestId = header("x-request-id")?.take(100)
  val reason = when (code) {
    "subscription_sharing_user_not_eligible" -> "La cuenta, el espacio de trabajo o la política no permite usar el plan en esta app."
    "subscription_sharing_usage_limit_exceeded" -> "Se alcanzó el límite de uso del plan para esta app."
    "subscription_sharing_usage_unavailable" -> "OpenAI no pudo comprobar el uso del plan. Inténtalo más tarde."
    "subscription_sharing_unsupported_capability", "subscription_sharing_route_not_supported" ->
      "Esta solicitud contiene una opción o ruta que el acceso del plan no admite."
    else -> when (httpStatus) {
      401 -> "ChatGPT no aceptó la autorización. Revisa la cuenta y vuelve a conectar."
      403 -> "ChatGPT bloqueó esta solicitud por permisos o región."
      429 -> "Se alcanzó un límite de solicitudes."
      503 -> "El servicio de uso del plan no está disponible temporalmente."
      else -> "OpenAI rechazó la solicitud."
    }
  }
  val diagnostic = buildList {
    add("HTTP $httpStatus")
    code?.let { add("código $it") }
    requestId?.let { add("ID $it") }
  }.joinToString(" · ")
  return ChatGptPlanHttpFailure(httpStatus, code, requestId,
    listOf(reason, diagnostic, detail).filterNotNull().joinToString(" — "))
}

private data class OAuthTokens(
  val accessToken: String,
  val refreshToken: String,
  val idToken: String,
  val scopes: Set<String>,
  val expiresAtMillis: Long,
)

private class ChatGptPlanStore(context: Context) {
  private val app = context.applicationContext
  private val file = AtomicFile(File(app.noBackupFilesDir, "chatgpt-plan.enc"))
  private val hostFile = AtomicFile(File(app.noBackupFilesDir, "chatgpt-plan-host-id"))
  private val clientFile = AtomicFile(File(app.noBackupFilesDir, "chatgpt-plan-client-id"))

  @Synchronized fun hostId(): String {
    if (hostFile.baseFile.exists()) return hostFile.openRead().use {
      it.readBytesBounded(256).toString(Charsets.UTF_8).also { value -> require(value.startsWith("urn:uuid:")) }
    }
    val created = "urn:uuid:${UUID.randomUUID()}"
    val output = hostFile.startWrite()
    try { output.write(created.toByteArray(Charsets.UTF_8)); hostFile.finishWrite(output) }
    catch (e: Exception) { hostFile.failWrite(output); throw e }
    return created
  }

  @Synchronized fun registeredClientId(): String? =
    if (clientFile.baseFile.exists()) clientFile.openRead().use { it.readBytesBounded(512).toString(Charsets.UTF_8) } else null

  @Synchronized fun rememberClientId(clientId: String) {
    require(clientId.startsWith("oaiapp_") && clientId.length <= 512)
    val output = clientFile.startWrite()
    try { output.write(clientId.toByteArray(Charsets.UTF_8)); clientFile.finishWrite(output) }
    catch (e: Exception) { clientFile.failWrite(output); throw e }
  }

  @Synchronized fun read(): ChatGptPlanAccount? {
    if (!file.baseFile.exists() && !File(file.baseFile.path + ".bak").exists()) return null
    val raw = file.openRead().use { input ->
      val data = input.readBytesBounded(100_000)
      require(data.size in 30..100_000) { "El archivo de ChatGPT no es válido." }
      val ivSize = data[0].toInt() and 255
      require(ivSize == 12)
      val cipher = Cipher.getInstance("AES/GCM/NoPadding")
      cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, data.copyOfRange(1, 13)))
      cipher.doFinal(data.copyOfRange(13, data.size)).toString(Charsets.UTF_8)
    }
    val json = JSONObject(raw)
    val account = ChatGptPlanAccount(
      email = json.getString("email"),
      name = json.optString("name", ""),
      clientId = json.getString("clientId"),
      accessToken = json.getString("accessToken"),
      refreshToken = json.getString("refreshToken"),
      idToken = json.getString("idToken"),
      scopes = json.getString("scopes").split(' ').filter(String::isNotBlank).toSet(),
      expiresAtMillis = json.getLong("expiresAtMillis"),
    )
    require(account.canUsePlan) { "La cuenta no autorizó el uso de su plan ChatGPT." }
    return account
  }

  @Synchronized fun write(account: ChatGptPlanAccount) {
    require(account.canUsePlan)
    val json = JSONObject()
      .put("email", account.email)
      .put("name", account.name)
      .put("clientId", account.clientId)
      .put("accessToken", account.accessToken)
      .put("refreshToken", account.refreshToken)
      .put("idToken", account.idToken)
      .put("scopes", account.scopes.sorted().joinToString(" "))
      .put("expiresAtMillis", account.expiresAtMillis)
      .toString().toByteArray(Charsets.UTF_8)
    val cipher = Cipher.getInstance("AES/GCM/NoPadding")
    cipher.init(Cipher.ENCRYPT_MODE, key())
    val encrypted = cipher.doFinal(json)
    rememberClientId(account.clientId)
    val output = file.startWrite()
    try {
      output.write(cipher.iv.size)
      output.write(cipher.iv)
      output.write(encrypted)
      file.finishWrite(output)
    } catch (e: Exception) {
      file.failWrite(output)
      throw e
    }
  }

  @Synchronized fun clear() { file.delete() }

  private fun key(): SecretKey {
    val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
    (store.getKey("rotom-chatgpt-plan-key", null) as? SecretKey)?.let { return it }
    return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
      init(KeyGenParameterSpec.Builder(
        "rotom-chatgpt-plan-key",
        KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
      ).setBlockModes(KeyProperties.BLOCK_MODE_GCM)
        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
        .build())
    }.generateKey()
  }
}

/**
 * OpenAI Sign in with ChatGPT plan usage for a locally running Android app.
 * OAuth tokens remain encrypted on-device; inference uses the public Responses API directly.
 */
class ChatGptPlanRepository(context: Context, private val client: OkHttpClient = OkHttpClient.Builder()
  .connectTimeout(15, TimeUnit.SECONDS)
  .readTimeout(180, TimeUnit.SECONDS)
  .callTimeout(190, TimeUnit.SECONDS)
  .followRedirects(false)
  .followSslRedirects(false)
  .retryOnConnectionFailure(false)
  .build()) {
  private val app = context.applicationContext
  private val store = ChatGptPlanStore(app)
  private val refreshLock = kotlinx.coroutines.sync.Mutex()

  suspend fun account(): ChatGptPlanAccount? = withContext(Dispatchers.IO) { store.read() }

  suspend fun signIn(activity: Activity): ChatGptPlanAccount = withContext(Dispatchers.IO) {
    val server = ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))
    server.soTimeout = 180_000
    val redirect = "http://127.0.0.1:${server.localPort}/auth/callback"
    val verifier = randomToken(64)
    val state = randomToken(32)
    val nonce = randomToken(32)
    val challenge = ChatGptPlanProtocol.pkceChallenge(verifier)
    val existing = store.read()
    val knownClientId = existing?.clientId ?: store.registeredClientId()
    val requestedClientId = knownClientId ?: "dynamic_agent_client"
    val authorization = Uri.parse("https://auth.openai.com/api/accounts/authorize").buildUpon()
      .appendQueryParameter("client_id", requestedClientId)
      .appendQueryParameter("response_type", "code")
      .appendQueryParameter("redirect_uri", redirect)
      .appendQueryParameter("scope", "openid profile email offline_access resource.invoke chatgpt.tokens.use.direct")
      .appendQueryParameter("resource", "https://api.openai.com/v1")
      .appendQueryParameter("state", state)
      .appendQueryParameter("nonce", nonce)
      .appendQueryParameter("code_challenge_method", "S256")
      .appendQueryParameter("code_challenge", challenge)
      .appendQueryParameter("ext_agent_host_id", store.hostId())
      .apply {
        if (knownClientId == null) appendQueryParameter("agent_name_hint", "TCG Lab")
        else if (existing != null) {
          appendQueryParameter("id_token_hint", existing.idToken)
          if (existing.email.isNotBlank()) appendQueryParameter("login_hint", existing.email)
        }
      }.build()

    try {
      withContext(Dispatchers.Main) {
        activity.startActivity(Intent(Intent.ACTION_VIEW, authorization))
      }
      val callback = server.accept().use { socket ->
        socket.soTimeout = 10_000
        val reader = socket.getInputStream().bufferedReader(Charsets.US_ASCII)
        val requestLine = reader.readLine().orEmpty()
        var bytesRead = requestLine.length
        while (true) {
          val line = reader.readLine() ?: break
          bytesRead += line.length
          require(bytesRead <= 16_384) { "La respuesta de inicio de sesión supera el límite." }
          if (line.isEmpty()) break
        }
        val target = requestLine.split(' ').getOrNull(1) ?: error("Retorno de inicio de sesión no válido.")
        val uri = Uri.parse("http://127.0.0.1$target")
        require(uri.path == "/auth/callback") { "Ruta de retorno no válida." }
        val html = "<!doctype html><meta charset=utf-8><title>TCG Lab</title><p>Puedes volver a TCG Lab.</p>"
        val response = "HTTP/1.1 200 OK\r\nContent-Type: text/html; charset=utf-8\r\nConnection: close\r\nContent-Length: ${html.toByteArray().size}\r\n\r\n$html"
        socket.getOutputStream().write(response.toByteArray(Charsets.UTF_8))
        socket.getOutputStream().flush()
        val returnedState = uri.getQueryParameter("state")
        require(returnedState == state) { "El estado OAuth no coincide." }
        uri.getQueryParameter("error")?.let { error("Inicio de sesión cancelado o rechazado.") }
        val code = uri.getQueryParameter("code") ?: error("OpenAI no devolvió un código de autorización.")
        val returnedClient = uri.getQueryParameter("client_id")
        val clientId = knownClientId ?: returnedClient
          ?: error("No se completó el registro de TCG Lab en OpenAI.")
        require(clientId != "dynamic_agent_client")
        if (knownClientId != null && returnedClient != null) require(returnedClient == knownClientId)
        code to clientId
      }
      exchangeCode(callback.first, callback.second, verifier, redirect, nonce)
    } finally {
      runCatching { server.close() }
    }
  }

  private suspend fun exchangeCode(code: String, clientId: String, verifier: String, redirect: String, nonce: String): ChatGptPlanAccount {
    val form = FormBody.Builder()
      .add("grant_type", "authorization_code")
      .add("client_id", clientId)
      .add("code", code)
      .add("code_verifier", verifier)
      .add("redirect_uri", redirect)
      .add("resource", "https://api.openai.com/v1")
      .build()
    val tokens = tokenRequest(form)
    val claims = validateIdToken(tokens.idToken, clientId, nonce)
    require(ChatGptPlanProtocol.grantsPlanUsage(tokens.scopes.joinToString(" "))) {
      "La cuenta inició sesión, pero no autorizó el uso del plan ChatGPT en TCG Lab."
    }
    val account = ChatGptPlanAccount(
      email = claims.getStringClaim("email") ?: "",
      name = claims.getStringClaim("name") ?: "",
      clientId = clientId,
      accessToken = tokens.accessToken,
      refreshToken = tokens.refreshToken,
      idToken = tokens.idToken,
      scopes = tokens.scopes,
      expiresAtMillis = tokens.expiresAtMillis,
    )
    store.write(account)
    return account
  }

  private data class ValidatedIdentity(val values: Map<String, Any>) {
    fun getStringClaim(name: String): String? = values[name] as? String
  }

  private suspend fun validateIdToken(idToken: String, clientId: String, nonce: String?): ValidatedIdentity {
    val signed = SignedJWT.parse(idToken)
    val algorithm = signed.header.algorithm
    require(algorithm == JWSAlgorithm.RS256 || algorithm == JWSAlgorithm.ES256) { "Algoritmo de identidad no admitido." }
    val jwks = withContext(Dispatchers.IO) {
      URL("https://auth.openai.com/.well-known/jwks.json").openStream().bufferedReader().use { it.readText() }
    }
    require(jwks.length <= 500_000) { "Llaves de identidad demasiado grandes." }
    val key = JWKSet.parse(jwks).getKeyByKeyId(signed.header.keyID) ?: error("No se pudo validar la identidad de OpenAI.")
    val verifier = when (key) {
      is RSAKey -> RSASSAVerifier(key.toRSAPublicKey())
      is ECKey -> ECDSAVerifier(key.toECPublicKey())
      else -> error("Tipo de llave de identidad no admitido.")
    }
    require(signed.verify(verifier)) { "La firma de identidad de OpenAI no es válida." }
    val claims = signed.jwtClaimsSet
    require(claims.issuer == "https://auth.openai.com") { "Emisor de identidad no válido." }
    require(claims.audience.contains(clientId)) { "La identidad no corresponde al registro seleccionado." }
    if (nonce != null) require(claims.getStringClaim("nonce") == nonce) { "La respuesta de inicio de sesión no corresponde a esta solicitud." }
    require(claims.expirationTime != null && claims.expirationTime.time > System.currentTimeMillis()) { "El token de identidad expiró." }
    return ValidatedIdentity(claims.claims)
  }

  private suspend fun tokenRequest(form: FormBody, fallbackIdToken: String = "", fallbackScopes: Set<String> = emptySet()): OAuthTokens = withContext(Dispatchers.IO) {
    val request = Request.Builder().url("https://auth.openai.com/api/accounts/oauth/token").post(form).build()
    client.newCall(request).execute().use { response ->
      if (!response.isSuccessful) throw response.toChatGptPlanFailure()
      parseTokens(response.body?.string() ?: error("OpenAI no devolvió credenciales."), fallbackIdToken, fallbackScopes)
    }
  }

  private fun parseTokens(body: String, fallbackIdToken: String = "", fallbackScopes: Set<String> = emptySet()): OAuthTokens {
    require(body.length <= 100_000)
    val json = JSONObject(body)
    val scopes = json.optString("scope").split(' ').filter(String::isNotBlank).toSet().ifEmpty { fallbackScopes }
    val lifetime = json.optLong("expires_in", 3600).coerceIn(60, 86_400)
    return OAuthTokens(
      accessToken = json.getString("access_token"),
      refreshToken = json.getString("refresh_token"),
      idToken = json.optString("id_token").ifBlank { fallbackIdToken },
      scopes = scopes,
      expiresAtMillis = System.currentTimeMillis() + lifetime * 1000,
    )
  }

  private suspend fun freshAccount(): ChatGptPlanAccount = refreshLock.withLock {
    val saved = withContext(Dispatchers.IO) { store.read() } ?: error("Conecta tu cuenta ChatGPT en Conexiones.")
    if (saved.expiresAtMillis > System.currentTimeMillis() + 60_000) return@withLock saved
    val form = FormBody.Builder()
      .add("grant_type", "refresh_token")
      .add("client_id", saved.clientId)
      .add("refresh_token", saved.refreshToken)
      .add("resource", "https://api.openai.com/v1")
      .build()
    val rotated = tokenRequest(form, saved.idToken, saved.scopes)
    val updated = saved.copy(
      accessToken = rotated.accessToken,
      refreshToken = rotated.refreshToken,
      idToken = rotated.idToken,
      scopes = rotated.scopes,
      expiresAtMillis = rotated.expiresAtMillis,
    )
    require(updated.canUsePlan) { "El plan ChatGPT ya no está autorizado para TCG Lab." }
    withContext(Dispatchers.IO) { store.write(updated) }
    updated
  }

  suspend fun models(): List<ChatGptModel> = withContext(Dispatchers.IO) {
    val account = freshAccount()
    val request = Request.Builder().url("https://api.openai.com/v1/models")
      .header("Authorization", "Bearer ${account.accessToken}").get().build()
    client.newCall(request).execute().use { response ->
      if (!response.isSuccessful) throw response.toChatGptPlanFailure()
      val json = JSONObject(response.body?.string() ?: error("OpenAI no devolvió modelos."))
      val rows = json.optJSONArray("models") ?: error("OpenAI devolvió un catálogo de modelos no compatible.")
      (0 until minOf(rows.length(), 500)).mapNotNull { index ->
        val row = rows.optJSONObject(index) ?: return@mapNotNull null
        val slug = row.optString("slug")
        if (row.optString("visibility") == "list" && slug.matches(Regex("[A-Za-z0-9._:/-]{1,120}")))
          ChatGptModel(slug, row.optString("display_name").ifBlank { slug }) else null
      }
    }
  }

  suspend fun request(model: String, prompt: String): String = withContext(Dispatchers.IO) {
    require(model.matches(Regex("[A-Za-z0-9._:/-]{1,120}"))) { "Selecciona un modelo ChatGPT válido." }
    require(prompt.toByteArray(Charsets.UTF_8).size <= 60_000) { "La colección genera una consulta demasiado grande." }
    val account = freshAccount()
    val payload = JSONObject()
      .put("model", model)
      .put("input", org.json.JSONArray().put(JSONObject().put("role", "user")
        .put("content", prompt)))
      .put("store", false)
      .put("stream", true)
      .put("max_output_tokens", 4096)
    val body = payload.toString().toRequestBody("application/json".toMediaType())
    val request = Request.Builder().url("https://api.openai.com/v1/responses")
      .header("Authorization", "Bearer ${account.accessToken}")
      .post(body).build()
    client.newCall(request).execute().use { response ->
      if (!response.isSuccessful) throw response.toChatGptPlanFailure()
      val input = response.body?.byteStream()?.bufferedReader(Charsets.UTF_8)
        ?: error("OpenAI no devolvió una respuesta.")
      val output = StringBuilder()
      var completed = false
      var bytes = 0
      input.useLines { lines ->
        lines.forEach { line ->
          bytes += line.length
          require(bytes <= 500_000) { "La respuesta de ChatGPT supera el límite." }
          if (!line.startsWith("data:")) return@forEach
          val data = line.removePrefix("data:").trim()
          if (data == "[DONE]") return@forEach
          val event = runCatching { JSONObject(data) }.getOrNull() ?: return@forEach
          when (event.optString("type")) {
            "response.output_text.delta" -> output.append(event.optString("delta"))
            "response.completed" -> completed = true
            "response.failed" -> {
              val failed = event.optJSONObject("response")
              val failure = failed?.optJSONObject("error")
              val code = failure?.optString("code")?.takeIf { !it.isNullOrBlank() }
              val detail = failure?.optString("message")?.takeIf { !it.isNullOrBlank() }?.take(240)
              error(listOfNotNull("La consulta de ChatGPT falló", code, detail).joinToString(" — "))
            }
            "response.incomplete" -> {
              val reason = event.optJSONObject("response")?.optJSONObject("incomplete_details")?.optString("reason")
              error("La respuesta de ChatGPT quedó incompleta" + reason?.takeIf { !it.isNullOrBlank() }?.let { " ($it)" }.orEmpty())
            }
          }
        }
      }
      require(completed && output.isNotBlank()) { "La respuesta de ChatGPT quedó incompleta." }
      output.toString()
    }
  }

  suspend fun signOut() = withContext(Dispatchers.IO) {
    val saved = store.read() ?: return@withContext
    try {
      val configText = URL("https://auth.openai.com/.well-known/openid-configuration").openStream()
        .bufferedReader().use { it.readText() }
      require(configText.length <= 100_000)
      val endpoint = JSONObject(configText).getString("revocation_endpoint")
      val body = FormBody.Builder()
        .add("token", saved.refreshToken)
        .add("token_type_hint", "refresh_token")
        .add("client_id", saved.clientId)
        .build()
      client.newCall(Request.Builder().url(endpoint).post(body).build()).execute().use {
        require(it.isSuccessful) { "No se confirmó la revocación en OpenAI." }
      }
    } finally {
      store.clear()
    }
  }

  private fun randomToken(bytes: Int): String = ByteArray(bytes).also(SecureRandom()::nextBytes)
    .let { Base64.getUrlEncoder().withoutPadding().encodeToString(it) }
}
