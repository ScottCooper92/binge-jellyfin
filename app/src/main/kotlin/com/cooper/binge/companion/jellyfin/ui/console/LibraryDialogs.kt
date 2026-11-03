package com.cooper.binge.companion.jellyfin.ui.console

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import com.binge.designsystem.component.BingeAdaptiveSheet
import com.binge.designsystem.component.BingeSheetKind
import com.binge.designsystem.component.TextEntrySurface
import com.cooper.binge.companion.jellyfin.R
import com.cooper.binge.companion.jellyfin.server.LIBRARY_DELIMITER_WHITELIST
import com.cooper.binge.companion.jellyfin.server.LIBRARY_SEASON_ZERO
import com.cooper.binge.companion.jellyfin.server.LIBRARY_TAG_DELIMITERS
import com.cooper.binge.companion.jellyfin.server.delimiterList
import com.cooper.binge.companion.jellyfin.server.delimiterWhitelist
import com.cooper.binge.companion.jellyfin.server.string
import com.cooper.binge.companion.jellyfin.server.tagDelimiters
import com.cooper.binge.companion.jellyfin.server.whitelistList

private const val RENAME_NAME = "RenameForm.name"
private const val SEASON_ZERO_NAME = "SeasonZeroForm.name"
private const val TAG_DELIMITERS_TYPED = "TagDelimitersForm.typed"
private const val DELIMITER_WHITELIST_TYPED = "DelimiterWhitelistForm.typed"
private const val ADD_FOLDER_TYPING = "AddFolderForm.typing"
private const val ADD_FOLDER_PATH = "AddFolderForm.path"

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
        LibraryDialog.Rename -> {
            // The drafts are held here, above the sheet's window, so they survive the activity being recreated.
            val name = rememberDraft(RENAME_NAME, state.library.name)
            BingeAdaptiveSheet(kind = BingeSheetKind.Task, onDismissRequest = close) {
                RenameForm(state.library.name, state.busy, state.nameTaken, actions, nameDraft = name)
            }
        }
        LibraryDialog.SeasonZero -> {
            val current = state.options.string(LIBRARY_SEASON_ZERO).orEmpty()
            val name = rememberDraft(SEASON_ZERO_NAME, current)
            BingeAdaptiveSheet(kind = BingeSheetKind.Task, onDismissRequest = close) {
                SeasonZeroForm(current, actions, nameDraft = name)
            }
        }
        LibraryDialog.TagDelimiters -> {
            val current = state.options.tagDelimiters()
            val typed = rememberDraft(TAG_DELIMITERS_TYPED, current)
            BingeAdaptiveSheet(kind = BingeSheetKind.Task, onDismissRequest = close) {
                TagDelimitersForm(current, actions, typedDraft = typed)
            }
        }
        LibraryDialog.DelimiterWhitelist -> {
            val current = state.options.delimiterWhitelist().joinToString("\n")
            val typed = rememberDraft(DELIMITER_WHITELIST_TYPED, current)
            BingeAdaptiveSheet(kind = BingeSheetKind.Task, onDismissRequest = close) {
                DelimiterWhitelistForm(current, actions, typedDraft = typed)
            }
        }
        LibraryDialog.AddFolder -> {
            val typing = rememberDraft(ADD_FOLDER_TYPING, false)
            val path = rememberDraft(ADD_FOLDER_PATH, "")
            BingeAdaptiveSheet(kind = BingeSheetKind.Task, onDismissRequest = close) {
                AddFolderForm(state, folders, folderActions, actions, typingDraft = typing, pathDraft = path)
            }
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
    nameDraft: MutableState<String> = rememberDraft(RENAME_NAME, current),
) {
    var name by nameDraft
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
    nameDraft: MutableState<String> = rememberDraft(SEASON_ZERO_NAME, current),
) {
    var name by nameDraft
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

/** The characters a music library splits artist and genre tags at, typed one after another. Saves with its button, and closes. */
@Composable
internal fun TagDelimitersForm(
    current: String,
    actions: LibraryActions,
    typedDraft: MutableState<String> = rememberDraft(TAG_DELIMITERS_TYPED, current),
) {
    var typed by typedDraft
    TextEntrySurface(
        title = stringResource(R.string.library_delimiters),
        value = typed,
        onValueChange = { typed = it },
        onSubmit = {
            actions.onJson(LIBRARY_TAG_DELIMITERS, delimiterList(typed))
            actions.onAsk(null)
        },
        onCancel = { actions.onAsk(null) },
        submitLabel = stringResource(R.string.library_save),
        hint = stringResource(R.string.library_delimiters_hint),
        minLines = 1,
        maxLines = 1,
    )
}

/** The names a music library does not split its tags at, one on each line. Saves with its button, and closes. */
@Composable
internal fun DelimiterWhitelistForm(
    current: String,
    actions: LibraryActions,
    typedDraft: MutableState<String> = rememberDraft(DELIMITER_WHITELIST_TYPED, current),
) {
    var typed by typedDraft
    TextEntrySurface(
        title = stringResource(R.string.library_whitelist),
        value = typed,
        onValueChange = { typed = it },
        onSubmit = {
            actions.onJson(LIBRARY_DELIMITER_WHITELIST, whitelistList(typed))
            actions.onAsk(null)
        },
        onCancel = { actions.onAsk(null) },
        submitLabel = stringResource(R.string.library_save),
        hint = stringResource(R.string.library_whitelist_hint),
        minLines = WHITELIST_MIN_LINES,
        maxLines = WHITELIST_MAX_LINES,
    )
}

private const val WHITELIST_MIN_LINES = 3
private const val WHITELIST_MAX_LINES = 8

/** Adding a folder: chosen from the server's own, or typed, and checked on the server before it is added. */
@Composable
internal fun AddFolderForm(
    state: LibraryUiState.Ready,
    folders: FolderBrowseState,
    folderActions: FolderActions,
    actions: LibraryActions,
    startTyping: Boolean = false,
    typingDraft: MutableState<Boolean> = rememberDraft(ADD_FOLDER_TYPING, startTyping),
    pathDraft: MutableState<String> = rememberDraft(ADD_FOLDER_PATH, ""),
) {
    var typing by typingDraft
    var path by pathDraft
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
                    state.failed?.let { stringResource(R.string.settings_not_saved, it.text()) }
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
