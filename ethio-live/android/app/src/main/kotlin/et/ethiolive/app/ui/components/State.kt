package et.ethiolive.app.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface Load<out T> {
    data object Loading : Load<Nothing>
    data class Ready<T>(val value: T, val refreshing: Boolean = false) : Load<T>
    data class Failed(val error: Throwable) : Load<Nothing>
}

/** Generic view model for read-mostly screens: loads once, supports pull-to-refresh and retry. */
class LoadViewModel<T>(private val loader: suspend () -> T) : ViewModel() {
    private val _state = MutableStateFlow<Load<T>>(Load.Loading)
    val state: StateFlow<Load<T>> = _state.asStateFlow()
    private var job: Job? = null

    init { reload() }

    fun reload() {
        job?.cancel()
        val current = _state.value
        _state.value = if (current is Load.Ready) current.copy(refreshing = true) else Load.Loading
        job = viewModelScope.launch {
            _state.value = try {
                Load.Ready(loader())
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (current is Load.Ready) current.copy(refreshing = false) else Load.Failed(e)
            }
        }
    }

    fun update(transform: (T) -> T) {
        val current = _state.value
        if (current is Load.Ready) _state.value = current.copy(value = transform(current.value))
    }
}

@Composable
fun <T> rememberLoad(key: String, loader: suspend () -> T): LoadViewModel<T> =
    viewModel(key = key) { LoadViewModel(loader) }

/** Renders loading / error / content for a [LoadViewModel]. */
@Composable
fun <T> LoadContent(vm: LoadViewModel<T>, modifier: Modifier = Modifier, content: @Composable (T) -> Unit) {
    val state by vm.state.collectAsState()
    when (val s = state) {
        is Load.Loading -> Loading(modifier)
        is Load.Failed -> ErrorState(s.error, onRetry = vm::reload, modifier = modifier)
        is Load.Ready -> content(s.value)
    }
}

/** Runs [block] once when [key] changes; convenience for screens that refresh on entry. */
@Composable
fun OnEnter(key: Any?, block: suspend () -> Unit) {
    LaunchedEffect(key) { block() }
}
