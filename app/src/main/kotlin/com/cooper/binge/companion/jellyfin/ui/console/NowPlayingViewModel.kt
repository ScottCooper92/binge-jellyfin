package com.cooper.binge.companion.jellyfin.ui.console

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cooper.binge.companion.jellyfin.server.Connection
import com.cooper.binge.companion.jellyfin.server.NowPlaying
import com.cooper.binge.companion.jellyfin.server.nowPlaying
import com.cooper.binge.companion.jellyfin.server.nowPlayingFeed
import com.cooper.binge.companion.jellyfin.server.stopPlaying
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface NowPlayingUiState {
    data object Loading : NowPlayingUiState

    data class Failed(
        val failure: ConsoleFailure,
    ) : NowPlayingUiState

    /**
     * What's playing, the stream the user is being asked about stopping, the one being stopped, and
     * the last stop that failed.
     */
    data class Ready(
        val sessions: List<NowPlaying>,
        val confirming: NowPlaying? = null,
        val stopping: String? = null,
        val stopFailed: ConsoleFailure? = null,
    ) : NowPlayingUiState
}

/**
 * Who is watching what: read once, then kept current by the server's session pushes on the page's
 * own event socket, which closes with the page. Stopping a stream asks first.
 */
class NowPlayingViewModel(
    private val connection: Connection,
) : ViewModel() {
    private val _state = MutableStateFlow<NowPlayingUiState>(NowPlayingUiState.Loading)
    val state: StateFlow<NowPlayingUiState> = _state.asStateFlow()

    private var feed: Job? = null

    init {
        load()
    }

    fun load() {
        feed?.cancel()
        _state.value = NowPlayingUiState.Loading
        feed =
            viewModelScope.launch {
                val first = connection.consoleCall(TAG) { _, client -> client.nowPlaying() to client }
                first
                    .onFailure { _state.value = NowPlayingUiState.Failed(it.consoleFailure()) }
                    .onSuccess { (sessions, client) ->
                        _state.value = NowPlayingUiState.Ready(sessions)
                        client.nowPlayingFeed().collect { pushed -> updateReady { following(pushed) } }
                    }
            }
    }

    fun askStop(session: NowPlaying) = updateReady { copy(confirming = session, stopFailed = null) }

    fun cancelStop() = updateReady { copy(confirming = null) }

    fun stop() {
        val session = (_state.value as? NowPlayingUiState.Ready)?.confirming ?: return
        updateReady { copy(confirming = null, stopping = session.sessionId, stopFailed = null) }
        viewModelScope.launch {
            connection
                .consoleCall(TAG) { _, client -> client.stopPlaying(session.sessionId) }
                // The list follows the server: the stream leaves it when the server's next push drops it.
                .onSuccess { updateReady { copy(stopping = null) } }
                .onFailure { error -> updateReady { copy(stopping = null, stopFailed = error.consoleFailure()) } }
        }
    }

    private fun updateReady(change: NowPlayingUiState.Ready.() -> NowPlayingUiState.Ready) =
        _state.update { (it as? NowPlayingUiState.Ready)?.change() ?: it }

    private companion object {
        const val TAG = "JellyfinNowPlaying"
    }
}

/** This state with the sessions the server just pushed. A confirm for a stream that has ended closes. */
private fun NowPlayingUiState.Ready.following(pushed: List<NowPlaying>): NowPlayingUiState.Ready =
    copy(
        sessions = pushed,
        confirming = confirming?.takeIf { asked -> pushed.any { it.sessionId == asked.sessionId } },
    )
