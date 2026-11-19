package com.cooper.homelab.ephyra.client.title

import com.binge.companion.sdk.attempt
import com.cooper.homelab.core.foundation.ServerFailure
import com.cooper.homelab.ephyra.browse.BrowseItem
import com.cooper.homelab.ephyra.browse.NextEpisode
import com.cooper.homelab.ephyra.browse.TitleDetail
import com.cooper.homelab.ephyra.browse.TitleRepository
import com.cooper.homelab.ephyra.browse.UserDataRepository
import com.cooper.homelab.ephyra.browse.with
import com.cooper.homelab.ephyra.browse.withChanges
import com.cooper.homelab.ephyra.server.UserDataChange
import com.cooper.homelab.ephyra.server.sameItem
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

private sealed interface Loaded {
    data object Loading : Loaded

    data class Failed(
        val failure: ServerFailure,
    ) : Loaded

    data class Series(
        val detail: TitleDetail,
        val seasons: List<BrowseItem>,
        val next: NextEpisode?,
        val similar: List<BrowseItem>,
        val selectedSeasonId: String?,
        val episodes: Map<String, List<BrowseItem>>,
        val failedSeasons: Map<String, ServerFailure>,
    ) : Loaded
}

/**
 * The series page's state. The series loads first, then its seasons and what to play next, then the episodes of the season shown
 * and the similar titles, each on its own: one that fails is left out (the episodes, in place, with a retry) and the page stays.
 * Watched and favourite go to the server for the series or one episode, and the page follows what the server says changed, so it
 * never shows a mark the server does not hold. No Android type; [scope] and [dispatcher] are the caller's, with no default.
 */
