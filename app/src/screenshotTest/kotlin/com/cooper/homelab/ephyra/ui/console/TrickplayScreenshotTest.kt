package com.cooper.homelab.ephyra.ui.console

import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import com.android.tools.screenshot.PreviewTest
import com.binge.designsystem.SaveStatus
import com.cooper.homelab.ephyra.preview.JellyfinFontScalePreviews
import com.cooper.homelab.ephyra.preview.JellyfinScreenStateLightPreview
import com.cooper.homelab.ephyra.preview.JellyfinScreenStatePreview
import com.cooper.homelab.ephyra.preview.JellyfinSpanishPreviews
import com.cooper.homelab.ephyra.server.TrickplaySettings

private val previewSettings =
    TrickplaySettings(
        hwAcceleration = true,
        keyFrameOnly = true,
        processPriority = "Normal",
        widths = listOf(320, 640),
        threads = 0,
    )

private val previewReady = TrickplayUiState.Ready(previewSettings)

/** Trickplay: the page across the matrix, without the 10.10 key frames switch, each dialog, then each other state. */
class TrickplayScreenshotTest {
    @PreviewTest
    @JellyfinScreenStatePreview
    @JellyfinFontScalePreviews
    @JellyfinSpanishPreviews
    @Composable
    fun trickplay() = TrickplayScreen(previewReady, TrickplayActions())

    @PreviewTest
    @JellyfinScreenStatePreview
    @Composable
    fun beforeKeyFrames() = TrickplayScreen(TrickplayUiState.Ready(TrickplaySettings()), TrickplayActions(), keyFrameOnlyAvailable = false)

    @PreviewTest
    @JellyfinScreenStatePreview
    @JellyfinSpanishPreviews
    @JellyfinScreenStateLightPreview
    @JellyfinFontScalePreviews
    @Composable
    fun number() = Surface { FieldSheet(TrickplayField.JpegQuality, previewSettings, TrickplayActions(), inWindow = false) }

    @PreviewTest
    @JellyfinScreenStatePreview
    @JellyfinSpanishPreviews
    @JellyfinScreenStateLightPreview
    @JellyfinFontScalePreviews
    @Composable
    fun numberFailed() =
        Surface {
            FieldSheet(TrickplayField.JpegQuality, previewSettings, TrickplayActions(), failure = ConsoleFailure.Refused, inWindow = false)
        }

    @PreviewTest
    @JellyfinScreenStatePreview
    @Composable
    fun numberSubmitting() =
        Surface { FieldSheet(TrickplayField.JpegQuality, previewSettings, TrickplayActions(), submitting = true, inWindow = false) }

    @PreviewTest
    @JellyfinScreenStatePreview
    @JellyfinScreenStateLightPreview
    @JellyfinFontScalePreviews
    @Composable
    fun widths() = Surface { FieldSheet(TrickplayField.Widths, previewSettings, TrickplayActions(), inWindow = false) }

    @PreviewTest
    @JellyfinScreenStatePreview
    @Composable
    fun saving() = TrickplayScreen(previewReady.copy(save = SaveProgress(SaveStatus.Saving)), TrickplayActions())

    @PreviewTest
    @JellyfinScreenStatePreview
    @Composable
    fun loading() = TrickplayScreen(TrickplayUiState.Loading, TrickplayActions())

    @PreviewTest
    @JellyfinScreenStatePreview
    @Composable
    fun failed() = TrickplayScreen(TrickplayUiState.Failed(ConsoleFailure.Refused), TrickplayActions())
}
