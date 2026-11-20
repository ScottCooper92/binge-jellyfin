package com.cooper.homelab.ephyra.ui.client

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.cooper.homelab.ephyra.AppGraph
import com.cooper.homelab.ephyra.browse.ItemKind
import com.cooper.homelab.ephyra.client.title.PersonActions
import com.cooper.homelab.ephyra.client.title.PersonScreen
import com.cooper.homelab.ephyra.server.JellyfinServerProfile

/**
 * A person's page, over the shell. [onOpenTitle] opens a movie or a series they appear in, which is of the kind it is given. The view
 * model is the page's own, keyed by the person so two people on the stack keep their own.
 */
@Composable
fun PersonPage(
    graph: AppGraph,
    profile: JellyfinServerProfile,
    itemId: String,
    onBack: () -> Unit,
    onOpenTitle: (kind: ItemKind, itemId: String) -> Unit,
) {
    val model: PersonPageViewModel =
        viewModel(
            key = "person-$itemId",
            factory =
                viewModelFactory {
                    initializer { PersonPageViewModel(graph.connection, graph.ioDispatcher, profile, graph.events.userDataChanges) }
                },
        )
    LaunchedEffect(model, itemId) { model.start(itemId) }
    val state by model.state.collectAsStateWithLifecycle()
    val actions =
        remember(model, onBack, onOpenTitle) {
            PersonActions(
                onBack = onBack,
                onRetry = model::retry,
                onLoadMore = model::loadMore,
                onOpenTitle = { onOpenTitle(it.kind, it.id) },
            )
        }
    PersonScreen(state = state, imageUrl = model::imageUrl, actions = actions)
}
