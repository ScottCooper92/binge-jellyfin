package com.cooper.binge.companion.jellyfin.ui.console

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cooper.binge.companion.jellyfin.server.ActivityEntry
import com.cooper.binge.companion.jellyfin.server.Connection
import com.cooper.binge.companion.jellyfin.server.activity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface ActivityUiState {
    data object Loading : ActivityUiState

    data class Failed(
        val failure: ConsoleFailure,
    ) : ActivityUiState

    /**
     * The entries read so far, newest first, and how many there are in all. [refreshing] while a pull
     * re-reads the newest; [loadingMore] while the next page is on its way; [moreFailed] when it didn't come.
     */
    data class Ready(
        val entries: List<ActivityEntry>,
        val total: Int,
        val refreshing: Boolean = false,
        val loadingMore: Boolean = false,
        val moreFailed: ConsoleFailure? = null,
    ) : ActivityUiState {
        val hasMore: Boolean get() = entries.size < total
    }
}

/**
 * The server's activity log, newest first, a page at a time. Admin only. Scrolling to the end reads
 * the next page; pulling down reads the newest again. The server's own activity push isn't sent
 * dependably, so the page doesn't wait on one.
 */
class ActivityViewModel(
    private val connection: Connection,
    private val pageSize: Int = PAGE_SIZE,
) : ViewModel() {
    private val _state = MutableStateFlow<ActivityUiState>(ActivityUiState.Loading)
    val state: StateFlow<ActivityUiState> = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        _state.value = ActivityUiState.Loading
        viewModelScope.launch {
            _state.value =
                connection
                    .consoleCall(TAG) { _, client -> client.activity(0, pageSize) }
                    .fold(
                        onSuccess = { ActivityUiState.Ready(it.entries, it.total) },
                        onFailure = { ActivityUiState.Failed(it.consoleFailure()) },
                    )
        }
    }

    /** Re-reads the newest page, keeping what's showing until it lands, and what it fails to replace. */
    fun refresh() {
        val ready = _state.value as? ActivityUiState.Ready ?: return
        if (ready.refreshing) return
        _state.value = ready.copy(refreshing = true)
        viewModelScope.launch {
            connection
                .consoleCall(TAG) { _, client -> client.activity(0, pageSize) }
                .onSuccess { page -> update { ActivityUiState.Ready(page.entries, page.total) } }
                .onFailure { error -> update { copy(refreshing = false, moreFailed = error.consoleFailure()) } }
        }
    }

    /** Reads the page after the last entry showing. Entries that arrived meanwhile shift the pages, so repeats are dropped. */
    fun loadMore() {
        val ready = _state.value as? ActivityUiState.Ready ?: return
        if (ready.loadingMore || ready.refreshing || !ready.hasMore) return
        _state.value = ready.copy(loadingMore = true, moreFailed = null)
        viewModelScope.launch {
            connection
                .consoleCall(TAG) { _, client -> client.activity(ready.entries.size, pageSize) }
                .onSuccess { page ->
                    update {
                        val seen = entries.map { it.id }.toSet()
                        val added = page.entries.filterNot { it.id in seen }
                        // A page that adds nothing new can't be paged past, so the list ends there.
                        copy(entries = entries + added, total = if (added.isEmpty()) entries.size else page.total, loadingMore = false)
                    }
                }.onFailure { error -> update { copy(loadingMore = false, moreFailed = error.consoleFailure()) } }
        }
    }

    private fun update(change: ActivityUiState.Ready.() -> ActivityUiState.Ready) =
        _state.update { (it as? ActivityUiState.Ready)?.change() ?: it }

    private companion object {
        const val TAG = "JellyfinActivity"
        const val PAGE_SIZE = 50
    }
}
