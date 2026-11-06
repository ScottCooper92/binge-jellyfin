package com.cooper.binge.companion.jellyfin.ui.console

import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import com.android.tools.screenshot.PreviewTest
import com.cooper.binge.companion.jellyfin.preview.JellyfinFontScalePreviews
import com.cooper.binge.companion.jellyfin.preview.JellyfinScreenPreviews
import com.cooper.binge.companion.jellyfin.preview.JellyfinScreenStatePreview
import com.cooper.binge.companion.jellyfin.preview.JellyfinSpanishPreviews
import com.cooper.binge.companion.jellyfin.server.ServerFolder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject

private val config: JsonObject =
    Json
        .parseToJsonElement(
            """{"GuideDays":7,"RecordingPath":"/media/recordings","MovieRecordingPath":"","SeriesRecordingPath":"/media/tv/recorded",""" +
                """"PrePaddingSeconds":60,"PostPaddingSeconds":300,"EnableRecordingSubfolders":true,""" +
                """"EnableOriginalAudioWithEncodedRecordings":false,"SaveRecordingNFO":true,"SaveRecordingImages":false}""",
        ).jsonObject

/** Release 10.8 sends neither the metadata files nor the images switch. */
private val older = JsonObject(config - "SaveRecordingNFO" - "SaveRecordingImages")

private fun ready(shown: JsonObject = config) = LiveTvRecordingUiState.Ready(shown)

/** Live TV recording: the page across the matrix, a release without two switches, and the sheets' content. */
class LiveTvRecordingScreenshotTest {
    @PreviewTest
    @JellyfinScreenPreviews
    @JellyfinFontScalePreviews
    @JellyfinSpanishPreviews
    @Composable
    fun recording() = LiveTvRecordingScreen(ready(), LiveTvRecordingActions())

    @PreviewTest
    @JellyfinScreenStatePreview
    @Composable
    fun olderRelease() = LiveTvRecordingScreen(ready(older), LiveTvRecordingActions())

    @PreviewTest
    @JellyfinScreenStatePreview
    @Composable
    fun guideAutomatic() = LiveTvRecordingScreen(ready(JsonObject(config + ("GuideDays" to JsonNull))), LiveTvRecordingActions())

    @PreviewTest
    @JellyfinScreenStatePreview
    @Composable
    fun loading() = LiveTvRecordingScreen(LiveTvRecordingUiState.Loading, LiveTvRecordingActions())

    @PreviewTest
    @JellyfinScreenStatePreview
    @Composable
    fun failed() = LiveTvRecordingScreen(LiveTvRecordingUiState.Failed(ConsoleFailure.Unreachable), LiveTvRecordingActions())

    @PreviewTest
    @JellyfinScreenStatePreview
    @JellyfinSpanishPreviews
    @Composable
    fun padding() = Surface { RecordingNumberForm(RecordingNumber.PostPadding, "300", LiveTvRecordingActions()) }

    @PreviewTest
    @JellyfinScreenStatePreview
    @JellyfinFontScalePreviews
    @Composable
    fun paddingOffStep() = Surface { RecordingNumberForm(RecordingNumber.PrePadding, "7", LiveTvRecordingActions()) }

    @PreviewTest
    @JellyfinScreenStatePreview
    @Composable
    fun paddingOutOfRange() = Surface { RecordingNumberForm(RecordingNumber.PostPadding, "9000", LiveTvRecordingActions()) }

    @PreviewTest
    @JellyfinScreenStatePreview
    @Composable
    fun guideDays() = Surface { RecordingNumberForm(RecordingNumber.GuideDays, "7", LiveTvRecordingActions()) }

    @PreviewTest
    @JellyfinScreenStatePreview
    @Composable
    fun folder() =
        Surface {
            RecordingFolderForm(
                RecordingFolder.Series,
                "/media/tv/recorded",
                FolderBrowseState.Ready(
                    listOf(ServerFolder("media", "/media")),
                    listOf(ServerFolder("recorded", "/media/tv/recorded")),
                ),
                FolderActions(),
                LiveTvRecordingActions(),
            )
        }

    @PreviewTest
    @JellyfinScreenStatePreview
    @Composable
    fun folderTyped() =
        Surface {
            RecordingFolderForm(
                RecordingFolder.Movies,
                "",
                FolderBrowseState.Loading,
                FolderActions(),
                LiveTvRecordingActions(),
                startTyping = true,
            )
        }
}
