package com.cooper.binge.companion.jellyfin.ui.console

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.AdminPanelSettings
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Password
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material.icons.outlined.PersonOff
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import com.binge.designsystem.component.BingePullToRefresh
import com.binge.designsystem.component.BingeTextButton
import com.binge.designsystem.component.ExpressiveIconButton
import com.binge.designsystem.component.ItemGroup
import com.binge.designsystem.component.ListItem
import com.binge.designsystem.component.ListItemNote
import com.binge.designsystem.component.PageIntro
import com.binge.designsystem.component.TextEntrySurface
import com.binge.designsystem.formatRelativeOrAbsolute
import com.binge.designsystem.template.BingeScreenScaffold
import com.binge.designsystem.template.ScreenBar
import com.binge.designsystem.template.ScreenBody
import com.binge.designsystem.template.screenListPadding
import com.cooper.binge.companion.jellyfin.R
import com.cooper.binge.companion.jellyfin.server.ENABLED_FOLDERS
import com.cooper.binge.companion.jellyfin.server.ENABLE_ALL_FOLDERS
import com.cooper.binge.companion.jellyfin.server.JellyfinServerProfile
import com.cooper.binge.companion.jellyfin.server.ServerUser
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import com.binge.designsystem.R as DesR

/** What the users page can ask of its view model, and of the console. */
data class UsersActions(
    val onOpen: (ServerUser?) -> Unit = {},
    val onShow: (UserDialog?) -> Unit = {},
    val onAdministrator: (Boolean) -> Unit = {},
    val onCanSignIn: (Boolean) -> Unit = {},
    val onPolicy: (key: String, value: JsonElement) -> Unit = { _, _ -> },
    val onList: (key: String, change: (List<JsonElement>) -> List<JsonElement>) -> Unit = { _, _ -> },
    val onSection: (UserSection?) -> Unit = {},
    val onChoicesRetry: (UserSection) -> Unit = {},
    val onCreate: (String, String) -> Unit = { _, _ -> },
    val onSetPassword: (String) -> Unit = {},
    val onDelete: () -> Unit = {},
    val onRetry: () -> Unit = {},
    val onRefresh: () -> Unit = {},
    val onBack: () -> Unit = {},
)

/**
 * The server's users, and one user's page: whether they can sign in and are an administrator, which
 * libraries they see, their password, and, last and red, deleting them. Each switch saves as it's
 * made. The signed-in user's own page has no sign-in, administrator or delete, and says why.
 */
