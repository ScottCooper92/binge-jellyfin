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
import com.cooper.homelab.ephyra.client.home.HomeCard
import com.cooper.homelab.ephyra.client.home.HomeScreen
import com.cooper.homelab.ephyra.client.home.HomeUiState
import com.cooper.homelab.ephyra.server.JellyfinServerProfile
import kotlinx.coroutines.Dispatchers

/**
 * The Home tab: the rows of the user's own server, with pull to refresh. [onOpen] is a card's tap, which opens its title page, and
 * [onResume] is a Continue watching card's, which plays it from where the user stopped; the shell hands both in, and they get the
 * item's id. The view model is the tab's, so the rows survive a switch of tab and a rotation.
 */
@Composable
fun HomeTab(
    graph: AppGraph,
    profile: JellyfinServerProfile,
    onOpen: (itemId: String) -> Unit,
    onResume: (itemId: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val model: HomeTabViewModel =
        viewModel(factory = viewModelFactory { initializer { HomeTabViewModel(graph.connection, Dispatchers.IO, profile) } })
    LaunchedEffect(model) { model.start() }
    val state by model.state.collectAsStateWithLifecycle()
    val refreshing by model.refreshing.collectAsStateWithLifecycle()
    HomeTabContent(
        state = state,
        refreshing = refreshing,
        imageUrl = model::imageUrl,
        onOpen = { onOpen(it.id) },
        onResume = { onResume(it.id) },
        onRefresh = model::refresh,
        modifier = modifier,
    )
}

/** [HomeTab] with its state in hand: Home under a pull to refresh. A failure has its own Try again, so it is not pulled. */
@Composable
internal fun HomeTabContent(
    state: HomeUiState,
    refreshing: Boolean,
    imageUrl: (com.cooper.homelab.ephyra.client.home.HomeImage, Int) -> String?,
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
