package com.cooper.binge.companion.jellyfin.ui.console

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import com.binge.designsystem.component.ItemGroup
import com.binge.designsystem.component.ListItem
import com.binge.designsystem.component.ListItemNote
import com.binge.designsystem.layout.LayoutAnchors
import com.binge.designsystem.layout.layoutAnchor
import com.binge.designsystem.template.FormScreen
import com.binge.designsystem.template.ScreenBar
import com.binge.designsystem.template.formSaveStatus
import com.cooper.binge.companion.jellyfin.R
import com.cooper.binge.companion.jellyfin.server.ENCODING_ACCELERATION
import com.cooper.binge.companion.jellyfin.server.JellyfinServerProfile
import com.cooper.binge.companion.jellyfin.server.encodingChoice
import com.cooper.binge.companion.jellyfin.server.string
import kotlinx.serialization.json.JsonElement
import com.binge.designsystem.R as DesR

/** What the transcoding page can ask of its view model, and of the console. */
data class TranscodingActions(
    val onEncoding: (key: String, value: JsonElement) -> Unit = { _, _ -> },
    val onServer: (key: String, value: JsonElement) -> Unit = { _, _ -> },
    val onCodec: (codec: String, on: Boolean) -> Unit = { _, _ -> },
    val onSheet: (TranscodingSheet?) -> Unit = {},
    val onNumber: (NumberField, String) -> Unit = { _, _ -> },
    val onRetry: () -> Unit = {},
    val onRetrySave: () -> Unit = {},
    val onBack: () -> Unit = {},
)

private const val LOADING_ROWS = 8

/** Lines a switch's detail may wrap to, so it reads in full at large fonts. */
internal const val TRANSCODING_DETAIL_LINES = 4

/**
 * The server's playback settings: hardware acceleration first, with what the wrong choice does, then tone mapping,
 * quality, audio, throttling and segments, subtitles and paths, and when playback resumes and the bitrate it
 * streams remotely. Each switch and choice saves as it's made; a number or text saves from its sheet. [profile]
 * says which options this release has, and the acceleration type chosen says which of its own options show.
 */
@Composable
fun TranscodingScreen(
    state: TranscodingUiState,
    actions: TranscodingActions,
    profile: JellyfinServerProfile,
    folders: FolderBrowseState = FolderBrowseState.Loading,
    folderActions: FolderActions = FolderActions(),
) {
    val host = remember { SnackbarHostState() }
    val ready = state as? TranscodingUiState.Ready
    FormScreen(
        title = stringResource(R.string.transcoding_title),
        bar = ScreenBar.Small,
        onBack = actions.onBack,
        snackbarHostState = host,
        saveStatus = ready?.save?.let { formSaveStatus(it.status, it.failed, actions.onRetrySave) },
        notReady =
            when (state) {
                TranscodingUiState.Loading -> { padding ->
                    ConsoleLoading(padding, LOADING_ROWS, plainIntro = stringResource(R.string.transcoding_body), formInset = true)
                }
                is TranscodingUiState.Failed -> { padding ->
                    ConsoleFailureScreen(state.failure, actions.onRetry, Modifier.padding(padding))
                }
                is TranscodingUiState.Ready -> null
            },
    ) { _ ->
        if (ready != null) {
            TranscodingBody(ready, actions, profile)
            TranscodingSheets(ready, actions, folders, folderActions)
            // A save the server refused is put back and said in a message, not in a space the page keeps for it.
            RefusedMessage(ready.refused, host)
        }
    }
}

@Composable
private fun TranscodingBody(
    state: TranscodingUiState.Ready,
    actions: TranscodingActions,
    profile: JellyfinServerProfile,
) {
    var picker by rememberSaveable { mutableStateOf<TranscodingPicker?>(null) }
    val type = encodingChoice(state.encoding.string(ENCODING_ACCELERATION), "none")
    Column(
        verticalArrangement = Arrangement.spacedBy(dimensionResource(DesR.dimen.padding_l)),
    ) {
        PlainIntro(stringResource(R.string.transcoding_body))
        ItemGroup(
            title = stringResource(R.string.transcoding_hardware),
            modifier = Modifier.layoutAnchor(LayoutAnchors.section(LayoutAnchors.Collection.FIRST_ITEM)),
            rows =
                listOf(
                    ListItem(
                        icon = Icons.Outlined.Memory,
                        label = stringResource(R.string.transcoding_acceleration),
                        detail = accelerationLabel(type),
                        note =
                            ListItemNote(
                                stringResource(R.string.transcoding_acceleration_warning),
                                icon = Icons.Outlined.Warning,
                                warning = true,
                            ),
                        onClick = { picker = TranscodingPicker.Acceleration },
                    ),
                ) + hardwareRows(state, type, profile, actions),
        )
        HardwareGroups(state, type, profile, actions)
        ToneMappingGroup(state, type, profile, actions) { picker = it }
        QualityGroup(state, actions) { picker = it }
        AudioGroup(state, profile, actions) { picker = it }
        StreamingGroups(state, profile, actions)
    }
    picker?.let { TranscodingPickerSheet(it, state, profile, actions) { picker = null } }
}
