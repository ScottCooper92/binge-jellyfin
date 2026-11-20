package com.cooper.homelab.ephyra.client.title

import com.binge.companion.sdk.attempt
import com.cooper.homelab.core.foundation.ServerFailure
import com.cooper.homelab.ephyra.browse.BrowseItem
import com.cooper.homelab.ephyra.browse.CollectionRepository
import com.cooper.homelab.ephyra.browse.TitleDetail
import com.cooper.homelab.ephyra.browse.TitleRepository
import com.cooper.homelab.ephyra.browse.UserDataRepository
import com.cooper.homelab.ephyra.browse.withChanges
import com.cooper.homelab.ephyra.server.UserDataChange
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private sealed interface Box {
    data object Loading : Box

    data class Failed(
        val failure: ServerFailure,
    ) : Box

    data class Loaded(
        val detail: TitleDetail,
        val parts: List<BrowseItem>,
    ) : Box
}

/**
 * The collection page's state: the box set and its parts in release order, with how many are watched. The parts follow the user's
 * data. No Android type; [scope] and [dispatcher] are the caller's, with no default.
 */
class CollectionViewModel(
    private val scope: CoroutineScope,
    private val dispatcher: CoroutineDispatcher,
    private val titles: TitleRepository,
    private val collections: CollectionRepository,
    private val userData: UserDataRepository,
    private val classify: suspend (Throwable) -> ServerFailure,
) {
    private var boxId: String? = null
    private val held = MutableStateFlow<Box>(Box.Loading)

    /** What the page shows now. */
    val state: StateFlow<CollectionUiState> = held.map(::uiState).stateIn(scope, SharingStarted.Eagerly, CollectionUiState.Loading)

    /** Opens the page for the collection [id] and follows the user's data from then on. Call it once. */
    fun start(id: String) {
        boxId = id
        scope.launch(dispatcher) { userData.changes.collect(::apply) }
        load()
    }

    /** Reads the collection again, after a failure. */
    fun retry() = load()

    private fun load() {
        val id = boxId ?: return
        held.value = Box.Loading
        scope.launch(dispatcher) {
            attempt { (titles.title(id) ?: error("No collection $id")) to collections.parts(id) }
                .onSuccess { (detail, parts) -> held.value = Box.Loaded(detail, parts) }
                .onFailure { held.value = Box.Failed(classify(it)) }
        }
    }

    private fun apply(change: UserDataChange) {
        held.update { if (it is Box.Loaded) it.copy(parts = it.parts.withChanges(listOf(change))) else it }
    }
}

private fun uiState(held: Box): CollectionUiState =
    when (held) {
        Box.Loading -> CollectionUiState.Loading
        is Box.Failed -> CollectionUiState.Failed(held.failure)
        is Box.Loaded -> CollectionUiState.Ready(collectionPageUi(held.detail, held.parts))
    }
