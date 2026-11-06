package com.cooper.binge.companion.jellyfin.ui.tv

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import com.android.tools.screenshot.PreviewTest
import com.binge.designsystem.SaveStatus
import com.cooper.binge.companion.jellyfin.flags.Flags
import com.cooper.binge.companion.jellyfin.flags.LocalFlags
import com.cooper.binge.companion.jellyfin.preview.JellyfinTvScreenPreviews
import com.cooper.binge.companion.jellyfin.preview.JellyfinTvStressPreviews
import com.cooper.binge.companion.jellyfin.server.Culture
import com.cooper.binge.companion.jellyfin.server.PlaybackPreferences
import com.cooper.binge.companion.jellyfin.server.SubtitleMode
import com.cooper.binge.companion.jellyfin.ui.Actions
import com.cooper.binge.companion.jellyfin.ui.UiState
import com.cooper.binge.companion.jellyfin.ui.console.ConsoleFailure
import com.cooper.binge.companion.jellyfin.ui.console.PlaybackActions
import com.cooper.binge.companion.jellyfin.ui.console.PlaybackUiState
import com.cooper.binge.companion.jellyfin.ui.console.SaveProgress
import com.cooper.binge.companion.jellyfin.ui.previewEnterServerError
import com.cooper.binge.companion.jellyfin.ui.previewEnterServerFound
import com.cooper.binge.companion.jellyfin.ui.previewEnterServerNotJellyfin
import com.cooper.binge.companion.jellyfin.ui.previewEnterServerSearching
import com.cooper.binge.companion.jellyfin.ui.previewQuickConnect
import com.cooper.binge.companion.jellyfin.ui.previewQuickConnectExpired
import com.cooper.binge.companion.jellyfin.ui.previewQuickConnectGetting
import com.cooper.binge.companion.jellyfin.ui.previewQuickConnectOff
import com.cooper.binge.companion.jellyfin.ui.previewSignIn
import com.cooper.binge.companion.jellyfin.ui.previewSignInWrongPassword
import com.cooper.binge.companion.jellyfin.ui.previewSignedIn
import com.cooper.binge.companion.jellyfin.ui.previewSignedInChecking
import com.cooper.binge.companion.jellyfin.ui.previewSignedInCouldNotLoad
import com.cooper.binge.companion.jellyfin.ui.previewSignedInFailed
import com.cooper.binge.companion.jellyfin.ui.previewSignedInLocalNetworkDenied
import com.cooper.binge.companion.jellyfin.ui.tv.ACCOUNT_KEY
import com.cooper.binge.companion.jellyfin.ui.tv.AUDIO_KEY
import com.cooper.binge.companion.jellyfin.ui.tv.AUTOPLAY_KEY

/** The television's screens: each step's main state in Spanish and at large fonts too, every other state once. */
class TvJellyfinScreenshotTest {
    @PreviewTest
    @JellyfinTvScreenPreviews
    @JellyfinTvStressPreviews
    @Composable
    fun serverFound() = TvJellyfinRoot(previewEnterServerFound, Actions())

    /** The address error is one line on TV, so its longest wording is checked in Spanish and at large fonts. */
    @PreviewTest
    @JellyfinTvScreenPreviews
    @JellyfinTvStressPreviews
    @Composable
    fun serverNotJellyfin() = TvJellyfinRoot(previewEnterServerNotJellyfin, Actions())

    @PreviewTest
    @JellyfinTvScreenPreviews
    @JellyfinTvStressPreviews
    @Composable
    fun serverUnreachable() = TvJellyfinRoot(previewEnterServerError, Actions())

    @PreviewTest
    @JellyfinTvScreenPreviews
    @Composable
    fun loading() = TvJellyfinRoot(UiState.Loading, Actions())

    @PreviewTest
    @JellyfinTvScreenPreviews
    @Composable
    fun serverSearching() = TvJellyfinRoot(previewEnterServerSearching, Actions())

    @PreviewTest
    @JellyfinTvScreenPreviews
    @JellyfinTvStressPreviews
    @Composable
    fun quickConnectCode() = TvJellyfinRoot(previewQuickConnect, Actions())

    @PreviewTest
    @JellyfinTvScreenPreviews
    @Composable
    fun quickConnectGetting() = TvJellyfinRoot(previewQuickConnectGetting, Actions())

    @PreviewTest
    @JellyfinTvScreenPreviews
    @Composable
    fun quickConnectAfterWrongPassword() = TvJellyfinRoot(previewSignInWrongPassword, Actions())

