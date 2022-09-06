package org.leakcanary

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.with
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallTopAppBar
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import org.leakcanary.Screen.ClientAppAnalyses
import org.leakcanary.Screen.ClientAppAnalysis
import org.leakcanary.Screen.Leak
import org.leakcanary.Screen.Leaks

// TODO Handle intents
@OptIn(ExperimentalMaterial3Api::class, ExperimentalAnimationApi::class)
@Composable
fun ScreenHost(backStack: BackStack = viewModel()) {
  val currentScreenState by backStack.currentScreenState.collectAsState()

  BackHandler(enabled = currentScreenState.canGoBack) {
    backStack.goBack()
  }
  Scaffold(
    topBar = {
      SmallTopAppBar(
        title = {
          Text(text = currentScreenState.screen.title)
        },
        navigationIcon = {
          if (currentScreenState.canGoBack) {
            IconButton(onClick = {
              backStack.goBack()
            }) {
              Icon(Icons.Filled.ArrowBack, contentDescription = "Go back")
            }
          }
        }
      )
    }
  ) {
    AnimatedContent(
      targetState = currentScreenState,
      transitionSpec = {
        val directionFactor = if (targetState.forward) 1 else -1
        slideInHorizontally(
          initialOffsetX = { fullWidth ->
            directionFactor * fullWidth
          }
        ) with slideOutHorizontally(targetOffsetX = { fullWidth ->
          directionFactor * -fullWidth
        })
      }
    ) { targetState ->
      Box(modifier = Modifier.fillMaxWidth()) {
        when (targetState.screen) {
          is ClientAppAnalyses -> ClientAppAnalysesScreen()
          is ClientAppAnalysis -> TODO()
          Screen.ClientApps -> ClientAppsScreen()
          is Leak -> TODO()
          Leaks -> TODO()
        }
      }
    }
  }
}
