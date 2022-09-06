package org.leakcanary.screens

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ActivityRetainedComponent
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.scopes.ActivityRetainedScoped
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.leakcanary.screens.Screen.ClientApps

/**
 * Makes the BackStack state stream injectable in activity scoped view models. This
 * is a dumb hack as I couldn't figure out how to inject a view model into a view model.
 * This is doubly dumb as we need to ensure the backstack is created before
 * BackStackHolder.backStack is accessed.
 */
@ActivityRetainedScoped
class BackStackHolder @Inject constructor() {
  lateinit var navigator: Navigator
}

@InstallIn(ActivityRetainedComponent::class)
@Module
class BackStackModule {
  @Provides fun provideBackStack(holder: BackStackHolder): Navigator = holder.navigator
}

interface Navigator {
  val currentScreenState: StateFlow<CurrentScreenState>

  fun goBack()

  fun goTo(screen: Screen)

  fun resetTo(screen: Screen)
}

// TODO This currently does not save UI state in the backstack.
@HiltViewModel
class BackStackViewModel @Inject constructor(
  private val savedStateHandle: SavedStateHandle,
  stateStream: BackStackHolder
) : ViewModel(), Navigator {

  private var screenStack: List<Screen> = savedStateHandle[BACKSTACK_KEY] ?: arrayListOf(ClientApps)
    set(value) {
      field = value
      savedStateHandle[BACKSTACK_KEY] = ArrayList(value)
    }

  private val _currentScreenState = MutableStateFlow(screenStack.asState(true))

  override val currentScreenState = _currentScreenState.asStateFlow()

  init {
    stateStream.navigator = this
  }

  override fun goBack() {
    check(_currentScreenState.value.canGoBack) {
      "Backstack cannot go further back."
    }
    navigate(screenStack.dropLast(1), forward = false)
  }

  override fun goTo(screen: Screen) {
    navigate(screenStack + screen, forward = true)
  }

  private fun navigate(newScreenStack: List<Screen>, forward: Boolean) {
    screenStack = newScreenStack
    _currentScreenState.value = newScreenStack.asState(forward)
  }

  companion object {
    private const val BACKSTACK_KEY = "backstack"
  }

  override fun resetTo(screen: Screen) {
    navigate(listOf(screen), forward = false)
  }
}

private fun List<Screen>.asState(forward: Boolean) =
  CurrentScreenState(screen = last(), canGoBack = size > 1, forward)

data class CurrentScreenState(
  val screen: Screen,
  val canGoBack: Boolean,
  val forward: Boolean
)
