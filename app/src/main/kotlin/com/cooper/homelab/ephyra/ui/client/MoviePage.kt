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
import com.cooper.homelab.ephyra.browse.ItemKind
import com.cooper.homelab.ephyra.client.title.MovieActions
import com.cooper.homelab.ephyra.client.title.MovieScreen
import com.cooper.homelab.ephyra.server.JellyfinServerProfile

/**
 * A film's page, over the shell. [onOpenTitle] opens a similar title or a collection's part, which is of the kind it is given;
 * [onOpenPerson] opens a person. The play button does nothing yet: playing in the app is #756. The view model is the page's own,
 * keyed by the film so two films on the stack keep their own.
 */
@Composable
fun MoviePage(
    graph: AppGraph,
    profile: JellyfinServerProfile,
    itemId: String,
    onBack: () -> Unit,
    onOpenTitle: (kind: ItemKind, itemId: String) -> Unit,
    onOpenPerson: (itemId: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val model: MoviePageViewModel =
        viewModel(
            key = "movie-$itemId",
            factory =
                viewModelFactory {
                    initializer { MoviePageViewModel(graph.connection, graph.ioDispatcher, profile, graph.events.userDataChanges) }
                },
        )
    LaunchedEffect(model, itemId) { model.start(itemId) }
    val state by model.state.collectAsStateWithLifecycle()
    val actions =
        remember(model, onBack, onOpenTitle, onOpenPerson) {
            MovieActions(
                onBack = onBack,
                onRetry = model::retry,
                // In-app play is #756: until then the button has nowhere to go.
                onPlay = {},
                onToggleWatched = { model.toggleWatched() },
                onToggleFavourite = { model.toggleFavourite() },
                onOpenTitle = { onOpenTitle(it.kind, it.id) },
                onOpenPerson = { onOpenPerson(it.id) },
            )
        }
    MovieScreen(state = state, imageUrl = model::imageUrl, actions = actions, modifier = modifier, events = model.events)
}
