package com.cooper.homelab.ephyra.ui.client

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.binge.designsystem.component.BingePullToRefresh
import com.cooper.homelab.ephyra.AppGraph
import com.cooper.homelab.ephyra.browse.ItemKind
import com.cooper.homelab.ephyra.client.home.HomeCaption
import com.cooper.homelab.ephyra.client.home.HomeCard
import com.cooper.homelab.ephyra.client.home.HomeImage
import com.cooper.homelab.ephyra.client.home.HomeScreen
import com.cooper.homelab.ephyra.client.home.HomeUiState
import com.cooper.homelab.ephyra.server.JellyfinServerProfile
import com.cooper.homelab.player.api.PlayTitle

/**
 * The Home tab: the rows of the user's own server, with pull to refresh. [onOpen] is a card's tap, which opens its title page, and
 * [onResume] is a Continue watching card's, which plays it from where the user stopped; the shell hands both in. [onOpen] gets the item's
 * kind as well as its id, to pick the page; [onResume] gets the id and the title as the player names it (an episode with its numbers and its own name). The view model is the tab's, so the rows survive a switch of tab and a rotation.
 */
@Composable
fun HomeTab(
    graph: AppGraph,
    profile: JellyfinServerProfile,
    onOpen: (kind: ItemKind, itemId: String) -> Unit,
    onResume: (itemId: String, title: PlayTitle) -> Unit,
    modifier: Modifier = Modifier,
) {
    val model: HomeTabViewModel =
        viewModel(factory = viewModelFactory { initializer { HomeTabViewModel(graph.connection, graph.ioDispatcher, profile) } })
    LaunchedEffect(model) { model.start() }
    val state by model.state.collectAsStateWithLifecycle()
    val refreshing by model.refreshing.collectAsStateWithLifecycle()
    HomeTabContent(
        state = state,
        refreshing = refreshing,
        imageUrl = model::imageUrl,
        onOpen = { onOpen(it.kind, it.id) },
        onResume = { onResume(it.id, it.playTitle()) },
        onRefresh = model::refresh,
        modifier = modifier,
    )
}

/** The card as a play names it: an episode carries its season and episode numbers and its own name beside its series'. */
internal fun HomeCard.playTitle(): PlayTitle =
    when (val c = caption) {
        is HomeCaption.Episode -> PlayTitle(title = title, seasonNumber = c.season, episodeNumber = c.episode, episodeTitle = c.name)
        else -> PlayTitle(title)
    }

/** [HomeTab] with its state in hand: Home under a pull to refresh. A failure has its own Try again, so it is not pulled. */
@Composable
internal fun HomeTabContent(
    state: HomeUiState,
    refreshing: Boolean,
    imageUrl: (HomeImage, Int) -> String?,
    onOpen: (HomeCard) -> Unit,
    onResume: (HomeCard) -> Unit,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier.fillMaxSize()) {
        BingePullToRefresh(isRefreshing = refreshing, onRefresh = onRefresh) {
            HomeScreen(
                state = state,
                imageUrl = imageUrl,
                onOpen = onOpen,
                onResume = onResume,
                onRetry = onRefresh,
            )
        }
    }
}
