package com.cooper.binge.companion.jellyfin.ui.console

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.cooper.binge.companion.jellyfin.AppGraph
import com.cooper.binge.companion.jellyfin.server.JellyfinServerProfile

/** The users page, with each user's own page and their settings pages inside it. Kept out of `ConsoleRoutes.kt`, which is at its function limit. */
@Composable
internal fun UsersRoute(
    graph: AppGraph,
    profile: JellyfinServerProfile,
    onBack: () -> Unit,
) {
    val users: UsersViewModel = viewModel(factory = viewModelFactory { initializer { UsersViewModel(graph.connection) } })
    val choices: UserChoicesViewModel = viewModel(factory = viewModelFactory { initializer { UserChoicesViewModel(graph.connection) } })
    val state by users.state.collectAsStateWithLifecycle()
    val picks by choices.state.collectAsStateWithLifecycle()
    // A section reads what it picks from the first time it opens.
    LaunchedEffect((state as? UsersUiState.Ready)?.section) { choices.ensure((state as? UsersUiState.Ready)?.section) }
    UsersScreen(
        state,
        UsersActions(
            onOpen = users::open,
            onShow = users::show,
            onAdministrator = users::setAdministrator,
            onCanSignIn = users::setCanSignIn,
            onPolicy = users::setPolicy,
            onList = users::editList,
            onSection = users::section,
            onChoicesRetry = choices::retry,
            onCreate = users::create,
            onSetPassword = users::setPassword,
            onDelete = users::delete,
            onRetry = users::load,
            onBack = onBack,
        ),
        choices = picks,
        profile = profile,
    )
}
