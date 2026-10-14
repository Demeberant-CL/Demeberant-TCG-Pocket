package com.example.data.util

import android.content.Context
import android.graphics.BitmapFactory
import com.example.data.network.PocketHttp
import com.example.data.network.await
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.io.File

/** Explicit published Spanish paths; downloaded originals are kept outside the evictable cache. */
object SpanishImageStore {
  private fun directory(context: Context) = File(context.filesDir, "spanish-card-originals").apply { mkdirs() }
  fun file(context: Context, id: String) = File(directory(context), "${CardId.normalize(id)}.png")
  fun index(context: Context): Map<String, String> {
    val images = org.json.JSONObject(context.assets.open("pocket-spanish-image-index.json").bufferedReader().use { it.readText() }).getJSONObject("images")
    return images.keys().asSequence().associateWith { images.getString(it) }
  }
  suspend fun download(context: Context, id: String, path: String): Boolean = withContext(Dispatchers.IO) {
    require(path.startsWith("/cards/") && "/es_ES/" in path && path.endsWith(".png"))
    val target = file(context, id)
    if (target.exists()) return@withContext false
    require(directory(context).usableSpace > 8_000_000) { "No hay espacio suficiente." }
    val request = Request.Builder().url("https://ptcgp-assets.centropkmn.com$path").build()
    val bytes = PocketHttp.imageClient.newCall(request).await().use { response ->
      check(response.isSuccessful) { "Imagen no disponible (${response.code})" }
      response.body?.byteStream()?.use { stream ->
        val result = stream.readBytesBounded(3_000_000)
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(result, 0, result.size, bounds)
        check(bounds.outWidth > 0 && bounds.outHeight > 0) { "Imagen inválida" }
        result
      } ?: error("Respuesta vacía")
    }
    val temp = File(target.parentFile, target.name + ".part")
    try { temp.writeBytes(bytes); check(temp.renameTo(target)) { "No se pudo guardar la imagen." } }
    finally { temp.delete() }
    true
  }
  suspend fun stats(context: Context): Pair<Int, Long> = withContext(Dispatchers.IO) {
    val files = directory(context).listFiles().orEmpty().filter { it.extension == "png" }
    files.size to files.sumOf { it.length() }
  }
}
