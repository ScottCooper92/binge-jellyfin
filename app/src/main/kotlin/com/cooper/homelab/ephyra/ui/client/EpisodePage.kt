package com.cooper.homelab.ephyra.ui.client

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.cooper.homelab.ephyra.AppGraph
import com.cooper.homelab.ephyra.client.title.EpisodeActions
import com.cooper.homelab.ephyra.client.title.EpisodeScreen
import com.cooper.homelab.ephyra.server.JellyfinServerProfile

/**
 * An episode's page, over the shell. [onOpenEpisode] opens another episode of its season;
 * [onOpenPerson] opens a person. The play button does nothing yet: playing in the app is #756. The view model is the page's own,
 * keyed by the episode so two episodes on the stack keep their own.
 */
@Composable
fun EpisodePage(
    graph: AppGraph,
    profile: JellyfinServerProfile,
    itemId: String,
    onBack: () -> Unit,
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
    val actions =
        remember(model, onBack, onOpenEpisode, onOpenSeries, onOpenPerson) {
            EpisodeActions(
                onBack = onBack,
                onRetry = model::retry,
                // In-app play is #756: until then the button has nowhere to go.
                onPlay = {},
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
