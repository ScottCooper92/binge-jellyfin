package com.cooper.binge.companion.jellyfin.ui.console

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cooper.binge.companion.jellyfin.server.ActivityEntry
import com.cooper.binge.companion.jellyfin.server.ActivityFilter
import com.cooper.binge.companion.jellyfin.server.ActivityTimePreset
import com.cooper.binge.companion.jellyfin.server.ActivityWho
import com.cooper.binge.companion.jellyfin.server.Connection
import com.cooper.binge.companion.jellyfin.server.Severity
import com.cooper.binge.companion.jellyfin.server.activity
import com.cooper.binge.companion.jellyfin.server.users
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId

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
        /** The id of the entry whose detail sheet is open. */
        val open: Long? = null,
    ) : ActivityUiState {
        val hasMore: Boolean get() = entries.size < total

        /** The open entry, or null when none is open or a read has since replaced it. */
        val openEntry: ActivityEntry? get() = entries.firstOrNull { it.id == open }
    }
}

/** One filter a chip can clear. */
enum class ActivityFilterKind { Time, Who, Severity, Type }

/** The users the "who" filter offers, read when its sheet opens. */
sealed interface ActivityUsers {
    data object Loading : ActivityUsers

    data object Failed : ActivityUsers

    data class Ready(
        val names: List<String>,
    ) : ActivityUsers
}

/**
 * The activity filters, beside the list's own state so a loading or failed list still has them. [applied] is what the list shows; [draft] is
 * what the filter sheet is editing, and only **Show results** makes it the [applied] one. [open] is the filter sheet; the sheet keeps its own pages.
 */
data class ActivityFilters(
    val applied: ActivityFilter = ActivityFilter(),
    val draft: ActivityFilter = ActivityFilter(),
    val open: Boolean = false,
    val users: ActivityUsers = ActivityUsers.Loading,
)

/**
 * The server's activity log, newest first, a page at a time. Admin only. Scrolling to the end reads
 * the next page; pulling down reads the newest again. The server's own activity push isn't sent
 * dependably, so the page doesn't wait on one.
 */
