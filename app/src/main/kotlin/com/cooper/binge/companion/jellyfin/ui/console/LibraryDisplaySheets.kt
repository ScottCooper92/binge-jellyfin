package com.cooper.binge.companion.jellyfin.ui.console

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.ListAlt
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import com.binge.designsystem.component.BingeAdaptiveSheet
import com.binge.designsystem.component.BingeSheetKind
import com.binge.designsystem.component.ExpressiveIconButton
import com.binge.designsystem.component.ItemGroup
import com.binge.designsystem.component.ListItem
import com.binge.designsystem.component.ListItemDestination
import com.binge.designsystem.component.TextEntrySurface
import com.cooper.binge.companion.jellyfin.R
import com.cooper.binge.companion.jellyfin.server.DisplayList
import com.cooper.binge.companion.jellyfin.server.ListEntry
import com.cooper.binge.companion.jellyfin.server.METADATA_DUMMY_CHAPTER
import com.cooper.binge.companion.jellyfin.server.METADATA_PATH
import com.cooper.binge.companion.jellyfin.server.int
import com.cooper.binge.companion.jellyfin.server.string
import com.binge.designsystem.R as DesR

/** Whichever sheet is open over the page. */
@Composable
internal fun DisplaySheets(
    state: LibraryDisplayUiState.Ready,
    actions: LibraryDisplayActions,
    folders: FolderBrowseState,
    folderActions: FolderActions,
) {
    val sheet = state.sheet ?: return
    BingeAdaptiveSheet(kind = BingeSheetKind.Task, onDismissRequest = { actions.onSheet(null) }) {
        when (sheet) {
            DisplaySheet.ChapterDuration -> ChapterDurationForm(state.configuration.int(METADATA_DUMMY_CHAPTER) ?: 0, actions)
            DisplaySheet.MetadataPath ->
                MetadataPathForm(
                    state.configuration.string(METADATA_PATH).orEmpty(),
                    folders,
                    folderActions,
                    actions,
                )
            DisplaySheet.NewEntry -> state.editing?.let { NewEntryForm(it, actions) }
        }
    }
}

/** The seconds a dummy chapter lasts: whole seconds, 0 for none. Saves with its button, and closes. Draws without a window for a frame. */
@Composable
internal fun ChapterDurationForm(
    current: Int,
    actions: LibraryDisplayActions,
) {
    var text by rememberSaveable { mutableStateOf(current.toString()) }
    val seconds = parseChapterSeconds(text)
    TextEntrySurface(
        title = stringResource(R.string.display_dummy_chapters),
        value = text,
        onValueChange = { text = it },
        onSubmit = {
            seconds?.let(actions.onSeconds)
            actions.onSheet(null)
        },
        onCancel = { actions.onSheet(null) },
        submitLabel = stringResource(R.string.library_save),
        hint = stringResource(R.string.display_dummy_chapters_hint),
        submitEnabled = seconds != null,
        error =
            stringResource(R.string.display_dummy_chapters_invalid, MAX_DUMMY_CHAPTER_SECONDS).takeIf {
                text.isNotBlank() &&
                    seconds == null
            },
        minLines = 1,
        maxLines = 1,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
    )
}

/**
 * Where the server keeps its metadata: a folder chosen from the server's own, or typed; blank for the default.
 * Saves as it's chosen, and closes.
 */
@Composable
internal fun MetadataPathForm(
    current: String,
    folders: FolderBrowseState,
    folderActions: FolderActions,
    actions: LibraryDisplayActions,
    startTyping: Boolean = false,
) {
    var typing by rememberSaveable { mutableStateOf(startTyping) }
    var path by rememberSaveable { mutableStateOf(current) }
    val choose = { value: String ->
        actions.onText(METADATA_PATH, value.trim())
        actions.onSheet(null)
    }
    if (typing) {
        TextEntrySurface(
            title = stringResource(R.string.display_path),
            value = path,
            onValueChange = { path = it },
            onSubmit = { choose(path) },
            onCancel = { typing = false },
            cancelLabel = stringResource(R.string.library_back),
            submitLabel = stringResource(R.string.library_save),
            hint = stringResource(R.string.display_path_hint),
            submitEnabled = true,
            minLines = 1,
            maxLines = 2,
        )
    } else {
        FolderPicker(
            state = folders,
            actions = folderActions,
            busy = false,
            onChoose = choose,
            onType = { typing = true },
            onCancel = { actions.onSheet(null) },
        )
    }
}

