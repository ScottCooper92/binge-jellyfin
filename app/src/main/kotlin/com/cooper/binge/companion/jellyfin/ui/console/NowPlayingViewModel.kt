package com.cooper.binge.companion.jellyfin.ui.console

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.binge.companion.sdk.attempt
import com.cooper.binge.companion.jellyfin.server.Connection
import com.cooper.binge.companion.jellyfin.server.JellyfinAccount
import com.cooper.binge.companion.jellyfin.server.JellyfinClient
import com.cooper.binge.companion.jellyfin.server.NowPlaying
import com.cooper.binge.companion.jellyfin.server.SessionControls
import com.cooper.binge.companion.jellyfin.server.controllableSessionIds
import com.cooper.binge.companion.jellyfin.server.nowPlaying
import com.cooper.binge.companion.jellyfin.server.nowPlayingFeed
import com.cooper.binge.companion.jellyfin.server.stopPlaying
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
        /** What the signed-in user may do to each session, by session id: a session with no entry takes no control. */
        val controls: Map<String, SessionControls> = emptyMap(),
        /** The session whose page is open, if any. */
        val selected: String? = null,
        /** A playback command is on its way, so the page's buttons wait. */
        val sending: Boolean = false,
        val sendFailed: ConsoleFailure? = null,
        /** The message sheet is open for this session, with the last send it refused, and whether one is on its way. */
        val messaging: String? = null,
        val messageSending: Boolean = false,
        val messageFailed: ConsoleFailure? = null,
    ) : NowPlayingUiState {
        /** The open session, while it is still in the feed. */
        val selectedSession: NowPlaying? get() = selected?.let { id -> sessions.firstOrNull { it.sessionId == id } }

        fun controlsFor(session: NowPlaying): SessionControls = controls[session.sessionId] ?: SessionControls()
    }
}

/**
 * Who is watching what: read once, then kept current by the server's session pushes on the page's
 * own event socket, which closes with the page. Stopping a stream asks first.
 */
class NowPlayingViewModel(
    private val connection: Connection,
    private val account: JellyfinAccount = JellyfinAccount(),
) : ViewModel() {
    private val _state = MutableStateFlow<NowPlayingUiState>(NowPlayingUiState.Loading)
    val state: StateFlow<NowPlayingUiState> = _state.asStateFlow()

    private var feed: Job? = null

    /** The playback, volume and message commands, sent through this page's state. */
    internal val remote = SessionRemote(connection, viewModelScope, _state)

    /** Whose session this is and which device: the pair that is never controlled, and the user whose list the server filters. */
    private var ownUserId = ""
    private var ownDeviceId = ""
    private var controllable: Set<String> = emptySet()
    private var asked: Set<String> = emptySet()

    init {
        load()
    }

    fun load() {
        feed?.cancel()
        _state.value = NowPlayingUiState.Loading
        feed =
            viewModelScope.launch {
                val first =
                    connection.consoleCall(TAG) { session, client ->
                        ownUserId = session.userId
                        ownDeviceId = connection.identity().deviceId
                        val sessions = client.nowPlaying()
                        controllable = client.controllableSessionIds(session.userId)
                        asked = sessions.mapTo(mutableSetOf()) { it.sessionId }
                        sessions to client
                    }
                first
                    .onFailure { _state.value = NowPlayingUiState.Failed(it.consoleFailure()) }
                    .onSuccess { (sessions, client) ->
                        _state.value = NowPlayingUiState.Ready(sessions).withControls()
                        client.nowPlayingFeed().collect { pushed ->
                            refreshControllable(client, pushed)
                            updateReady { following(pushed).withControls() }
                        }
                    }
            }
    }

    /** Opens the page of [session], which must be one the feed lists. */
    fun select(session: NowPlaying) =
        updateReady {
            if (sessions.any { it.sessionId == session.sessionId }) {
                copy(
                    selected = session.sessionId,
                    sendFailed = null,
                )
            } else {
                this
            }
        }

    fun deselect() = updateReady { copy(selected = null, sendFailed = null) }

    /** Reads the controllable list again when the feed shows a session it has not been asked about. */
    private suspend fun refreshControllable(
        client: JellyfinClient,
        pushed: List<NowPlaying>,
    ) {
        val ids = pushed.mapTo(mutableSetOf()) { it.sessionId }
        if (asked.containsAll(ids)) return
        attempt { client.controllableSessionIds(ownUserId) }.onSuccess {
            controllable = it
            asked = asked + ids
        }
    }

    private fun NowPlayingUiState.Ready.withControls(): NowPlayingUiState.Ready =
        copy(controls = sessions.associate { it.sessionId to SessionControls.of(it, controllable, account, ownUserId, ownDeviceId) })

    fun askStop(session: NowPlaying) =
        updateReady { if (controlsFor(session).stop) copy(confirming = session, stopFailed = null) else this }

    fun cancelStop() = updateReady { copy(confirming = null) }

    fun stop() {
        val ready = _state.value as? NowPlayingUiState.Ready ?: return
        val session = ready.confirming ?: return
        if (!ready.controlsFor(session).stop) return
        updateReady { copy(confirming = null, stopping = session.sessionId, stopFailed = null) }
        viewModelScope.launch {
            connection
                .consoleCall(TAG) { _, client -> client.stopPlaying(session.sessionId) }
                // The list follows the server: the stream leaves it when the server's next push drops it.
                .onSuccess { updateReady { copy(stopping = null) } }
                .onFailure { error -> updateReady { copy(stopping = null, stopFailed = error.consoleFailure()) } }
        }
    }

    private fun updateReady(change: NowPlayingUiState.Ready.() -> NowPlayingUiState.Ready) = _state.updateReady(change)

    private companion object {
        const val TAG = "JellyfinNowPlaying"
    }
}

/** This state with the sessions the server just pushed. A confirm for a stream that has ended closes. */
private fun NowPlayingUiState.Ready.following(pushed: List<NowPlaying>): NowPlayingUiState.Ready =
    copy(
        sessions = pushed,
        confirming = confirming?.takeIf { asked -> pushed.any { it.sessionId == asked.sessionId } },
        selected = selected?.takeIf { id -> pushed.any { it.sessionId == id } },
        messaging = messaging?.takeIf { id -> pushed.any { it.sessionId == id } },
    )
