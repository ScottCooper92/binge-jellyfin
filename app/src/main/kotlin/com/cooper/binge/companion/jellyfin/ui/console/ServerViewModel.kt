package com.cooper.binge.companion.jellyfin.ui.console

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cooper.binge.companion.jellyfin.server.Connection
import com.cooper.binge.companion.jellyfin.server.ServerInfo
import com.cooper.binge.companion.jellyfin.server.restartServer
import com.cooper.binge.companion.jellyfin.server.serverInfo
import com.cooper.binge.companion.jellyfin.server.shutdownServer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Where a restart has got to. */
enum class Restart { Restarting, Restarted, NotBack, Failed }

/** Where a shutdown has got to. A server that's shut down stays down, so there's no coming back to wait for. */
enum class Shutdown { ShuttingDown, ShutDown, Failed }

sealed interface ServerUiState {
    data object Loading : ServerUiState

    data class Failed(
        val failure: ConsoleFailure,
    ) : ServerUiState

    /**
     * The server's info; [confirming] while the restart confirm is open, [confirmingShutdown] while the
     * shutdown's is; [restart] and [shutdown], where each has got to.
     */
    data class Ready(
        val info: ServerInfo,
        val confirming: Boolean = false,
        val restart: Restart? = null,
        val confirmingShutdown: Boolean = false,
        val shutdown: Shutdown? = null,
    ) : ServerUiState {
        /** Whether the server is going or gone, so nothing more can be asked of it. */
        val down: Boolean get() = shutdown == Shutdown.ShuttingDown || shutdown == Shutdown.ShutDown
    }
}

/**
 * The server itself: its version and address, whether an update or a restart is waiting, and Restart,
 * and Shut down, each behind a confirm. Admin only. A restart is reported as it goes: restarting while the server goes down
 * and comes back, then restarted, with its info read again.
 */
class ServerViewModel(
    private val connection: Connection,
    pollMillis: Long = RestartTiming.POLL_MILLIS,
    downWaitMillis: Long = RestartTiming.DOWN_WAIT_MILLIS,
    backWaitMillis: Long = RestartTiming.BACK_WAIT_MILLIS,
) : ViewModel() {
    private val timing = RestartTiming(pollMillis, downWaitMillis, backWaitMillis)

    private val _state = MutableStateFlow<ServerUiState>(ServerUiState.Loading)
    val state: StateFlow<ServerUiState> = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        _state.value = ServerUiState.Loading
        viewModelScope.launch {
            _state.value =
                connection
                    .consoleCall(TAG) { _, client -> client.serverInfo() }
                    .fold(onSuccess = { ServerUiState.Ready(it) }, onFailure = { ServerUiState.Failed(it.consoleFailure()) })
        }
    }

    fun confirm(showing: Boolean) = update { copy(confirming = showing && info.canRestart && !down) }

    /** Clears the notice that the server never came back. A restart that is going, or done, is not cleared. */
    fun dismissRestart() = update { if (restart == Restart.NotBack) copy(restart = null) else this }

    fun confirmShutdown(showing: Boolean) = update { copy(confirmingShutdown = showing && !down) }

    fun restart() {
        val ready = _state.value as? ServerUiState.Ready ?: return
        if (!ready.info.canRestart || ready.restart == Restart.Restarting || ready.down) return
        update { copy(confirming = false, restart = Restart.Restarting, shutdown = null) }
        viewModelScope.launch {
            connection
                .consoleCall(TAG) { _, client ->
                    client.restartServer()
                    client.awaitBack(timing)
                }.fold(
                    onSuccess = { back -> if (back) reloaded() else update { copy(restart = Restart.NotBack) } },
                    onFailure = { update { copy(restart = Restart.Failed) } },
                )
        }
    }

    /** Shuts the server down. It's reported shut down once it has taken the request: it won't answer again. */
    fun shutdown() {
        val ready = _state.value as? ServerUiState.Ready ?: return
        if (ready.down || ready.restart == Restart.Restarting) return
        update { copy(confirmingShutdown = false, shutdown = Shutdown.ShuttingDown, restart = null) }
        viewModelScope.launch {
            connection
                .consoleCall(TAG) { _, client -> client.shutdownServer() }
                .fold(
                    onSuccess = { update { copy(shutdown = Shutdown.ShutDown) } },
                    onFailure = { update { copy(shutdown = Shutdown.Failed) } },
                )
        }
    }

    private suspend fun reloaded() {
        val info = connection.consoleCall(TAG) { _, client -> client.serverInfo() }.getOrNull()
        update { copy(info = info ?: this.info, restart = Restart.Restarted) }
    }

    private fun update(change: ServerUiState.Ready.() -> ServerUiState.Ready) =
        _state.update { (it as? ServerUiState.Ready)?.change() ?: it }

    private companion object {
        const val TAG = "JellyfinServer"
    }
}