@Composable
fun UsersScreen(
    state: UsersUiState,
    actions: UsersActions,
    now: Long = System.currentTimeMillis(),
    choices: UserChoices = UserChoices(),
    profile: JellyfinServerProfile = JellyfinServerProfile.from(null),
    avatar: @Composable (ServerUser) -> Unit = { UserAvatar(it.name) },
) {
    val user = (state as? UsersUiState.Ready)?.openUser
    val section = (state as? UsersUiState.Ready)?.section.takeIf { user != null }
    val back =
        if (section != null) {
            ({ actions.onSection(null) })
        } else if (user != null) {
            ({ actions.onOpen(null) })
        } else {
            actions.onBack
        }
    BackHandler(enabled = user != null, onBack = back)
    val ready = state as? UsersUiState.Ready
    val host = remember { SnackbarHostState() }
    // On a window with room for it the list and the open user sit side by side, under the page's own title.
    val split = LocalPageSpansTwoPanes.current
    val addUser: @Composable () -> Unit = {
        CreateFooter(
            label = stringResource(R.string.users_add),
            icon = Icons.Outlined.PersonAdd,
            onClick = { actions.onShow(UserDialog.Create) },
            visible = state !is UsersUiState.Failed,
            enabled = ready != null,
            loading = ready?.busy == true,
        )
    }
    BingeScreenScaffold(
        title =
            if (split) {
                stringResource(R.string.users_title)
            } else {
                section?.let { stringResource(it.title()) } ?: user?.name
                    ?: stringResource(R.string.users_title)
            },
        bar = ScreenBar.Small,
        onBack = if (split) actions.onBack else back,
        snackbarHostState = host,
        bottomBar = {
            // On the list only: a user's own page and its sections have nothing to create. Beside the list the button
            // sits under the list, not across both panes.
            if (!split && user == null) addUser()
        },
    ) { padding ->
        ScreenBody(padding) { _ ->
            val inner = padding.screenListPadding()
            when (state) {
                UsersUiState.Loading ->
                    if (split) {
                        // The button is part of the page from its first frame, disabled until the list is ready.
                        ListDetailBody(
                            detailOpen = false,
                            list = {
                                Column(Modifier.fillMaxSize()) {
                                    Box(Modifier.weight(1f)) { ConsoleLoading(inner, LOADING_ROWS) }
                                    addUser()
                                }
                            },
                            detail = {},
                        )
                    } else {
                        ConsoleLoading(inner, LOADING_ROWS)
                    }
                is UsersUiState.Failed -> ConsoleFailureScreen(state.failure, actions.onRetry, Modifier.padding(inner))
                is UsersUiState.Ready ->
                    ListDetailBody(
                        detailOpen = user != null,
                        list = {
                            Column(Modifier.fillMaxSize()) {
                                Box(Modifier.weight(1f)) {
                                    BingePullToRefresh(
                                        isRefreshing = state.refreshing,
                                        onRefresh = actions.onRefresh,
                                        indicatorTopInset = padding.calculateTopPadding(),
                                    ) { UserList(state, actions, inner, now, avatar) }
                                }
                                if (split) addUser()
                            }
                        },
                        detail = { UserDetailPane(state, user, section, choices, profile, actions, inner) },
                    )
            }
        }
    }
    ready?.let {
        UserDialogs(it, actions)
        // What the last change did, or why it didn't go through, is said in a message, not in a space the page keeps for it.
        RefusedMessage(it.actionFailed, host, R.string.users_not_changed)
        RefusedMessage(it.refreshFailed, host, R.string.console_not_refreshed)
        SnackbarMessage(it.done?.let { done -> stringResource(done.message) }, host)
    }
}

/**
 * The open user: their page, or the section of their settings it opened. In place of the list on one pane, and beside it
 * under their name on a wider one, where a section has a Back of its own to their page. While none is open the wider
 * pane says to pick a user.
 */
@Composable
private fun UserDetailPane(
    state: UsersUiState.Ready,
    user: ServerUser?,
    section: UserSection?,
    choices: UserChoices,
    profile: JellyfinServerProfile,
    actions: UsersActions,
    inner: PaddingValues,
) {
    val split = LocalPageSpansTwoPanes.current
    when {
        user == null ->
            NothingSelected(
                stringResource(R.string.users_select_title),
                stringResource(R.string.users_select),
                Icons.Outlined.Person,
                inner,
            )
        !split -> UserBody(state, user, section, choices, profile, actions, inner)
        else ->
            Column(Modifier.fillMaxSize()) {
                if (section == null) {
                    DetailHeading(user.name, inner)
                } else {
                    Row(
                        Modifier.padding(
                            top = inner.calculateTopPadding(),
                            start = inner.calculateStartPadding(LocalLayoutDirection.current),
                        ),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        ExpressiveIconButton(
                            onClick = { actions.onSection(null) },
                            icon = Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = stringResource(DesR.string.cd_navigate_back),
                        )
                        DetailHeading(
                            stringResource(section.title()),
                            PaddingValues(),
                            Modifier.padding(start = dimensionResource(DesR.dimen.padding_s)),
                        )
                    }
                }
                Box(Modifier.weight(1f)) { UserBody(state, user, section, choices, profile, actions, inner.belowHeading()) }
            }
    }
}

/** A user's page, or the section of their settings it opened. */
@Composable
private fun UserBody(
    state: UsersUiState.Ready,
    user: ServerUser,
    section: UserSection?,
    choices: UserChoices,
    profile: JellyfinServerProfile,
    actions: UsersActions,
    inner: PaddingValues,
) {
    if (section == null) UserPage(state, user, actions, inner) else PolicySection(section, state, user, choices, profile, actions, inner)
}

