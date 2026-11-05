package com.cooper.binge.companion.jellyfin.ui.console

import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import com.android.tools.screenshot.PreviewTest
import com.cooper.binge.companion.jellyfin.preview.JellyfinFontScalePreviews
import com.cooper.binge.companion.jellyfin.preview.JellyfinScreenPreviews
import com.cooper.binge.companion.jellyfin.preview.JellyfinScreenStateLightPreview
import com.cooper.binge.companion.jellyfin.preview.JellyfinScreenStatePreview
import com.cooper.binge.companion.jellyfin.preview.JellyfinSpanishPreviews
import com.cooper.binge.companion.jellyfin.preview.JellyfinWidePreview
import com.cooper.binge.companion.jellyfin.preview.JellyfinWideSpanishPreview
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
    fun sessionPage() = NowPlayingSessionScreen(page(previewEpisode, fullControls), previewEpisode.sessionId, NowPlayingActions())

    @PreviewTest
    @JellyfinScreenStatePreview
    @JellyfinScreenStateLightPreview
    @JellyfinSpanishPreviews
    @JellyfinFontScalePreviews
    @Composable
    fun sessionPageRemote() =
        NowPlayingSessionScreen(
            page(previewEpisode.copy(volumeLevel = 40), remoteControls),
            previewEpisode.copy(volumeLevel = 40).sessionId,
            NowPlayingActions(),
        )

    @PreviewTest
    @JellyfinScreenStatePreview
    @Composable
    fun sessionPageMuted() =
        NowPlayingSessionScreen(
            page(previewEpisode.copy(volumeLevel = 0, muted = true), remoteControls),
            previewEpisode.copy(volumeLevel = 0, muted = true).sessionId,
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
    fun sessionPagePaused() = NowPlayingSessionScreen(page(previewMovie, fullControls), previewMovie.sessionId, NowPlayingActions())

    @PreviewTest
    @JellyfinScreenStatePreview
    @Composable
    fun sessionPageSending() =
        NowPlayingSessionScreen(page(previewEpisode, fullControls, sending = true), previewEpisode.sessionId, NowPlayingActions())

    @PreviewTest
    @JellyfinScreenStatePreview
    @Composable
    fun sessionPageNoSeek() =
        NowPlayingSessionScreen(
            page(previewEpisode, SessionControls(playPause = true, seek = false, skip = true, stop = true)),
            previewEpisode.sessionId,
            NowPlayingActions(),
        )

    @PreviewTest
    @JellyfinScreenStatePreview
    @Composable
    fun sessionPageNoStop() =
        NowPlayingSessionScreen(
            page(previewEpisode, SessionControls(playPause = true, seek = true, skip = true, stop = false)),
            previewEpisode.sessionId,
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

/** The page on a landscape tablet, where a stream opens in a third pane: the invitation to pick one while none is. */
class NowPlayingPaneScreenshotTest {
    @PreviewTest
    @JellyfinWidePreview
    @JellyfinWideSpanishPreview
    @Composable
    fun placeholder() = Surface { NowPlayingPlaceholder() }
}
