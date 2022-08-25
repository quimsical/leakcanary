package org.leakcanary

import android.app.Service
import android.content.Intent
import android.os.IBinder
import org.leakcanary.internal.LeakUiApp

class LeakUiAppService : Service() {

  // TODO Stubs can be longer lived than the outer service, handle
  // manually clearing out the stub reference to the service.
  private val binder = object : LeakUiApp.Stub() {
    override fun sayHi() {
      println("LeakUiApp says hi")
    }
  }

  override fun onBind(intent: Intent): IBinder {
    // TODO Return null if we can't handle the caller's version
    return binder
  }
}
