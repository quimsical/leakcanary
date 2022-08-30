package org.leakcanary

import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import org.leakcanary.internal.LeakUiApp
import org.leakcanary.internal.ParcelableHeapAnalysis

@AndroidEntryPoint
class LeakUiAppService : Service() {

  @Inject lateinit var heapRepository: HeapRepository

  // TODO Stubs can be longer lived than the outer service, handle
  // manually clearing out the stub reference to the service.
  private val binder = object : LeakUiApp.Stub() {

    override fun sendHeapAnalysis(heapAnalysis: ParcelableHeapAnalysis) {
      val callerPackageName = packageManager.getNameForUid(Binder.getCallingUid())!!
      // TODO maybe return an intent for notification?
      heapRepository.insertHeapAnalysis(callerPackageName, heapAnalysis.wrapped)
    }
  }

  override fun onBind(intent: Intent): IBinder {
    // TODO Return null if we can't handle the caller's version
    return binder
  }
}
