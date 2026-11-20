package com.cooper.homelab.ephyra.ui.client

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.cooper.homelab.ephyra.AppGraph
import com.cooper.homelab.ephyra.browse.ItemKind
import com.cooper.homelab.ephyra.client.title.MovieActions
import com.cooper.homelab.ephyra.client.title.MovieScreen
import com.cooper.homelab.ephyra.client.title.MovieUiState
import com.cooper.homelab.ephyra.playback.PlayItem
import com.cooper.homelab.ephyra.server.JellyfinServerProfile
import com.cooper.homelab.player.api.PlayTitle

/**
 * A film's page, over the shell. [onOpenTitle] opens a similar title, a collection's part or the collection itself, which is of the kind it is given;
 * [onOpenPerson] opens a person. The play button begins a play of the film and hands its id to [onPlay], which opens the player; where
 * the server at [serverAddress] cannot be played from (the demo, or plain http at a public address) it says why instead. The view model
 * is the page's own, keyed by the film so two films on the stack keep their own.
 */
@Composable
fun MoviePage(
    graph: AppGraph,
    profile: JellyfinServerProfile,
    itemId: String,
    serverAddress: String,
    onBack: () -> Unit,
    onPlay: (sessionId: String, title: String) -> Unit,
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
    val stateNow by rememberUpdatedState(state)
    var blocked by remember { mutableStateOf<PlayBlock?>(null) }
    val actions =
        remember(model, onBack, onPlay, onOpenTitle, onOpenPerson) {
            MovieActions(
                onBack = onBack,
                onRetry = model::retry,
                onPlay = {
                    val title = (stateNow as? MovieUiState.Ready)?.movie?.title
                    val block = playBlockFor(serverAddress)
                    when {
                        title == null -> Unit
                        block != null -> blocked = block
                        else -> onPlay(graph.playbackSittings.start(PlayItem(itemId, PlayTitle(title))), title)
                    }
                },
                onToggleWatched = { model.toggleWatched() },
                onToggleFavourite = { model.toggleFavourite() },
                onOpenTitle = { onOpenTitle(it.kind, it.id) },
                onOpenPerson = { onOpenPerson(it.id) },
                onOpenCollection = { onOpenTitle(ItemKind.BoxSet, it) },
            )
        }
    MovieScreen(state = state, imageUrl = model::imageUrl, actions = actions, modifier = modifier, events = model.events)
    blocked?.let { PlayBlockedMessage(it, onDismiss = { blocked = null }) }
}
