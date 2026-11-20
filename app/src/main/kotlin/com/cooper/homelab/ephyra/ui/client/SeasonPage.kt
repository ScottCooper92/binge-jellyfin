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
import com.cooper.homelab.ephyra.client.title.SeasonActions
import com.cooper.homelab.ephyra.client.title.SeasonPageUi
import com.cooper.homelab.ephyra.client.title.SeasonScreen
import com.cooper.homelab.ephyra.client.title.SeasonUiState
import com.cooper.homelab.ephyra.client.title.SeriesPlay
import com.cooper.homelab.ephyra.playback.PlayItem
import com.cooper.homelab.ephyra.server.JellyfinServerProfile
import com.cooper.homelab.player.api.PlayTitle

/**
 * A season's page, over the shell. [onOpenSeries] opens the series it belongs to and [onOpenEpisode] one of its episodes. The play button does
 * nothing yet: playing in the app is #756. The view model is the page's own, keyed by the season so two seasons on the stack keep their own.
 */
@Composable
fun SeasonPage(
    graph: AppGraph,
    profile: JellyfinServerProfile,
    itemId: String,
    serverAddress: String,
    onBack: () -> Unit,
    onPlay: (sessionId: String, title: String) -> Unit,
    onOpenSeries: (itemId: String) -> Unit,
    onOpenEpisode: (itemId: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val model: SeasonPageViewModel =
        viewModel(
            key = "season-$itemId",
            factory =
                viewModelFactory {
                    initializer { SeasonPageViewModel(graph.connection, graph.ioDispatcher, profile, graph.events.userDataChanges) }
                },
        )
    LaunchedEffect(model, itemId) { model.start(itemId) }
    val state by model.state.collectAsStateWithLifecycle()
    val stateNow by rememberUpdatedState(state)
    PlayStartHost(graph, serverAddress, onPlay) { play ->
        val playNow by rememberUpdatedState(play)
        val actions =
            remember(model, onBack, onOpenSeries, onOpenEpisode) {
                SeasonActions(
                    onBack = onBack,
                    onRetry = model::retry,
                    onPlay = {
                        (stateNow as? SeasonUiState.Ready)?.season?.let { season ->
                            season.play.episode()?.let { playNow(season.playItem(it)) }
                        }
                    },
                    onToggleWatched = { model.toggleWatched() },
                    onRetryEpisodes = model::retryEpisodes,
                    onOpenEpisode = { onOpenEpisode(it.id) },
                    onToggleEpisodeWatched = { model.toggleEpisodeWatched(it.id) },
                    onOpenSeries = onOpenSeries,
                )
            }
        SeasonScreen(state = state, imageUrl = model::imageUrl, actions = actions, modifier = modifier, events = model.events)
    }
}

private fun SeasonPageUi.playItem(episode: SeriesPlay.Episode) =
    PlayItem(episode.episodeId, PlayTitle(title = seriesName ?: name, seasonNumber = episode.season, episodeNumber = episode.episode))
