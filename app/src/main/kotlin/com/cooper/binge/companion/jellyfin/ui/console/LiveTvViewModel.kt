package com.cooper.binge.companion.jellyfin.ui.console

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cooper.binge.companion.jellyfin.server.Connection
import com.cooper.binge.companion.jellyfin.server.LiveTvSummary
import com.cooper.binge.companion.jellyfin.server.liveTvSummary
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface LiveTvUiState {
    data object Loading : LiveTvUiState

    data class Failed(
        val failure: ConsoleFailure,
    ) : LiveTvUiState

    data class Ready(
        val summary: LiveTvSummary,
    ) : LiveTvUiState
}

/**
 * The server's Live TV setup: its tuner hosts and guide data providers, and when the guide ends. [withGuide] is whether the user has
 * Live TV access, which the server's guide lookup needs on top of the management flag that opens this page.
 */
class LiveTvViewModel(
    private val connection: Connection,
    private val withGuide: Boolean,
) : ViewModel() {
    private val _state = MutableStateFlow<LiveTvUiState>(LiveTvUiState.Loading)
    val state: StateFlow<LiveTvUiState> = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        _state.value = LiveTvUiState.Loading
        viewModelScope.launch {
            _state.value =
                connection
                    .consoleCall(TAG) { _, client -> client.liveTvSummary(withGuide) }
                    .fold(
                        onSuccess = { LiveTvUiState.Ready(it) },
                        onFailure = { LiveTvUiState.Failed(it.consoleFailure()) },
                    )
        }
    }

    /** Reads the setup again without the page's skeleton, after a change to what it lists. */
    fun refresh() {
        viewModelScope.launch {
            connection.consoleCall(TAG) { _, client -> client.liveTvSummary(withGuide) }.onSuccess { fresh ->
                _state.update { (it as? LiveTvUiState.Ready)?.copy(summary = fresh) ?: it }
            }
        }
    }

    private companion object {
        const val TAG = "JellyfinLiveTv"
    }
}
