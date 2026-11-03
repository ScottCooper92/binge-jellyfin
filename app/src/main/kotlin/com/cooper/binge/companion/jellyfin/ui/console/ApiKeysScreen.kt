package com.cooper.binge.companion.jellyfin.ui.console

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import com.binge.designsystem.component.BingePullToRefresh
import com.binge.designsystem.component.BingeTextButton
import com.binge.designsystem.component.ExpressiveIconButton
import com.binge.designsystem.component.ItemGroup
import com.binge.designsystem.component.ListItem
import com.binge.designsystem.component.PageIntro
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
    val onRefresh: () -> Unit = {},
    val onBack: () -> Unit = {},
)

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
    val ready = state as? ApiKeysUiState.Ready
    val host = remember { SnackbarHostState() }
    BingeScreenScaffold(
        title = stringResource(R.string.api_keys_title),
        bar = ScreenBar.Small,
        onBack = actions.onBack,
        snackbarHostState = host,
        bottomBar = {
            CreateFooter(
                label = stringResource(R.string.api_keys_new),
                icon = Icons.Outlined.Key,
                onClick = { actions.onAskCreate(true) },
                visible = state !is ApiKeysUiState.Failed,
                enabled = ready != null,
                loading = ready?.busy == true,
            )
        },
    ) { padding ->
        ScreenBody(padding) { _ ->
            val inner = padding.screenListPadding()
            when (state) {
                ApiKeysUiState.Loading -> ConsoleLoading(inner, LOADING_ROWS)
                is ApiKeysUiState.Failed -> ConsoleFailureScreen(state.failure, actions.onRetry, Modifier.padding(inner))
                is ApiKeysUiState.Ready -> {
                    BingePullToRefresh(
                        isRefreshing = state.refreshing,
                        onRefresh = actions.onRefresh,
                        indicatorTopInset = padding.calculateTopPadding(),
                    ) { Keys(state, actions, inner, now) }
                    RefusedMessage(state.actionFailed, host, R.string.api_keys_not_changed)
                    RefusedMessage(state.refreshFailed, host, R.string.console_not_refreshed)
                }
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
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = inner,
        verticalArrangement = Arrangement.spacedBy(dimensionResource(DesR.dimen.padding_l)),
    ) {
        item { PageIntro(stringResource(R.string.api_keys_body)) }
        if (state.keys.isEmpty()) {
            item { ConsoleEmptyScreen(stringResource(R.string.api_keys_empty), Icons.Outlined.Key) }
        } else {
            item { ItemGroup(title = null, rows = state.keys.map { it.row(state, actions, now) }) }
        }
    }
    if (state.naming) {
        NewKeySheet(
            actions,
            submitting = state.busy,
            error =
                state.formFailure?.let {
                    stringResource(R.string.api_keys_not_changed, it.text())
                },
        )
    }
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
        detail = listOfNotNull(stringResource(R.string.api_keys_ends_in, lastFour), made).joinToString("\n"),
        // Two lines, either of which wraps at a large font: the key's ending, then the date.
        supportingMaxLines = 4,
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

/** The app a new key is for. Its Create key saves too. [inWindow] false draws the form for a frame. */
@Composable
internal fun NewKeySheet(
    actions: ApiKeysActions,
    submitting: Boolean = false,
    error: String? = null,
    inWindow: Boolean = true,
) {
    var name by rememberSaveable { mutableStateOf("") }
    ConsoleSheet(onDismissRequest = { actions.onAskCreate(false) }, inWindow = inWindow) {
        ConsoleTextEntry(
            title = stringResource(R.string.api_keys_new_title),
            body = stringResource(R.string.api_keys_new_body),
            value = name,
            onValueChange = { name = it },
            submitLabel = stringResource(R.string.api_keys_create),
            onSubmit = { actions.onCreate(name) },
            onCancel = { actions.onAskCreate(false) },
            hint = stringResource(R.string.api_keys_app_field),
            error = error,
            isSubmitting = submitting,
        )
    }
}

/**
 * The key just made, in full, the one time it is shown. Copy is the only way it leaves the screen. Only its button closes it, not
 * Back or a tap outside: the list keeps the last four characters, so a stray tap would lose the key and the key would have to be
 * revoked and made again.
 */
@Composable
internal fun CreatedKeyDialog(
    key: ApiKey,
    actions: ApiKeysActions,
    inWindow: Boolean = true,
) {
    ConsoleMessage(
        title = stringResource(R.string.api_keys_created_title, key.app),
        message = stringResource(R.string.api_keys_created_body),
        onDismiss = actions.onDismissCreated,
        inWindow = inWindow,
        confirmLabel = stringResource(R.string.api_keys_copied),
        dismissible = false,
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
