package org.leakcanary

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class LeakCanaryApp : Application() {

  override fun onCreate() {
    super.onCreate()
    // TODO
  }
}
