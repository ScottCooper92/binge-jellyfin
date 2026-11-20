package com.cooper.homelab.ephyra.ui.client

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.cooper.homelab.ephyra.AppGraph
import com.cooper.homelab.ephyra.client.libraries.LibrariesScreen
import com.cooper.homelab.ephyra.server.JellyfinServerProfile
import com.cooper.homelab.ephyra.ui.nav.EphyraTab

/**
 * The Libraries tab: the user's movie and series libraries, with pull to refresh. [onOpen] is a tile's tap and gets the library's id;
 * the shell hands it in and opens the library's screen above the bar. The view model is the tab's, so the tiles survive a switch of tab
 * and a rotation.
 */
@Composable
fun LibrariesTab(
    graph: AppGraph,
    profile: JellyfinServerProfile,
    onOpen: (libraryId: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val model: LibrariesTabViewModel =
        viewModel(factory = viewModelFactory { initializer { LibrariesTabViewModel(graph.connection, graph.ioDispatcher, profile) } })
    LaunchedEffect(model) { model.start() }
    val state by model.state.collectAsStateWithLifecycle()
    val refreshing by model.refreshing.collectAsStateWithLifecycle()
    LibrariesScreen(
        state = state,
        refreshing = refreshing,
        imageUrl = model::imageUrl,
        onOpen = { onOpen(it.id) },
        onRefresh = model::refresh,
        reselect = EphyraTab.Libraries,
        modifier = modifier,
    )
}
