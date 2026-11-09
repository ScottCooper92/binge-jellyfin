package com.cooper.binge.companion.jellyfin.ui.console

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import com.binge.designsystem.component.BingePullToRefresh
import com.binge.designsystem.component.BingeTextButton
import com.binge.designsystem.component.ItemGroup
import com.binge.designsystem.component.ListItem
import com.binge.designsystem.component.ListItemNote
import com.binge.designsystem.component.PageIntro
import com.binge.designsystem.component.TextEntrySurface
import com.binge.designsystem.formatRelativeOrAbsolute
import com.binge.designsystem.template.BingeScreenScaffold
import com.binge.designsystem.template.EmptyScreen
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

/** What the users screens can ask of their view model, and of the console. */
data class UsersActions(
    /** Opens a user's own screen. */
    val onOpen: (ServerUser) -> Unit = {},
    /** Says which user the policy screens are showing, so the changes they make are to them; null for none. */
    val onSelect: (String?) -> Unit = {},
    val onShow: (UserDialog?) -> Unit = {},
    val onAdministrator: (Boolean) -> Unit = {},
    val onCanSignIn: (Boolean) -> Unit = {},
    val onPolicy: (key: String, value: JsonElement) -> Unit = { _, _ -> },
    val onList: (key: String, change: (List<JsonElement>) -> List<JsonElement>) -> Unit = { _, _ -> },
    /** Opens a section of the user's settings over their screen. */
    val onSection: (UserSection) -> Unit = {},
    /** Opens the picture page of the user whose screen it is. */
    val onPicture: () -> Unit = {},
    val onChoicesRetry: (UserSection) -> Unit = {},
    val onCreate: (String, String) -> Unit = { _, _ -> },
    val onSetPassword: (String) -> Unit = {},
    val onDelete: () -> Unit = {},
    val onRetry: () -> Unit = {},
    val onRefresh: () -> Unit = {},
    val onBack: () -> Unit = {},
)

/**
 * The server's users, by name. A tap opens the user's own screen ([UserScreen]) above this one on the stack, and Add a
 * user, in the footer, a sheet for a new one. The user showing, where this list is beside it, is marked.
 */
@Composable
fun UsersScreen(
    state: UsersUiState,
    actions: UsersActions,
    now: Long = System.currentTimeMillis(),
    avatar: @Composable (ServerUser) -> Unit = { UserAvatar(it.name) },
    openUser: String? = LocalOpenItem.current?.substringAfter("user:")?.substringBefore('/'),
) {
    // Held above the loading, failed and ready branches, so a reload does not drop where the user was.
    val listState = rememberLazyListState()
    val ready = state as? UsersUiState.Ready
    val host = remember { SnackbarHostState() }
    BingeScreenScaffold(
        title = stringResource(R.string.users_title),
        bar = ScreenBar.Small,
        onBack = actions.onBack,
        snackbarHostState = host,
        bottomBar = {
            CreateFooter(
                label = stringResource(R.string.users_add),
                icon = Icons.Outlined.PersonAdd,
                onClick = { actions.onShow(UserDialog.Create) },
                visible = state !is UsersUiState.Failed,
                enabled = ready != null,
                loading = ready?.busy == true,
            )
        },
    ) { padding ->
        ScreenBody(padding) { _ ->
            val inner = padding.screenListPadding()
            when (state) {
                UsersUiState.Loading -> ConsoleLoading(inner, LOADING_ROWS)
                is UsersUiState.Failed -> ConsoleFailureScreen(state.failure, actions.onRetry, Modifier.padding(inner))
                is UsersUiState.Ready ->
                    BingePullToRefresh(
                        isRefreshing = state.refreshing,
                        onRefresh = actions.onRefresh,
                        indicatorTopInset = padding.calculateTopPadding(),
                    ) { UserList(state, actions, inner, now, avatar, openUser, listState) }
            }
        }
    }
    ready?.let {
        if (it.dialog == UserDialog.Create) CreateSheet(actions, submitting = it.busy, error = it.formError())
        RefusedMessage(it.refreshFailed, host, R.string.console_not_refreshed)
        // A user created or deleted is said on the list; the screen of a user says what was done to them.
        SnackbarMessage(it.done?.takeIf { done -> done != UserDone.PasswordSet }?.let { done -> stringResource(done.message) }, host)
    }
}

/**
 * One user: whether they can sign in and are an administrator, which libraries they see, the sections of their
 * settings, their password, and, last and red, deleting them. A screen of its own above the list on the stack, so Back
 * returns to it. Each switch saves as it's made. The signed-in user's own page has no sign-in, administrator or
 * delete, and says why. A user the server no longer lists (deleted, say) closes it.
 */