class SeriesViewModel(
    private val scope: CoroutineScope,
    private val dispatcher: CoroutineDispatcher,
    private val titles: TitleRepository,
    private val userData: UserDataRepository,
    private val classify: suspend (Throwable) -> ServerFailure,
) {
    private var seriesId: String? = null
    private val loaded = MutableStateFlow<Loaded>(Loaded.Loading)
    private val eventFlow = MutableSharedFlow<SeriesEvent>(extraBufferCapacity = EVENT_BUFFER)

    /** What the page shows now. */
    val state: StateFlow<SeriesUiState> = loaded.map(::uiState).stateIn(scope, SharingStarted.Eagerly, SeriesUiState.Loading)

    /** What the page tells the user once, as a snackbar. */
    val events: SharedFlow<SeriesEvent> = eventFlow.asSharedFlow()

    /** Opens the page for the series [id] and follows the changes to the user's data from then on. Call it once. */
    fun start(id: String) {
        seriesId = id
        scope.launch(dispatcher) { userData.changes.collect(::apply) }
        load()
    }

    /** Reads the series again, after a failure. */
    fun retry() = load()

    /** Shows the season [seasonId], reading its episodes the first time. */
    fun selectSeason(seasonId: String) {
        val current = loaded.value as? Loaded.Series ?: return
        loaded.value = current.copy(selectedSeasonId = seasonId)
        if (seasonId !in current.episodes) loadEpisodes(seasonId)
    }

    /** Reads the shown season's episodes again, after they failed. */
    fun retryEpisodes() {
        val season = (loaded.value as? Loaded.Series)?.selectedSeasonId ?: return
        loaded.update { if (it is Loaded.Series) it.copy(failedSeasons = it.failedSeasons - season) else it }
        loadEpisodes(season)
    }

    /** Marks the whole series watched, or not watched when it is. */
    fun toggleWatched() = writeSeries { id, held -> userData.setPlayed(id, !held.item.userData.played) }

    /** Makes the series a favourite, or not one when it is. */
    fun toggleFavourite() = writeSeries { id, held -> userData.setFavourite(id, !held.item.userData.favourite) }

    /** Marks one episode watched, or not watched when it is. */
    fun toggleEpisodeWatched(episodeId: String) {
        val current = loaded.value as? Loaded.Series ?: return
        val held =
            current.episodes.values
                .flatten()
                .firstOrNull { it.id == episodeId } ?: return
        write { userData.setPlayed(episodeId, !held.userData.played) }
    }

    private fun writeSeries(action: suspend (String, TitleDetail) -> UserDataChange) {
        val id = seriesId ?: return
        val held = (loaded.value as? Loaded.Series)?.detail ?: return
        write { action(id, held) }
    }

    private fun write(action: suspend () -> UserDataChange) {
        scope.launch(dispatcher) {
            attempt { action() }.onFailure { eventFlow.emit(SeriesEvent.WriteFailed(classify(it))) }
        }
    }

    private fun load() {
        val id = seriesId ?: return
        loaded.value = Loaded.Loading
        scope.launch(dispatcher) {
            attempt { titles.title(id) ?: error("No series $id") }
                .onSuccess { detail ->
                    val seasons = attempt { titles.seasons(id) }.getOrNull().orEmpty()
                    val next = attempt { titles.nextEpisode(id) }.getOrNull()
                    val selected = initialSeason(seasons, next)
                    loaded.value = Loaded.Series(detail, seasons, next, emptyList(), selected, emptyMap(), emptyMap())
                    selected?.let { loadEpisodes(it) }
                    launch { loadSimilar(id) }
                }.onFailure { loaded.value = Loaded.Failed(classify(it)) }
        }
    }

    private fun loadEpisodes(seasonId: String) {
        val id = seriesId ?: return
        scope.launch(dispatcher) {
            attempt { titles.episodes(id, seasonId) }
                .onSuccess { list ->
                    loaded.update { if (it is Loaded.Series) it.copy(episodes = it.episodes + (seasonId to list)) else it }
                }.onFailure { error ->
                    val failure = classify(error)
                    loaded.update { if (it is Loaded.Series) it.copy(failedSeasons = it.failedSeasons + (seasonId to failure)) else it }
                }
        }
    }

    private suspend fun loadSimilar(id: String) {
        attempt { titles.similar(id) }.onSuccess { similar ->
            loaded.update { if (it is Loaded.Series) it.copy(similar = similar) else it }
        }
    }

    /** What to play next after a mark that can move it: the series' own, or the very episode the button names. */
    private fun refreshNext(change: UserDataChange) {
        val id = seriesId ?: return
        val current = loaded.value as? Loaded.Series ?: return
        val moves = sameItem(change.itemId, id) || current.next?.episode?.let { sameItem(change.itemId, it.id) } == true
        if (!moves || change.played == null) return
        scope.launch(dispatcher) {
            attempt { titles.nextEpisode(id) }.onSuccess { next -> loaded.update { if (it is Loaded.Series) it.copy(next = next) else it } }
        }
    }

    private fun apply(change: UserDataChange) {
        refreshNext(change)
        loaded.update { current ->
            if (current !is Loaded.Series) return@update current
            current.copy(
                detail = current.detail.copy(item = current.detail.item.with(change)),
                seasons = current.seasons.withChanges(listOf(change)),
                next = current.next?.withChange(change),
                similar = current.similar.withChanges(listOf(change)),
                episodes = current.episodes.mapValues { (_, list) -> list.withChanges(listOf(change)) },
            )
        }
    }

    private companion object {
        const val EVENT_BUFFER = 4
    }
}

private fun NextEpisode.withChange(change: UserDataChange): NextEpisode =
    when (this) {
        is NextEpisode.Start -> copy(episode = episode.with(change))
        is NextEpisode.Next -> copy(episode = episode.with(change))
        is NextEpisode.Resume -> copy(episode = episode.with(change))
        NextEpisode.None, NextEpisode.AllWatched -> this
    }

private fun uiState(loaded: Loaded): SeriesUiState =
    when (loaded) {
        Loaded.Loading -> SeriesUiState.Loading
        is Loaded.Failed -> SeriesUiState.Failed(loaded.failure)
        is Loaded.Series -> SeriesUiState.Ready(seriesPage(loaded))
    }

private fun seriesPage(series: Loaded.Series): SeriesUi {
    val selected = series.selectedSeasonId
    val episodes =
        when {
            selected == null -> EpisodesState.Ready(emptyList())
            selected in series.failedSeasons -> EpisodesState.Failed(series.failedSeasons.getValue(selected))
            else -> series.episodes[selected]?.let { list -> EpisodesState.Ready(list.map(::episodeUi)) } ?: EpisodesState.Loading
        }
    return seriesUi(series.detail, series.seasons, series.next, series.similar, selected, episodes)
}
