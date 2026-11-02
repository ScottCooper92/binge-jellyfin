package com.cooper.binge.companion.jellyfin.ui.console

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cooper.binge.companion.jellyfin.server.Connection
import com.cooper.binge.companion.jellyfin.server.LogEntry
import com.cooper.binge.companion.jellyfin.server.LogFile
import com.cooper.binge.companion.jellyfin.server.LogLevel
import com.cooper.binge.companion.jellyfin.server.logFiles
import com.cooper.binge.companion.jellyfin.server.logTail
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface LogsUiState {
    data object Loading : LogsUiState

    data class Failed(
        val failure: ConsoleFailure,
    ) : LogsUiState

    /** The server's log files, newest first, and the one open, if any. */
    data class Ready(
        val files: List<LogFile>,
        val open: OpenLog? = null,
    ) : LogsUiState
}

/** The log being read: what [content] has, narrowed to [level] and above and by [query]. */
data class OpenLog(
    val name: String,
    val content: LogContent = LogContent.Loading,
    val query: String = "",
    val level: LogLevel = LogLevel.Debug,
) {
    /** The entries to show: those at [level] or worse, and those with [query] in them. An entry with no level counts as Info. */
    val visible: List<LogEntry>
        get() {
            val entries = (content as? LogContent.Ready)?.entries ?: return emptyList()
            return entries.filter {
                (it.level ?: LogLevel.Info) >= level &&
                    (query.isBlank() || it.text.contains(query.trim(), ignoreCase = true))
            }
        }
}

sealed interface LogContent {
    data object Loading : LogContent

    data class Failed(
        val failure: ConsoleFailure,
    ) : LogContent

    /** The end of the file as entries, oldest first, and whether the file was longer than what was kept. */
    data class Ready(
        val entries: List<LogEntry>,
        val truncated: Boolean,
    ) : LogContent
}

/**
 * The server's log files, and one read at a time: its end, with credentials blanked, searchable and
 * narrowed by level. Admin only. Nothing is saved; reading is all this page does.
 */
class LogsViewModel(
    private val connection: Connection,
) : ViewModel() {
    private val _state = MutableStateFlow<LogsUiState>(LogsUiState.Loading)
    val state: StateFlow<LogsUiState> = _state.asStateFlow()
    private var reading: Job? = null

    init {
        load()
    }

    fun load() {
        _state.value = LogsUiState.Loading
        viewModelScope.launch {
            _state.value =
                connection
                    .consoleCall(TAG) { _, client -> client.logFiles() }
                    .fold(
                        onSuccess = { LogsUiState.Ready(it) },
                        onFailure = { LogsUiState.Failed(it.consoleFailure()) },
                    )
        }
    }

    fun open(name: String) {
        if (_state.value !is LogsUiState.Ready) return
        update { copy(open = OpenLog(name)) }
        read(name)
    }

    /** Reads the open log again, after it failed. */
    fun retryOpen() {
        val name = (_state.value as? LogsUiState.Ready)?.open?.name ?: return
        updateOpen { copy(content = LogContent.Loading) }
        read(name)
    }

    fun closeLog() {
        reading?.cancel()
        update { copy(open = null) }
    }

    fun setQuery(query: String) = updateOpen { copy(query = query) }

    fun setLevel(level: LogLevel) = updateOpen { copy(level = level) }

    private fun read(name: String) {
        reading?.cancel()
        reading =
            viewModelScope.launch {
                val content =
                    connection
                        .consoleCall(TAG) { session, client -> client.logTail(name, session.token) }
                        .fold(
                            onSuccess = { LogContent.Ready(it.entries, it.truncated) },
                            onFailure = { LogContent.Failed(it.consoleFailure()) },
                        )
                // Only the log still open takes the answer: a slow read that was closed or replaced is dropped.
                updateOpen(only = name) { copy(content = content) }
            }
    }

    private fun update(change: LogsUiState.Ready.() -> LogsUiState.Ready) = _state.update { (it as? LogsUiState.Ready)?.change() ?: it }

    private fun updateOpen(
        only: String? = null,
        change: OpenLog.() -> OpenLog,
    ) = update {
        val open = open
        if (open == null || (only != null && open.name != only)) this else copy(open = open.change())
    }

    private companion object {
        const val TAG = "JellyfinLogs"
    }
}
