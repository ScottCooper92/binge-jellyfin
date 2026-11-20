package com.cooper.homelab.ephyra.client.title

import com.binge.companion.sdk.attempt
import com.cooper.homelab.core.foundation.ServerFailure
import com.cooper.homelab.ephyra.browse.BrowseItem
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

private sealed interface EpisodeLoad {
    data object Loading : EpisodeLoad

    data class Failed(
        val failure: ServerFailure,
    ) : EpisodeLoad

    data class Loaded(
        val detail: TitleDetail,
        val siblings: List<BrowseItem>? = null,
        val siblingsFailure: ServerFailure? = null,
    ) : EpisodeLoad
}

/**
 * The episode page's state, the movie page's shape over an episode. The episode loads first; the other episodes of its season load
 * after it and fail in place. Watched and favourite go to the server and the page follows what the server says changed. No Android
 * type; [scope] and [dispatcher] are the caller's, with no default.
 */
class EpisodeViewModel(
    private val scope: CoroutineScope,
    private val dispatcher: CoroutineDispatcher,
    private val titles: TitleRepository,
    private val userData: UserDataRepository,
    private val classify: suspend (Throwable) -> ServerFailure,
) {
    private var episodeId: String? = null
    private val loaded = MutableStateFlow<EpisodeLoad>(EpisodeLoad.Loading)
    private val eventFlow = MutableSharedFlow<TitleEvent>(extraBufferCapacity = EVENT_BUFFER)

    /** What the page shows now. */
    val state: StateFlow<EpisodeUiState> = loaded.map(::uiState).stateIn(scope, SharingStarted.Eagerly, EpisodeUiState.Loading)

    /** What the page tells the user once, as a snackbar. */
    val events: SharedFlow<TitleEvent> = eventFlow.asSharedFlow()

    /** Opens the page for the episode [id] and follows the changes to the user's data from then on. Call it once. */
    fun start(id: String) {
        episodeId = id
        scope.launch(dispatcher) { userData.changes.collect(::apply) }
        load()
    }

    /** Reads the episode again, after a failure. */
    fun retry() = load()

    /** Reads the season's other episodes again, after they failed. */
    fun retrySeason() {
        val held = (loaded.value as? EpisodeLoad.Loaded)?.detail ?: return
        loaded.update { if (it is EpisodeLoad.Loaded) it.copy(siblingsFailure = null) else it }
        scope.launch(dispatcher) { loadSeason(held.item) }
    }

    /** Marks the episode watched, or not watched when it is. */
    fun toggleWatched() = write { id, held -> userData.setPlayed(id, !held.item.userData.played) }

    /** Makes the episode a favourite, or not one when it is. */
    fun toggleFavourite() = write { id, held -> userData.setFavourite(id, !held.item.userData.favourite) }

    private fun write(action: suspend (String, TitleDetail) -> UserDataChange) {
        val id = episodeId ?: return
        val held = (loaded.value as? EpisodeLoad.Loaded)?.detail ?: return
        scope.launch(dispatcher) {
            attempt { action(id, held) }.onFailure { eventFlow.emit(TitleEvent.WriteFailed(classify(it))) }
        }
    }

    private fun load() {
        val id = episodeId ?: return
        loaded.value = EpisodeLoad.Loading
        scope.launch(dispatcher) {
            attempt { titles.title(id) ?: error("No episode $id") }
                .onSuccess { detail ->
                    loaded.value = EpisodeLoad.Loaded(detail)
                    launch { loadSeason(detail.item) }
                }.onFailure { loaded.value = EpisodeLoad.Failed(classify(it)) }
        }
    }

    /** The season's episodes. An episode the server gives no series for has none to list, and the row is left out. */
    private suspend fun loadSeason(episode: BrowseItem) {
        val series = episode.seriesId ?: return
        attempt { titles.episodes(series, episode.seasonId) }
            .onSuccess { list -> loaded.update { if (it is EpisodeLoad.Loaded) it.copy(siblings = list) else it } }
            .onFailure { error ->
                val failure = classify(error)
                loaded.update { if (it is EpisodeLoad.Loaded) it.copy(siblingsFailure = failure) else it }
            }
    }

    private fun apply(change: UserDataChange) {
        loaded.update { current ->
            if (current !is EpisodeLoad.Loaded) return@update current
            current.copy(
                detail = current.detail.copy(item = current.detail.item.with(change)),
                siblings = current.siblings?.withChanges(listOf(change)),
            )
        }
    }

    private companion object {
        const val EVENT_BUFFER = 4
    }
}

private fun uiState(loaded: EpisodeLoad): EpisodeUiState =
    when (loaded) {
        EpisodeLoad.Loading -> EpisodeUiState.Loading
        is EpisodeLoad.Failed -> EpisodeUiState.Failed(loaded.failure)
        is EpisodeLoad.Loaded -> {
            val season =
                when {
                    loaded.siblingsFailure != null -> EpisodesState.Failed(loaded.siblingsFailure)
                    loaded.siblings == null -> EpisodesState.Loading
                    else -> EpisodesState.Ready(loaded.siblings.map(::episodeUi))
                }
            EpisodeUiState.Ready(episodePageUi(loaded.detail, season))
        }
    }
