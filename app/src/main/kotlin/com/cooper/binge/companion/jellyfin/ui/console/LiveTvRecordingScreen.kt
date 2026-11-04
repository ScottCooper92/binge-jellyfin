package com.cooper.binge.companion.jellyfin.ui.console

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Audiotrack
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Tv
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.binge.designsystem.component.ItemGroup
import com.binge.designsystem.component.ListItem
import com.binge.designsystem.template.FormScreen
import com.binge.designsystem.template.ScreenBar
import com.binge.designsystem.template.formSaveStatus
import com.cooper.binge.companion.jellyfin.R
import com.cooper.binge.companion.jellyfin.server.LIVE_TV_ORIGINAL_AUDIO
import com.cooper.binge.companion.jellyfin.server.LIVE_TV_SAVE_IMAGES
import com.cooper.binge.companion.jellyfin.server.LIVE_TV_SAVE_NFO
import com.cooper.binge.companion.jellyfin.server.LIVE_TV_SUBFOLDERS
import com.cooper.binge.companion.jellyfin.server.bool
import com.cooper.binge.companion.jellyfin.server.string
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import com.binge.designsystem.R as DesR

/** What the recording page can ask of its view model, and of the console. */
data class LiveTvRecordingActions(
    val onSet: (key: String, value: JsonElement) -> Unit = { _, _ -> },
    val onSheet: (RecordingSheet?) -> Unit = {},
    val onFolder: (RecordingFolder, String) -> Unit = { _, _ -> },
    val onNumber: (RecordingNumber, String) -> Unit = { _, _ -> },
    val onRetry: () -> Unit = {},
    val onRetrySave: () -> Unit = {},
    val onBack: () -> Unit = {},
)

private const val LOADING_ROWS = 6

/** Lines a switch's detail may wrap to, so it reads in full at large fonts. */
private const val DETAIL_LINES = 3

/**
 * How the server records: where recordings go, how long before and after a programme it records, and the files it saves beside
 * one. A switch saves when it is tapped and a folder or number from its sheet. A setting the server did not send is not shown, which
 * is how a release without it (metadata files and images are 10.9) hides it.
 */
@Composable
fun LiveTvRecordingScreen(
    state: LiveTvRecordingUiState,
    actions: LiveTvRecordingActions,
    folders: FolderBrowseState = FolderBrowseState.Loading,
    folderActions: FolderActions = FolderActions(),
) {
    val host = remember { SnackbarHostState() }
    val ready = state as? LiveTvRecordingUiState.Ready
    FormScreen(
        title = stringResource(R.string.live_tv_recording_title),
        bar = ScreenBar.Small,
        onBack = actions.onBack,
        snackbarHostState = host,
        saveStatus = ready?.save?.let { formSaveStatus(it.status, it.failed, actions.onRetrySave) },
        notReady =
            when (state) {
                LiveTvRecordingUiState.Loading -> { padding -> ConsoleLoading(padding, LOADING_ROWS) }
                is LiveTvRecordingUiState.Failed -> { padding ->
                    ConsoleFailureScreen(state.failure, actions.onRetry, Modifier.padding(padding))
                }
                is LiveTvRecordingUiState.Ready -> null
            },
    ) { _ ->
        if (ready != null) {
            RecordingBody(ready.config, actions)
            RecordingSheets(ready, actions, folders, folderActions)
            RefusedMessage(ready.refused, host)
        }
    }
}

@Composable
private fun RecordingBody(
    config: JsonObject,
    actions: LiveTvRecordingActions,
) {
    Column(verticalArrangement = Arrangement.spacedBy(dimensionResource(DesR.dimen.padding_l))) {
        Text(stringResource(R.string.live_tv_recording_body), style = MaterialTheme.typography.bodyMedium)
        ItemGroup(
            title = stringResource(R.string.live_tv_recording_folders),
            rows = RecordingFolder.entries.map { folderRow(it, config, actions) },
        )
        Text(stringResource(R.string.live_tv_recording_folder_note), style = MaterialTheme.typography.bodySmall)
        ItemGroup(
            title = stringResource(R.string.live_tv_recording_padding),
            rows =
                listOfNotNull(
                    numberRow(RecordingNumber.PrePadding, R.string.live_tv_recording_pre, Icons.Outlined.Schedule, config, actions),
                    numberRow(RecordingNumber.PostPadding, R.string.live_tv_recording_post, Icons.Outlined.Schedule, config, actions),
                ),
        )
        ItemGroup(title = stringResource(R.string.live_tv_recording_files), rows = fileRows(config, actions))
        guideRow(config, actions)?.let { ItemGroup(title = stringResource(R.string.live_tv_recording_guide), rows = listOf(it)) }
    }
}

