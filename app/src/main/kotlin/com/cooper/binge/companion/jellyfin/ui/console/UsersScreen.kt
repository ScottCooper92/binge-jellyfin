package com.cooper.binge.companion.jellyfin.ui.console

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import com.binge.designsystem.component.BingeConfirmDialog
import com.binge.designsystem.component.BingeLoadingIndicator
import com.binge.designsystem.component.BingeOutlinedButton
import com.binge.designsystem.component.ItemGroup
import com.binge.designsystem.component.ListItem
import com.binge.designsystem.component.ListItemNote
import com.binge.designsystem.formatRelativeOrAbsolute
import com.binge.designsystem.template.BingeScreenScaffold
import com.binge.designsystem.template.ScreenBar
import com.binge.designsystem.template.ScreenBody
import com.binge.designsystem.template.screenListPadding
import com.cooper.binge.companion.jellyfin.R
import com.cooper.binge.companion.jellyfin.server.ServerUser
import com.binge.designsystem.R as DesR

/** What the users page can ask of its view model, and of the console. */
data class UsersActions(
    val onOpen: (ServerUser?) -> Unit = {},
    val onShow: (UserDialog?) -> Unit = {},
    val onAdministrator: (Boolean) -> Unit = {},
    val onCanSignIn: (Boolean) -> Unit = {},
    val onAllLibraries: (Boolean) -> Unit = {},
    val onLibrary: (String, Boolean) -> Unit = { _, _ -> },
    val onCreate: (String, String) -> Unit = { _, _ -> },
    val onSetPassword: (String) -> Unit = {},
    val onDelete: () -> Unit = {},
    val onRetry: () -> Unit = {},
    val onBack: () -> Unit = {},
)

/** Lines the last change's message takes, so it never moves what's above it. */
private const val STATUS_LINES = 2

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
) {
    val user = (state as? UsersUiState.Ready)?.openUser
    BackHandler(enabled = user != null) { actions.onOpen(null) }
    BingeScreenScaffold(
        title = user?.name ?: stringResource(R.string.users_title),
        bar = ScreenBar.Small,
        onBack = if (user != null) ({ actions.onOpen(null) }) else actions.onBack,
    ) { padding ->
        ScreenBody(padding) { _ ->
            val inner = padding.screenListPadding()
            when (state) {
                UsersUiState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { BingeLoadingIndicator() }
                is UsersUiState.Failed -> ConsoleFailed(state.failure, actions.onRetry, inner)
                is UsersUiState.Ready -> if (user == null) UserList(state, actions, inner, now) else UserPage(state, user, actions, inner)
            }
        }
    }
    (state as? UsersUiState.Ready)?.let { UserDialogs(it, actions) }
}

@Composable
private fun UserList(
    state: UsersUiState.Ready,
    actions: UsersActions,
    inner: PaddingValues,
    now: Long,
) {
    LazyColumn(contentPadding = inner, verticalArrangement = Arrangement.spacedBy(dimensionResource(DesR.dimen.padding_l))) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(dimensionResource(DesR.dimen.padding_m))) {
                Text(stringResource(R.string.users_body), style = MaterialTheme.typography.bodyMedium)
                BingeOutlinedButton(
                    label = stringResource(R.string.users_add),
                    onClick = { actions.onShow(UserDialog.Create) },
                    leadingIcon = Icons.Outlined.PersonAdd,
                    loading = state.busy,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        item { ItemGroup(title = null, rows = state.users.map { it.row(state.me, now, actions) }) }
        item { Status(state) }
    }
}

@Composable
private fun ServerUser.row(
    me: String,
    now: Long,
    actions: UsersActions,
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
        label = if (id == me) stringResource(R.string.users_you, name) else name,
        detail = listOfNotNull(role, seen).joinToString(" · ").ifBlank { stringResource(R.string.users_never_seen) },
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
        item { Status(state) }
        if (!mine) {
            item {
                BingeOutlinedButton(
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
            onClick = { actions.onAllLibraries(!user.allLibraries) },
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
                    onClick = { actions.onLibrary(library.id, !on) },
                )
            }
        }
    ItemGroup(title = stringResource(R.string.users_libraries), rows = listOf(all) + each)
}

/** What the last change did, or why it didn't go through. */
@Composable
private fun Status(state: UsersUiState.Ready) {
    val failed = state.actionFailed
    Text(
        text =
            when {
                failed != null -> stringResource(R.string.users_not_changed, stringResource(failed.message))
                state.done != null -> stringResource(state.done.message)
                else -> ""
            },
        style = MaterialTheme.typography.bodyMedium,
        color = if (failed != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
        minLines = STATUS_LINES,
        maxLines = STATUS_LINES,
    )
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
    when (state.dialog) {
        UserDialog.Create -> CreateDialog(actions)
        UserDialog.Password -> if (user != null) PasswordDialog(user, actions)
        UserDialog.Delete ->
            if (user != null) {
                BingeConfirmDialog(
                    title = stringResource(R.string.users_delete_title, user.name),
                    message = stringResource(R.string.users_delete_body),
                    confirmLabel = stringResource(R.string.users_delete),
                    onConfirm = actions.onDelete,
                    onDismissRequest = { actions.onShow(null) },
                    destructive = true,
                )
            }
        null -> Unit
    }
}

@Composable
private fun CreateDialog(actions: UsersActions) {
    var name by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    BingeConfirmDialog(
        title = stringResource(R.string.users_add),
        message = stringResource(R.string.users_add_body),
        confirmLabel = stringResource(R.string.users_add_action),
        onConfirm = { actions.onCreate(name, password) },
        onDismissRequest = { actions.onShow(null) },
        extraContent = {
            Column(verticalArrangement = Arrangement.spacedBy(dimensionResource(DesR.dimen.padding_s))) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.setup_username)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                PasswordField(password, { password = it }, R.string.setup_password)
            }
        },
    )
}

@Composable
private fun PasswordDialog(
    user: ServerUser,
    actions: UsersActions,
) {
    var password by rememberSaveable { mutableStateOf("") }
    BingeConfirmDialog(
        title = stringResource(R.string.users_password_title, user.name),
        message = stringResource(R.string.users_password_body),
        confirmLabel = stringResource(R.string.users_password_action),
        onConfirm = { actions.onSetPassword(password) },
        onDismissRequest = { actions.onShow(null) },
        extraContent = { PasswordField(password, { password = it }, R.string.password_new) },
    )
}

@Composable
private fun PasswordField(
    value: String,
    onValueChange: (String) -> Unit,
    label: Int,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(stringResource(label)) },
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        modifier = Modifier.fillMaxWidth(),
    )
}
