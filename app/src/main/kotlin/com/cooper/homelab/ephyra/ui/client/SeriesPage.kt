package com.cooper.homelab.ephyra.ui.client

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.cooper.homelab.ephyra.AppGraph
import com.cooper.homelab.ephyra.browse.ItemKind
import com.cooper.homelab.ephyra.client.title.SeriesActions
import com.cooper.homelab.ephyra.client.title.SeriesPlay
import com.cooper.homelab.ephyra.client.title.SeriesScreen
import com.cooper.homelab.ephyra.client.title.SeriesUiState
import com.cooper.homelab.ephyra.playback.PlayItem
import com.cooper.homelab.ephyra.server.JellyfinServerProfile
import com.cooper.homelab.ephyra.ui.openInBrowser
import com.cooper.homelab.ephyra.ui.shareTitle
import com.cooper.homelab.player.api.PlayTitle

/**
 * A series' page, over the shell. [onOpenTitle] opens a similar series, which is of the kind it is given; [onOpenSeason] opens a season and [onOpenEpisode] an episode;
 * [onOpenPerson] opens a person. The play button plays the episode to resume, else the next one, or the first when all are watched.
 * The view model is the page's own, keyed by the series so two series on the stack keep their own.
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
    val context = LocalContext.current
    PlayStartHost(graph, serverAddress, onPlay) { play ->
        val playNow by rememberUpdatedState(play)
        val actions =
            remember(model, context, onBack, onOpenTitle, onOpenSeason, onOpenEpisode, onOpenPerson) {
                SeriesActions(
                    onBack = onBack,
                    onRetry = model::retry,
                    onPlay = {
                        (stateNow as? SeriesUiState.Ready)?.series?.let { series ->
                            series.play.episode()?.let { playNow(it.playItem(series.title)) }
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
                    onOpenLink = context::openInBrowser,
                    onShare = context::shareTitle,
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

/** The episode as a play names it: the series' name, with the episode's numbers and its own name. */
internal fun SeriesPlay.Episode.playItem(seriesName: String) =
    PlayItem(episodeId, PlayTitle(title = seriesName, seasonNumber = season, episodeNumber = episode, episodeTitle = name))
