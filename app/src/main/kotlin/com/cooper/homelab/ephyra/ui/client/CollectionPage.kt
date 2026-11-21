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
import com.cooper.homelab.ephyra.client.title.CollectionActions
import com.cooper.homelab.ephyra.client.title.CollectionScreen
import com.cooper.homelab.ephyra.server.JellyfinServerProfile

/**
 * A collection's page, over the shell. [onOpenTitle] opens one of its parts, which is of the kind it is given. The view model is the page's
 * own, keyed by the collection so two collections on the stack keep their own.
 */
@Composable
fun CollectionPage(
    graph: AppGraph,
    profile: JellyfinServerProfile,
    itemId: String,
    onBack: () -> Unit,
    onOpenTitle: (kind: ItemKind, itemId: String) -> Unit,
) {
    val model: CollectionPageViewModel =
        viewModel(
            key = "collection-$itemId",
            factory =
                viewModelFactory {
                    initializer {
                        CollectionPageViewModel(
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
    val actions =
        remember(model, onBack, onOpenTitle) {
            CollectionActions(
                onBack = onBack,
                onRetry = model::retry,
                onOpenTitle = { onOpenTitle(it.kind, it.id) },
            )
        }
    CollectionScreen(state = state, imageUrl = model::imageUrl, actions = actions)
}