@Composable
fun UserScreen(
    state: UsersUiState,
    userId: String,
    actions: UsersActions,
) {
    val ready = state as? UsersUiState.Ready
    val user = ready?.users?.firstOrNull { it.id == userId }
    UserScreenEffects(ready, userId, user, actions)
    // Held above the loading, failed and ready branches, so a reload does not drop where the user was.
    val listState = rememberLazyListState()
    val host = remember { SnackbarHostState() }
    BingeScreenScaffold(
        title = user?.name ?: stringResource(R.string.users_title),
        bar = ScreenBar.Small,
        onBack = actions.onBack,
        snackbarHostState = host,
    ) { padding ->
        ScreenBody(padding) { _ ->
            val inner = padding.screenListPadding()
            when {
                state is UsersUiState.Loading -> ConsoleLoading(inner, LOADING_ROWS)
                state is UsersUiState.Failed -> ConsoleFailureScreen(state.failure, actions.onRetry, Modifier.padding(inner))
                ready != null && user != null -> UserPage(ready, user, actions, inner, listState)
            }
        }
    }
    if (ready != null && user != null) {
        when (ready.dialog) {
            UserDialog.Password -> PasswordSheet(user, actions, submitting = ready.busy, error = ready.formError())
            UserDialog.Delete -> DeleteDialog(user, actions)
            else -> Unit
        }
        RefusedMessage(ready.actionFailed, host, R.string.users_not_changed)
        SnackbarMessage(ready.done?.takeIf { it == UserDone.PasswordSet }?.let { stringResource(it.message) }, host)
    }
}

/** One section of a user's settings: a screen of its own above the user's. [choices] is what its pickers pick from. */
@Composable
fun UserSectionScreen(
    state: UsersUiState,
    userId: String,
    section: UserSection,
    choices: UserChoices,
    profile: JellyfinServerProfile,
    actions: UsersActions,
) {
    val ready = state as? UsersUiState.Ready
    val user = ready?.users?.firstOrNull { it.id == userId }
    UserScreenEffects(ready, userId, user, actions)
    val host = remember { SnackbarHostState() }
    BingeScreenScaffold(
        title = stringResource(section.title()),
        bar = ScreenBar.Small,
        onBack = actions.onBack,
        snackbarHostState = host,
    ) { padding ->
        ScreenBody(padding) { _ ->
            val inner = padding.screenListPadding()
            when {
                state is UsersUiState.Loading -> ConsoleLoading(inner, LOADING_ROWS)
                state is UsersUiState.Failed -> ConsoleFailureScreen(state.failure, actions.onRetry, Modifier.padding(inner))
                ready != null && user != null -> PolicySection(section, ready, user, choices, profile, actions, inner)
            }
        }
    }
    ready?.let { RefusedMessage(it.actionFailed, host, R.string.users_not_changed) }
}

/** The view model acts on the user the screens show, so a user's screen says which; one the server no longer lists closes. */
@Composable
private fun UserScreenEffects(
    ready: UsersUiState.Ready?,
    userId: String,
    user: ServerUser?,
    actions: UsersActions,
) {
    LaunchedEffect(userId, ready != null, ready?.open) { if (ready != null && ready.open != userId) actions.onSelect(userId) }
    val gone = ready != null && user == null
    LaunchedEffect(gone) { if (gone) actions.onBack() }
}

/** Why the open form (a new user, a password) was refused, as the form says it. */
@Composable
private fun UsersUiState.Ready.formError(): String? = formFailure?.let { stringResource(R.string.users_not_changed, it.text()) }

/** What fills a landscape tablet's third pane while no user is open. */
@Composable
internal fun UserPlaceholder() {
    EmptyScreen(
        message = stringResource(R.string.users_select),
        title = stringResource(R.string.users_select_title),
        icon = Icons.Outlined.Person,
    )
}

@Composable
private fun UserList(
    state: UsersUiState.Ready,
    actions: UsersActions,
    inner: PaddingValues,
    now: Long,
    avatar: @Composable (ServerUser) -> Unit,
    openUser: String?,
    listState: LazyListState,
) {
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = inner,
        verticalArrangement = Arrangement.spacedBy(dimensionResource(DesR.dimen.padding_l)),
    ) {
        item { PageIntro(stringResource(R.string.users_body)) }
        item { ItemGroup(title = null, rows = state.users.map { it.row(state.me, it.id == openUser, now, actions, avatar) }) }
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
        selected = open,
        onClick = { actions.onOpen(this) },
    )
}

@Composable
private fun UserPage(
    state: UsersUiState.Ready,
    user: ServerUser,
    actions: UsersActions,
    inner: PaddingValues,
    listState: LazyListState,
) {
    val mine = user.id == state.me
    LazyColumn(
        state = listState,
        contentPadding = inner,
        verticalArrangement = Arrangement.spacedBy(dimensionResource(DesR.dimen.padding_l)),
    ) {
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
        item { ItemGroup(title = stringResource(R.string.users_settings), rows = sectionRows(user, actions, mine)) }
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
