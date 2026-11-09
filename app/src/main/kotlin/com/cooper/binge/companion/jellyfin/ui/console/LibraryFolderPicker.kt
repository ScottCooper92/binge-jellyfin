package com.cooper.binge.companion.jellyfin.ui.console

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.binge.designsystem.component.BingeFilledButton
import com.binge.designsystem.component.BingeOutlinedButton
import com.binge.designsystem.component.BingeSheetDockingHeader
import com.binge.designsystem.component.BingeSheetTopBar
import com.binge.designsystem.component.BingeTextButton
import com.binge.designsystem.component.ItemGroup
import com.binge.designsystem.component.ListItem
import com.binge.designsystem.component.ListItemDestination
import com.binge.designsystem.component.LocalBingeSheetDock
import com.binge.designsystem.component.bingeSheetPinnedFooter
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
private const val FOLDER_SKELETON_ROWS = 5

/**
 * Choosing a folder on the server, as jellyfin-web does: drives first, then the folders in each. The server's
 * own file system is browsed because a library's folder is a path on the server, not on this device. [onChoose]
 * takes the folder the user is in; [onType] is for a path that isn't offered. It is the content of a sheet.
 *
 * In a docking sheet (one opened part-way and `dockable`) the list takes the height the sheet gives it, so a swipe up
 * on it grows the sheet before it scrolls the rows, and the header becomes a bar once the sheet is full. The buttons
 * stay on the bottom edge throughout. In any other sheet the list is capped, as it was, so the sheet stays its own height.
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
    // Only a docking sheet gives the list the height it has; anywhere else (a side sheet, a frame) the list keeps its cap.
    val docking = LocalBingeSheetDock.current != null
    val current = (state as? FolderBrowseState.Ready)?.current
    val title = stringResource(R.string.library_folder_title)
    val where = current?.path ?: stringResource(R.string.library_folder_roots)
    val gap = dimensionResource(DesR.dimen.padding_m)
    Column(modifier = if (docking) Modifier.fillMaxSize() else Modifier.fillMaxWidth()) {
        // The list's room: all that is left in a docking sheet, a fixed window in any other.
        val listModifier = if (docking) Modifier.fillMaxWidth().weight(1f) else Modifier.fillMaxWidth().heightIn(max = FolderListHeight)
        val loadingModifier = if (docking) listModifier else Modifier.fillMaxWidth().height(FolderListHeight)
        BingeSheetDockingHeader(
            header = {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = gap, vertical = gap),
                    verticalArrangement = Arrangement.spacedBy(gap),
                ) {
                    Text(title, style = MaterialTheme.typography.titleLarge)
                    Text(where, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            dockedTopBar = { onClose -> BingeSheetTopBar(title = title, subtitle = where, onClose = onClose) },
        )
        when (state) {
            // The shape of the list that is coming, rows the height of a folder's, so it does not jump when the folders arrive.
            FolderBrowseState.Loading ->
                ConsoleLoading(inner = PaddingValues(horizontal = gap), rows = FOLDER_SKELETON_ROWS, modifier = loadingModifier)
            is FolderBrowseState.Failed ->
                Column(
                    modifier = listModifier.padding(horizontal = gap),
                    verticalArrangement = Arrangement.spacedBy(gap),
                ) {
                    Text(state.failure.text(), style = MaterialTheme.typography.bodyLarge)
                    BingeOutlinedButton(label = stringResource(R.string.console_retry), onClick = actions.onRetry)
                }
            is FolderBrowseState.Ready -> FolderList(state, actions, listModifier.padding(horizontal = gap))
        }
        Column(
            modifier =
                Modifier
                    .bingeSheetPinnedFooter(LocalBingeSheetDock.current, MaterialTheme.colorScheme.surfaceContainerHigh)
                    .padding(gap),
            verticalArrangement = Arrangement.spacedBy(gap),
        ) {
            failed?.let {
                Text(
                    text = stringResource(R.string.settings_not_saved, it.text()),
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
}

@Composable
private fun FolderList(
    state: FolderBrowseState.Ready,
    actions: FolderActions,
    modifier: Modifier,
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
    // The list scrolls itself, and the sheet sees it: a swipe up grows a part-way sheet before the rows move.
    Column(modifier.verticalScroll(rememberScrollState())) {
        ItemGroup(title = null, rows = rows)
    }
}