    /** A code the server let go of: its own wording, with Try again where the code was. */
    @PreviewTest
    @JellyfinTvScreenPreviews
    @JellyfinTvStressPreviews
    @Composable
    fun quickConnectExpired() = TvJellyfinRoot(previewQuickConnectExpired, Actions())

    @PreviewTest
    @JellyfinTvScreenPreviews
    @Composable
    fun quickConnectOff() = TvJellyfinRoot(previewQuickConnectOff, Actions())

    @PreviewTest
    @JellyfinTvScreenPreviews
    @JellyfinTvStressPreviews
    @Composable
    fun password() = TvSignInPage(previewSignIn, Actions(), startWithPassword = true)

    @PreviewTest
    @JellyfinTvScreenPreviews
    @Composable
    fun passwordWrong() = TvSignInPage(previewSignInWrongPassword, Actions(), startWithPassword = true)

    @PreviewTest
    @JellyfinTvScreenPreviews
    @JellyfinTvStressPreviews
    @Composable
    fun signedIn() = TvJellyfinRoot(previewSignedIn, Actions())

    @PreviewTest
    @JellyfinTvScreenPreviews
    @Composable
    fun signedInChecking() =
        CompositionLocalProvider(LocalFlags provides Flags.AllOn) { TvJellyfinRoot(previewSignedInChecking, Actions()) }

    @PreviewTest
    @JellyfinTvScreenPreviews
    @Composable
    fun signedInFailed() = CompositionLocalProvider(LocalFlags provides Flags.AllOn) { TvJellyfinRoot(previewSignedInFailed, Actions()) }

    @PreviewTest
    @JellyfinTvScreenPreviews
    @Composable
    fun signedInCouldNotLoad() = TvJellyfinRoot(previewSignedInCouldNotLoad, Actions())

    @PreviewTest
    @JellyfinTvScreenPreviews
    @Composable
    fun signedInLocalNetworkDenied() = TvJellyfinRoot(previewSignedInLocalNetworkDenied, Actions())

    /** With the Binge flag on: the library check, with what Binge can see, leads the page. */
    @PreviewTest
    @JellyfinTvScreenPreviews
    @Composable
    fun signedInPromoted() = CompositionLocalProvider(LocalFlags provides Flags.AllOn) { TvJellyfinRoot(previewSignedIn, Actions()) }

    private val cultures = listOf(Culture("English", "en", listOf("eng")), Culture("French", "fr", listOf("fre", "fra")))
    private val prefs = PlaybackPreferences(audio = "eng", subtitles = "", subtitleMode = SubtitleMode.Default)

    @Composable
    private fun withPlayback(
        playback: PlaybackUiState,
        describing: String = ACCOUNT_KEY,
        changed: String? = null,
    ) = TvJellyfinRoot(previewSignedIn, Actions(), playback = TvPlayback(playback, PlaybackActions(), changed), describing = describing)

    /** The user's own playback settings follow the account on the signed-in page. */
    @PreviewTest
    @JellyfinTvScreenPreviews
    @JellyfinTvStressPreviews
    @Composable
    fun playback() = withPlayback(PlaybackUiState.Ready(prefs, cultures))

    @PreviewTest
    @JellyfinTvScreenPreviews
    @Composable
    fun playbackSaved() = withPlayback(PlaybackUiState.Ready(prefs, cultures, SaveProgress(SaveStatus.Saved)), AUDIO_KEY, AUDIO_KEY)

    @PreviewTest
    @JellyfinTvScreenPreviews
    @Composable
    fun playbackSaveFailed() = withPlayback(PlaybackUiState.Ready(prefs, cultures, SaveProgress(failed = true)), AUDIO_KEY, AUDIO_KEY)

    /** A language row's pane: Any language, then the languages, the one in use ticked. */
    @PreviewTest
    @JellyfinTvScreenPreviews
    @JellyfinTvStressPreviews
    @Composable
    fun playbackLanguages() = withPlayback(PlaybackUiState.Ready(prefs, cultures), AUDIO_KEY)

    /** A switch's pane: its one option, ticked while it is on. */
    @PreviewTest
    @JellyfinTvScreenPreviews
    @Composable
    fun playbackSwitch() = withPlayback(PlaybackUiState.Ready(prefs, cultures), AUTOPLAY_KEY)

    @PreviewTest
    @JellyfinTvScreenPreviews
    @Composable
    fun playbackLoading() = withPlayback(PlaybackUiState.Loading)

    @PreviewTest
    @JellyfinTvScreenPreviews
    @Composable
    fun playbackFailed() = withPlayback(PlaybackUiState.Failed(ConsoleFailure.Unreachable))
}
