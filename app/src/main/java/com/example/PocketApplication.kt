package com.example

import android.app.Application
import com.example.data.util.ErrorLogManager

class PocketApplication : Application() {
  override fun onCreate() {
    super.onCreate()
    ErrorLogManager.init(this)
    ErrorLogManager.event("APP_START", "Application initialized")
  }
}
