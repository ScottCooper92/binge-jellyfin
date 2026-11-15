package com.cooper.binge.companion.jellyfin.ui.console

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.binge.companion.sdk.attempt
import com.cooper.binge.companion.jellyfin.server.Connection
import com.cooper.binge.companion.jellyfin.server.JellyfinClient
import com.cooper.binge.companion.jellyfin.server.ServerInfo
import com.cooper.binge.companion.jellyfin.server.ServerStorage
import com.cooper.binge.companion.jellyfin.server.restartServer
import com.cooper.binge.companion.jellyfin.server.serverInfo
import com.cooper.binge.companion.jellyfin.server.serverStorage
import com.cooper.binge.companion.jellyfin.server.shutdownServer
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
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
        /** The disk space of the server's folders; null where the server has none to give (an older release, or a read that failed). */
        val storage: ServerStorage? = null,
        val confirming: Boolean = false,
        val restart: Restart? = null,
        val confirmingShutdown: Boolean = false,
        val shutdown: Shutdown? = null,
        /** True while a pull reads again; the page stays as it is. [refreshFailed] says why a pull didn't. */
        val refreshing: Boolean = false,
        val refreshFailed: ConsoleFailure? = null,
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
    /** Whether the server has the storage report (`hasStorageInfo`); a release without it is not asked. */
    private val storageAvailable: Boolean = false,
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
                    .consoleCall(TAG) { _, client -> client.infoAndStorage() }
                    .fold(
                        onSuccess = { (info, storage) -> ServerUiState.Ready(info, storage) },
                        onFailure = { ServerUiState.Failed(it.consoleFailure()) },
                    )
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

    /**
     * A pull: reads the server's info again in place, keeping the page, and says in a message when it couldn't. Not while a
     * restart or shutdown is going on: it reads the info itself, and the server is about to be gone.
     */
    fun refresh() {
        val ready = _state.value as? ServerUiState.Ready ?: return
        if (ready.refreshing || ready.restart == Restart.Restarting || ready.down) return
        update { copy(refreshing = true, refreshFailed = null) }
        viewModelScope.launch {
            connection
                .consoleCall(TAG) { _, client -> client.infoAndStorage() }
                .onSuccess { (fresh, storage) -> update { copy(info = fresh, storage = storage, refreshing = false) } }
                .onFailure { error -> update { copy(refreshing = false, refreshFailed = error.consoleFailure()) } }
        }
    }

    private suspend fun reloaded() {
        val read = connection.consoleCall(TAG) { _, client -> client.infoAndStorage() }.getOrNull()
        update { copy(info = read?.first ?: info, storage = read?.second ?: storage, restart = Restart.Restarted) }
    }

    /** The server's info, with the disk space beside it where the server has it. The space is a nicety: a read that fails leaves it out. */
    private suspend fun JellyfinClient.infoAndStorage(): Pair<ServerInfo, ServerStorage?> =
        coroutineScope {
            val storage = async { if (storageAvailable) attempt { serverStorage() }.getOrNull()?.takeUnless { it.isEmpty } else null }
            serverInfo() to storage.await()
        }

    private fun update(change: ServerUiState.Ready.() -> ServerUiState.Ready) =
        _state.update { (it as? ServerUiState.Ready)?.change() ?: it }

    private companion object {
        const val TAG = "JellyfinServer"
    }
}
