package org.leakcanary

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

sealed class Screen(val title: String) : Parcelable {

  @Parcelize
  object ClientApps : Screen("Apps")

  @Parcelize
  class ClientAppAnalyses(val packageName: String) : Screen(packageName)

  @Parcelize
  class ClientAppAnalysis(val packageName: String, val analysisId: Long) : Screen("Analysis")

  @Parcelize
  object Leaks : Screen("Leaks")

  @Parcelize
  class Leak(
    val leakSignature: String,
    val selectedAnalysisId: Long? = null
  ) : Screen("Leak")
}