@Composable
private fun UserList(
    state: UsersUiState.Ready,
    actions: UsersActions,
    inner: PaddingValues,
    now: Long,
    avatar: @Composable (ServerUser) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = inner,
        verticalArrangement = Arrangement.spacedBy(dimensionResource(DesR.dimen.padding_l)),
    ) {
        item { PageIntro(stringResource(R.string.users_body)) }
        item { ItemGroup(title = null, rows = state.users.map { it.row(state.me, it.id == state.openUser?.id, now, actions, avatar) }) }
    }
}

@Composable
private fun ServerUser.row(
    me: String,
    open: Boolean,
    now: Long,
    actions: UsersActions,
    avatar: @Composable (ServerUser) -> Unit,
): ListItem {
    val seen = formatRelativeOrAbsolute(lastSeenMillis, now)?.let { stringResource(R.string.devices_seen, it) }
    val role =
        when {
            disabled -> stringResource(R.string.users_disabled)
            administrator -> stringResource(R.string.users_administrator)
            else -> null
        }
    return ListItem(
        icon =
            when {
                disabled -> Icons.Outlined.PersonOff
                administrator -> Icons.Outlined.AdminPanelSettings
                else -> Icons.Outlined.Person
            },
        leadingContent = { avatar(this) },
        label = if (id == me) stringResource(R.string.users_you, name) else name,
        detail = listOfNotNull(role, seen).joinToString(" · ").ifBlank { stringResource(R.string.users_never_seen) },
        selected = open && LocalPageSpansTwoPanes.current,
        onClick = { actions.onOpen(this) },
    )
}

