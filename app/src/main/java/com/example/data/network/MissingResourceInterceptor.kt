package com.example.data.network

import okhttp3.*
import okhttp3.ResponseBody.Companion.toResponseBody

/** Bounded 404 cache shared by all image/detail callers; never caches 503 or I/O errors. */
class MissingResourceInterceptor(private val now: () -> Long = { android.os.SystemClock.elapsedRealtime() }) : Interceptor {
  private val missing = linkedMapOf<String, Long>()
  fun reset(urls: List<String>) = synchronized(missing) { urls.forEach { missing.remove(it) } }
  override fun intercept(chain: Interceptor.Chain): Response {
    val request = chain.request()
    val eligible = request.method == "GET" && request.url.host in setOf("assets.tcgdex.net", "api.tcgdex.net")
    if (!eligible) return chain.proceed(request)
    val key = request.url.toString()
    val cached = synchronized(missing) {
      val time = now()
      missing.entries.removeAll { time - it.value !in 0 until 3_600_000L }
      key in missing
    }
    if (cached) return Response.Builder().request(request).protocol(Protocol.HTTP_1_1)
      .code(404).message("Previously missing resource").body(ByteArray(0).toResponseBody(null))
      .header("X-Pocket-Negative-Cache", "hit").build()
    return chain.proceed(request).also { response ->
      if (response.code == 404) synchronized(missing) {
        missing.remove(key); missing[key] = now()
        if (missing.size > 4096) missing.remove(missing.keys.first())
      }
    }
  }
}
