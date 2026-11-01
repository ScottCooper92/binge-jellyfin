package com.cooper.binge.companion.jellyfin.ui.console

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cooper.binge.companion.jellyfin.server.Connection
import com.cooper.binge.companion.jellyfin.server.LIBRARY_SCAN_TASK
import com.cooper.binge.companion.jellyfin.server.LibraryPush
import com.cooper.binge.companion.jellyfin.server.ServerLibrary
import com.cooper.binge.companion.jellyfin.server.addLibrary
import com.cooper.binge.companion.jellyfin.server.libraries
import com.cooper.binge.companion.jellyfin.server.libraryPushes
import com.cooper.binge.companion.jellyfin.server.runTask
import com.cooper.binge.companion.jellyfin.server.scanLibrary
import com.cooper.binge.companion.jellyfin.server.stopTask
import com.cooper.binge.companion.jellyfin.server.tasks
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** One library's scan as this page follows it: its progress, and whether its start has been reported. */
data class LibraryScan(
    val percent: Int = 0,
    val started: Boolean = false,
)

sealed interface LibrariesUiState {
    data object Loading : LibrariesUiState

    data class Failed(
        val failure: ConsoleFailure,
    ) : LibrariesUiState

    /**
     * The libraries and their scans; the scheduled tasks, for the scan of everything; how each
     * library's last scan just ended; the last scan or stop that couldn't be sent, with `stopFailed` set when it was a stop;
     * the library whose page is open ([opened]); and the new library sheet, when [adding], with the add in flight
     * ([saving]) or refused ([addFailed]).
     */
    data class Ready(
        val libraries: List<ServerLibrary>,
        val board: TaskBoard,
        val scanning: Map<String, LibraryScan> = emptyMap(),
        val outcomes: Map<String, JobOutcome> = emptyMap(),
        val actionFailed: ConsoleFailure? = null,
        val stopFailed: Boolean = false,
        val opened: String? = null,
        val adding: Boolean = false,
        val saving: Boolean = false,
        val addFailed: ConsoleFailure? = null,
    ) : LibrariesUiState
}

/**
 * The server's libraries, a scan of each, and the scan of everything, which is the "Scan Media
 * Library" task. Admin only. Progress comes from the page's own event socket, which closes with the
 * page. A run's outcome shows on its row for a moment, as binge-seerr's jobs do.
 */
