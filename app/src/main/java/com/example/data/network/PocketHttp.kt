package com.example.data.network

import com.example.data.util.ErrorLogManager
import okhttp3.*
import java.io.IOException
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

class DiagnosticInterceptor(private val sink: (String, String, Throwable?) -> Unit = ErrorLogManager::event) : Interceptor {
  override fun intercept(chain: Interceptor.Chain): Response {
    val request = chain.request()
    val id = UUID.randomUUID().toString().take(8)
    val started = System.nanoTime()
    val route = "${request.method} ${request.url.host}${request.url.encodedPath}"
    sink("HTTP_START", "$id $route bytes=${request.body?.contentLength() ?: 0}", null)
    try {
      val response = chain.proceed(request)
      sink("HTTP_END", "$id $route status=${response.code} ms=${(System.nanoTime()-started)/1_000_000} bytes=${response.body?.contentLength() ?: -1}", null)
      return response
    } catch (e: IOException) {
      sink("HTTP_FAIL", "$id $route ms=${(System.nanoTime()-started)/1_000_000}", e)
      throw e
    }
  }
}

object PocketHttp {
  val client: OkHttpClient = OkHttpClient.Builder()
    .connectTimeout(10, TimeUnit.SECONDS).readTimeout(90, TimeUnit.SECONDS)
    .callTimeout(100, TimeUnit.SECONDS).retryOnConnectionFailure(false)
    .followRedirects(false).followSslRedirects(false)
    .addInterceptor(DiagnosticInterceptor()).build()
  // Image GETs can recover a reset connection without retrying AI requests.
  val imageClient: OkHttpClient = client.newBuilder().retryOnConnectionFailure(true)
    .followRedirects(true).followSslRedirects(false)
    .readTimeout(10, TimeUnit.SECONDS).callTimeout(15, TimeUnit.SECONDS).build()
  val detailsClient: OkHttpClient = client.newBuilder().followRedirects(true).followSslRedirects(false).readTimeout(10, TimeUnit.SECONDS).callTimeout(12, TimeUnit.SECONDS).build()
}

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
suspend fun Call.await(): Response = suspendCancellableCoroutine { continuation ->
  continuation.invokeOnCancellation { cancel() }
  enqueue(object : Callback {
    override fun onFailure(call: Call, e: IOException) {
      if (continuation.isActive) continuation.resumeWithException(e)
    }
    override fun onResponse(call: Call, response: Response) {
      continuation.resume(response) { response.close() }
    }
  })
}
