package com.cooper.binge.companion.jellyfin.ui.console

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cooper.binge.companion.jellyfin.server.Connection
import com.cooper.binge.companion.jellyfin.server.LOG_FILE_RETENTION_DAYS
import com.cooper.binge.companion.jellyfin.server.LOG_RETENTION_MAX_DAYS
import com.cooper.binge.companion.jellyfin.server.LOG_RETENTION_MIN_DAYS
import com.cooper.binge.companion.jellyfin.server.LogEntry
import com.cooper.binge.companion.jellyfin.server.LogFile
import com.cooper.binge.companion.jellyfin.server.LogLevel
import com.cooper.binge.companion.jellyfin.server.logFiles
import com.cooper.binge.companion.jellyfin.server.logRetentionDays
import com.cooper.binge.companion.jellyfin.server.logTail
import com.cooper.binge.companion.jellyfin.server.saveServerConfiguration
import com.cooper.binge.companion.jellyfin.server.serverConfiguration
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonPrimitive

sealed interface LogsUiState {
    data object Loading : LogsUiState

    data class Failed(
        val failure: ConsoleFailure,
    ) : LogsUiState

    /**
     * The server's log files, newest first, and the one open, if any. [retention] is null when the server sent no
     * retention setting; [save] says where its saves stand, and [refused] the last one the server refused.
     */
    data class Ready(
        val files: List<LogFile>,
        val open: OpenLog? = null,
        val retention: Int? = null,
        val editingRetention: Boolean = false,
        val save: SaveProgress = SaveProgress(),
        val refused: ConsoleFailure? = null,
        /** True while a pull reads again; the page stays as it is. [refreshFailed] says why a pull didn't. */
        val refreshing: Boolean = false,
        val refreshFailed: ConsoleFailure? = null,
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

    private val saves =
        ConfigurationSaves(
            connection,
            viewModelScope,
            TAG,
            { _, client, json -> client.saveServerConfiguration(json) },
            onProgress = { progress -> update { copy(save = progress) } },
        ) { shown, failure ->
            update { copy(retention = shown.logRetentionDays(), refused = refusedAfter(refused, failure)) }
        }

    init {
        load()
    }

    fun load() {
        _state.value = LogsUiState.Loading
        viewModelScope.launch {
            connection.awaitSaves()
            val configuration = connection.consoleCall(TAG) { _, client -> client.serverConfiguration() }.getOrNull()
            configuration?.let(saves::loaded)
            _state.value =
                connection
                    .consoleCall(TAG) { _, client -> client.logFiles() }
                    .fold(
                        onSuccess = { LogsUiState.Ready(it, retention = configuration?.logRetentionDays()) },
                        onFailure = { LogsUiState.Failed(it.consoleFailure()) },
                    )
        }
    }

    /**
     * A pull on the list of files: reads the files again in place, keeping the page, and says in a message when it couldn't.
     * The retention setting is not read again: it saves as it is changed, and a read would put back what is still on its way.
     */
    fun refresh() {
        val ready = _state.value as? LogsUiState.Ready ?: return
        if (ready.refreshing) return
        update { copy(refreshing = true, refreshFailed = null) }
        viewModelScope.launch {
            connection
                .consoleCall(TAG) { _, client -> client.logFiles() }
                .onSuccess { fresh -> update { copy(files = fresh, refreshing = false) } }
                .onFailure { error -> update { copy(refreshing = false, refreshFailed = error.consoleFailure()) } }
        }
    }

    fun open(name: String) {
        if (_state.value !is LogsUiState.Ready) return
        update { copy(open = OpenLog(name)) }
        read(name)
    }

    /**
     * A log is being opened: what the list last reported (a refused change, a pull that failed) is not this screen's, and
     * would be said again by any screen composed afresh over this shared view model.
     */
    fun opened() = update { copy(refused = null, refreshFailed = null) }

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

    /** Opens the retention sheet, or closes it. */
    fun editRetention(editing: Boolean) = update { copy(editingRetention = editing) }

    /** Saves [text] as the retention in days and closes the sheet; text that isn't a whole number in range keeps it open and saves nothing. */
    fun saveRetention(text: String) {
        val days = text.trim().toIntOrNull()?.takeIf { it in LOG_RETENTION_MIN_DAYS..LOG_RETENTION_MAX_DAYS } ?: return
        update { copy(editingRetention = false) }
        saves.set(LOG_FILE_RETENTION_DAYS, JsonPrimitive(days))
    }

    fun retrySave() = saves.retry()

    /** A change still waiting out its delay is sent now, where this page's scope can no longer cancel it. */
    override fun onCleared() {
        saves.cleared()
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
