package com.cooper.homelab.ephyra.ui.tv

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.android.tools.screenshot.PreviewTest
import com.binge.designsystem.SaveStatus
import com.binge.designsystem.tv.component.TvButton
import com.cooper.homelab.core.flags.Flags
import com.cooper.homelab.core.flags.SharedFlags
import com.cooper.homelab.ephyra.flags.LocalFlags
import com.cooper.homelab.ephyra.preview.JellyfinTvScreenPreviews
import com.cooper.homelab.ephyra.preview.JellyfinTvStressPreviews
import com.cooper.homelab.ephyra.server.Culture
import com.cooper.homelab.ephyra.server.ForgotPasswordAction
import com.cooper.homelab.ephyra.server.PlaybackPreferences
import com.cooper.homelab.ephyra.server.SubtitleMode
import com.cooper.homelab.ephyra.ui.Actions
import com.cooper.homelab.ephyra.ui.ForgotPasswordStep
import com.cooper.homelab.ephyra.ui.UiState
import com.cooper.homelab.ephyra.ui.console.ConsoleFailure
import com.cooper.homelab.ephyra.ui.console.PlaybackActions
import com.cooper.homelab.ephyra.ui.console.PlaybackUiState
import com.cooper.homelab.ephyra.ui.console.SaveProgress
import com.cooper.homelab.ephyra.ui.previewEnterServerCleartextRefused
import com.cooper.homelab.ephyra.ui.previewEnterServerClientCertificate
import com.cooper.homelab.ephyra.ui.previewEnterServerError
import com.cooper.homelab.ephyra.ui.previewEnterServerFound
import com.cooper.homelab.ephyra.ui.previewEnterServerInsecure
import com.cooper.homelab.ephyra.ui.previewEnterServerNotJellyfin
import com.cooper.homelab.ephyra.ui.previewEnterServerSearching
import com.cooper.homelab.ephyra.ui.previewQuickConnect
import com.cooper.homelab.ephyra.ui.previewQuickConnectExpired
import com.cooper.homelab.ephyra.ui.previewQuickConnectGetting
import com.cooper.homelab.ephyra.ui.previewQuickConnectNotOffered
import com.cooper.homelab.ephyra.ui.previewQuickConnectOff
import com.cooper.homelab.ephyra.ui.previewSetupIncomplete
import com.cooper.homelab.ephyra.ui.previewSignIn
import com.cooper.homelab.ephyra.ui.previewSignInUsers
import com.cooper.homelab.ephyra.ui.previewSignInUsersPictures
import com.cooper.homelab.ephyra.ui.previewSignInWrongPassword
import com.cooper.homelab.ephyra.ui.previewSignedIn
import com.cooper.homelab.ephyra.ui.previewSignedInChecking
import com.cooper.homelab.ephyra.ui.previewSignedInClientCertificate
import com.cooper.homelab.ephyra.ui.previewSignedInCouldNotLoad
import com.cooper.homelab.ephyra.ui.previewSignedInFailed
import com.cooper.homelab.ephyra.ui.previewSignedInLocalNetworkDenied
import com.cooper.homelab.ephyra.ui.previewSignedInWaiting
import com.cooper.homelab.ephyra.ui.tv.ACCOUNT_KEY
import com.cooper.homelab.ephyra.ui.tv.AUDIO_KEY
import com.cooper.homelab.ephyra.ui.tv.AUTOPLAY_KEY
import com.cooper.homelab.ephyra.ui.tv.TvUserPicture

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

    /** A public plain-HTTP address: the agreement under the field, with its reason beside it. */
    @PreviewTest
    @JellyfinTvScreenPreviews
    @JellyfinTvStressPreviews
    @Composable
    fun serverInsecure() = TvJellyfinRoot(previewEnterServerInsecure, Actions())

    /** The refusal's wording is the longest address error on TV, so it is checked in Spanish and at large fonts. */
    @PreviewTest
    @JellyfinTvScreenPreviews
    @JellyfinTvStressPreviews
    @Composable
    fun serverCleartextRefused() = TvJellyfinRoot(previewEnterServerCleartextRefused, Actions())

    @PreviewTest
    @JellyfinTvScreenPreviews
    @JellyfinTvStressPreviews
    @Composable
    fun serverClientCertificate() = TvJellyfinRoot(previewEnterServerClientCertificate, Actions())

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
    @JellyfinTvStressPreviews
    @Composable
    fun setupIncomplete() = TvJellyfinRoot(previewSetupIncomplete, Actions())

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

    /** Quick Connect is off on the server, so the step opens on the password with no code behind it. */
    @PreviewTest
    @JellyfinTvScreenPreviews
    @Composable
    fun quickConnectNotOffered() = TvJellyfinRoot(previewQuickConnectNotOffered, Actions())

    @PreviewTest
    @JellyfinTvScreenPreviews
    @JellyfinTvStressPreviews
    @Composable
    fun password() = TvSignInPage(previewSignIn, Actions(), startWithPassword = true)

    @PreviewTest
    @JellyfinTvScreenPreviews
    @JellyfinTvStressPreviews
    @Composable
    fun passwordWithUsers() = TvSignInPage(previewSignInUsers, Actions(), startWithPassword = true)

    @PreviewTest
    @JellyfinTvScreenPreviews
    @JellyfinTvStressPreviews
    @Composable
    fun passwordWithPictures() = TvSignInPage(previewSignInUsersPictures, Actions(), startWithPassword = true)

    /**
     * A listed account's button focused, which fills the pill: with a picture and with the initials inside it, the two things its
     * leading slot holds, and at large fonts too.
     */
    @PreviewTest
    @JellyfinTvScreenPreviews
    @JellyfinTvStressPreviews
    @Composable
    fun accountButtonFocused() =
        Column(modifier = Modifier.padding(48.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
            val picture = previewSignInUsersPictures.publicPictures.values.first()
            TvButton(label = "Mum", onClick = {}, initiallyFocused = true, leadingContent = { TvUserPicture("Mum", picture) })
            TvButton(label = "Dad", onClick = {}, initiallyFocused = true, leadingContent = { TvUserPicture("Dad", null) })
            TvButton(label = "Kid", onClick = {}, leadingContent = { TvUserPicture("Kid", picture) })
        }

    @PreviewTest
    @JellyfinTvScreenPreviews
    @Composable
    fun passwordWrong() = TvSignInPage(previewSignInWrongPassword, Actions(), startWithPassword = true)

    @PreviewTest
    @JellyfinTvScreenPreviews
    @JellyfinTvStressPreviews
    @Composable
    fun forgotContactAdmin() =
        TvSignInPage(
            previewSignIn.copy(forgot = ForgotPasswordStep.Told(ForgotPasswordAction.ContactAdmin)),
            Actions(),
            startWithPassword = true,
        )

    @PreviewTest
    @JellyfinTvScreenPreviews
    @Composable
    fun forgotInNetworkRequired() =
        TvSignInPage(
            previewSignIn.copy(forgot = ForgotPasswordStep.Told(ForgotPasswordAction.InNetworkRequired)),
            Actions(),
            startWithPassword = true,
        )

    @PreviewTest
    @JellyfinTvScreenPreviews
    @JellyfinTvStressPreviews
    @Composable
    fun forgotPin() =
        TvSignInPage(
            previewSignIn.copy(forgot = ForgotPasswordStep.EnterPin("/config/data/passwordreset.json")),
            Actions(),
            startWithPassword = true,
        )

    @PreviewTest
    @JellyfinTvScreenPreviews
    @Composable
    fun forgotPinWrong() =
        TvSignInPage(
            previewSignIn.copy(forgot = ForgotPasswordStep.EnterPin("/config/data/passwordreset.json", wrongPin = true)),
            Actions(),
            startWithPassword = true,
        )

    @PreviewTest
    @JellyfinTvScreenPreviews
    @Composable
    fun forgotFailed() = TvSignInPage(previewSignIn.copy(forgot = ForgotPasswordStep.Failed), Actions(), startWithPassword = true)

    @PreviewTest
    @JellyfinTvScreenPreviews
    @Composable
    fun forgotDone() =
        TvSignInPage(previewSignIn.copy(forgot = ForgotPasswordStep.Done, password = "A1B2-C3D4"), Actions(), startWithPassword = true)

    @PreviewTest
    @JellyfinTvScreenPreviews
    @JellyfinTvStressPreviews
    @Composable
    fun signedIn() = TvJellyfinRoot(previewSignedIn, Actions())

    @PreviewTest
    @JellyfinTvScreenPreviews
    @Composable
    fun signedInChecking() =
        CompositionLocalProvider(LocalFlags provides Flags.of(SharedFlags.BingePromotion to true)) {
            TvJellyfinRoot(previewSignedInChecking, Actions())
        }

    /** The saved server has not answered yet: its row says it is checking, and only the way out is offered. */
    @PreviewTest
    @JellyfinTvScreenPreviews
    @Composable
    fun signedInWaiting() =
        CompositionLocalProvider(LocalFlags provides Flags.of(SharedFlags.BingePromotion to true)) {
            TvJellyfinRoot(previewSignedInWaiting, Actions())
        }

    @PreviewTest
    @JellyfinTvScreenPreviews
    @Composable
    fun signedInFailed() =
        CompositionLocalProvider(LocalFlags provides Flags.of(SharedFlags.BingePromotion to true)) {
            TvJellyfinRoot(previewSignedInFailed, Actions())
        }

    @PreviewTest
    @JellyfinTvScreenPreviews
    @Composable
    fun signedInCouldNotLoad() = TvJellyfinRoot(previewSignedInCouldNotLoad, Actions())

    @PreviewTest
    @JellyfinTvScreenPreviews
    @Composable
    fun signedInClientCertificate() = TvJellyfinRoot(previewSignedInClientCertificate, Actions())

    @PreviewTest
    @JellyfinTvScreenPreviews
    @Composable
    fun signedInLocalNetworkDenied() = TvJellyfinRoot(previewSignedInLocalNetworkDenied, Actions())

    /** With the Binge flag on: the library check, with what Binge can see, leads the page. */
    @PreviewTest
    @JellyfinTvScreenPreviews
    @Composable
    fun signedInPromoted() =
        CompositionLocalProvider(LocalFlags provides Flags.of(SharedFlags.BingePromotion to true)) {
            TvJellyfinRoot(previewSignedIn, Actions())
        }

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
