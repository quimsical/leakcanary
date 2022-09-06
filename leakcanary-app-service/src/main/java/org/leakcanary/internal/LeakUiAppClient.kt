package org.leakcanary.internal

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.leakcanary.internal.ParcelableHeapAnalysis.Companion.asParcelable
import shark.HeapAnalysis
import shark.SharkLog

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
class LeakUiAppClient(
  context: Context
) {
  private val appContext = context.applicationContext

  fun sendHeapAnalysis(heapAnalysis: HeapAnalysis) {
    val sendLatch = CountDownLatch(1)
    lateinit var leakUiApp: LeakUiApp

    val serviceConnection = object : ServiceConnection {
      // Note: this is on the main thread, don't block.
      override fun onServiceConnected(name: ComponentName, service: IBinder) {
        leakUiApp = LeakUiApp.Stub.asInterface(service)
        sendLatch.countDown()
      }

      override fun onServiceDisconnected(name: ComponentName) = Unit
    }

    // TODO Enforce package signature, here or on service connected.
    val intent = Intent(LeakUiApp::class.qualifiedName)
      .apply {
        setPackage("org.leakcanary")
      }
    val bringingServiceUp =
      appContext.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
    SharkLog.d { "LeakUiAppService up=$bringingServiceUp" }
    val serviceConnected = sendLatch.await(20, TimeUnit.SECONDS)
    if (serviceConnected) {
      leakUiApp.sendHeapAnalysis(heapAnalysis.asParcelable())
    } else {
      // TODO Handle service connection error
    }
    appContext.unbindService(serviceConnection)
  }
}
