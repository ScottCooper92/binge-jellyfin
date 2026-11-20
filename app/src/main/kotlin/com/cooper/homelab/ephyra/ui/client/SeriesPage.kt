package com.cooper.homelab.ephyra.ui.client

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.cooper.homelab.ephyra.AppGraph
import com.cooper.homelab.ephyra.browse.ItemKind
import com.cooper.homelab.ephyra.client.title.SeriesActions
import com.cooper.homelab.ephyra.client.title.SeriesPlay
import com.cooper.homelab.ephyra.client.title.SeriesScreen
import com.cooper.homelab.ephyra.client.title.SeriesUi
import com.cooper.homelab.ephyra.client.title.SeriesUiState
import com.cooper.homelab.ephyra.playback.PlayItem
import com.cooper.homelab.ephyra.server.JellyfinServerProfile
import com.cooper.homelab.player.api.PlayTitle

/**
 * A series' page, over the shell. [onOpenTitle] opens a similar series, which is of the kind it is given; [onOpenSeason] opens a season and [onOpenEpisode] an episode;
 * [onOpenPerson] opens a person. The play button does nothing yet: playing in the app is #756. The view model is the page's own,
 * keyed by the series so two series on the stack keep their own.
 */
@Composable
fun SeriesPage(
    graph: AppGraph,
    profile: JellyfinServerProfile,
    itemId: String,
    serverAddress: String,
    onBack: () -> Unit,
    onPlay: (sessionId: String, title: String) -> Unit,
    onOpenTitle: (kind: ItemKind, itemId: String) -> Unit,
    onOpenSeason: (itemId: String) -> Unit,
    onOpenEpisode: (itemId: String) -> Unit,
    onOpenPerson: (itemId: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val model: SeriesPageViewModel =
        viewModel(
            key = "series-$itemId",
            factory =
                viewModelFactory {
                    initializer { SeriesPageViewModel(graph.connection, graph.ioDispatcher, profile, graph.events.userDataChanges) }
                },
        )
    LaunchedEffect(model, itemId) { model.start(itemId) }
    val state by model.state.collectAsStateWithLifecycle()
    val stateNow by rememberUpdatedState(state)
    PlayStartHost(graph, serverAddress, onPlay) { play ->
        val playNow by rememberUpdatedState(play)
        val actions =
            remember(model, onBack, onOpenTitle, onOpenSeason, onOpenEpisode, onOpenPerson) {
                SeriesActions(
                    onBack = onBack,
                    onRetry = model::retry,
                    onPlay = {
                        (stateNow as? SeriesUiState.Ready)?.series?.let { series ->
                            series.play.episode()?.let { playNow(series.playItem(it)) }
                        }
                    },
                    onToggleWatched = { model.toggleWatched() },
                    onToggleFavourite = { model.toggleFavourite() },
                    onSelectSeason = { model.selectSeason(it.id) },
                    onRetryEpisodes = model::retryEpisodes,
                    onOpenSeason = { onOpenSeason(it.id) },
                    onOpenEpisode = { onOpenEpisode(it.id) },
                    onToggleEpisodeWatched = { model.toggleEpisodeWatched(it.id) },
                    onOpenTitle = { onOpenTitle(it.kind, it.id) },
                    onOpenPerson = { onOpenPerson(it.id) },
                )
            }
        SeriesScreen(state = state, imageUrl = model::imageUrl, actions = actions, modifier = modifier, events = model.events)
    }
}

/** The episode a series' button plays or resumes, or the first to watch again when all are watched; null when there is none. */
internal fun SeriesPlay?.episode(): SeriesPlay.Episode? =
    when (this) {
        is SeriesPlay.Episode -> this
        is SeriesPlay.AllWatched -> first
        SeriesPlay.NoEpisodes, null -> null
    }

private fun SeriesUi.playItem(episode: SeriesPlay.Episode) =
    PlayItem(episode.episodeId, PlayTitle(title = title, seasonNumber = episode.season, episodeNumber = episode.episode))
