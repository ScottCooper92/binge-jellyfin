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

private sealed interface SeasonLoad {
    data object Loading : SeasonLoad

    data class Failed(
        val failure: ServerFailure,
    ) : SeasonLoad

    data class Loaded(
        val detail: TitleDetail,
        val episodes: List<BrowseItem>? = null,
        val episodesFailure: ServerFailure? = null,
    ) : SeasonLoad
}

/**
 * The season page's state: the season, then its episodes, which fail in place with a retry. Watched goes to the server for the
 * season or one episode, and the page follows what the server says changed. No Android type; [scope] and [dispatcher] are the
 * caller's, with no default.
 */
class SeasonViewModel(
    private val scope: CoroutineScope,
    private val dispatcher: CoroutineDispatcher,
    private val titles: TitleRepository,
    private val userData: UserDataRepository,
    private val classify: suspend (Throwable) -> ServerFailure,
) {
    private var seasonId: String? = null
    private val loaded = MutableStateFlow<SeasonLoad>(SeasonLoad.Loading)
    private val eventFlow = MutableSharedFlow<TitleEvent>(extraBufferCapacity = EVENT_BUFFER)

    /** What the page shows now. */
    val state: StateFlow<SeasonUiState> = loaded.map(::uiState).stateIn(scope, SharingStarted.Eagerly, SeasonUiState.Loading)

    /** What the page tells the user once, as a snackbar. */
    val events: SharedFlow<TitleEvent> = eventFlow.asSharedFlow()

    /** Opens the page for the season [id] and follows the changes to the user's data from then on. Call it once. */
    fun start(id: String) {
        seasonId = id
        scope.launch(dispatcher) { userData.changes.collect(::apply) }
        load()
    }

    /** Reads the season again, after a failure. */
    fun retry() = load()

    /** Reads the episodes again, after they failed. */
    fun retryEpisodes() {
        val held = (loaded.value as? SeasonLoad.Loaded)?.detail ?: return
        loaded.update { if (it is SeasonLoad.Loaded) it.copy(episodesFailure = null) else it }
        scope.launch(dispatcher) { loadEpisodes(held.item) }
    }

    /** Marks the whole season watched, or not watched when it is. */
    fun toggleWatched() {
        val id = seasonId ?: return
        val held = (loaded.value as? SeasonLoad.Loaded)?.detail ?: return
        write { userData.setPlayed(id, !held.item.userData.played) }
    }

    /** Marks one episode watched, or not watched when it is. */
    fun toggleEpisodeWatched(episodeId: String) {
        val held = (loaded.value as? SeasonLoad.Loaded)?.episodes?.firstOrNull { it.id == episodeId } ?: return
        write { userData.setPlayed(episodeId, !held.userData.played) }
    }

    private fun write(action: suspend () -> UserDataChange) {
        scope.launch(dispatcher) {
            attempt { action() }.onFailure { eventFlow.emit(TitleEvent.WriteFailed(classify(it))) }
        }
    }

    private fun load() {
        val id = seasonId ?: return
        loaded.value = SeasonLoad.Loading
        scope.launch(dispatcher) {
            attempt { titles.title(id) ?: error("No season $id") }
                .onSuccess { detail ->
                    loaded.value = SeasonLoad.Loaded(detail)
                    launch { loadEpisodes(detail.item) }
                }.onFailure { loaded.value = SeasonLoad.Failed(classify(it)) }
        }
    }

    private suspend fun loadEpisodes(season: BrowseItem) {
        attempt { titles.episodes(season.seriesId ?: error("No series for season ${season.id}"), season.id) }
            .onSuccess { list -> loaded.update { if (it is SeasonLoad.Loaded) it.copy(episodes = list) else it } }
            .onFailure { error ->
                val failure = classify(error)
                loaded.update { if (it is SeasonLoad.Loaded) it.copy(episodesFailure = failure) else it }
            }
    }

    private fun apply(change: UserDataChange) {
        loaded.update { current ->
            if (current !is SeasonLoad.Loaded) return@update current
            current.copy(
                detail = current.detail.copy(item = current.detail.item.with(change)),
                episodes = current.episodes?.withChanges(listOf(change)),
            )
        }
    }

    private companion object {
        const val EVENT_BUFFER = 4
    }
}

private fun uiState(loaded: SeasonLoad): SeasonUiState =
    when (loaded) {
        SeasonLoad.Loading -> SeasonUiState.Loading
        is SeasonLoad.Failed -> SeasonUiState.Failed(loaded.failure)
        is SeasonLoad.Loaded ->
            SeasonUiState.Ready(
                seasonPageUi(loaded.detail, loaded.episodes, loaded.episodesFailure?.let { EpisodesState.Failed(it) }),
            )
    }
