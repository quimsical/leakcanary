package org.leakcanary.screens

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

sealed class Screen(val title: String) : Parcelable {

  @Parcelize
  object ClientApps : Screen("Apps")

  // TODO Figure out dynamic titles, this should say "X Heap Analyses"
  // Should also show the app name, icon..
  // Can use content for now.
  @Parcelize
  class ClientAppAnalyses(val packageName: String) : Screen("Heap Analyses")

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
