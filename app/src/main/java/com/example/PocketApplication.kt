package com.example

import android.app.Application
import com.example.data.util.ErrorLogManager

class PocketApplication : Application(), coil.ImageLoaderFactory {
  override fun newImageLoader(): coil.ImageLoader = coil.ImageLoader.Builder(this)
    .okHttpClient(com.example.data.network.PocketHttp.imageClient).build()

  override fun onCreate() {
    super.onCreate()
    ErrorLogManager.init(this)
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
