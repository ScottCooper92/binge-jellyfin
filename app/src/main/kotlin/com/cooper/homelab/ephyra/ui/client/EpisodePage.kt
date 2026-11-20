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
import com.cooper.homelab.ephyra.client.title.EpisodeActions
import com.cooper.homelab.ephyra.client.title.EpisodePageUi
import com.cooper.homelab.ephyra.client.title.EpisodeScreen
import com.cooper.homelab.ephyra.client.title.EpisodeUiState
import com.cooper.homelab.ephyra.playback.PlayItem
import com.cooper.homelab.ephyra.server.JellyfinServerProfile
import com.cooper.homelab.player.api.PlayTitle

/**
 * An episode's page, over the shell. [onOpenEpisode] opens another episode of its season;
 * [onOpenPerson] opens a person. The play button plays the episode through [PlayStartHost], with [serverAddress] and [onPlay].
 * The view model is the page's own, keyed by the episode so two episodes on the stack keep their own.
 */
@Composable
fun EpisodePage(
    graph: AppGraph,
    profile: JellyfinServerProfile,
    itemId: String,
    serverAddress: String,
    onBack: () -> Unit,
    onPlay: (sessionId: String, title: String) -> Unit,
    onOpenEpisode: (itemId: String) -> Unit,
    onOpenSeries: (itemId: String) -> Unit,
    onOpenPerson: (itemId: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val model: EpisodePageViewModel =
        viewModel(
            key = "episode-$itemId",
            factory =
                viewModelFactory {
                    initializer { EpisodePageViewModel(graph.connection, graph.ioDispatcher, profile, graph.events.userDataChanges) }
                },
        )
    LaunchedEffect(model, itemId) { model.start(itemId) }
    val state by model.state.collectAsStateWithLifecycle()
    val stateNow by rememberUpdatedState(state)
    PlayStartHost(graph, serverAddress, onPlay) { play ->
        val playNow by rememberUpdatedState(play)
        val actions =
            remember(model, onBack, onOpenEpisode, onOpenSeries, onOpenPerson) {
                EpisodeActions(
                    onBack = onBack,
                    onRetry = model::retry,
                    onPlay = {
                        (stateNow as? EpisodeUiState.Ready)?.episode?.let { playNow(it.playItem()) }
                    },
                    onToggleWatched = { model.toggleWatched() },
                    onToggleFavourite = { model.toggleFavourite() },
                    onRetrySeason = model::retrySeason,
                    onOpenEpisode = { onOpenEpisode(it.id) },
                    onOpenSeries = onOpenSeries,
                    onOpenPerson = { onOpenPerson(it.id) },
                )
            }
        EpisodeScreen(state = state, imageUrl = model::imageUrl, actions = actions, modifier = modifier, events = model.events)
    }
}

/** The episode as a play names it: its series' name, with the episode's numbers and its own name. */
private fun EpisodePageUi.playItem() =
    PlayItem(id, PlayTitle(title = seriesName ?: title, seasonNumber = seasonNumber, episodeNumber = episodeNumber, episodeTitle = title))
