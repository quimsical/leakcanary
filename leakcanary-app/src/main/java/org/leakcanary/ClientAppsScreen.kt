package org.leakcanary

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import org.leakcanary.ClientAppState.Loaded
import org.leakcanary.ClientAppState.Loading
import org.leakcanary.Screen.ClientAppAnalyses

class ClientApp(val packageName: String, val leakCount: Int)

sealed interface ClientAppState {
  object Loading : ClientAppState
  class Loaded(val clientApps: List<ClientApp>) : ClientAppState
}

@HiltViewModel
class ClientAppsViewModel @Inject constructor(
  private val repository: HeapRepository,
) : ViewModel() {
  val clientAppState = clientAppsStateStream().stateIn(
    viewModelScope,
    started = WhileSubscribedOrRetained,
    initialValue = Loading
  )

  private fun clientAppsStateStream() = repository.listClientApps()
    .map { app -> Loaded(app.map { ClientApp(it.package_name, it.leak_count!!) }) }
}

@Composable
fun ClientAppsScreen(
  viewModel: ClientAppsViewModel = viewModel(),
  backStack: BackStack = viewModel()
) {
  val clientAppState: ClientAppState by viewModel.clientAppState.collectAsState()

  when (val state = clientAppState) {
    is Loading -> {
      Text(text = "Loading...")
    }
    is Loaded -> {
      if (state.clientApps.isEmpty()) {
        Text(text = "No apps")
      } else {
        ClientAppList(apps = state.clientApps, onAppClicked = { app ->
          backStack.goTo(ClientAppAnalyses(app.packageName))
        })
      }
    }
  }
}

@Composable
private fun ClientAppList(apps: List<ClientApp>, onAppClicked: (ClientApp) -> Unit) {
  LazyColumn(modifier = Modifier.fillMaxHeight()) {
    items(apps) { app ->
      // TODO Icon & package name
      Text(
        modifier = Modifier.clickable {
          onAppClicked(app)
        },
        text = "${app.packageName} : ${app.leakCount} leaks"
      )
    }
  }
}
