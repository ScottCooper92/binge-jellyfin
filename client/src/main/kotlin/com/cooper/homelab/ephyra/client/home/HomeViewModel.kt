package com.cooper.homelab.ephyra.client.home

import com.binge.companion.sdk.attempt
import com.cooper.homelab.core.foundation.ServerFailure
import com.cooper.homelab.core.presentation.hub.HubLoader
import com.cooper.homelab.core.presentation.hub.HubState
import com.cooper.homelab.ephyra.browse.BrowseItem
import com.cooper.homelab.ephyra.browse.LatestRepository
import com.cooper.homelab.ephyra.browse.LibraryRepository
import com.cooper.homelab.ephyra.browse.LibraryView
import com.cooper.homelab.ephyra.browse.NextUpRepository
import com.cooper.homelab.ephyra.browse.ResumeRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** The user's libraries, which say which rows Home asks for, as far as they have been read. */
private sealed interface Libraries {
    data object Loading : Libraries

    data class Loaded(
        val views: List<LibraryView>,
    ) : Libraries

    data class Failed(
        val failure: ServerFailure,
    ) : Libraries
}

/**
 * Home's state, from the rows the user's server answers. The rows load on their own through the shared [HubLoader]: one that fails or
 * is empty is left out, and Home fails only when every row did, or when the libraries could not be read. It uses no Android type, and
 * [scope] and [dispatcher] are the caller's, with no default.
 */
class HomeViewModel(
    private val scope: CoroutineScope,
    private val dispatcher: CoroutineDispatcher,
    private val libraries: LibraryRepository,
    private val resume: ResumeRepository,
    private val nextUp: NextUpRepository,
    private val latest: LatestRepository,
    private val classify: suspend (Throwable) -> ServerFailure,
) {
    private val read = MutableStateFlow<Libraries>(Libraries.Loading)
    private val loader = HubLoader<HomeRowKey, BrowseItem>(scope, dispatcher, classify, ::load)

    /** What Home shows now. */
    val state: StateFlow<HomeUiState> =
        combine(read, loader.state) { libraries, rows -> uiState(libraries, rows) }
            .stateIn(scope, SharingStarted.Eagerly, HomeUiState.Loading)

    /** Whether a [refresh] is still running, for a pull-to-refresh indicator. */
    val refreshing: StateFlow<Boolean> = loader.refreshing

    /** Reads the libraries, then asks for the rows they name. Call it once when Home opens. */
    fun start() = readLibraries()

    /** Reads everything again, keeping the rows on screen until the new ones land. After a failure it is a retry. */
    fun refresh() = readLibraries(refresh = true)

    private fun readLibraries(refresh: Boolean = false) {
        scope.launch(dispatcher) {
            // Rows already shown are reloaded by a refresh. With no libraries read yet, `show` starts every row itself, so a refresh as
            // well would cancel each one it had just started and load it again.
            val rowsShown = read.value is Libraries.Loaded
            attempt { libraries.views() }
                .onSuccess { views ->
                    read.value = Libraries.Loaded(views)
                    loader.show(homeRowKeys(views))
                    if (refresh && rowsShown) loader.refresh()
                }.onFailure { error ->
                    if (rowsShown) {
                        // The libraries could not be read again, but the rows are asked for anyway: the list they were made from still
                        // stands, and a retry that did nothing at all would look like a button that does not work.
                        if (refresh) loader.refresh()
                    } else {
                        read.value = Libraries.Failed(classify(error))
                    }
                }
        }
    }

    private suspend fun load(key: HomeRowKey): List<BrowseItem> =
        when (key) {
            HomeRowKey.ContinueWatching -> resume.resume().items
            HomeRowKey.NextUp -> nextUp.nextUp().items
            is HomeRowKey.Latest -> latest.latest(libraryOf(key))
        }

    private fun libraryOf(key: HomeRowKey.Latest): LibraryView =
        (read.value as? Libraries.Loaded)?.views?.firstOrNull { it.id == key.libraryId }
            ?: error("No library ${key.libraryId} for its row")

    private fun uiState(
        libraries: Libraries,
        rows: HubState<HomeRowKey, BrowseItem>,
    ): HomeUiState =
        when (libraries) {
            Libraries.Loading -> HomeUiState.Loading
            is Libraries.Failed -> HomeUiState.Error(libraries.failure)
            is Libraries.Loaded ->
                when (rows) {
                    HubState.Loading -> HomeUiState.Loading
                    HubState.Empty -> HomeUiState.Empty
                    is HubState.Failed -> HomeUiState.Error(rows.failure)
                    is HubState.Ready -> homeState(rows.rows.mapNotNull { homeRow(it.key, it.items) }, rows.pending)
                }
        }
}