@Composable
private fun folderRow(
    folder: RecordingFolder,
    config: JsonObject,
    actions: LiveTvRecordingActions,
): ListItem =
    ListItem(
        icon =
            when (folder) {
                RecordingFolder.All -> Icons.Outlined.Folder
                RecordingFolder.Movies -> Icons.Outlined.Movie
                RecordingFolder.Series -> Icons.Outlined.Tv
            },
        label =
            stringResource(
                when (folder) {
                    RecordingFolder.All -> R.string.live_tv_recording_folder_all
                    RecordingFolder.Movies -> R.string.live_tv_recording_folder_movies
                    RecordingFolder.Series -> R.string.live_tv_recording_folder_series
                },
            ),
        detail = config.string(folder.key)?.takeIf { it.isNotBlank() } ?: stringResource(R.string.live_tv_recording_folder_unset),
        supportingMaxLines = DETAIL_LINES,
        onClick = { actions.onSheet(RecordingSheet.Folder(folder)) },
    )

@Composable
private fun numberRow(
    field: RecordingNumber,
    label: Int,
    icon: ImageVector,
    config: JsonObject,
    actions: LiveTvRecordingActions,
): ListItem? {
    if (field.key !in config) return null
    return ListItem(
        icon = icon,
        label = stringResource(label),
        detail = numberDetail(field, field.shown(config)),
        onClick = { actions.onSheet(RecordingSheet.Number(field)) },
    )
}

@Composable
private fun numberDetail(
    field: RecordingNumber,
    shown: String,
): String {
    val count = shown.toIntOrNull() ?: return stringResource(R.string.live_tv_recording_guide_auto)
    return if (field.seconds) {
        stringResource(R.string.transcoding_unit_seconds, shown)
    } else {
        pluralStringResource(R.plurals.live_tv_recording_days, count, count)
    }
}

@Composable
private fun guideRow(
    config: JsonObject,
    actions: LiveTvRecordingActions,
): ListItem? = numberRow(RecordingNumber.GuideDays, R.string.live_tv_recording_guide_days, Icons.Outlined.CalendarMonth, config, actions)

@Composable
private fun fileRows(
    config: JsonObject,
    actions: LiveTvRecordingActions,
): List<ListItem> =
    listOfNotNull(
        switchRow(config, actions, LIVE_TV_SUBFOLDERS, Icons.Outlined.VideoLibrary, R.string.live_tv_recording_subfolders, null),
        switchRow(
            config,
            actions,
            LIVE_TV_ORIGINAL_AUDIO,
            Icons.Outlined.Audiotrack,
            R.string.live_tv_recording_original_audio,
            R.string.live_tv_recording_original_audio_detail,
        ),
        switchRow(
            config,
            actions,
            LIVE_TV_SAVE_NFO,
            Icons.Outlined.Description,
            R.string.live_tv_recording_nfo,
            R.string.live_tv_recording_nfo_detail,
        ),
        switchRow(
            config,
            actions,
            LIVE_TV_SAVE_IMAGES,
            Icons.Outlined.Image,
            R.string.live_tv_recording_images,
            R.string.live_tv_recording_images_detail,
        ),
    )

@Composable
private fun switchRow(
    config: JsonObject,
    actions: LiveTvRecordingActions,
    key: String,
    icon: ImageVector,
    label: Int,
    detail: Int?,
): ListItem? {
    if (key !in config) return null
    val on = config.bool(key) ?: false
    return ListItem(
        icon = icon,
        label = stringResource(label),
        detail = detail?.let { stringResource(it) },
        supportingMaxLines = DETAIL_LINES,
        toggled = on,
        onClick = { actions.onSet(key, JsonPrimitive(!on)) },
    )
}
