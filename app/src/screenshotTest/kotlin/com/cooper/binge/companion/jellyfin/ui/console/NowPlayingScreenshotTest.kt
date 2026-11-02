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
import com.cooper.binge.companion.jellyfin.server.SessionControls

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

private val fullControls = SessionControls(playPause = true, seek = true, skip = true, stop = true)

private val previewReady =
    NowPlayingUiState.Ready(
        listOf(previewEpisode, previewMovie),
        controls = mapOf(previewEpisode.sessionId to fullControls, previewMovie.sessionId to fullControls),
    )

private val remoteControls =
    SessionControls(playPause = true, seek = true, skip = true, stop = true, volume = true, mute = true, message = true)

private fun page(
    session: NowPlaying,
    controls: SessionControls,
    sending: Boolean = false,
) = NowPlayingUiState.Ready(
    listOf(session),
    controls = mapOf(session.sessionId to controls),
    selected = session.sessionId,
    sending = sending,
)

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
    @JellyfinFontScalePreviews
    @Composable
    fun sessionPage() = NowPlayingScreen(page(previewEpisode, fullControls), administrator = true, NowPlayingActions())

    @PreviewTest
    @JellyfinScreenStatePreview
    @JellyfinScreenStateLightPreview
    @JellyfinSpanishPreviews
    @JellyfinFontScalePreviews
    @Composable
    fun sessionPageRemote() =
        NowPlayingScreen(page(previewEpisode.copy(volumeLevel = 40), remoteControls), administrator = true, NowPlayingActions())

    @PreviewTest
    @JellyfinScreenStatePreview
    @Composable
    fun sessionPageMuted() =
        NowPlayingScreen(
            page(previewEpisode.copy(volumeLevel = 0, muted = true), remoteControls),
            administrator = true,
            NowPlayingActions(),
        )

    @PreviewTest
    @JellyfinScreenStatePreview
    @JellyfinSpanishPreviews
    @JellyfinFontScalePreviews
    @Composable
    fun messageSheet() =
        Surface { MessageSheet(previewEpisode, page(previewEpisode, remoteControls), NowPlayingActions(), inWindow = false) }

    @PreviewTest
    @JellyfinScreenStatePreview
    @Composable
    fun messageSheetSending() =
        Surface {
            MessageSheet(
                previewEpisode,
                page(previewEpisode, remoteControls).copy(messageSending = true),
                NowPlayingActions(),
                inWindow = false,
            )
        }

    @PreviewTest
    @JellyfinScreenStatePreview
    @Composable
    fun messageSheetFailed() =
        Surface {
            MessageSheet(
                previewEpisode,
                page(previewEpisode, remoteControls).copy(messageFailed = ConsoleFailure.Unreachable),
                NowPlayingActions(),
                inWindow = false,
            )
        }

    @PreviewTest
    @JellyfinScreenStatePreview
    @Composable
    fun sessionPagePaused() = NowPlayingScreen(page(previewMovie, fullControls), administrator = true, NowPlayingActions())

    @PreviewTest
    @JellyfinScreenStatePreview
    @Composable
    fun sessionPageSending() =
        NowPlayingScreen(page(previewEpisode, fullControls, sending = true), administrator = true, NowPlayingActions())

    @PreviewTest
    @JellyfinScreenStatePreview
    @Composable
    fun sessionPageNoSeek() =
        NowPlayingScreen(
            page(previewEpisode, SessionControls(playPause = true, seek = false, skip = true, stop = true)),
            administrator = true,
            NowPlayingActions(),
        )

    @PreviewTest
    @JellyfinScreenStatePreview
    @Composable
    fun sessionPageNoStop() =
        NowPlayingScreen(
            page(previewEpisode, SessionControls(playPause = true, seek = true, skip = true, stop = false)),
            administrator = true,
            NowPlayingActions(),
        )

    @PreviewTest
    @JellyfinScreenStatePreview
    @Composable
    fun loading() = NowPlayingScreen(NowPlayingUiState.Loading, administrator = true, NowPlayingActions())

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
    fun failed() = NowPlayingScreen(NowPlayingUiState.Failed(ConsoleFailure.Refused), administrator = false, NowPlayingActions())
}
