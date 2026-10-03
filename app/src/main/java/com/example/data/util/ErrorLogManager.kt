package com.example.data.util

import android.content.Context
import android.content.Intent
import android.content.ClipData
import android.util.Log
import androidx.core.content.FileProvider
import kotlinx.coroutines.*
import java.io.File
import java.time.LocalDate
import java.time.Instant
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

object ErrorLogManager {
  private val initialized = java.util.concurrent.atomic.AtomicBoolean(false)
  private val fileLock = Any()
  private var appContext: Context? = null
  private const val MAX_BYTES = 1_048_576
  private val dropped = java.util.concurrent.atomic.AtomicLong(0)
  private val writer = ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS,
    ArrayBlockingQueue<Runnable>(256), { task -> Thread(task, "pocket-diagnostics").apply { isDaemon = true } },
    ThreadPoolExecutor.AbortPolicy())
  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

  private val bearerPattern = Regex("(?i)Bearer\\s+[^\\s,;]+")
  private val secretPattern = Regex("(?:sk-[A-Za-z0-9_-]+|AIza[A-Za-z0-9_-]{25,})")
  private val credentialPattern = Regex("(?i)([\"']?(?:api[_-]?key|authorization|token|password)[\"']?\\s*[:=]\\s*)[\"']?[^\\s,;\"'}]+")
  fun redact(value: String): String = value
    .replace(bearerPattern, "Bearer [REDACTED]")
    .replace(secretPattern, "[REDACTED]")
    .replace(credentialPattern, "$1[REDACTED]")

  fun init(context: Context) {
    appContext = context.applicationContext
    if (!initialized.compareAndSet(false, true)) return
    val previous = Thread.getDefaultUncaughtExceptionHandler()
    Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
      write("FATAL_CRASH", "thread=${thread.name}; ${safeTrace(throwable)}")
      previous?.uncaughtException(thread, throwable)
    }
  }

  private fun safeTrace(error: Throwable): String =
    error.javaClass.simpleName + "\n" + error.stackTrace.take(40).joinToString("\n") { "  at $it" }
  fun event(tag: String, message: String, error: Throwable? = null) {
    val safe = redact(message) + if (error == null) "" else "\n" + safeTrace(error)
    try { writer.execute { write(tag, safe) } }
    catch (_: java.util.concurrent.RejectedExecutionException) { dropped.incrementAndGet() }
  }
  fun log(context: Context, tag: String, message: String, throwable: Throwable? = null) =
    logError(context, tag, message, throwable)
  fun logError(context: Context, tag: String, message: String, throwable: Throwable? = null) {
    if (appContext == null) init(context)
    event(tag, if (throwable == null) redact(message) else "Operation failed", throwable)
  }

  private fun write(tag: String, message: String) {
    val context = appContext ?: return
    try {
      synchronized(fileLock) {
        val directory = File(context.filesDir, "diagnostics").apply { mkdirs() }
        val file = File(directory, "error_log_${LocalDate.now()}.txt")
        val entry = "[${Instant.now()}] [${tag.take(80)}] ${redact(message).take(12_000)}\n"
        if (file.length() + entry.toByteArray().size > MAX_BYTES) {
          val rotated = File(directory, file.name + ".previous")
          rotated.delete(); file.renameTo(rotated)
        }
        file.appendText(entry, Charsets.UTF_8)
        directory.listFiles()?.sortedByDescending { it.lastModified() }?.drop(6)?.forEach { it.delete() }
      }
    } catch (_: Exception) { Log.e("PocketDiagnostics", "Unable to write diagnostic file") }
  }

  suspend fun flush() = withContext(Dispatchers.IO) {
    val completed = java.util.concurrent.CountDownLatch(1)
    val barrier = Runnable { completed.countDown() }
    try { writer.execute(barrier) }
    catch (_: java.util.concurrent.RejectedExecutionException) {
      check(writer.queue.offer(barrier, 5, TimeUnit.SECONDS)) { "Diagnostic writer is busy." }
    }
    check(completed.await(5, TimeUnit.SECONDS)) { "Diagnostic writer is busy." }
  }
  suspend fun readLogs(context: Context): String = withContext(Dispatchers.IO) {
    flush()
    synchronized(fileLock) {
      File(context.filesDir, "diagnostics").listFiles()?.sortedBy { it.lastModified() }
        ?.joinToString("\n") { redact(it.readText()) }?.ifBlank { "No hay registros." } ?: "No hay registros."
    }
  }
  suspend fun briefReport(context: Context): String = withContext(Dispatchers.IO) {
    flush()
    @Suppress("DEPRECATION")
    val info = context.packageManager.getPackageInfo(context.packageName, 0)
    synchronized(fileLock) {
      val files = File(context.filesDir, "diagnostics").listFiles()?.sortedBy { it.lastModified() }.orEmpty()
      val lines = sequence {
        files.forEach { file -> file.bufferedReader(Charsets.UTF_8).use { reader ->
          reader.lineSequence().forEach { yield(it) }
        } }
      }
      DiagnosticSummary.create(lines, info.versionName ?: "desconocida", android.os.Build.VERSION.SDK_INT, dropped.get())
    }
  }
  suspend fun clearLogs(context: Context): Boolean = withContext(Dispatchers.IO) {
    flush()
    synchronized(fileLock) { File(context.filesDir, "diagnostics").listFiles()?.all { it.delete() } ?: true }
  }
  suspend fun saveLogs(context: Context, uri: android.net.Uri) {
    val content = readLogs(context)
    withContext(Dispatchers.IO) {
      val output = context.contentResolver.openOutputStream(uri, "wt") ?: error("Destino no disponible.")
      output.bufferedWriter(Charsets.UTF_8).use { it.write(content) }
    }
  }

  suspend fun saveBriefReport(context: Context, uri: android.net.Uri, report: String) = withContext(Dispatchers.IO) {
    require(report.length <= DiagnosticSummary.MAX_CHARS)
    val output = context.contentResolver.openOutputStream(uri, "wt") ?: error("Destino no disponible.")
    output.bufferedWriter(Charsets.UTF_8).use { it.write(report) }
  }

  fun exportErrorLogs(context: Context) {
    scope.launch {
      try {
        val content = readLogs(context)
        val directory = File(context.cacheDir, "log_export").apply { mkdirs() }
        directory.listFiles()?.forEach { it.delete() }
        val file = File(directory, "error_log_${LocalDate.now()}.txt").apply { writeText(content) }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        withContext(Dispatchers.Main) {
          val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"; putExtra(Intent.EXTRA_STREAM, uri)
            clipData = ClipData.newRawUri("Diagnostic log", uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
          }
          context.startActivity(Intent.createChooser(intent, "Compartir diagnóstico")
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
      } catch (e: CancellationException) { throw e }
      catch (e: Exception) { event("LOG_EXPORT", "Export failed", e) }
    }
  }
}
