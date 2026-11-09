package com.cooper.binge.companion.jellyfin.ui.console

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.cooper.binge.companion.jellyfin.AppGraph
import com.cooper.binge.companion.jellyfin.server.JellyfinServerProfile

/**
 * The users page. A user opens as an entry above it on the stack ([openItem]), and a section of their settings above
 * that; their screens ([UserItemRoute]) read this page's view models. With none open the view model has no user selected.
 */
@Composable
internal fun UsersRoute(
    graph: AppGraph,
    openItem: (String) -> Unit,
    onBack: () -> Unit,
) {
    val users: UsersViewModel = viewModel(factory = viewModelFactory { initializer { UsersViewModel(graph.connection) } })
    val state by users.state.collectAsStateWithLifecycle()
    val open = LocalOpenItem.current
    LaunchedEffect(open == null) { if (open == null) users.select(null) }
    // A user just created is opened, as a new user's page always was.
    val ready = state as? UsersUiState.Ready
    LaunchedEffect(ready?.created) {
        ready?.created?.let {
            users.createdOpened()
            openItem(userItem(it))
        }
    }
    UsersScreen(
        state,
        UsersActions(
            onOpen = {
                users.itemOpened()
                openItem(userItem(it.id))
            },
            onShow = users::show,
            onCreate = users::create,
            onRetry = users::load,
            onRefresh = users::refresh,
            onBack = onBack,
        ),
        avatar = { ServerUserAvatar(it, graph.userPictures) },
    )
}

/** A user's screen, or a section of their settings, with the page's view models. */
@Composable
internal fun UserItemRoute(
    graph: AppGraph,
    profile: JellyfinServerProfile,
    item: String,
    openSubItem: (String) -> Unit,
    onBack: () -> Unit,
) {
    val users: UsersViewModel = viewModel(factory = viewModelFactory { initializer { UsersViewModel(graph.connection) } })
    val choices: UserChoicesViewModel = viewModel(factory = viewModelFactory { initializer { UserChoicesViewModel(graph.connection) } })
    val state by users.state.collectAsStateWithLifecycle()
    val picks by choices.state.collectAsStateWithLifecycle()
    val userId = item.removePrefix("user:").substringBefore('/')
    val section =
        item.substringAfter('/', "").takeIf { it.isNotEmpty() }?.let { name ->
            UserSection.entries.firstOrNull { it.name == name }
        }
    // A section reads what it picks from the first time it opens.
    LaunchedEffect(section) { choices.ensure(section) }
    val actions =
        UsersActions(
            onSelect = users::select,
            onShow = users::show,
            onAdministrator = users::setAdministrator,
            onCanSignIn = users::setCanSignIn,
            onPolicy = users::setPolicy,
            onList = users::editList,
            onSection = { openSubItem(sectionItem(userId, it)) },
            onPicture = { openSubItem("user:$userId/$PICTURE_ITEM") },
            onChoicesRetry = choices::retry,
            onSetPassword = users::setPassword,
            onDelete = users::delete,
            onRetry = users::load,
            onBack = onBack,
        )
    if (item.substringAfter('/', "") == PICTURE_ITEM) {
        // What the page shows beside a name is read again on leaving, so a changed picture shows there.
        DisposableEffect(Unit) { onDispose { users.refresh() } }
        val name =
            (state as? UsersUiState.Ready)
                ?.users
                ?.firstOrNull { it.id == userId }
                ?.name
                .orEmpty()
        ProfilePictureRoute(graph, profile.hasUserImageRoute, onBack, userId, name)
    } else if (section == null) {
        UserScreen(state, userId, actions)
    } else {
        UserSectionScreen(state, userId, section, picks, profile, actions)
    }
}

/** The item keys of a user, and of a section of their settings, on the stack. */
internal fun userItem(id: String) = "user:$id"

internal fun sectionItem(
    userId: String,
    section: UserSection,
) = "user:$userId/${section.name}"

/** The item suffix of a user's picture page, beside the names of the sections of their settings. */
private const val PICTURE_ITEM = "picture"
