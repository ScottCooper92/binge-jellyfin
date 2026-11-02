package com.cooper.binge.companion.jellyfin.ui.console

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Label
import androidx.compose.material.icons.outlined.Power
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
import com.binge.designsystem.component.BingeTextButton
import com.binge.designsystem.component.ExpressiveIconButton
import com.binge.designsystem.component.ItemGroup
import com.binge.designsystem.component.ListItem
import com.binge.designsystem.component.ListItemDestination
import com.binge.designsystem.component.ListRowSkeletonColumn
import com.binge.designsystem.template.FormScreen
import com.binge.designsystem.template.ScreenBar
import com.cooper.binge.companion.jellyfin.R
import com.cooper.binge.companion.jellyfin.server.JellyfinServerProfile
import com.cooper.binge.companion.jellyfin.server.LIBRARY_ENABLED
import com.cooper.binge.companion.jellyfin.server.ProviderGroup
import com.cooper.binge.companion.jellyfin.server.bool
import com.binge.designsystem.R as DesR

/** What a library's page can ask of its view model, and of the console. */
data class LibraryActions(
    val onFlag: (key: String, on: Boolean) -> Unit = { _, _ -> },
    val onText: (key: String, text: String) -> Unit = { _, _ -> },
    val onDays: (Int) -> Unit = {},
    val onPage: (LibraryPage) -> Unit = {},
    val onAsk: (LibraryDialog?) -> Unit = {},
    val onRename: (String) -> Unit = {},
    val onAddFolder: (String) -> Unit = {},
    val onRemoveFolder: (String) -> Unit = {},
    val onRemove: () -> Unit = {},
    val onProvider: (ProviderGroup, String, Boolean) -> Unit = { _, _, _ -> },
    val onMove: (ProviderGroup, String, Int) -> Unit = { _, _, _ -> },
    val onRetry: () -> Unit = {},
    val onRetrySave: () -> Unit = {},
    val onBack: () -> Unit = {},
)

/** Rows in the page's skeleton while it loads. */
private const val LOADING_ROWS = 8

/** Lines a switch's detail may wrap to, so it reads in full at large fonts. */
internal const val LIBRARY_DETAIL_LINES = 4

/**
 * One library's page, after jellyfin-web's library editor: its name and folders, then the options its kind
 * and this server have, then Remove library, last. Each option saves as it's made. Back goes from a provider
 * to its list, and from the list to the library; [profile] says which options this release has.
 */
@Composable
fun LibraryScreen(
    state: LibraryUiState,
    actions: LibraryActions,
    profile: JellyfinServerProfile,
    folders: FolderBrowseState = FolderBrowseState.Loading,
    folderActions: FolderActions = FolderActions(),
) {
    val ready = state as? LibraryUiState.Ready
    val page = ready?.page ?: LibraryPage.Main
    val back = if (page == LibraryPage.Main) actions.onBack else ({ actions.onPage(page.parent()) })
    BackHandler(onBack = back)
    val host = remember { SnackbarHostState() }
    FormScreen(
        title = page.title(ready),
        bar = ScreenBar.Small,
        onBack = back,
        snackbarHostState = host,
        saveStatus = ready?.save?.formStatus(actions.onRetrySave),
        notReady =
            when (state) {
                LibraryUiState.Loading -> { padding -> ListRowSkeletonColumn(contentPadding = padding, count = LOADING_ROWS) }
                is LibraryUiState.Failed -> { padding -> ConsoleFailureScreen(state.failure, actions.onRetry, Modifier.padding(padding)) }
                is LibraryUiState.Ready -> null
            },
    ) { _ ->
        if (ready != null) {
            when (page) {
                LibraryPage.Main -> LibraryBody(ready, actions, profile)
                is LibraryPage.Providers -> ProvidersPage(page.group, ready, actions)
                is LibraryPage.One -> ProviderPage(page, ready, actions)
            }
            LibraryDialogs(ready, actions, folders, folderActions)
            RefusedMessage(ready.refused, host)
            RefusedMessage(ready.failed, host)
        }
    }
}

