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
import com.cooper.homelab.ephyra.client.title.MovieActions
import com.cooper.homelab.ephyra.client.title.MovieScreen
import com.cooper.homelab.ephyra.client.title.MovieUiState
import com.cooper.homelab.ephyra.playback.PlayItem
import com.cooper.homelab.ephyra.server.JellyfinServerProfile
import com.cooper.homelab.ephyra.ui.openInBrowser
import com.cooper.homelab.ephyra.ui.shareTitle
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
    onOpenPeople: (itemId: String, tab: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val model: MoviePageViewModel =
        viewModel(
            key = "movie-$itemId",
            factory =
                viewModelFactory {
                    initializer {
                        MoviePageViewModel(
                            graph.connection,
                            graph.ioDispatcher,
                            profile,
                            graph.events.userDataChanges,
                            graph.index.boxSets,
                        )
                    }
                },
        )
    LaunchedEffect(model, itemId) { model.start(itemId) }
    val state by model.state.collectAsStateWithLifecycle()
    val stateNow by rememberUpdatedState(state)
    val context = LocalContext.current
    PlayStartHost(graph, serverAddress, onPlay) { play ->
        val playNow by rememberUpdatedState(play)
        val actions =
            remember(model, context, onOpenPeople, onBack, onOpenTitle, onOpenPerson) {
                MovieActions(
                    onBack = onBack,
                    onRetry = model::retry,
                    onPlay = {
                        (stateNow as? MovieUiState.Ready)?.movie?.title?.let { title -> playNow(PlayItem(itemId, PlayTitle(title))) }
                    },
                    onToggleWatched = { model.toggleWatched() },
                    onToggleFavourite = { model.toggleFavourite() },
                    onOpenTitle = { onOpenTitle(it.kind, it.id) },
                    onOpenPerson = { onOpenPerson(it.id) },
                    onOpenCollection = { onOpenTitle(ItemKind.BoxSet, it) },
                    onOpenLink = context::openInBrowser,
                    onShare = context::shareTitle,
                    onOpenPeople = { tab -> onOpenPeople(itemId, tab) },
                )
            }
        MovieScreen(state = state, imageUrl = model::imageUrl, actions = actions, modifier = modifier, events = model.events)
    }
}
