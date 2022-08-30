package org.leakcanary

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import org.leakcanary.Screen.ClientApps

// TODO This currently does not save UI state in the backstack.
@HiltViewModel
class BackStack @Inject constructor(
  private val savedStateHandle: SavedStateHandle
) : ViewModel() {

  private var screenStack: List<Screen> = savedStateHandle[BACKSTACK_KEY] ?: arrayListOf(ClientApps)
    set(value) {
      field = value
      savedStateHandle[BACKSTACK_KEY] = ArrayList(value)
    }

  var currentScreenState by mutableStateOf(screenStack.asState(true))
    private set

  fun goBack() {
    check(currentScreenState.canGoBack) {
      "Backstack cannot go further back."
    }
    navigate(screenStack.dropLast(1), forward = false)
  }

  fun goTo(screen: Screen) {
    navigate(screenStack + screen, forward = true)
  }

  private fun navigate(newScreenStack: List<Screen>, forward: Boolean) {
    screenStack = newScreenStack
    currentScreenState = newScreenStack.asState(forward)
  }

  companion object {
    private const val BACKSTACK_KEY = "backstack"
  }

  fun resetTo(screen: Screen) {
    navigate(listOf(screen), forward = false)
  }

}

private fun List<Screen>.asState(forward: Boolean) = CurrentScreenState(screen = last(), canGoBack = size > 1, forward)

data class CurrentScreenState(
  val screen: Screen,
  val canGoBack: Boolean,
  val forward: Boolean
)