class LibrariesViewModel(
    private val connection: Connection,
) : ViewModel() {
    private val _state = MutableStateFlow<LibrariesUiState>(LibrariesUiState.Loading)
    val state: StateFlow<LibrariesUiState> = _state.asStateFlow()

    internal var outcomeMillis = OUTCOME_MILLIS
    private var pushes: Job? = null

    init {
        load()
    }

    fun load() {
        pushes?.cancel()
        _state.value = LibrariesUiState.Loading
        pushes =
            viewModelScope.launch {
                connection
                    .consoleCall(TAG) { _, client ->
                        coroutineScope {
                            val tasks = async { client.tasks() }
                            Triple(client.libraries(), tasks.await(), client)
                        }
                    }.onFailure { _state.value = LibrariesUiState.Failed(it.consoleFailure()) }
                    .onSuccess { (libraries, tasks, client) ->
                        _state.value = LibrariesUiState.Ready(libraries, TaskBoard(tasks))
                        client.libraryPushes().collect { push -> following(push) }
                    }
            }
    }

    fun scan(library: ServerLibrary) {
        update {
            copy(
                scanning = scanning + (library.id to LibraryScan()),
                outcomes = outcomes - library.id,
                actionFailed = null,
                stopFailed = false,
            )
        }
        viewModelScope.launch {
            connection
                .consoleCall(TAG) { _, client -> client.scanLibrary(library.id) }
                .onFailure { error ->
                    update {
                        copy(
                            scanning = scanning - library.id,
                            outcomes = outcomes + (library.id to JobOutcome.Failed),
                            actionFailed = error.consoleFailure(),
                            stopFailed = false,
                        )
                    }
                    clearLater(library.id)
                }
        }
    }

    /** Opens the page of the library with [id], or the list for null. */
    fun open(id: String?) = update { copy(opened = id) }

    /** Opens the new library sheet, or closes it. */
    fun ask(adding: Boolean) = update { copy(adding = adding, addFailed = null) }

    /** Reads the libraries again, quietly: the list stays as it is until the answer comes. */
    fun refresh() {
        viewModelScope.launch {
            connection.awaitSaves()
            connection
                .consoleCall(TAG) { _, client -> client.libraries() }
                .onSuccess { fresh -> update { copy(libraries = fresh, opened = opened?.takeIf { id -> fresh.any { it.id == id } }) } }
        }
    }

    /** Makes a library named [name] of [kind] over the folder [path]. The sheet doesn't offer a name another library has. */
    fun add(
        name: String,
        kind: String,
        path: String,
    ) {
        val ready = _state.value as? LibrariesUiState.Ready ?: return
        val trimmed = name.trim()
        if (trimmed.isEmpty() || path.isBlank()) return
        if (ready.libraries.any { it.name.equals(trimmed, ignoreCase = true) }) return
        update { copy(saving = true, addFailed = null) }
        viewModelScope.launch {
            connection
                .consoleCall(TAG) { _, client ->
                    client.addLibrary(trimmed, kind, path)
                    client.libraries()
                }.onSuccess { fresh -> update { copy(libraries = fresh, saving = false, adding = false) } }
                .onFailure { error -> update { copy(saving = false, addFailed = error.consoleFailure()) } }
        }
    }

    fun scanEverything() = taskCall(run = true)

    fun stopEverything() = taskCall(run = false)

    private fun taskCall(run: Boolean) {
        val task = (_state.value as? LibrariesUiState.Ready)?.board?.task(LIBRARY_SCAN_TASK) ?: return
        update { copy(board = board.copy(busy = board.busy + task.id), actionFailed = null, stopFailed = false) }
        viewModelScope.launch {
            connection
                .consoleCall(TAG) { _, client -> if (run) client.runTask(task.id) else client.stopTask(task.id) }
                .onSuccess { update { copy(board = if (run) board.started(task.id) else board.copy(busy = board.busy - task.id)) } }
                .onFailure { error ->
                    update {
                        copy(
                            board = board.copy(busy = board.busy - task.id),
                            actionFailed = error.consoleFailure(),
                            stopFailed = !run,
                        )
                    }
                }
        }
    }

    private fun following(push: LibraryPush) {
        val before = _state.value as? LibrariesUiState.Ready ?: return
        update { followingPush(push) }
        val after = _state.value as? LibrariesUiState.Ready ?: return
        (after.outcomes.keys - before.outcomes.keys).forEach(::clearLater)
        (after.board.outcomes.keys - before.board.outcomes.keys).forEach(::clearLater)
    }

    /** An outcome shows for a moment, then the row goes back to Run. */
    private fun clearLater(id: String) {
        viewModelScope.launch {
            delay(outcomeMillis)
            update { copy(outcomes = outcomes - id, board = board.copy(outcomes = board.outcomes - id)) }
        }
    }

    private fun update(change: LibrariesUiState.Ready.() -> LibrariesUiState.Ready) =
        _state.update { (it as? LibrariesUiState.Ready)?.change() ?: it }

    private companion object {
        const val TAG = "JellyfinLibraries"
        const val OUTCOME_MILLIS = 4_000L
    }
}

private const val DONE = 100

/**
 * This state with a [push]. The server reports 0 when a library's scan starts and again when it ends,
 * and 100 only for the root of everything, so a library is scanning while its last push is between
 * the two. A scan tapped here shows from the tap: its first 0 is its start, and the next 0, or 100,
 * its end, which shows as done for a moment. A scan the server started shows from its first step.
 */
internal fun LibrariesUiState.Ready.followingPush(push: LibraryPush): LibrariesUiState.Ready =
    when (push) {
        is LibraryPush.Tasks -> copy(board = board.following(push.tasks))
        is LibraryPush.Progress -> {
            val scan = scanning[push.id]
            when {
                libraries.none { it.id == push.id } -> this
                push.percent in 1 until DONE -> copy(scanning = scanning + (push.id to LibraryScan(push.percent, started = true)))
                scan == null -> this
                !scan.started -> copy(scanning = scanning + (push.id to scan.copy(started = true)))
                else -> copy(scanning = scanning - push.id, outcomes = outcomes + (push.id to JobOutcome.Succeeded))
            }
        }
    }
