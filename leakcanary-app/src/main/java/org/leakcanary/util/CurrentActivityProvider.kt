package org.leakcanary.util

import android.app.Activity
import android.app.Application
import android.app.Application.ActivityLifecycleCallbacks
import android.os.Bundle
import androidx.activity.ComponentActivity
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.components.ActivityComponent
import dagger.hilt.android.scopes.ActivityRetainedScoped
import dagger.hilt.internal.GeneratedComponentManagerHolder
import javax.inject.Inject

@EntryPoint
@InstallIn(ActivityComponent::class)
interface ActivityProviderEntryPoint {
  val activityProvider: CurrentActivityProvider
}

@ActivityRetainedScoped
class CurrentActivityProvider @Inject constructor() {

  private var _currentActivity: Activity? = null

  /**
   * Prefer using [withActivity]
   * Provides the current activity inside an activity retained scoped
   * object. DO NOT STORE THE RETURNED REFERENCE ANYWHERE.
   */
  val currentActivity: Activity
    get() {
      checkMainThread()
      return _currentActivity!!
    }

  fun <T> withActivity(block: Activity.() -> T) : T {
    return currentActivity.block()
  }

  companion object {
    private fun ComponentActivity.getProvider() =
      EntryPointAccessors.fromActivity<ActivityProviderEntryPoint>(this).activityProvider

    fun onActivityCreated(activity: Activity) {
      if (activity is ComponentActivity && activity is GeneratedComponentManagerHolder) {
        activity.getProvider()._currentActivity = activity
      }
    }

    fun onActivityDestroyed(activity: Activity) {
      if (activity is ComponentActivity && activity is GeneratedComponentManagerHolder) {
        val provider = activity.getProvider()
        if (provider._currentActivity === activity) {
          provider._currentActivity = null
        }
      }
    }
  }
}

class ActivityProviderCallbacks : ActivityLifecycleCallbacks {

  companion object {
    fun Application.installActivityProviderCallbacks() {
      registerActivityLifecycleCallbacks(ActivityProviderCallbacks())
    }
  }

  override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
    CurrentActivityProvider.onActivityCreated(activity)
  }

  override fun onActivityDestroyed(activity: Activity) {
    CurrentActivityProvider.onActivityDestroyed(activity)
  }

  override fun onActivityStarted(activity: Activity) = Unit

  override fun onActivityResumed(activity: Activity) = Unit

  override fun onActivityPaused(activity: Activity) = Unit

  override fun onActivityStopped(activity: Activity) = Unit

  override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
}
