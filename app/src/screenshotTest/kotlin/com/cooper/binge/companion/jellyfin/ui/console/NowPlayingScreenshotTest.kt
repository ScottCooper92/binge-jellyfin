package com.cooper.binge.companion.jellyfin.ui.console

import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import com.android.tools.screenshot.PreviewTest
import com.cooper.binge.companion.jellyfin.preview.JellyfinFontScalePreviews
import com.cooper.binge.companion.jellyfin.preview.JellyfinScreenPreviews
import com.cooper.binge.companion.jellyfin.preview.JellyfinScreenStateLightPreview
import com.cooper.binge.companion.jellyfin.preview.JellyfinScreenStatePreview
import com.cooper.binge.companion.jellyfin.preview.JellyfinSpanishPreviews
import com.cooper.binge.companion.jellyfin.server.NowPlaying
import com.cooper.binge.companion.jellyfin.server.PlayMethod

private const val MINUTE_TICKS = 600_000_000L

private val previewEpisode =
    NowPlaying(
        sessionId = "tv",
        userName = "Ana",
        deviceName = "Living room",
        client = "Jellyfin Android TV",
        title = "Severance",
        episode = "S2:E3 · Who Is Alive?",
        positionTicks = 31 * MINUTE_TICKS,
        runtimeTicks = 52 * MINUTE_TICKS,
        paused = false,
        method = PlayMethod.Transcode,
        transcodeReasons = listOf("AudioCodecNotSupported"),
    )

private val previewMovie =
    NowPlaying(
        sessionId = "phone",
        userName = "Claude",
        deviceName = "Pixel 10 Pro XL",
        client = "Binge",
        title = "Sintel",
        episode = null,
        positionTicks = 4 * MINUTE_TICKS,
        runtimeTicks = 15 * MINUTE_TICKS,
        paused = true,
        method = PlayMethod.DirectPlay,
        transcodeReasons = emptyList(),
    )

private val previewReady = NowPlayingUiState.Ready(listOf(previewEpisode, previewMovie))

/** Now playing: two streams across the matrix for an admin, then each other state on the phone cell. */
class NowPlayingScreenshotTest {
    @PreviewTest
    @JellyfinScreenPreviews
    @JellyfinFontScalePreviews
    @JellyfinSpanishPreviews
    @Composable
    fun streams() = NowPlayingScreen(previewReady, administrator = true, NowPlayingActions())

    @PreviewTest
    @JellyfinScreenStatePreview
    @Composable
    fun ownStreams() = NowPlayingScreen(NowPlayingUiState.Ready(listOf(previewMovie)), administrator = false, NowPlayingActions())

    @PreviewTest
    @JellyfinScreenStatePreview
    @JellyfinScreenStateLightPreview
    @JellyfinSpanishPreviews
    @Composable
    fun nothingPlaying() = NowPlayingScreen(NowPlayingUiState.Ready(emptyList()), administrator = true, NowPlayingActions())

    @PreviewTest
    @JellyfinScreenStatePreview
    @JellyfinSpanishPreviews
    @Composable
    fun confirmingStop() = Surface { StopDialog(previewEpisode, NowPlayingActions(), inWindow = false) }

    @PreviewTest
    @JellyfinScreenStatePreview
    @Composable
    fun stopping() = NowPlayingScreen(previewReady.copy(stopping = "tv"), administrator = true, NowPlayingActions())

    @PreviewTest
    @JellyfinScreenStatePreview
    @Composable
    fun notStopped() =
        NowPlayingScreen(previewReady.copy(stopFailed = ConsoleFailure.Unreachable), administrator = true, NowPlayingActions())

    @PreviewTest
    @JellyfinScreenStatePreview
    @Composable
    fun failed() = NowPlayingScreen(NowPlayingUiState.Failed(ConsoleFailure.Refused), administrator = false, NowPlayingActions())
}
