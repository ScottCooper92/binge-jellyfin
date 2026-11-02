package com.cooper.binge.companion.jellyfin.ui.console

import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import com.android.tools.screenshot.PreviewTest
import com.binge.designsystem.SaveStatus
import com.cooper.binge.companion.jellyfin.preview.JellyfinFontScalePreviews
import com.cooper.binge.companion.jellyfin.preview.JellyfinScreenPreviews
import com.cooper.binge.companion.jellyfin.preview.JellyfinScreenStatePreview
import com.cooper.binge.companion.jellyfin.preview.JellyfinSpanishPreviews
import com.cooper.binge.companion.jellyfin.server.TrickplaySettings

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
    @JellyfinScreenPreviews
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
    @Composable
    fun number() = Surface { FieldDialog(TrickplayField.JpegQuality, previewSettings, TrickplayActions(), inWindow = false) }

    @PreviewTest
    @JellyfinScreenStatePreview
    @Composable
    fun widths() = Surface { FieldDialog(TrickplayField.Widths, previewSettings, TrickplayActions(), inWindow = false) }

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