private fun LibraryPage.parent(): LibraryPage =
    when (this) {
        LibraryPage.Main -> LibraryPage.Main
        is LibraryPage.Providers -> LibraryPage.Main
        is LibraryPage.One -> LibraryPage.Providers(group)
    }

@Composable
private fun LibraryPage.title(ready: LibraryUiState.Ready?): String =
    when (this) {
        LibraryPage.Main -> ready?.library?.name?.ifBlank { null } ?: stringResource(R.string.libraries_unnamed)
        is LibraryPage.Providers -> stringResource(group.title())
        is LibraryPage.One -> name
    }

@Composable
private fun LibraryBody(
    state: LibraryUiState.Ready,
    actions: LibraryActions,
    profile: JellyfinServerProfile,
) {
    var picker by rememberSaveable { mutableStateOf<LibraryPicker?>(null) }
    Column(verticalArrangement = Arrangement.spacedBy(dimensionResource(DesR.dimen.padding_l))) {
        ItemGroup(title = null, rows = libraryRows(state, actions, profile))
        FoldersGroup(state, actions)
        MetadataGroup(state, actions) { picker = it }
        VideoGroups(state, actions, profile)
        MusicGroups(state, actions, profile)
        SegmentsGroup(state, actions, profile)
        // Remove is last, red, and behind a confirm that opens on Cancel.
        Text(stringResource(R.string.library_remove_detail), style = MaterialTheme.typography.bodyMedium)
        BingeTextButton(
            label = stringResource(R.string.library_remove),
            onClick = { actions.onAsk(LibraryDialog.Remove) },
            leadingIcon = Icons.Outlined.Delete,
            enabled = !state.busy,
            destructive = true,
            modifier = Modifier.fillMaxWidth(),
        )
    }
    picker?.let { LibraryPickerSheet(it, state, actions) { picker = null } }
}

@Composable
private fun libraryRows(
    state: LibraryUiState.Ready,
    actions: LibraryActions,
    profile: JellyfinServerProfile,
): List<ListItem> {
    val enabled = state.options.bool(LIBRARY_ENABLED) ?: true
    return listOfNotNull(
        ListItem(
            icon = Icons.Outlined.Label,
            label = stringResource(R.string.library_name),
            detail = state.library.name,
            enabled = !state.busy,
            onClick = { actions.onAsk(LibraryDialog.Rename) },
        ),
        ListItem(
            icon = Icons.Outlined.Power,
            label = stringResource(R.string.library_enabled),
            detail = stringResource(R.string.library_enabled_detail),
            supportingMaxLines = LIBRARY_DETAIL_LINES,
            toggled = enabled,
            onClick = { actions.onFlag(LIBRARY_ENABLED, !enabled) },
        ).takeIf { profile.canDisableLibraries },
    )
}

@Composable
private fun FoldersGroup(
    state: LibraryUiState.Ready,
    actions: LibraryActions,
) {
    ItemGroup(
        title = stringResource(R.string.library_folders),
        rows =
            state.library.folders.map { path ->
                ListItem(
                    icon = Icons.Outlined.Folder,
                    label = path,
                    clickable = false,
                    trailingContent = {
                        ExpressiveIconButton(
                            onClick = { actions.onAsk(LibraryDialog.RemoveFolder(path)) },
                            icon = Icons.Outlined.Delete,
                            contentDescription = stringResource(R.string.library_folder_remove),
                            enabled = !state.busy,
                            tint = MaterialTheme.colorScheme.error,
                        )
                    },
                )
            } +
                ListItem(
                    icon = Icons.Outlined.Add,
                    label = stringResource(R.string.library_folder_add),
                    destination = ListItemDestination.Action,
                    enabled = !state.busy,
                    onClick = { actions.onAsk(LibraryDialog.AddFolder) },
                ),
    )
}
