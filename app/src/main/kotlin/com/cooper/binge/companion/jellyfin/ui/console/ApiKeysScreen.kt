package com.cooper.binge.companion.jellyfin.ui.console

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import com.binge.designsystem.component.BingeOutlinedButton
import com.binge.designsystem.component.BingeTextButton
import com.binge.designsystem.component.ExpressiveIconButton
import com.binge.designsystem.component.ItemGroup
import com.binge.designsystem.component.ListItem
import com.binge.designsystem.formatRelativeOrAbsolute
import com.binge.designsystem.template.BingeScreenScaffold
import com.binge.designsystem.template.ScreenBar
import com.binge.designsystem.template.ScreenBody
import com.binge.designsystem.template.screenListPadding
import com.cooper.binge.companion.jellyfin.R
import com.cooper.binge.companion.jellyfin.server.ApiKey
import com.binge.designsystem.R as DesR

/** What the API keys page can ask of its view model, and of the console. */
data class ApiKeysActions(
    val onAskCreate: (Boolean) -> Unit = {},
    val onCreate: (String) -> Unit = {},
    /** Puts the key on the clipboard, marked sensitive. Called only from the person's tap on Copy. */
    val onCopy: (String) -> Unit = {},
    val onDismissCreated: () -> Unit = {},
    val onAskRemove: (ApiKey?) -> Unit = {},
    val onRemove: () -> Unit = {},
    val onRetry: () -> Unit = {},
    val onBack: () -> Unit = {},
)

/** Lines a failed change's message takes, so it never moves the list. */
private const val STATUS_LINES = 2

/**
 * The server's API keys, newest first, each by its app's name with only the last four characters of the
 * key and when it was made. New key asks for an app name, then shows the key once, with Copy. Revoke, at
 * a row's end and red, asks first, naming the app.
 */
@Composable
fun ApiKeysScreen(
    state: ApiKeysUiState,
    actions: ApiKeysActions,
    now: Long = System.currentTimeMillis(),
) {
    BingeScreenScaffold(title = stringResource(R.string.api_keys_title), bar = ScreenBar.Small, onBack = actions.onBack) { padding ->
        ScreenBody(padding) { _ ->
            val inner = padding.screenListPadding()
            when (state) {
                ApiKeysUiState.Loading -> ConsoleLoading(inner, LOADING_ROWS)
                is ApiKeysUiState.Failed -> ConsoleFailureScreen(state.failure, actions.onRetry, Modifier.padding(inner))
                is ApiKeysUiState.Ready -> Keys(state, actions, inner, now)
            }
        }
    }
}

@Composable
private fun Keys(
    state: ApiKeysUiState.Ready,
    actions: ApiKeysActions,
    inner: PaddingValues,
    now: Long,
) {
    LazyColumn(contentPadding = inner, verticalArrangement = Arrangement.spacedBy(dimensionResource(DesR.dimen.padding_l))) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(dimensionResource(DesR.dimen.padding_m))) {
                Text(stringResource(R.string.api_keys_body), style = MaterialTheme.typography.bodyMedium)
                BingeOutlinedButton(
                    label = stringResource(R.string.api_keys_new),
                    onClick = { actions.onAskCreate(true) },
                    leadingIcon = Icons.Outlined.Key,
                    loading = state.busy,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        if (state.keys.isEmpty()) {
            item { ConsoleEmptyScreen(stringResource(R.string.api_keys_empty), Icons.Outlined.Key) }
        } else {
            item { ItemGroup(title = null, rows = state.keys.map { it.row(state, actions, now) }) }
        }
        item {
            Text(
                text = state.actionFailed?.let { stringResource(R.string.api_keys_not_changed, stringResource(it.message)) }.orEmpty(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                minLines = STATUS_LINES,
                maxLines = STATUS_LINES,
            )
        }
    }
    if (state.naming) NewKeyDialog(actions)
    state.created?.let { CreatedKeyDialog(it, actions) }
    state.removing?.let { RevokeDialog(it, actions) }
}

@Composable
private fun ApiKey.row(
    state: ApiKeysUiState.Ready,
    actions: ApiKeysActions,
    now: Long,
): ListItem {
    val made = formatRelativeOrAbsolute(createdMillis, now)?.let { stringResource(R.string.api_keys_created, it) }
    return ListItem(
        icon = Icons.Outlined.Key,
        label = app.ifBlank { stringResource(R.string.api_keys_unnamed) },
        detail = listOfNotNull(stringResource(R.string.api_keys_ends_in, lastFour), made).joinToString(" · "),
        supportingMaxLines = 2,
        trailingContent =
            if (state.busy) {
                null
            } else {
                {
                    ExpressiveIconButton(
                        onClick = { actions.onAskRemove(this) },
                        icon = Icons.Outlined.Delete,
                        contentDescription = stringResource(R.string.api_keys_revoke),
                        tint = MaterialTheme.colorScheme.error,
                    )
                }
            },
    )
}

/** Revoking a key, after its app's name, as a confirm. [inWindow] false draws its body for a frame. */
@Composable
internal fun RevokeDialog(
    key: ApiKey,
    actions: ApiKeysActions,
    inWindow: Boolean = true,
) {
    ConsoleConfirm(
        title = stringResource(R.string.api_keys_revoke_title, key.app.ifBlank { stringResource(R.string.api_keys_unnamed) }),
        message = stringResource(R.string.api_keys_revoke_body),
        confirmLabel = stringResource(R.string.api_keys_revoke),
        onConfirm = actions.onRemove,
        onDismissRequest = { actions.onAskRemove(null) },
        destructive = true,
        inWindow = inWindow,
    )
}

/** The app a new key is for. Its Create key saves too. */
@Composable
internal fun NewKeyDialog(
    actions: ApiKeysActions,
    inWindow: Boolean = true,
) {
    var name by rememberSaveable { mutableStateOf("") }
    ConsoleConfirm(
        title = stringResource(R.string.api_keys_new_title),
        message = stringResource(R.string.api_keys_new_body),
        confirmLabel = stringResource(R.string.api_keys_create),
        onConfirm = { actions.onCreate(name) },
        onDismissRequest = { actions.onAskCreate(false) },
        inWindow = inWindow,
        extraContent = {
            ConsoleTextField(
                value = name,
                onValueChange = { name = it },
                label = stringResource(R.string.api_keys_app_field),
                onDone = { actions.onCreate(name) },
            )
        },
    )
}

/** The key just made, in full, the one time it is shown. Copy is the only way it leaves the screen. */
@Composable
internal fun CreatedKeyDialog(
    key: ApiKey,
    actions: ApiKeysActions,
    inWindow: Boolean = true,
) {
    ConsoleConfirm(
        title = stringResource(R.string.api_keys_created_title, key.app),
        message = stringResource(R.string.api_keys_created_body),
        confirmLabel = stringResource(R.string.api_keys_done),
        onConfirm = actions.onDismissCreated,
        onDismissRequest = actions.onDismissCreated,
        inWindow = inWindow,
        extraContent = {
            Column(verticalArrangement = Arrangement.spacedBy(dimensionResource(DesR.dimen.padding_s))) {
                Text(key.key, style = MaterialTheme.typography.bodyMedium, fontFamily = FontFamily.Monospace)
                BingeTextButton(
                    label = stringResource(R.string.api_keys_copy),
                    leadingIcon = Icons.Outlined.ContentCopy,
                    onClick = { actions.onCopy(key.key) },
                )
            }
        },
    )
}

private const val LOADING_ROWS = 5
