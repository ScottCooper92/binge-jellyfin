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
import com.cooper.homelab.ephyra.client.title.TitlePeopleActions
import com.cooper.homelab.ephyra.client.title.TitlePeopleScreen
import com.cooper.homelab.ephyra.server.JellyfinServerProfile

/**
 * A title's cast and crew, over its page. [tab] is the one to open on, and [onOpenPerson] opens a person's page. The view model is the
 * page's own, keyed by the title so two titles on the stack keep their own.
 */
@Composable
fun TitlePeoplePage(
    graph: AppGraph,
    profile: JellyfinServerProfile,
    itemId: String,
    tab: Int,
    onBack: () -> Unit,
    onOpenPerson: (itemId: String) -> Unit,
) {
    val model: TitlePeoplePageViewModel =
        viewModel(
            key = "people-$itemId",
            factory =
                viewModelFactory {
                    initializer { TitlePeoplePageViewModel(graph.connection, graph.ioDispatcher, profile, graph.events.userDataChanges) }
                },
        )
    LaunchedEffect(model, itemId) { model.start(itemId) }
    val state by model.state.collectAsStateWithLifecycle()
    val actions =
        remember(model, onBack, onOpenPerson) {
            TitlePeopleActions(onBack = onBack, onRetry = model::retry, onOpenPerson = { onOpenPerson(it.id) })
        }
    TitlePeopleScreen(state = state, initialTab = tab, imageUrl = model::imageUrl, actions = actions)
}
