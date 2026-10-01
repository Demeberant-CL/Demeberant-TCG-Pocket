package com.example.data.util

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object ErrorLogManager {

  private const val FILE_NAME = "error_logs.txt"
  private val initialized = java.util.concurrent.atomic.AtomicBoolean(false)
  private val fileLock = Any()
  private const val MAX_LOG_BYTES = 1024 * 1024
  private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())

  fun init(context: Context) {
    if (!initialized.compareAndSet(false, true)) return
    val appContext = context.applicationContext
    val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
    Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
      logError(appContext, "FATAL_CRASH", "Excepción no capturada en hilo ${thread.name}: ${throwable.localizedMessage}", throwable)
      defaultHandler?.uncaughtException(thread, throwable)
    }
  }

  fun log(context: Context, tag: String, message: String, throwable: Throwable? = null) {
    logError(context, tag, message, throwable)
  }

  fun logError(context: Context, tag: String, message: String, throwable: Throwable? = null) {
    val timestamp = synchronized(dateFormat) { dateFormat.format(Date()) }
    val stacktrace = throwable?.stackTraceToString()?.let { "\n$it" } ?: ""
    val logEntry = "[$timestamp] [$tag] $message$stacktrace\n"

    try {
      Log.e("TcgPocket-$tag", message, throwable)
      val file = File(context.filesDir, FILE_NAME)
      synchronized(fileLock) {
        if (file.length() > MAX_LOG_BYTES) file.writeText(file.readText().takeLast(MAX_LOG_BYTES / 4))
        file.appendText(logEntry)
      }
    } catch (e: Exception) {
      Log.e("ErrorLogManager", "Fallo al escribir en log de errores", e)
    }
  }

  suspend fun readLogs(context: Context): String = withContext(Dispatchers.IO) {
    val file = File(context.filesDir, FILE_NAME)
    if (file.exists()) {
      try {
        file.readText()
      } catch (e: Exception) {
        "Error al leer archivo de logs: ${e.localizedMessage}"
      }
    } else {
      "No hay registros de error almacenados."
    }
  }

  suspend fun clearLogs(context: Context): Boolean = withContext(Dispatchers.IO) {
    val file = File(context.filesDir, FILE_NAME)
    if (file.exists()) {
      file.delete()
    } else {
      true
    }
  }

  fun exportErrorLogs(context: Context) {
    try {
      val file = File(context.filesDir, FILE_NAME)
      if (!file.exists()) {
        file.writeText("[${synchronized(dateFormat) { dateFormat.format(Date()) }}] [INFO] Inicialización de log de errores Demeberant TCG Pocket.\n")
      }

      val uri = FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        file
      )

      val shareIntent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_STREAM, uri)
        putExtra(Intent.EXTRA_SUBJECT, "Reporte de Errores - Demeberant TCG Pocket")
        putExtra(Intent.EXTRA_TEXT, "Adjunto el registro de errores de la aplicación Demeberant TCG Pocket.")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
      }

      val chooser = Intent.createChooser(shareIntent, "Exportar Log de Errores")
      chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
      context.startActivity(chooser)
    } catch (e: Exception) {
      logError(context, "EXPORT_LOGS", "Fallo al exportar log de errores", e)
    }
  }
}
