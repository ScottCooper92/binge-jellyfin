package com.cooper.homelab.ephyra.client.title

import com.binge.companion.sdk.attempt
import com.cooper.homelab.core.foundation.ServerFailure
import com.cooper.homelab.ephyra.browse.TitleRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * The cast and crew page's state: the title's people, read from the same title read the film's and the series' page make. It uses no
 * Android type, and [scope] and [dispatcher] are the caller's, with no default.
 */
class TitlePeopleViewModel(
    private val scope: CoroutineScope,
    private val dispatcher: CoroutineDispatcher,
    private val titles: TitleRepository,
    private val classify: suspend (Throwable) -> ServerFailure,
) {
    private var itemId: String? = null
    private val held = MutableStateFlow<TitlePeopleUiState>(TitlePeopleUiState.Loading)

    /** What the page shows now. */
    val state: StateFlow<TitlePeopleUiState> get() = held

    /** Opens the page for the title [id] and reads it. Call it once. */
    fun start(id: String) {
        itemId = id
        load()
    }

    /** Reads the title again, after a failure. */
    fun retry() = load()

    private fun load() {
        val id = itemId ?: return
        held.value = TitlePeopleUiState.Loading
        scope.launch(dispatcher) {
            attempt { titles.title(id) ?: error("No title $id") }
                .onSuccess { held.value = TitlePeopleUiState.Ready(titlePeopleUi(it)) }
                .onFailure { held.value = TitlePeopleUiState.Failed(classify(it)) }
        }
    }
}
