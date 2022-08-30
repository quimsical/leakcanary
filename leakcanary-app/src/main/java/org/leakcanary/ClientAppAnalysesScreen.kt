package org.leakcanary

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun ClientAppAnalysesScreen(
  backStack: BackStack = viewModel()
) {
  Text(text = "Yo")

}
