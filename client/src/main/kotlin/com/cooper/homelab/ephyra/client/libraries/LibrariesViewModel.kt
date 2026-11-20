package com.cooper.homelab.ephyra.client.libraries

import com.binge.companion.sdk.attempt
import com.cooper.homelab.core.foundation.ServerFailure
import com.cooper.homelab.ephyra.browse.LibraryRepository
import com.cooper.homelab.ephyra.client.TabModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * The Libraries tab's state, from the libraries the user's server lists. It uses no Android type, and [scope] and [dispatcher] are the
 * caller's, with no default. A refresh that fails keeps the libraries already on screen; a first read that fails is the failure.
 */
class LibrariesViewModel(
    private val scope: CoroutineScope,
    private val dispatcher: CoroutineDispatcher,
    private val libraries: LibraryRepository,
    private val classify: suspend (Throwable) -> ServerFailure,
) : TabModel<LibrariesUiState> {
    private val mutableState = MutableStateFlow<LibrariesUiState>(LibrariesUiState.Loading)
    private val mutableRefreshing = MutableStateFlow(false)

    /** What the tab shows now. */
    override val state: StateFlow<LibrariesUiState> = mutableState.asStateFlow()

    /** Whether a [refresh] is still running, for a pull-to-refresh indicator. */
    override val refreshing: StateFlow<Boolean> = mutableRefreshing.asStateFlow()

    /** Reads the libraries. Call it once when the tab opens. */
    override fun start() = read(refresh = false)

    /** Reads them again, keeping what is on screen until the answer lands. After a failure it is a retry, shown as loading. */
    override fun refresh() = read(refresh = true)

    private fun read(refresh: Boolean) {
        val showing = mutableState.value
        // A retry after a failure shows the skeleton again; a pull keeps the tiles and shows the indicator.
        if (showing is LibrariesUiState.Error) mutableState.value = LibrariesUiState.Loading
        if (refresh && showing !is LibrariesUiState.Error) mutableRefreshing.value = true
        scope.launch(dispatcher) {
            attempt { libraries.views() }
                .onSuccess { views ->
                    val tiles = views.tiles()
                    mutableState.value = if (tiles.isEmpty()) LibrariesUiState.Empty else LibrariesUiState.Ready(tiles)
                }.onFailure { error ->
                    if (mutableState.value !is LibrariesUiState.Ready && mutableState.value !is LibrariesUiState.Empty) {
                        mutableState.value = LibrariesUiState.Error(classify(error))
                    }
                }
            mutableRefreshing.value = false
        }
    }
}