class ActivityViewModel(
    private val connection: Connection,
    private val pageSize: Int = PAGE_SIZE,
    private val now: () -> Instant = Instant::now,
    private val zone: ZoneId = ZoneId.systemDefault(),
) : ViewModel() {
    private val _state = MutableStateFlow<ActivityUiState>(ActivityUiState.Loading)
    val state: StateFlow<ActivityUiState> = _state.asStateFlow()

    private val _filters = MutableStateFlow(ActivityFilters())
    val filters: StateFlow<ActivityFilters> = _filters.asStateFlow()

    init {
        load()
    }

    fun load() {
        _state.value = ActivityUiState.Loading
        val applied = filters.value.applied
        viewModelScope.launch {
            val read =
                connection
                    .consoleCall(TAG) { _, client -> client.activity(0, pageSize, applied) }
                    .fold(
                        onSuccess = { ActivityUiState.Ready(it.entries, it.total) },
                        onFailure = { ActivityUiState.Failed(it.consoleFailure()) },
                    )
            // A read for filters the user has since changed is dropped: its answer is for another list.
            if (applied == filters.value.applied) _state.value = read
        }
    }

    /** Opens [id]'s detail sheet, or closes it for null. */
    fun open(id: Long?) = update { copy(open = id) }

    /** Re-reads the newest page, unless a page is on its way, keeping what's showing until it lands, and what it fails to replace. */
    fun refresh() {
        val ready = _state.value as? ActivityUiState.Ready ?: return
        if (ready.refreshing || ready.loadingMore) return
        _state.value = ready.copy(refreshing = true, moreFailed = null)
        val applied = filters.value.applied
        viewModelScope.launch {
            connection
                .consoleCall(TAG) { _, client -> client.activity(0, pageSize, applied) }
                .onSuccess { page ->
                    if (applied ==
                        filters.value.applied
                    ) {
                        update { ActivityUiState.Ready(page.entries, page.total, open = open) }
                    }
                }.onFailure { error ->
                    if (applied == filters.value.applied) update { copy(refreshing = false, moreFailed = error.consoleFailure()) }
                }
        }
    }

    /** Reads the page after the last entry showing. Entries that arrived meanwhile shift the pages, so repeats are dropped. */
    fun loadMore() {
        val ready = _state.value as? ActivityUiState.Ready ?: return
        if (ready.loadingMore || ready.refreshing) return
        // A failed refresh shows the same row, and with nothing more to page, Try again re-reads the newest.
        if (!ready.hasMore) {
            if (ready.moreFailed != null) refresh()
            return
        }
        _state.value = ready.copy(loadingMore = true, moreFailed = null)
        val applied = filters.value.applied
        viewModelScope.launch {
            connection
                .consoleCall(TAG) { _, client -> client.activity(ready.entries.size, pageSize, applied) }
                .onSuccess { page ->
                    if (applied != filters.value.applied) return@onSuccess
                    update {
                        val seen = entries.map { it.id }.toSet()
                        val added = page.entries.filterNot { it.id in seen }
                        // A page that adds nothing new can't be paged past, so the list ends there.
                        copy(entries = entries + added, total = if (added.isEmpty()) entries.size else page.total, loadingMore = false)
                    }
                }.onFailure { error ->
                    if (applied == filters.value.applied) update { copy(loadingMore = false, moreFailed = error.consoleFailure()) }
                }
        }
    }

    /** Opens the filter sheet on what the list shows now. */
    fun openFilters() {
        _filters.update { it.copy(open = true, draft = it.applied) }
    }

    /** Closes the sheet without applying: the draft is dropped. */
    fun closeFilters() {
        _filters.update { it.copy(open = false, draft = it.applied) }
    }

    /** Changes the draft; the list is not touched until [apply]. */
    fun edit(change: ActivityFilter.() -> ActivityFilter) = _filters.update { it.copy(draft = it.draft.change()) }

    /** The draft's time as a span ending now, or as the range picked, so a preset means the same for every page read. */
    fun preset(preset: ActivityTimePreset?) =
        edit { copy(preset = preset, fromMillis = preset?.from(now(), zone)?.toEpochMilli(), toMillis = null) }

    /** A range the user picked, from the start of [from]'s day to the end of [to]'s, or just the start for a null [to]. */
    fun range(
        from: Long,
        to: Long?,
    ) = edit { copy(preset = null, fromMillis = from, toMillis = to) }

    /** Shows the list narrowed by the draft, from its first page. */
    fun apply() {
        _filters.update { it.copy(applied = it.draft, open = false) }
        load()
    }

    /** Clears what the sheet is editing; the list is not read again until **Show results**. */
    fun clearDraft() = _filters.update { it.copy(draft = ActivityFilter()) }

    /** Clears every filter and shows the whole log. */
    fun clearAll() {
        _filters.update { ActivityFilters(users = it.users) }
        load()
    }

    /** Clears one filter, from the chip that says so, and reads the list again. */
    fun clear(kind: ActivityFilterKind) {
        _filters.update { filters ->
            val cleared =
                when (kind) {
                    ActivityFilterKind.Time -> filters.applied.copy(fromMillis = null, toMillis = null, preset = null)
                    ActivityFilterKind.Who -> filters.applied.copy(who = ActivityWho.Anyone, username = null)
                    ActivityFilterKind.Severity -> filters.applied.copy(severity = null)
                    ActivityFilterKind.Type -> filters.applied.copy(type = null)
                }
            filters.copy(applied = cleared, draft = cleared)
        }
        load()
    }

    /** Reads the users the "who" filter picks from, again after a failure; a list already read is kept. */
    fun loadUsers() {
        if (_filters.value.users is ActivityUsers.Ready) return
        _filters.update { it.copy(users = ActivityUsers.Loading) }
        viewModelScope.launch {
            val users =
                connection
                    .consoleCall(TAG) { _, client -> client.users() }
                    .fold(
                        onSuccess = { list -> ActivityUsers.Ready(list.map { it.name }) },
                        onFailure = { ActivityUsers.Failed },
                    )
            _filters.update { it.copy(users = users) }
        }
    }

    private fun update(change: ActivityUiState.Ready.() -> ActivityUiState.Ready) =
        _state.update { (it as? ActivityUiState.Ready)?.change() ?: it }

    private companion object {
        const val TAG = "JellyfinActivity"
        const val PAGE_SIZE = 50
    }
}
