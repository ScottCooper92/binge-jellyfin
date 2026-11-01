package com.cooper.binge.companion.jellyfin.ui.console

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.FolderOff
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.binge.designsystem.component.BingeFilledButton
import com.binge.designsystem.component.BingeLoadingIndicator
import com.binge.designsystem.component.BingeOutlinedButton
import com.binge.designsystem.component.BingeTextButton
import com.binge.designsystem.component.ItemGroup
import com.binge.designsystem.component.ListItem
import com.binge.designsystem.component.ListItemDestination
import com.cooper.binge.companion.jellyfin.R
import com.cooper.binge.companion.jellyfin.server.ServerFolder
import com.binge.designsystem.R as DesR

/** What choosing a folder on the server can ask of its view model. */
data class FolderActions(
    val onEnter: (ServerFolder) -> Unit = {},
    val onUp: () -> Unit = {},
    val onRetry: () -> Unit = {},
)

/** How tall the folder list grows before it scrolls, so the buttons under it stay in reach. */
private val FolderListHeight = 320.dp

/**
 * Choosing a folder on the server, as jellyfin-web does: drives first, then the folders in each. The server's
 * own file system is browsed because a library's folder is a path on the server, not on this device. [onChoose]
 * takes the folder the user is in; [onType] is for a path that isn't offered. It is the content of a sheet.
 */
@Composable
internal fun FolderPicker(
    state: FolderBrowseState,
    actions: FolderActions,
    busy: Boolean,
    onChoose: (String) -> Unit,
    onType: () -> Unit,
    onCancel: () -> Unit,
    failed: ConsoleFailure? = null,
) {
    val current = (state as? FolderBrowseState.Ready)?.current
    Column(
        modifier = Modifier.fillMaxWidth().padding(dimensionResource(DesR.dimen.padding_m)),
        verticalArrangement = Arrangement.spacedBy(dimensionResource(DesR.dimen.padding_m)),
    ) {
        Text(stringResource(R.string.library_folder_title), style = MaterialTheme.typography.titleLarge)
        Text(
            text = current?.path ?: stringResource(R.string.library_folder_roots),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        when (state) {
            FolderBrowseState.Loading ->
                Box(Modifier.fillMaxWidth().heightIn(min = FolderListHeight), contentAlignment = Alignment.Center) {
                    BingeLoadingIndicator()
                }
            is FolderBrowseState.Failed ->
                Column(verticalArrangement = Arrangement.spacedBy(dimensionResource(DesR.dimen.padding_m))) {
                    Text(stringResource(state.failure.message), style = MaterialTheme.typography.bodyLarge)
                    BingeOutlinedButton(label = stringResource(R.string.console_retry), onClick = actions.onRetry)
                }
            is FolderBrowseState.Ready -> FolderList(state, actions)
        }
        failed?.let {
            Text(
                text = stringResource(R.string.settings_not_saved, stringResource(it.message)),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
        }
        BingeFilledButton(
            label = stringResource(R.string.library_folder_choose),
            onClick = { current?.let { onChoose(it.path) } },
            leadingIcon = Icons.Outlined.Check,
            enabled = current != null && !busy,
            loading = busy,
            modifier = Modifier.fillMaxWidth(),
        )
        BingeOutlinedButton(
            label = stringResource(R.string.library_folder_type),
            onClick = onType,
            leadingIcon = Icons.Outlined.Edit,
            enabled = !busy,
            modifier = Modifier.fillMaxWidth(),
        )
        BingeTextButton(label = stringResource(DesR.string.text_entry_cancel), onClick = onCancel, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun FolderList(
    state: FolderBrowseState.Ready,
    actions: FolderActions,
) {
    val rows =
        listOfNotNull(
            ListItem(
                icon = Icons.Outlined.ArrowUpward,
                label = stringResource(R.string.library_folder_up),
                destination = ListItemDestination.Action,
                onClick = actions.onUp,
            ).takeIf { state.trail.isNotEmpty() },
        ) +
            state.folders
                .map { folder ->
                    ListItem(
                        icon = if (state.trail.isEmpty()) Icons.Outlined.Storage else Icons.Outlined.Folder,
                        label = folder.name,
                        onClick = { actions.onEnter(folder) },
                    )
                }.ifEmpty {
                    listOf(
                        ListItem(icon = Icons.Outlined.FolderOff, label = stringResource(R.string.library_folder_empty), clickable = false),
                    )
                }
    Column(Modifier.heightIn(max = FolderListHeight).verticalScroll(rememberScrollState())) {
        ItemGroup(title = null, rows = rows)
    }
}
