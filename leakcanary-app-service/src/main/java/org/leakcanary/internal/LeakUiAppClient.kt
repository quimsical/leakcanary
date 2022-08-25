package org.leakcanary.internal

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.util.Log
import shark.HeapAnalysis

/**
 * Note about Target API 30 bindService() restrictions.
 * https://asvid.github.io/2021-09-03-android-service-binding-on-api30
 * https://medium.com/androiddevelopers/package-visibility-in-android-11-cc857f221cd9
 *
 * Binding to a service now requires either to add its package in the queries tag of the manifest,
 * or fall into one of the cases where visibility is automatic:
 * https://developer.android.com/training/package-visibility/automatic
 *
 * We can ensure apps have the LeakCanary app in their manifest, however the other way round
 * isn't possible, we don't know in advance which apps we'll talk to.
 *
 * One of the automatic cases is "Any app that starts or binds to a service in your app".
 *
 * So we'll have apps poke the LeakCanary app by binding, which then gives it permission to bind
 * a service back.
 */
class PokeLeakUiApp(
  context: Context
) {
  private val appContext = context.applicationContext

  fun poke(heapAnalysis: HeapAnalysis) {
    val serviceConnection = object : ServiceConnection {
      override fun onServiceConnected(name: ComponentName, service: IBinder) {
        LeakUiApp.Stub.asInterface(service).sayHi()
        appContext.unbindService(this)
      }

      override fun onServiceDisconnected(name: ComponentName) = Unit
    }

    val intent = Intent(LeakUiApp::class.qualifiedName)
      .apply {
        setPackage("org.leakcanary")
      }
    val bringingServiceUp = appContext.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)

    Log.d("PokeLeakUiApp", "LeakUiAppService up=$bringingServiceUp")
  }

}
