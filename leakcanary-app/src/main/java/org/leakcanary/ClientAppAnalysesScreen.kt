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
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import org.leakcanary.ClientAppAnalysesState.Loaded
import org.leakcanary.ClientAppAnalysesState.Loading

sealed class ClientAppAnalysis(val id: Long, val createdAtTimeMillis: Long) {
  class Success(id: Long, createdAtTimeMillis: Long, val leakCount: Int) : ClientAppAnalysis(id, createdAtTimeMillis)
  class Failure(id: Long, createdAtTimeMillis: Long, val exceptionSummary: String) : ClientAppAnalysis(id, createdAtTimeMillis)
}

sealed interface ClientAppAnalysesState {
  object Loading : ClientAppAnalysesState
  class Loaded(val analyses: List<ClientAppAnalysis>) : ClientAppAnalysesState
}

@HiltViewModel
class ClientAppAnalysesViewModel @Inject constructor(
  private val repository: HeapRepository,
) : ViewModel() {

  val state = stateStream().stateIn(
    viewModelScope,
    started = WhileSubscribedOrRetained,
    initialValue = Loading
  )

  // TODO need the current screen to find out which app this is for.
  private fun stateStream() = repository.listAppAnalyses("")
    .map { app -> Loaded(app.map { row ->
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
    }) }

}

@Composable
fun ClientAppAnalysesScreen(
  backStack: BackStack = viewModel(),
  viewModel: ClientAppAnalysesViewModel = viewModel()
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

@Composable
private fun ClientAppAnalysisList(analyses: List<ClientAppAnalysis>, onRowClicked: (ClientAppAnalysis) -> Unit) {
  LazyColumn(modifier = Modifier.fillMaxHeight()) {
    items(analyses) { analysis ->
      // TODO Details
      Text(
        modifier = Modifier.clickable {
          onRowClicked(analysis)
        },
        text = "??"
      )
    }
  }
}