/**
 * A new entry for [list]: a word or character, or for the substitutions a from and then a to. It joins the
 * list, which saves as it's added.
 */
@Composable
internal fun NewEntryForm(
    list: DisplayList,
    actions: LibraryDisplayActions,
    startStep: Int = 0,
) {
    var step by rememberSaveable { mutableIntStateOf(startStep) }
    var first by rememberSaveable { mutableStateOf("") }
    var second by rememberSaveable { mutableStateOf("") }
    if (list.pairs && step == 1) {
        TextEntrySurface(
            title = stringResource(R.string.display_substitution_to),
            value = second,
            onValueChange = { second = it },
            onSubmit = { actions.onAdd(ListEntry(first, second)) },
            onCancel = { step = 0 },
            cancelLabel = stringResource(R.string.library_back),
            submitLabel = stringResource(R.string.display_add),
            hint = stringResource(R.string.display_substitution_to_hint),
            minLines = 1,
            maxLines = 2,
        )
    } else {
        TextEntrySurface(
            title = stringResource(if (list.pairs) R.string.display_substitution_from else R.string.display_entry_title),
            value = first,
            onValueChange = { first = it },
            onSubmit = { if (list.pairs) step = 1 else actions.onAdd(ListEntry(first)) },
            onCancel = { actions.onSheet(null) },
            submitLabel = stringResource(if (list.pairs) R.string.library_next else R.string.display_add),
            hint = if (list.pairs) stringResource(R.string.display_substitution_from_hint) else null,
            minLines = 1,
            maxLines = 2,
        )
    }
}

/** A list's editor: its entries with a red remove on each, and Add. Each saves as it's made. */
@Composable
internal fun ListEditor(
    list: DisplayList,
    state: LibraryDisplayUiState.Ready,
    actions: LibraryDisplayActions,
    inner: PaddingValues,
) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(inner),
        verticalArrangement = Arrangement.spacedBy(dimensionResource(DesR.dimen.padding_l)),
    ) {
        Text(stringResource(list.body()), style = MaterialTheme.typography.bodyMedium)
        ItemGroup(
            title = null,
            rows =
                state.draft
                    .mapIndexed { index, entry ->
                        ListItem(
                            icon = Icons.Outlined.ListAlt,
                            label = entry.shown(),
                            clickable = false,
                            trailingContent = {
                                ExpressiveIconButton(
                                    onClick = { actions.onRemove(index) },
                                    icon = Icons.Outlined.Delete,
                                    contentDescription = stringResource(R.string.display_remove),
                                    tint = MaterialTheme.colorScheme.error,
                                )
                            },
                        )
                    }.ifEmpty {
                        listOf(
                            ListItem(icon = Icons.Outlined.ListAlt, label = stringResource(R.string.display_list_empty), clickable = false),
                        )
                    } +
                    ListItem(
                        icon = Icons.Outlined.Add,
                        label = stringResource(R.string.display_add),
                        destination = ListItemDestination.Action,
                        onClick = { actions.onSheet(DisplaySheet.NewEntry) },
                    ),
        )
        FailureText(state.saveFailed?.let { stringResource(R.string.settings_not_saved, stringResource(it.message)) })
    }
}

private fun DisplayList.body(): Int =
    when (this) {
        DisplayList.RemoveWords -> R.string.display_remove_words_body
        DisplayList.RemoveCharacters -> R.string.display_remove_characters_body
        DisplayList.ReplaceCharacters -> R.string.display_replace_characters_body
        DisplayList.Substitutions -> R.string.display_substitutions_body
    }
