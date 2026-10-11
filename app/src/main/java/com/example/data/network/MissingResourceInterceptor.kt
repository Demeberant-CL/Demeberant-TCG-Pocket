package com.example.data.network

import okhttp3.*
import okhttp3.ResponseBody.Companion.toResponseBody

/** Bounded 404 cache shared by all image/detail callers; never caches 503 or I/O errors. */
class MissingResourceInterceptor(private val now: () -> Long = { System.currentTimeMillis() }) : Interceptor {
  private val missing = linkedMapOf<String, Long>()
  @Volatile private var preferences: android.content.SharedPreferences? = null
  fun restore(context: android.content.Context) {
    val prefs = context.getSharedPreferences("missing-card-resources", android.content.Context.MODE_PRIVATE)
    synchronized(missing) {
      prefs.all.forEach { (url, timestamp) ->
        if (timestamp is Long && now() - timestamp in 0 until 3_600_000L) missing.putIfAbsent(url, timestamp)
      }
      while (missing.size > 4096) missing.remove(missing.keys.first())
      preferences = prefs
    }
  }
  fun reset(urls: List<String>) = synchronized(missing) {
    urls.forEach { missing.remove(it) }
    preferences?.edit()?.apply { urls.forEach { remove(it) }; apply() }
    Unit
  }
  override fun intercept(chain: Interceptor.Chain): Response {
    val request = chain.request()
    val eligible = request.method == "GET" && request.url.host in setOf("assets.tcgdex.net", "api.tcgdex.net")
    if (!eligible) return chain.proceed(request)
    val key = request.url.toString()
    val cached = synchronized(missing) {
      val time = now()
      val expired = missing.filterValues { time - it !in 0 until 3_600_000L }.keys.toList()
      expired.forEach { missing.remove(it) }
      if (expired.isNotEmpty()) preferences?.edit()?.apply { expired.forEach { remove(it) }; apply() }
      key in missing
    }
    if (cached) return Response.Builder().request(request).protocol(Protocol.HTTP_1_1)
      .code(404).message("Previously missing resource").body(ByteArray(0).toResponseBody(null))
      .header("X-Pocket-Negative-Cache", "hit").build()
    return chain.proceed(request).also { response ->
      if (response.code == 404) synchronized(missing) {
        missing.remove(key); missing[key] = now()
        val evicted = if (missing.size > 4096) missing.keys.first().also { missing.remove(it) } else null
        preferences?.edit()?.apply { putLong(key, missing.getValue(key)); evicted?.let { remove(it) }; apply() }
      }
    }
  }
}
