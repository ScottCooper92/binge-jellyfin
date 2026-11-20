package com.cooper.homelab.ephyra.client.title

import com.binge.companion.sdk.attempt
import com.cooper.homelab.core.foundation.ServerFailure
import com.cooper.homelab.ephyra.browse.BrowseItem
import com.cooper.homelab.ephyra.browse.CollectionRepository
import com.cooper.homelab.ephyra.browse.TitleDetail
import com.cooper.homelab.ephyra.browse.TitleRepository
import com.cooper.homelab.ephyra.browse.UserDataRepository
import com.cooper.homelab.ephyra.browse.with
import com.cooper.homelab.ephyra.browse.withChanges
import com.cooper.homelab.ephyra.server.UserDataChange
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** The collection a film is in, as read: the box set, and its parts. */
private data class LoadedCollection(
    val id: String,
    val name: String,
    val parts: List<BrowseItem>,
)

private sealed interface Snapshot {
    data object Loading : Snapshot

    data class Failed(
        val failure: ServerFailure,
    ) : Snapshot

    data class Loaded(
        val detail: TitleDetail,
        val similar: List<BrowseItem> = emptyList(),
        val collection: LoadedCollection? = null,
    ) : Snapshot
}

/**
 * The movie page's state, from the title and what the user does with it. The title loads first; similar titles and the collection load
 * after it, each on its own, and one that fails or has nothing is left out. Watched and favourite go to the server, and the page
 * follows what the server says changed (its answer, or a change made in another app), so it never shows a mark the server does not
 * hold. It uses no Android type, and [scope] and [dispatcher] are the caller's, with no default.
 */
class MovieViewModel(
    private val scope: CoroutineScope,
    private val dispatcher: CoroutineDispatcher,
    private val titles: TitleRepository,
    private val userData: UserDataRepository,
    private val collections: CollectionRepository,
    private val classify: suspend (Throwable) -> ServerFailure,
) {
    private var itemId: String? = null
    private val snapshot = MutableStateFlow<Snapshot>(Snapshot.Loading)
    private val eventFlow = MutableSharedFlow<TitleEvent>(extraBufferCapacity = EVENT_BUFFER)

    /** What the page shows now. */
    val state: StateFlow<MovieUiState> =
        snapshot.map(::uiState).stateIn(scope, SharingStarted.Eagerly, MovieUiState.Loading)

    /** What the page tells the user once, as a snackbar. */
    val events: SharedFlow<TitleEvent> = eventFlow.asSharedFlow()

    /** Opens the page for the film [id]: reads it, and from then on follows the changes to the user's data. Call it once. */
    fun start(id: String) {
        itemId = id
        scope.launch(dispatcher) { userData.changes.collect(::apply) }
        load()
    }

    /** Reads the film again, after a failure. */
    fun retry() = load()

    /** Marks the film watched, or not watched when it is. */
    fun toggleWatched() = write { id, held -> userData.setPlayed(id, !held.item.userData.played) }

    /** Makes the film a favourite, or not one when it is. */
    fun toggleFavourite() = write { id, held -> userData.setFavourite(id, !held.item.userData.favourite) }

    private fun write(action: suspend (String, TitleDetail) -> UserDataChange) {
        val id = itemId ?: return
        val held = (snapshot.value as? Snapshot.Loaded)?.detail ?: return
        scope.launch(dispatcher) {
            attempt { action(id, held) }.onFailure { eventFlow.emit(TitleEvent.WriteFailed(classify(it))) }
        }
    }

    private fun load() {
        val id = itemId ?: return
        snapshot.value = Snapshot.Loading
        scope.launch(dispatcher) {
            attempt { titles.title(id) ?: error("No title $id") }
                .onSuccess { detail ->
                    snapshot.value = Snapshot.Loaded(detail)
                    launch { loadSimilar(id) }
                    launch { loadCollection(id) }
                }.onFailure { snapshot.value = Snapshot.Failed(classify(it)) }
        }
    }

    private suspend fun loadSimilar(id: String) {
        attempt { titles.similar(id) }.onSuccess { similar ->
            snapshot.update { if (it is Snapshot.Loaded) it.copy(similar = similar) else it }
        }
    }

    /** The first collection the film is in and its parts. A film in none has no row. */
    private suspend fun loadCollection(id: String) {
        attempt {
            val box = collections.collectionsOf(id).firstOrNull()
            box?.let { LoadedCollection(it.id, it.name, collections.parts(it.id)) }
        }.onSuccess { found ->
            if (found != null) snapshot.update { if (it is Snapshot.Loaded) it.copy(collection = found) else it }
        }
    }

    private fun apply(change: UserDataChange) {
        snapshot.update { current ->
            if (current !is Snapshot.Loaded) return@update current
            current.copy(
                detail = current.detail.copy(item = current.detail.item.with(change)),
                similar = current.similar.withChanges(listOf(change)),
                collection = current.collection?.let { it.copy(parts = it.parts.withChanges(listOf(change))) },
            )
        }
    }

    private fun uiState(snapshot: Snapshot): MovieUiState =
        when (snapshot) {
            Snapshot.Loading -> MovieUiState.Loading
            is Snapshot.Failed -> MovieUiState.Failed(snapshot.failure)
            is Snapshot.Loaded ->
                MovieUiState.Ready(
                    movieUi(
                        snapshot.detail,
                        snapshot.similar,
                        snapshot.collection?.let { CollectionRow(it.id, it.name, it.parts.map(::titleCard)) },
                    ),
                )
        }

    private companion object {
        const val EVENT_BUFFER = 4
    }
}
