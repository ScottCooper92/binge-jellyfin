package com.cooper.binge.companion.jellyfin.ui.console

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cooper.binge.companion.jellyfin.server.Connection
import com.cooper.binge.companion.jellyfin.server.ServerTask
import com.cooper.binge.companion.jellyfin.server.runTask
import com.cooper.binge.companion.jellyfin.server.stopTask
import com.cooper.binge.companion.jellyfin.server.taskPushes
import com.cooper.binge.companion.jellyfin.server.tasks
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface TasksUiState {
    data object Loading : TasksUiState

    data class Failed(
        val failure: ConsoleFailure,
    ) : TasksUiState

    /** The tasks and what this page has asked of them, and the last run or stop that couldn't be sent. */
    data class Ready(
        val board: TaskBoard,
        val actionFailed: ConsoleFailure? = null,
    ) : TasksUiState
}

/**
 * The server's scheduled tasks, each run or stopped in place, as binge-seerr's jobs are. Admin only.
 * They follow the page's own event socket, which closes with the page, and a run's outcome shows on its
 * row for a moment.
 */
class TasksViewModel(
    private val connection: Connection,
) : ViewModel() {
    private val _state = MutableStateFlow<TasksUiState>(TasksUiState.Loading)
    val state: StateFlow<TasksUiState> = _state.asStateFlow()

    internal var outcomeMillis = OUTCOME_MILLIS
    private var pushes: Job? = null

    init {
        load()
    }

    fun load() {
        pushes?.cancel()
        _state.value = TasksUiState.Loading
        pushes =
            viewModelScope.launch {
                connection
                    .consoleCall(TAG) { _, client -> client.tasks() to client }
                    .onFailure { _state.value = TasksUiState.Failed(it.consoleFailure()) }
                    .onSuccess { (tasks, client) ->
                        _state.value = TasksUiState.Ready(TaskBoard(tasks))
                        client.taskPushes().collect { pushed -> following(pushed) }
                    }
            }
    }

    fun run(task: ServerTask) = call(task, run = true)

    fun stop(task: ServerTask) = call(task, run = false)

    private fun call(
        task: ServerTask,
        run: Boolean,
    ) {
        update { copy(board = board.copy(busy = board.busy + task.id, outcomes = board.outcomes - task.id), actionFailed = null) }
        viewModelScope.launch {
            connection
                .consoleCall(TAG) { _, client -> if (run) client.runTask(task.id) else client.stopTask(task.id) }
                .onSuccess { update { copy(board = if (run) board.started(task.id) else board.copy(busy = board.busy - task.id)) } }
                .onFailure { error ->
                    update { copy(board = board.copy(busy = board.busy - task.id), actionFailed = error.consoleFailure()) }
                }
        }
    }

    private fun following(pushed: List<ServerTask>) {
        val before =
            (_state.value as? TasksUiState.Ready)
                ?.board
                ?.outcomes
                ?.keys
                .orEmpty()
        update { copy(board = board.following(pushed)) }
        val after =
            (_state.value as? TasksUiState.Ready)
                ?.board
                ?.outcomes
                ?.keys
                .orEmpty()
        (after - before).forEach { id ->
            viewModelScope.launch {
                delay(outcomeMillis)
                update { copy(board = board.copy(outcomes = board.outcomes - id)) }
            }
        }
    }

    private fun update(change: TasksUiState.Ready.() -> TasksUiState.Ready) = _state.update { (it as? TasksUiState.Ready)?.change() ?: it }

    private companion object {
        const val TAG = "JellyfinTasks"
        const val OUTCOME_MILLIS = 4_000L
    }
}
