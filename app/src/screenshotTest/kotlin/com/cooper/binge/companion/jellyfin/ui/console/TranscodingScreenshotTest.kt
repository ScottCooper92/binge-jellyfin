package com.cooper.binge.companion.jellyfin.ui.console

import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import com.android.tools.screenshot.PreviewTest
import com.cooper.binge.companion.jellyfin.preview.JellyfinFontScalePreviews
import com.cooper.binge.companion.jellyfin.preview.JellyfinScreenPreviews
import com.cooper.binge.companion.jellyfin.preview.JellyfinScreenStatePreview
import com.cooper.binge.companion.jellyfin.preview.JellyfinSpanishPreviews
import com.cooper.binge.companion.jellyfin.server.JellyfinServerProfile
import com.cooper.binge.companion.jellyfin.server.ServerFolder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject

private val newest = JellyfinServerProfile.from("10.11.6")

private fun encoding(type: String): JsonObject =
    JsonObject(
        Json
            .parseToJsonElement(
                """{"VaapiDevice":"/dev/dri/renderD128","QsvDevice":"","EnableHardwareEncoding":true,"AllowHevcEncoding":true,
                "EnableIntelLowPowerHevcHwEncoder":false,"EnableEnhancedNvdecDecoder":true,"PreferSystemNativeHwDecoder":false,
                "HardwareDecodingCodecs":["h264","hevc","vp9"],"EnableTonemapping":true,"TonemappingAlgorithm":"bt2390",
                "TonemappingMode":"auto","TonemappingRange":"auto","TonemappingDesat":0,"TonemappingPeak":100,"TonemappingParam":0,
                "EnableVppTonemapping":true,"VppTonemappingBrightness":16,"VppTonemappingContrast":1.2,"EnableVideoToolboxTonemapping":false,
                "EncoderPreset":"auto","EncodingThreadCount":-1,"DeinterlaceMethod":"yadif","DeinterlaceDoubleRate":false,
                "DownMixAudioBoost":2,"DownMixStereoAlgorithm":"Dave750","EnableAudioVbr":true,"EnableThrottling":true,
                "ThrottleDelaySeconds":180,"EnableSegmentDeletion":true,"SegmentKeepSeconds":720,"EnableSubtitleExtraction":true,
                "EnableFallbackFont":false,"FallbackFontPath":"","TranscodingTempPath":"/config/transcodes"}""",
            ).jsonObject + ("HardwareAccelerationType" to JsonPrimitive(type)),
    )

private val server =
    Json
        .parseToJsonElement(
            """{"MinResumePct":5,"MaxResumePct":90,"MinResumeDurationSeconds":300,"MinAudiobookResume":5,"MaxAudiobookResume":5,
        "RemoteClientBitrateLimit":8000000}""",
        ).jsonObject

private fun ready(type: String = "nvenc") = TranscodingUiState.Ready(encoding(type), server)

/** Playback and transcoding: the page across the matrix, each acceleration type's options, the older releases, the sheets and the other states. */
class TranscodingScreenshotTest {
    @PreviewTest
    @JellyfinScreenPreviews
    @JellyfinFontScalePreviews
    @JellyfinSpanishPreviews
    @Composable
    fun transcoding() = TranscodingScreen(ready(), TranscodingActions(), newest)

    @PreviewTest
    @JellyfinScreenStatePreview
    @Composable
    fun quickSync() = TranscodingScreen(ready("qsv"), TranscodingActions(), newest)

    @PreviewTest
    @JellyfinScreenStatePreview
    @Composable
    fun noHardware() = TranscodingScreen(ready("none"), TranscodingActions(), newest)

    @PreviewTest
    @JellyfinScreenStatePreview
    @Composable
    fun videoToolbox() = TranscodingScreen(ready("videotoolbox"), TranscodingActions(), newest)

    @PreviewTest
    @JellyfinScreenStatePreview
    @Composable
    fun v4l2() = TranscodingScreen(ready("v4l2m2m"), TranscodingActions(), newest)

    @PreviewTest
    @JellyfinScreenStatePreview
    @Composable
    fun olderServer() = TranscodingScreen(ready("qsv"), TranscodingActions(), JellyfinServerProfile.from("10.8.13"))

    @PreviewTest
    @JellyfinScreenStatePreview
    @Composable
    fun saveFailed() = TranscodingScreen(ready().copy(saveFailed = ConsoleFailure.Unreachable), TranscodingActions(), newest)

    @PreviewTest
    @JellyfinScreenStatePreview
    @JellyfinSpanishPreviews
    @Composable
    fun bitrate() = Surface { NumberForm(NumberField.BitrateLimit, "8", TranscodingActions()) }

    @PreviewTest
    @JellyfinScreenStatePreview
    @Composable
    fun percent() = Surface { NumberForm(NumberField.MaxResumePercent, "90", TranscodingActions()) }

    @PreviewTest
    @JellyfinScreenStatePreview
    @Composable
    fun numberOutOfRange() = Surface { NumberForm(NumberField.ThrottleDelay, "5", TranscodingActions()) }

    @PreviewTest
    @JellyfinScreenStatePreview
    @Composable
    fun device() = Surface { TextForm(TextField.VaapiDevice, "/dev/dri/renderD128", TranscodingActions()) }

    @PreviewTest
    @JellyfinScreenStatePreview
    @Composable
    fun tempFolder() =
        Surface {
            TempFolderForm(
                "/config/transcodes",
                FolderBrowseState.Ready(
                    listOf(ServerFolder("config", "/config")),
                    listOf(ServerFolder("transcodes", "/config/transcodes")),
                ),
                FolderActions(),
                TranscodingActions(),
            )
        }

    @PreviewTest
    @JellyfinScreenStatePreview
    @Composable
    fun tempFolderTyped() =
        Surface { TempFolderForm("", FolderBrowseState.Loading, FolderActions(), TranscodingActions(), startTyping = true) }

    @PreviewTest
    @JellyfinScreenStatePreview
    @Composable
    fun loading() = TranscodingScreen(TranscodingUiState.Loading, TranscodingActions(), newest)

    @PreviewTest
    @JellyfinScreenStatePreview
    @Composable
    fun failed() = TranscodingScreen(TranscodingUiState.Failed(ConsoleFailure.Refused), TranscodingActions(), newest)
}
