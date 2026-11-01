package com.cooper.binge.companion.jellyfin.ui.console

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import com.binge.designsystem.component.BingeAdaptiveSheet
import com.binge.designsystem.component.BingeSheetKind
import com.binge.designsystem.component.BingeTextButton
import com.binge.designsystem.component.ItemGroup
import com.binge.designsystem.component.ListItem
import com.binge.designsystem.component.TextEntrySurface
import com.cooper.binge.companion.jellyfin.R
import com.cooper.binge.companion.jellyfin.server.LIBRARY_KINDS
import com.cooper.binge.companion.jellyfin.server.MIXED_LIBRARY
import com.binge.designsystem.R as DesR

/** The new library sheet: a task sheet over the page, closed by its own Cancel or by dismissing it. */
@Composable
internal fun AddLibrarySheet(
    names: List<String>,
    saving: Boolean,
    failed: ConsoleFailure?,
    folders: FolderBrowseState,
    folderActions: FolderActions,
    onAdd: (name: String, kind: String, path: String) -> Unit,
    onDismiss: () -> Unit,
    folderMissing: Boolean = false,
) {
    BingeAdaptiveSheet(kind = BingeSheetKind.Task, onDismissRequest = onDismiss) {
        AddLibraryForm(names, saving, failed, folders, folderActions, onAdd, onDismiss, folderMissing = folderMissing)
    }
}

/** The steps of making a library. */
private object Step {
    const val NAME = 0
    const val KIND = 1
    const val FOLDER = 2
    const val TYPE = 3
}

/**
 * A new library in four steps, the way jellyfin-web asks: its name, what is in it, and its folder, chosen from
 * the server's own or typed. A name another library has isn't accepted. It is the content of [AddLibrarySheet],
 * and draws without a window for a frame, at [startStep] with [startName] typed.
 */
@Composable
internal fun AddLibraryForm(
    names: List<String>,
    saving: Boolean,
    failed: ConsoleFailure?,
    folders: FolderBrowseState,
    folderActions: FolderActions,
    onAdd: (name: String, kind: String, path: String) -> Unit,
    onCancel: () -> Unit,
    startStep: Int = Step.NAME,
    startName: String = "",
    folderMissing: Boolean = false,
) {
    var step by rememberSaveable { mutableIntStateOf(startStep) }
    var name by rememberSaveable { mutableStateOf(startName) }
    var kind by rememberSaveable { mutableStateOf(LIBRARY_KINDS.first()) }
    var path by rememberSaveable { mutableStateOf("") }
    // The folder the server last said it hasn't got; editing the path drops the line.
    var tried by rememberSaveable { mutableStateOf("") }
    when (step) {
        Step.NAME -> {
            val taken = names.any { it.equals(name.trim(), ignoreCase = true) }
            TextEntrySurface(
                title = stringResource(R.string.library_name_title),
                value = name,
                onValueChange = { name = it },
                onSubmit = { step = Step.KIND },
                onCancel = onCancel,
                submitLabel = stringResource(R.string.library_next),
                hint = stringResource(R.string.library_name_hint),
                submitEnabled = name.isNotBlank() && !taken,
                error = stringResource(R.string.library_name_taken).takeIf { taken },
                minLines = 1,
                maxLines = 1,
                maxLength = NAME_MAX,
            )
        }
        Step.KIND ->
            KindStep(
                onPick = {
                    kind = it
                    step = Step.FOLDER
                },
                onBack = { step = Step.NAME },
            )
        Step.FOLDER ->
            FolderPicker(
                state = folders,
                actions = folderActions,
                busy = saving,
                onChoose = { onAdd(name, kind, it) },
                onType = { step = Step.TYPE },
                onCancel = { step = Step.KIND },
                failed = failed,
            )
        else ->
            TextEntrySurface(
                title = stringResource(R.string.library_path_title),
                value = path,
                onValueChange = { path = it },
                onSubmit = {
                    tried = path
                    onAdd(name, kind, path)
                },
                onCancel = { step = Step.FOLDER },
                cancelLabel = stringResource(R.string.library_back),
                submitLabel = stringResource(R.string.library_add_submit),
                hint = stringResource(R.string.library_path_hint),
                isSubmitting = saving,
                error =
                    if (folderMissing && path == tried) {
                        stringResource(R.string.library_path_missing)
                    } else {
                        failed?.let { stringResource(R.string.settings_not_saved, stringResource(it.message)) }
                    },
                minLines = 1,
                maxLines = 2,
            )
    }
}

@Composable
private fun KindStep(
    onPick: (String) -> Unit,
    onBack: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(dimensionResource(DesR.dimen.padding_m)),
        verticalArrangement = Arrangement.spacedBy(dimensionResource(DesR.dimen.padding_m)),
    ) {
        Text(stringResource(R.string.library_kind_title), style = MaterialTheme.typography.titleLarge)
        Text(stringResource(R.string.library_add_scan_note), style = MaterialTheme.typography.bodyMedium)
        ItemGroup(
            title = null,
            rows =
                LIBRARY_KINDS.map { kind ->
                    ListItem(icon = typeIcon(kind), label = stringResource(kindLabel(kind)), onClick = { onPick(kind) })
                },
        )
        BingeTextButton(label = stringResource(R.string.library_back), onClick = onBack, modifier = Modifier.fillMaxWidth())
    }
}

/** The words for a kind of library. */
internal fun kindLabel(kind: String): Int =
    when (kind) {
        "movies" -> R.string.library_kind_movies
        "tvshows" -> R.string.library_kind_shows
        "music" -> R.string.library_kind_music
        "musicvideos" -> R.string.library_kind_music_videos
        "homevideos" -> R.string.library_kind_home_videos
        "books" -> R.string.library_kind_books
        MIXED_LIBRARY -> R.string.library_kind_mixed
        else -> R.string.library_kind_other
    }

private const val NAME_MAX = 60
