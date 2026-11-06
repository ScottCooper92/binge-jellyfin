package com.cooper.binge.companion.jellyfin.ui.console

import android.content.res.Resources
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import com.binge.designsystem.component.BingeAdaptiveSheet
import com.binge.designsystem.component.BingeNumberSlider
import com.binge.designsystem.component.BingeSheetKind
import com.binge.designsystem.component.TextEntrySurface
import com.cooper.binge.companion.jellyfin.R
import com.cooper.binge.companion.jellyfin.server.string
import com.binge.designsystem.R as DesR

private const val PATH_LINES = 2

/** Whichever sheet is open over the page. */
@Composable
internal fun RecordingSheets(
    state: LiveTvRecordingUiState.Ready,
    actions: LiveTvRecordingActions,
    folders: FolderBrowseState,
    folderActions: FolderActions,
) {
    val sheet = state.sheet ?: return
    BingeAdaptiveSheet(kind = BingeSheetKind.Task, onDismissRequest = { actions.onSheet(null) }, stackPagesWhenCompact = true) { nav ->
        when (sheet) {
            is RecordingSheet.Folder ->
                RecordingFolderForm(
                    sheet.folder,
                    state.config.string(sheet.folder.key).orEmpty(),
                    folders,
                    folderActions,
                    actions,
                    nav.typePath(),
                )
            is RecordingSheet.Number -> RecordingNumberForm(sheet.field, sheet.field.shown(state.config), actions)
        }
    }
}

/** A recording folder chosen from the server's own, or typed; blank leaves it unset. */
@Composable
internal fun RecordingFolderForm(
    folder: RecordingFolder,
    current: String,
    folders: FolderBrowseState,
    folderActions: FolderActions,
    actions: LiveTvRecordingActions,
    typePath: TypePath = TypePath.Off,
) {
    var path by rememberSaveable { mutableStateOf(current) }
    if (typePath.typing) {
        TextEntrySurface(
            title = stringResource(folder.title()),
            value = path,
            onValueChange = { path = it },
            onSubmit = { actions.onFolder(folder, path) },
            onCancel = typePath.close,
            cancelLabel = stringResource(R.string.library_back),
            submitLabel = stringResource(R.string.library_save),
            hint = stringResource(R.string.live_tv_recording_folder_hint),
            submitEnabled = true,
            minLines = 1,
            maxLines = PATH_LINES,
        )
    } else {
        FolderPicker(
            state = folders,
            actions = folderActions,
            busy = false,
            onChoose = { actions.onFolder(folder, it) },
            onType = typePath.open,
            onCancel = { actions.onSheet(null) },
        )
    }
}

internal fun RecordingFolder.title(): Int =
    when (this) {
        RecordingFolder.All -> R.string.live_tv_recording_folder_all
        RecordingFolder.Movies -> R.string.live_tv_recording_folder_movies
        RecordingFolder.Series -> R.string.live_tv_recording_folder_series
    }

/** A whole number in its range, from a sheet: Save keeps it if it is in range, and says what range if it isn't. */
@Composable
internal fun RecordingNumberForm(
    field: RecordingNumber,
    current: String,
    actions: LiveTvRecordingActions,
) {
    if (field.seconds) {
        RecordingPaddingSlider(field, current.toIntOrNull() ?: 0, actions)
        return
    }
    var text by rememberSaveable { mutableStateOf(current) }
    val valid = field.parse(text) != null
    TextEntrySurface(
        title = stringResource(field.title()),
        value = text,
        onValueChange = { text = it },
        onSubmit = { actions.onNumber(field, text) },
        onCancel = { actions.onSheet(null) },
        submitLabel = stringResource(R.string.library_save),
        header = { Text(stringResource(R.string.live_tv_recording_guide_hint), style = MaterialTheme.typography.bodyMedium) },
        submitEnabled = valid,
        error = stringResource(R.string.transcoding_number_invalid, field.min, field.max).takeIf { text.isNotBlank() && !valid },
        minLines = 1,
        maxLines = 1,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
    )
}

/** Padding moves in whole minutes over 0 to 120 min, stored as seconds, and a stored value that is not a whole minute shows exactly. It saves once on release and keeps its sheet open. */
@Composable
internal fun RecordingPaddingSlider(
    field: RecordingNumber,
    current: Int,
    actions: LiveTvRecordingActions,
) {
    var draft by rememberSaveable(field, current) { mutableIntStateOf(current) }
    val title = stringResource(field.title())
    val resources = LocalContext.current.resources
    Column(
        modifier = Modifier.padding(dimensionResource(DesR.dimen.padding_m)),
        verticalArrangement = Arrangement.spacedBy(dimensionResource(DesR.dimen.padding_m)),
    ) {
        Text(title, style = MaterialTheme.typography.titleLarge)
        BingeNumberSlider(
            value = draft,
            onValueChange = { it?.let { picked -> draft = picked } },
            range = field.min..field.max,
            step = SECONDS_PER_MINUTE,
            format = { paddingLabel(resources, it) },
            description = title,
            onValueChangeFinished = { field.parse(draft.toString())?.let { actions.onSet(field.key, it) } },
        )
    }
}

/** A padding as the page row and the slider both show it: "N min" for a whole minute, otherwise the exact "N s". */
internal fun paddingLabel(
    resources: Resources,
    seconds: Int,
): String =
    if (seconds % SECONDS_PER_MINUTE == 0) {
        resources.getString(R.string.transcoding_unit_minutes, (seconds / SECONDS_PER_MINUTE).toString())
    } else {
        resources.getString(R.string.transcoding_unit_seconds, seconds.toString())
    }

private const val SECONDS_PER_MINUTE = 60

internal fun RecordingNumber.title(): Int =
    when (this) {
        RecordingNumber.PrePadding -> R.string.live_tv_recording_pre
        RecordingNumber.PostPadding -> R.string.live_tv_recording_post
        RecordingNumber.GuideDays -> R.string.live_tv_recording_guide_days
    }
