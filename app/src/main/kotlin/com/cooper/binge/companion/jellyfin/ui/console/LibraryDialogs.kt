package com.cooper.binge.companion.jellyfin.ui.console

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import com.binge.designsystem.component.BingeAdaptiveSheet
import com.binge.designsystem.component.BingeSheetKind
import com.binge.designsystem.component.TextEntrySurface
import com.cooper.binge.companion.jellyfin.R
import com.cooper.binge.companion.jellyfin.server.LIBRARY_SEASON_ZERO
import com.cooper.binge.companion.jellyfin.server.string

/** Whatever is open over a library's page: a sheet to type in or pick from, or a confirm. */
@Composable
internal fun LibraryDialogs(
    state: LibraryUiState.Ready,
    actions: LibraryActions,
    folders: FolderBrowseState,
    folderActions: FolderActions,
) {
    val close = { actions.onAsk(null) }
    when (val dialog = state.dialog) {
        null -> Unit
        LibraryDialog.Rename ->
            BingeAdaptiveSheet(kind = BingeSheetKind.Task, onDismissRequest = close) {
                RenameForm(state.library.name, state.busy, state.nameTaken, actions)
            }
        LibraryDialog.SeasonZero ->
            BingeAdaptiveSheet(kind = BingeSheetKind.Task, onDismissRequest = close) {
                SeasonZeroForm(state.options.string(LIBRARY_SEASON_ZERO).orEmpty(), actions)
            }
        LibraryDialog.AddFolder ->
            BingeAdaptiveSheet(kind = BingeSheetKind.Task, onDismissRequest = close) {
                AddFolderForm(state, folders, folderActions, actions)
            }
        is LibraryDialog.RemoveFolder -> RemoveFolderDialog(dialog.path, actions)
        LibraryDialog.Remove -> RemoveLibraryDialog(state.library.name, actions)
    }
}

/** Renaming the library: a name no other library has. Draws without a window for a frame. */
@Composable
internal fun RenameForm(
    current: String,
    saving: Boolean,
    taken: Boolean,
    actions: LibraryActions,
) {
    var name by rememberSaveable { mutableStateOf(current) }
    TextEntrySurface(
        title = stringResource(R.string.library_rename_title),
        value = name,
        onValueChange = { name = it },
        onSubmit = { actions.onRename(name) },
        onCancel = { actions.onAsk(null) },
        submitLabel = stringResource(R.string.library_rename),
        submitEnabled = name.isNotBlank() && name.trim() != current,
        isSubmitting = saving,
        error = stringResource(R.string.library_name_taken).takeIf { taken },
        minLines = 1,
        maxLines = 1,
        maxLength = NAME_LENGTH,
    )
}

/** The name shows give the episodes of season 0. Saves with its button, and closes. */
@Composable
internal fun SeasonZeroForm(
    current: String,
    actions: LibraryActions,
) {
    var name by rememberSaveable { mutableStateOf(current) }
    TextEntrySurface(
        title = stringResource(R.string.library_season_zero),
        value = name,
        onValueChange = { name = it },
        onSubmit = {
            actions.onText(LIBRARY_SEASON_ZERO, name.trim())
            actions.onAsk(null)
        },
        onCancel = { actions.onAsk(null) },
        submitLabel = stringResource(R.string.library_save),
        hint = stringResource(R.string.library_season_zero_hint),
        minLines = 1,
        maxLines = 1,
        maxLength = NAME_LENGTH,
    )
}

/** Adding a folder: chosen from the server's own, or typed, and checked on the server before it is added. */
@Composable
internal fun AddFolderForm(
    state: LibraryUiState.Ready,
    folders: FolderBrowseState,
    folderActions: FolderActions,
    actions: LibraryActions,
    startTyping: Boolean = false,
) {
    var typing by rememberSaveable { mutableStateOf(startTyping) }
    var path by rememberSaveable { mutableStateOf("") }
    if (typing) {
        TextEntrySurface(
            title = stringResource(R.string.library_path_title),
            value = path,
            onValueChange = { path = it },
            onSubmit = { actions.onAddFolder(path) },
            onCancel = { typing = false },
            cancelLabel = stringResource(R.string.library_back),
            submitLabel = stringResource(R.string.library_folder_add),
            hint = stringResource(R.string.library_path_hint),
            isSubmitting = state.busy,
            error =
                if (state.folderMissing) {
                    stringResource(R.string.library_path_missing)
                } else {
                    state.failed?.let { stringResource(R.string.settings_not_saved, stringResource(it.message)) }
                },
            minLines = 1,
            maxLines = 2,
        )
    } else {
        FolderPicker(
            state = folders,
            actions = folderActions,
            busy = state.busy,
            onChoose = actions.onAddFolder,
            onType = { typing = true },
            onCancel = { actions.onAsk(null) },
            failed = state.failed,
        )
    }
}

/** Removing a folder from the library, after its path, as a confirm that opens on Cancel. [inWindow] false draws its body for a frame. */
@Composable
internal fun RemoveFolderDialog(
    path: String,
    actions: LibraryActions,
    inWindow: Boolean = true,
) {
    ConsoleConfirm(
        title = stringResource(R.string.library_folder_remove_title, path),
        message = stringResource(R.string.library_folder_remove_body),
        confirmLabel = stringResource(R.string.library_folder_remove),
        onConfirm = { actions.onRemoveFolder(path) },
        onDismissRequest = { actions.onAsk(null) },
        destructive = true,
        inWindow = inWindow,
    )
}

/** Removing the library, after its name, as a confirm that opens on Cancel. [inWindow] false draws its body for a frame. */
@Composable
internal fun RemoveLibraryDialog(
    name: String,
    actions: LibraryActions,
    inWindow: Boolean = true,
) {
    ConsoleConfirm(
        title = stringResource(R.string.library_remove_title, name),
        message = stringResource(R.string.library_remove_body),
        confirmLabel = stringResource(R.string.library_remove),
        onConfirm = actions.onRemove,
        onDismissRequest = { actions.onAsk(null) },
        destructive = true,
        inWindow = inWindow,
    )
}

private const val NAME_LENGTH = 60