@Composable
private fun UserPage(
    state: UsersUiState.Ready,
    user: ServerUser,
    actions: UsersActions,
    inner: PaddingValues,
) {
    val mine = user.id == state.me
    LazyColumn(contentPadding = inner, verticalArrangement = Arrangement.spacedBy(dimensionResource(DesR.dimen.padding_l))) {
        item {
            ItemGroup(
                title = stringResource(R.string.users_access),
                rows =
                    if (mine) {
                        listOf(
                            ListItem(
                                icon = Icons.Outlined.AdminPanelSettings,
                                label = stringResource(R.string.users_administrator),
                                note = ListItemNote(stringResource(R.string.users_mine), icon = Icons.Outlined.Info),
                                clickable = false,
                            ),
                        )
                    } else {
                        listOf(
                            ListItem(
                                icon = Icons.AutoMirrored.Outlined.Logout,
                                label = stringResource(R.string.users_can_sign_in),
                                toggled = !user.disabled,
                                onClick = { actions.onCanSignIn(user.disabled) },
                            ),
                            ListItem(
                                icon = Icons.Outlined.AdminPanelSettings,
                                label = stringResource(R.string.users_administrator),
                                detail = stringResource(R.string.users_administrator_detail),
                                toggled = user.administrator,
                                onClick = { actions.onAdministrator(!user.administrator) },
                            ),
                        )
                    },
            )
        }
        item { LibraryAccess(state, user, actions) }
        item { ItemGroup(title = stringResource(R.string.users_settings), rows = sectionRows(user, actions)) }
        if (!mine) {
            item {
                ItemGroup(
                    title = null,
                    rows =
                        listOf(
                            ListItem(
                                icon = Icons.Outlined.Password,
                                label = stringResource(R.string.users_set_password),
                                detail = stringResource(if (user.hasPassword) R.string.users_has_password else R.string.users_no_password),
                                loading = state.busy,
                                onClick = { actions.onShow(UserDialog.Password) },
                            ),
                        ),
                )
            }
        }
        if (!mine) {
            item {
                BingeTextButton(
                    label = stringResource(R.string.users_delete),
                    onClick = { actions.onShow(UserDialog.Delete) },
                    leadingIcon = Icons.Outlined.Delete,
                    destructive = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

/** Every library, or the ones chosen: a switch for all, and when it's off, one for each library. */
@Composable
private fun LibraryAccess(
    state: UsersUiState.Ready,
    user: ServerUser,
    actions: UsersActions,
) {
    val all =
        ListItem(
            icon = Icons.Outlined.VideoLibrary,
            label = stringResource(R.string.users_all_libraries),
            toggled = user.allLibraries,
            onClick = { actions.onPolicy(ENABLE_ALL_FOLDERS, JsonPrimitive(!user.allLibraries)) },
        )
    val each =
        if (user.allLibraries) {
            emptyList()
        } else {
            state.libraries.map { library ->
                val on = library.id in user.libraries
                ListItem(
                    icon = typeIcon(library.type),
                    label = library.name,
                    toggled = on,
                    onClick = { actions.onList(ENABLED_FOLDERS, memberChange(library.id, !on)) },
                )
            }
        }
    ItemGroup(title = stringResource(R.string.users_libraries), rows = listOf(all) + each)
}

private val UserDone.message: Int
    get() =
        when (this) {
            UserDone.Created -> R.string.users_created
            UserDone.PasswordSet -> R.string.users_password_set
            UserDone.Deleted -> R.string.users_deleted
        }

@Composable
private fun UserDialogs(
    state: UsersUiState.Ready,
    actions: UsersActions,
) {
    val user = state.openUser
    val error = state.formFailure?.let { stringResource(R.string.users_not_changed, it.text()) }
    when (state.dialog) {
        UserDialog.Create -> CreateSheet(actions, submitting = state.busy, error = error)
        UserDialog.Password -> if (user != null) PasswordSheet(user, actions, submitting = state.busy, error = error)
        UserDialog.Delete -> if (user != null) DeleteDialog(user, actions)
        null -> Unit
    }
}

/** Deleting a user, after their name, as a confirm. [inWindow] false draws its body for a frame. */
@Composable
internal fun DeleteDialog(
    user: ServerUser,
    actions: UsersActions,
    inWindow: Boolean = true,
) {
    ConsoleConfirm(
        title = stringResource(R.string.users_delete_title, user.name),
        message = stringResource(R.string.users_delete_body),
        confirmLabel = stringResource(R.string.users_delete),
        onConfirm = actions.onDelete,
        onDismissRequest = { actions.onShow(null) },
        destructive = true,
        inWindow = inWindow,
    )
}

/** A new user: a name and a password. [inWindow] false draws the form for a frame. */
@Composable
internal fun CreateSheet(
    actions: UsersActions,
    submitting: Boolean = false,
    error: String? = null,
    inWindow: Boolean = true,
) {
    var name by rememberSaveable { mutableStateOf("") }
    // A password is not saved: saved state outlives the sheet (a rotation, a killed process), and a secret must not.
    var password by remember { mutableStateOf("") }
    ConsoleSheet(onDismissRequest = { actions.onShow(null) }, inWindow = inWindow) {
        ConsoleFieldsForm(
            title = stringResource(R.string.users_add),
            body = stringResource(R.string.users_add_body),
            submitLabel = stringResource(R.string.users_add_action),
            onSubmit = { actions.onCreate(name, password) },
            onCancel = { actions.onShow(null) },
            submitting = submitting,
            error = error,
        ) {
            ConsoleTextField(
                value = name,
                onValueChange = { name = it },
                label = stringResource(R.string.setup_username),
                enabled = !submitting,
                imeAction = ImeAction.Next,
            )
            ConsoleTextField(
                value = password,
                onValueChange = { password = it },
                label = stringResource(R.string.setup_password),
                enabled = !submitting,
                secret = true,
            )
        }
    }
}

/** A new password for a user, on the design system's secret text entry. [inWindow] false draws the form for a frame. */
@Composable
internal fun PasswordSheet(
    user: ServerUser,
    actions: UsersActions,
    submitting: Boolean = false,
    error: String? = null,
    inWindow: Boolean = true,
) {
    // A password is not saved: saved state outlives the sheet (a rotation, a killed process), and a secret must not.
    var password by remember { mutableStateOf("") }
    ConsoleSheet(onDismissRequest = { actions.onShow(null) }, inWindow = inWindow) {
        TextEntrySurface(
            title = stringResource(R.string.users_password_title, user.name),
            supportingText = stringResource(R.string.users_password_body),
            value = password,
            onValueChange = { password = it },
            onSubmit = { actions.onSetPassword(password) },
            onCancel = { actions.onShow(null) },
            submitLabel = stringResource(R.string.users_password_action),
            hint = stringResource(R.string.password_new),
            submitEnabled = true,
            isSubmitting = submitting,
            error = error,
            singleLine = true,
            secret = true,
        )
    }
}

private const val LOADING_ROWS = 6
