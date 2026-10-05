package com.example

import android.app.Application
import com.example.data.util.ErrorLogManager

class PocketApplication : Application(), coil.ImageLoaderFactory {
  override fun newImageLoader(): coil.ImageLoader = coil.ImageLoader.Builder(this)
    .okHttpClient(com.example.data.network.PocketHttp.imageClient).build()

  override fun onCreate() {
    super.onCreate()
    ErrorLogManager.init(this)
    ErrorLogManager.event("APP_START", "Application initialized")
  }
}
