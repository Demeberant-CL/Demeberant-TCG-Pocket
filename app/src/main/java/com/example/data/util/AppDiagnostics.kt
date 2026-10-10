package com.example.data.util

import android.app.Activity
import android.app.ActivityManager
import android.app.Application
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/** Foreground stall detector, not an assertion that Android declared an ANR. */
object AppDiagnostics : Application.ActivityLifecycleCallbacks {
  private val started = AtomicBoolean(false)
  private val foreground = AtomicBoolean(false)
  private val pending = AtomicBoolean(false)
  private val main = Handler(Looper.getMainLooper())
  @Volatile private var postedAt = 0L
  @Volatile private var reported = false
  @Volatile private var screen = "START"
  fun screen(value: String) {
    if (value != screen) {
      screen = value.replace(Regex("[^A-Z0-9_]"), "").take(60)
      ErrorLogManager.event("NAVIGATION", "screen=$screen")
    }
  }
  fun start(app: Application) {
    if (!started.compareAndSet(false, true)) return
    app.registerActivityLifecycleCallbacks(this)
    val timer = Executors.newSingleThreadScheduledExecutor { task ->
      Thread(task, "pocket-health").apply { isDaemon = true }
    }
    timer.execute {
      try {
        if (Build.VERSION.SDK_INT >= 30) {
          val prefs = app.getSharedPreferences("diagnostic-health", 0)
          val last = prefs.getLong("last-exit", 0)
          val exits = (app.getSystemService(Application.ACTIVITY_SERVICE) as ActivityManager)
            .getHistoricalProcessExitReasons(app.packageName, 0, 5)
          exits.filter { it.timestamp > last }.sortedBy { it.timestamp }.forEach {
            ErrorLogManager.event("PROCESS_EXIT", "reason=${it.reason} status=${it.status} at=${it.timestamp}")
          }
          exits.maxOfOrNull { it.timestamp }?.let { prefs.edit().putLong("last-exit", it).apply() }
        }
      } catch (error: Exception) { ErrorLogManager.event("DIAGNOSTIC_HEALTH", "Exit history unavailable", error) }
    }
    timer.scheduleWithFixedDelay({
      if (!foreground.get()) return@scheduleWithFixedDelay
      if (pending.compareAndSet(false, true)) {
        postedAt = SystemClock.uptimeMillis(); reported = false
        main.post { pending.set(false) }
      } else if (!reported && SystemClock.uptimeMillis() - postedAt >= 8000L && !android.os.Debug.isDebuggerConnected()) {
        reported = true
        val trace = Looper.getMainLooper().thread.stackTrace.take(40).joinToString("\n") { "  at $it" }
        ErrorLogManager.event("UI_STALL", "screen=$screen blockedMs=${SystemClock.uptimeMillis() - postedAt}\n$trace")
      }
    }, 2, 2, TimeUnit.SECONDS)
  }
  override fun onActivityResumed(activity: Activity) {
    pending.set(false); foreground.set(true)
    ErrorLogManager.event("LIFECYCLE", "RESUMED")
  }
  override fun onActivityPaused(activity: Activity) {
    foreground.set(false); pending.set(false)
    ErrorLogManager.event("LIFECYCLE", "PAUSED")
  }
  override fun onActivityCreated(activity: Activity, state: Bundle?) = Unit
  override fun onActivityStarted(activity: Activity) = Unit
  override fun onActivityStopped(activity: Activity) = Unit
  override fun onActivitySaveInstanceState(activity: Activity, state: Bundle) = Unit
  override fun onActivityDestroyed(activity: Activity) = Unit
}
