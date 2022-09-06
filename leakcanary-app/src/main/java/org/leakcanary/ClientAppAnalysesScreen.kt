package org.leakcanary

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import org.leakcanary.ClientAppAnalysesState.Loaded
import org.leakcanary.ClientAppAnalysesState.Loading
import org.leakcanary.ClientAppAnalysis.Failure
import org.leakcanary.ClientAppAnalysis.Success
import org.leakcanary.Screen.ClientAppAnalyses

sealed class ClientAppAnalysis(val id: Long, val createdAtTimeMillis: Long) {
  class Success(id: Long, createdAtTimeMillis: Long, val leakCount: Int) :
    ClientAppAnalysis(id, createdAtTimeMillis)

  class Failure(id: Long, createdAtTimeMillis: Long, val exceptionSummary: String) :
    ClientAppAnalysis(id, createdAtTimeMillis)
}

sealed interface ClientAppAnalysesState {
  object Loading : ClientAppAnalysesState
  class Loaded(val analyses: List<ClientAppAnalysis>) : ClientAppAnalysesState
}

@HiltViewModel
class ClientAppAnalysesViewModel @Inject constructor(
  private val repository: HeapRepository,
  backStackHolder: BackStackHolder
) : ViewModel() {

  // This flow is stopped when unsubscribed, so renavigating to the same
  // screen always polls the latest screen. Yeah it should be a flow instead.
  val state = backStackHolder.backStack.currentScreenState
    .filter { it.screen is ClientAppAnalyses }
    .flatMapLatest { state ->
      stateStream((state.screen as ClientAppAnalyses).packageName)
    }.stateIn(
      viewModelScope, started = WhileSubscribedOrRetained, initialValue = Loading
    )

  private fun stateStream(appPackageName: String) =
    repository.listAppAnalyses(appPackageName).map { app ->
      Loaded(app.map { row ->
        if (row.exception_summary == null) {
          ClientAppAnalysis.Success(
            id = row.id,
            createdAtTimeMillis = row.created_at_time_millis!!,
            leakCount = row.leak_count!!
          )
        } else {
          ClientAppAnalysis.Failure(
            id = row.id,
            createdAtTimeMillis = row.created_at_time_millis!!,
            exceptionSummary = row.exception_summary
          )
        }
      })
    }
}

@Composable fun ClientAppAnalysesScreen(
  backStack: BackStack = viewModel(), viewModel: ClientAppAnalysesViewModel = viewModel()
) {
  val stateProp by viewModel.state.collectAsState()

  when (val state = stateProp) {
    is Loading -> {
      Text(text = "Loading...")
    }
    is Loaded -> {
      if (state.analyses.isEmpty()) {
        Text(text = "No analysis")
      } else {
        ClientAppAnalysisList(analyses = state.analyses, onRowClicked = { analysis ->
          TODO("Go to analysis")
        })
      }
    }
  }
}

@Composable private fun ClientAppAnalysisList(
  analyses: List<ClientAppAnalysis>, onRowClicked: (ClientAppAnalysis) -> Unit
) {
  LazyColumn(modifier = Modifier.fillMaxHeight()) {
    items(analyses) { analysis ->
      Column {
        val context = LocalContext.current
        val createdAt = TimeFormatter.formatTimestamp(context, analysis.createdAtTimeMillis)
        Text(text = createdAt)
        when (analysis) {
          is Failure -> Text(text = analysis.exceptionSummary)
          is Success -> Text(
            text = "${analysis.leakCount} Distinct Leak"
              + if (analysis.leakCount == 0) "" else "s"
          )
        }
      }
    }
  }
}
