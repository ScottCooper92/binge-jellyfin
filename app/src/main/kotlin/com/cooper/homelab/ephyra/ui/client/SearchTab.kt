package com.cooper.homelab.ephyra.ui.client

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.cooper.homelab.ephyra.AppGraph
import com.cooper.homelab.ephyra.client.search.SearchActions
import com.cooper.homelab.ephyra.client.search.SearchKind
import com.cooper.homelab.ephyra.client.search.SearchTabScreen
import com.cooper.homelab.ephyra.server.JellyfinServerProfile
import com.cooper.homelab.ephyra.ui.nav.EphyraTab

/**
 * The Search tab: the user's own server searched by name, with the recent queries kept on this device. [onOpen] is a result's tap and
 * gets its kind and id; the shell opens its page above the bar. What the field held is kept across a process death, as the route is.
 */
@Composable
fun SearchTab(
    graph: AppGraph,
    profile: JellyfinServerProfile,
    onOpen: (kind: SearchKind, id: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var savedText by rememberSaveable { mutableStateOf("") }
    val model: SearchTabViewModel =
        viewModel(
            factory =
                viewModelFactory {
                    initializer { SearchTabViewModel(graph.connection, graph.ioDispatcher, profile, graph.queryHistory, savedText) }
                },
        )
    LaunchedEffect(model) { model.start() }
    val state by model.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.text) { savedText = state.text }
    SearchTabScreen(
        state = state,
        imageUrl = model::imageUrl,
        actions =
            SearchActions(
                onQueryChange = model::setText,
                onClear = model::clear,
                onSubmit = model::submit,
                onOpen = onOpen,
                onLoadMore = model::loadMore,
                onRetry = model::retry,
                onType = model::selectType,
                onPickRecent = model::pickRecent,
                onClearRecents = model::clearRecents,
            ),
        modifier = modifier,
        reselect = EphyraTab.Search,
    )
}
