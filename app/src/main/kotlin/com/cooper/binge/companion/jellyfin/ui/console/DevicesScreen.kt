package com.cooper.binge.companion.jellyfin.ui.console

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Devices
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.Tv
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import com.binge.designsystem.component.BingeConfirmDialog
import com.binge.designsystem.component.BingeLoadingIndicator
import com.binge.designsystem.component.ExpressiveIconButton
import com.binge.designsystem.component.ItemGroup
import com.binge.designsystem.component.ListItem
import com.binge.designsystem.component.ListItemNote
import com.binge.designsystem.formatRelativeOrAbsolute
import com.binge.designsystem.template.BingeScreenScaffold
import com.binge.designsystem.template.ScreenBar
import com.binge.designsystem.template.ScreenBody
import com.binge.designsystem.template.screenListPadding
import com.cooper.binge.companion.jellyfin.R
import com.cooper.binge.companion.jellyfin.server.ServerDevice
import com.binge.designsystem.R as DesR

/** What the devices page can ask of its view model, and of the console. */
data class DevicesActions(
    val onAskRename: (ServerDevice?) -> Unit = {},
    val onRename: (String) -> Unit = {},
    val onAskRemove: (ServerDevice?) -> Unit = {},
    val onRemove: () -> Unit = {},
    val onRetry: () -> Unit = {},
    val onBack: () -> Unit = {},
)

/** Lines a device's detail may take: who, which app, and when it was last seen. */
private const val DETAIL_LINES = 3

/** Lines a failed change's message takes, so it never moves the list. */
private const val STATUS_LINES = 2

/**
 * The devices signed in to the server, most recently seen first: who used each last, with which app,
 * and when. A tap renames one; Remove, at the row's end and red, signs one out after a confirm. This
 * app's own device has no Remove, and says why.
 */
@Composable
fun DevicesScreen(
    state: DevicesUiState,
    actions: DevicesActions,
    now: Long = System.currentTimeMillis(),
) {
    BingeScreenScaffold(title = stringResource(R.string.devices_title), bar = ScreenBar.Small, onBack = actions.onBack) { padding ->
        ScreenBody(padding) { _ ->
            val inner = padding.screenListPadding()
            when (state) {
                DevicesUiState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { BingeLoadingIndicator() }
                is DevicesUiState.Failed -> ConsoleFailed(state.failure, actions.onRetry, inner)
                is DevicesUiState.Ready -> Devices(state, actions, inner, now)
            }
        }
    }
}

@Composable
private fun Devices(
    state: DevicesUiState.Ready,
    actions: DevicesActions,
    inner: PaddingValues,
    now: Long,
) {
    LazyColumn(contentPadding = inner, verticalArrangement = Arrangement.spacedBy(dimensionResource(DesR.dimen.padding_l))) {
        item { Text(stringResource(R.string.devices_body), style = MaterialTheme.typography.bodyMedium) }
        item { ItemGroup(title = null, rows = state.devices.map { it.row(state, actions, now) }) }
        item {
            Text(
                text = state.actionFailed?.let { stringResource(R.string.devices_not_changed, stringResource(it.message)) }.orEmpty(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                minLines = STATUS_LINES,
                maxLines = STATUS_LINES,
            )
        }
    }
    state.renaming?.let { RenameDialog(it, actions) }
    state.removing?.let { device ->
        BingeConfirmDialog(
            title = stringResource(R.string.devices_remove_title, device.displayName),
            message = stringResource(R.string.devices_remove_body),
            confirmLabel = stringResource(R.string.devices_remove),
            onConfirm = actions.onRemove,
            onDismissRequest = { actions.onAskRemove(null) },
            destructive = true,
        )
    }
}

@Composable
private fun ServerDevice.row(
    state: DevicesUiState.Ready,
    actions: DevicesActions,
    now: Long,
): ListItem {
    val mine = id == state.thisDevice
    val seen = formatRelativeOrAbsolute(lastSeenMillis, now)?.let { stringResource(R.string.devices_seen, it) }
    return ListItem(
        icon = deviceIcon(app, displayName),
        label = displayName.ifBlank { stringResource(R.string.devices_unnamed) },
        detail = listOfNotNull(user.ifBlank { null }, app.ifBlank { null }, seen).joinToString(" · "),
        // Who, which app and when: three short facts, so at large fonts the time still fits.
        supportingMaxLines = DETAIL_LINES,
        note = if (mine) ListItemNote(stringResource(R.string.devices_this_one), icon = Icons.Outlined.Info) else null,
        loading = id in state.busy,
        onClick = { actions.onAskRename(this) },
        trailingContent =
            if (mine || id in state.busy) {
                null
            } else {
                {
                    ExpressiveIconButton(
                        onClick = { actions.onAskRemove(this) },
                        icon = Icons.Outlined.Delete,
                        contentDescription = stringResource(R.string.devices_remove),
                        tint = MaterialTheme.colorScheme.error,
                    )
                }
            },
    )
}

/** A name for the device; left empty, it goes back to the name the device gives itself. */
@Composable
private fun RenameDialog(
    device: ServerDevice,
    actions: DevicesActions,
) {
    var name by rememberSaveable(device.id) { mutableStateOf(device.customName.orEmpty()) }
    BingeConfirmDialog(
        title = stringResource(R.string.devices_rename_title),
        message = stringResource(R.string.devices_rename_body),
        confirmLabel = stringResource(R.string.devices_rename_save),
        onConfirm = { actions.onRename(name) },
        onDismissRequest = { actions.onAskRename(null) },
        extraContent = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(R.string.devices_rename_field)) },
                placeholder = { Text(device.name) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { actions.onRename(name) }),
                modifier = Modifier.fillMaxWidth(),
            )
        },
    )
}

/** A device's icon, from its app's or its own name: a TV, a browser, a phone, or devices in general. */
internal fun deviceIcon(
    app: String,
    name: String,
): ImageVector {
    val both = "$app $name".lowercase()
    return when {
        listOf("tv", "shield", "streamer", "roku", "fire tv", "firetv", "tizen", "webos").any { it in both } -> Icons.Outlined.Tv
        listOf("web", "chrome", "firefox", "safari", "edge").any { it in both } -> Icons.Outlined.Language
        listOf("android", "iphone", "ios", "pixel", "phone", "fold", "galaxy").any { it in both } -> Icons.Outlined.PhoneAndroid
        else -> Icons.Outlined.Devices
    }
}
