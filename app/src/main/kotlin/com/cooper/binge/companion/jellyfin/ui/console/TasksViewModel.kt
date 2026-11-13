package com.cooper.binge.companion.jellyfin.ui.console

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cooper.binge.companion.jellyfin.review.NoReviewSignals
import com.cooper.binge.companion.jellyfin.review.ReviewSignalSink
import com.cooper.binge.companion.jellyfin.server.Connection
import com.cooper.binge.companion.jellyfin.server.ServerTask
import com.cooper.binge.companion.jellyfin.server.TaskTrigger
import com.cooper.binge.companion.jellyfin.server.runTask
import com.cooper.binge.companion.jellyfin.server.saveTaskTriggers
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

    /**
     * The tasks and what this page has asked of them. [actionFailed] is the last run or stop that couldn't be sent, and
     * [triggerFailed] the last trigger change that couldn't, each said by the screen that asked. [adding] is the new
     * trigger sheet; [saving] a trigger change in flight. Which task is open is not here: it is an entry on the stack.
     */
    data class Ready(
        val board: TaskBoard,
        val actionFailed: ConsoleFailure? = null,
        val triggerFailed: ConsoleFailure? = null,
        val adding: Boolean = false,
        val saving: Boolean = false,
        /** True while a pull reads the tasks again; the list stays as it is. [refreshFailed] says why a pull didn't. */
        val refreshing: Boolean = false,
        val refreshFailed: ConsoleFailure? = null,
    ) : TasksUiState
}

/**
 * The server's scheduled tasks, each run or stopped in place, as binge-seerr's jobs are. Admin only.
 * They follow the page's own event socket, which closes with the page, and a run's outcome shows on its
 * row for a moment.
 */
class TasksViewModel(
    private val connection: Connection,
    private val reviewSignals: ReviewSignalSink = NoReviewSignals,
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

    /** A pull: reads the tasks again in place, keeping the page, and says in a message when it couldn't. */
    fun refresh() {
        val ready = _state.value as? TasksUiState.Ready ?: return
        if (ready.refreshing) return
        update { copy(refreshing = true, refreshFailed = null) }
        viewModelScope.launch {
            connection
                .consoleCall(TAG) { _, client -> client.tasks() }
                .onSuccess { fresh ->
                    following(fresh)
                    update { copy(refreshing = false) }
                }.onFailure { error -> update { copy(refreshing = false, refreshFailed = error.consoleFailure()) } }
        }
    }

    fun run(task: ServerTask) = call(task, run = true)

    fun stop(task: ServerTask) = call(task, run = false)

    /** A task is opening: what the list or another task reported is not this one's to show. */
    fun opened() = update { copy(actionFailed = null, triggerFailed = null, refreshFailed = null, adding = false) }

    fun askAdd(on: Boolean) = update { copy(adding = on, triggerFailed = null) }

    /** Adds [trigger] to task [taskId]'s triggers, after the ones it has. */
    fun addTrigger(
        taskId: String,
        trigger: TaskTrigger,
    ) = changeTriggers(taskId) { it + trigger }

    /** Removes task [taskId]'s trigger at [index]. No confirm: adding it back undoes it. */
    fun removeTrigger(
        taskId: String,
        index: Int,
    ) = changeTriggers(taskId) { triggers -> triggers.filterIndexed { at, _ -> at != index } }

    /**
     * Sends task [taskId]'s triggers as [change] makes them, the whole list as the server wants it, then
     * reads the tasks again so the page shows what the server now holds. One change at a time: one in
     * flight turns the next away, since its list would be built on triggers about to change.
     */
    private fun changeTriggers(
        taskId: String,
        change: (List<TaskTrigger>) -> List<TaskTrigger>,
    ) {
        val ready = _state.value as? TasksUiState.Ready ?: return
        val task = ready.board.tasks.firstOrNull { it.id == taskId } ?: return
        if (ready.saving) return
        update { copy(adding = false, saving = true, triggerFailed = null) }
        viewModelScope.launch {
            connection
                .consoleCall(TAG) { _, client ->
                    client.saveTaskTriggers(task.id, change(task.triggers))
                    client.tasks()
                }.onSuccess { tasks ->
                    // The read-back can carry a run that ended meanwhile: it goes through the same path as a push, which
                    // is what clears that run's outcome from its row after a moment.
                    following(tasks)
                    update { copy(saving = false) }
                }.onFailure { error -> update { copy(saving = false, triggerFailed = error.consoleFailure()) } }
        }
    }

    private fun call(
        task: ServerTask,
        run: Boolean,
    ) {
        update { copy(board = board.copy(busy = board.busy + task.id, outcomes = board.outcomes - task.id), actionFailed = null) }
        viewModelScope.launch {
            connection
                .consoleCall(TAG) { _, client -> if (run) client.runTask(task.id) else client.stopTask(task.id) }
                .onSuccess {
                    if (run) reviewSignals.recordPositiveAction()
                    update { copy(board = if (run) board.started(task.id) else board.copy(busy = board.busy - task.id)) }
                }.onFailure { error ->
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
