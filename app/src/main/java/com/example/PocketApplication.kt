package com.example

import android.app.Application
import com.example.data.util.ErrorLogManager

class PocketApplication : Application(), coil.ImageLoaderFactory {
  override fun newImageLoader(): coil.ImageLoader = coil.ImageLoader.Builder(this)
    .diskCache {
      coil.disk.DiskCache.Builder().directory(cacheDir.resolve("card-images"))
        .maxSizeBytes(256L * 1024 * 1024).build()
    }
    .okHttpClient(com.example.data.network.PocketHttp.imageClient).build()

  override fun onCreate() {
    super.onCreate()
    ErrorLogManager.init(this)
    // Restore a bounded, expiring absence cache off the UI thread.
    java.util.concurrent.Executors.newSingleThreadExecutor().let { worker ->
      worker.execute { try { runCatching { com.example.data.network.PocketHttp.missingResources.restore(this) }
        .onFailure { ErrorLogManager.event("IMAGE_CACHE", "Could not restore absence cache", it) } } finally { worker.shutdown() } }
    }
    @Suppress("DEPRECATION")
    val version = runCatching { packageManager.getPackageInfo(packageName, 0).versionName }.getOrNull() ?: "unknown"
    ErrorLogManager.event("APP_START", "version=$version api=${android.os.Build.VERSION.SDK_INT}")
    com.example.data.util.AppDiagnostics.start(this)
  }

  override fun onTrimMemory(level: Int) {
    super.onTrimMemory(level)
    ErrorLogManager.event("MEMORY_PRESSURE", "level=$level")
  }
}
