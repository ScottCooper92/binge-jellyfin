package com.cooper.homelab.ephyra.ui

import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.android.tools.screenshot.PreviewTest
import com.cooper.homelab.core.flags.Flags
import com.cooper.homelab.core.flags.SharedFlags
import com.cooper.homelab.core.presentation.search.SettingsSearchState
import com.cooper.homelab.core.presentation.tip.TipState
import com.cooper.homelab.core.tip.TIP_STEPS
import com.cooper.homelab.core.uiandroid.flags.LocalFlags
import com.cooper.homelab.core.uiandroid.search.SettingsSearchScreen
import com.cooper.homelab.ephyra.preview.JellyfinFontScalePreviews
import com.cooper.homelab.ephyra.preview.JellyfinHomeLayoutPreviews
import com.cooper.homelab.ephyra.preview.JellyfinScreenStatePreview
import com.cooper.homelab.ephyra.preview.JellyfinSpanishFontScalePreviews
import com.cooper.homelab.ephyra.preview.JellyfinSpanishPreviews
import com.cooper.homelab.ephyra.server.ForgotPasswordAction
import com.cooper.homelab.ephyra.ui.console.ConsolePage
import com.cooper.homelab.ephyra.ui.search.available
import com.cooper.homelab.ephyra.ui.search.settingsIndex

/** The phone screens: each step's layout across the matrix, then every other state on the phone cell alone. */
class JellyfinScreenshotTest {
    // The server step.

    @PreviewTest
    @JellyfinScreenStatePreview
    @JellyfinFontScalePreviews
    @JellyfinSpanishPreviews
    @Composable
    fun serverFound() = JellyfinRoot(previewEnterServerFound, Actions())

    @PreviewTest
    @JellyfinScreenStatePreview
    @Composable
    fun serverEmpty() = JellyfinRoot(previewEnterServerEmpty, Actions())

    @PreviewTest
    @JellyfinScreenStatePreview
    @Composable
    fun serverSearching() = JellyfinRoot(previewEnterServerSearching, Actions())

    @PreviewTest
    @JellyfinScreenStatePreview
    @JellyfinSpanishPreviews
    @Composable
    fun serverUnreachable() = JellyfinRoot(previewEnterServerError, Actions())

    @PreviewTest
    @JellyfinScreenStatePreview
    @JellyfinSpanishPreviews
    @Composable
    fun serverLocalNetworkDenied() = JellyfinRoot(previewEnterServerLocalNetwork, Actions())

    @PreviewTest
    @JellyfinScreenStatePreview
    @Composable
    fun serverInvalidAddress() = JellyfinRoot(previewEnterServerInvalid, Actions())

    @PreviewTest
    @JellyfinScreenStatePreview
    @Composable
    fun serverNotJellyfin() = JellyfinRoot(previewEnterServerNotJellyfin, Actions())

    /** A public plain-HTTP address: the warning and the agreement beside it, with Connect waiting. */
    @PreviewTest
    @JellyfinScreenStatePreview
    @JellyfinSpanishPreviews
    @Composable
    fun serverInsecure() = JellyfinRoot(previewEnterServerInsecure, Actions())

    @PreviewTest
    @JellyfinScreenStatePreview
    @Composable
    fun serverInsecureAgreed() = JellyfinRoot(previewEnterServerInsecureAgreed, Actions())

    @PreviewTest
    @JellyfinScreenStatePreview
    @JellyfinSpanishPreviews
    @Composable
    fun serverCleartextRefused() = JellyfinRoot(previewEnterServerCleartextRefused, Actions())

    @PreviewTest
    @JellyfinScreenStatePreview
    @JellyfinSpanishPreviews
    @Composable
    fun serverClientCertificate() = JellyfinRoot(previewEnterServerClientCertificate, Actions())

    @PreviewTest
    @JellyfinScreenStatePreview
    @Composable
    fun serverGenericError() = JellyfinRoot(previewEnterServerGeneric, Actions())

    @PreviewTest
    @JellyfinScreenStatePreview
    @Composable
    fun loading() = JellyfinRoot(UiState.Loading, Actions())

    // Sign-in.

    @PreviewTest
    @JellyfinScreenStatePreview
    @JellyfinFontScalePreviews
    @JellyfinSpanishPreviews
    @JellyfinSpanishFontScalePreviews
    @Composable
    fun signIn() = JellyfinRoot(previewSignIn, Actions())

    /** A server that has not finished its first-run setup: the notice stands where the sign-in would. */
    @PreviewTest
    @JellyfinScreenStatePreview
    @JellyfinSpanishPreviews
    @Composable
    fun setupIncomplete() = JellyfinRoot(previewSetupIncomplete, Actions())

    @PreviewTest
    @JellyfinScreenStatePreview
    @JellyfinFontScalePreviews
    @JellyfinSpanishPreviews
    @Composable
    fun signInUsers() = JellyfinRoot(previewSignInUsers, Actions())

    @PreviewTest
    @JellyfinScreenStatePreview
    @JellyfinFontScalePreviews
    @JellyfinSpanishPreviews
    @Composable
    fun signInUsersPictures() = JellyfinRoot(previewSignInUsersPictures, Actions())

    // The setup wizard of a server that has not finished its first-run setup.

    @PreviewTest
    @JellyfinScreenStatePreview
    @JellyfinFontScalePreviews
    @JellyfinSpanishPreviews
    @Composable
    fun startup() = JellyfinRoot(previewStartup, Actions())

    @PreviewTest
    @JellyfinScreenStatePreview
    @JellyfinFontScalePreviews
    @JellyfinSpanishPreviews
    @Composable
    fun startupAccount() = JellyfinRoot(previewStartupAccountFilled, Actions())

    @PreviewTest
    @JellyfinScreenStatePreview
    @Composable
    fun startupAccountEmpty() = JellyfinRoot(previewStartupAccount, Actions())

    @PreviewTest
    @JellyfinScreenStatePreview
    @Composable
    fun startupAccountMismatch() = JellyfinRoot(previewStartupAccountMismatch, Actions())

    @PreviewTest
    @JellyfinScreenStatePreview
    @Composable
    fun startupAccountSaving() = JellyfinRoot(previewStartupAccountSaving, Actions())

    @PreviewTest
    @JellyfinScreenStatePreview
    @Composable
    fun startupAccountRefused() = JellyfinRoot(previewStartupAccountRefused, Actions())

    /** The server refused the account and said why: its own words stand in place of the generic sentence. */
    @PreviewTest
    @JellyfinScreenStatePreview
    @Composable
    fun startupAccountReason() = JellyfinRoot(previewStartupAccountReason, Actions())

    @PreviewTest
    @JellyfinScreenStatePreview
    @Composable
    fun startupNoName() = JellyfinRoot(previewStartupNoName, Actions())

    @PreviewTest
    @JellyfinScreenStatePreview
    @Composable
    fun startupSaving() = JellyfinRoot(previewStartupSaving, Actions())

    @PreviewTest
    @JellyfinScreenStatePreview
    @Composable
    fun startupRefused() = JellyfinRoot(previewStartupRefused, Actions())

    @PreviewTest
    @JellyfinScreenStatePreview
    @Composable
    fun startupLoading() = JellyfinRoot(previewStartupLoading, Actions())

    @PreviewTest
    @JellyfinScreenStatePreview
    @Composable
    fun startupFailed() = JellyfinRoot(previewStartupFailed, Actions())

    @PreviewTest
    @JellyfinScreenStatePreview
    @JellyfinFontScalePreviews
    @JellyfinSpanishPreviews
    @Composable
    fun startupFinish() = JellyfinRoot(previewStartupFinish, Actions())

    @PreviewTest
    @JellyfinScreenStatePreview
    @Composable
    fun startupFinishPorts() = JellyfinRoot(previewStartupFinishPorts, Actions())

    @PreviewTest
    @JellyfinScreenStatePreview
    @Composable
    fun startupFinishOff() = JellyfinRoot(previewStartupFinishOff, Actions())

    @PreviewTest
    @JellyfinScreenStatePreview
    @Composable
    fun startupFinishSaving() = JellyfinRoot(previewStartupFinishSaving, Actions())

    @PreviewTest
    @JellyfinScreenStatePreview
    @Composable
    fun startupFinishRefused() = JellyfinRoot(previewStartupFinishRefused, Actions())

    @PreviewTest
    @JellyfinScreenStatePreview
    @Composable
    fun signInHttps() = JellyfinRoot(previewSignInHttps, Actions())

    @PreviewTest
    @JellyfinScreenStatePreview
    @Composable
    fun signInWrongPassword() = JellyfinRoot(previewSignInWrongPassword, Actions())

    @PreviewTest
    @JellyfinScreenStatePreview
    @JellyfinFontScalePreviews
    @JellyfinSpanishPreviews
    @Composable
    fun quickConnectCode() = QuickConnectCodeBody(requireNotNull(previewQuickConnect.quickConnectCode), Actions())

    /** The sign-in step on its Quick Connect tab, before a code is asked for, with the tab labels at large font and in Spanish. */
    @PreviewTest
    @JellyfinScreenStatePreview
    @JellyfinFontScalePreviews
    @JellyfinSpanishPreviews
    @JellyfinSpanishFontScalePreviews
    @Composable
    fun quickConnectTab() = JellyfinRoot(previewQuickConnectTab, Actions())

    @PreviewTest
    @JellyfinScreenStatePreview
    @Composable
    fun quickConnectOff() = JellyfinRoot(previewQuickConnectOff, Actions())

    @PreviewTest
    @JellyfinScreenStatePreview
    @Composable
    fun quickConnectNotOffered() = JellyfinRoot(previewQuickConnectNotOffered, Actions())

    @PreviewTest
    @JellyfinScreenStatePreview
    @Composable
    fun quickConnectExpired() = JellyfinRoot(previewQuickConnectExpired, Actions())

    @PreviewTest
    @JellyfinScreenStatePreview
    @Composable
    fun quickConnectGetting() = JellyfinRoot(previewQuickConnectGetting, Actions())

    /** A failure that isn't the password's is said under Sign in, and the field stays unmarked. */
    @PreviewTest
    @JellyfinScreenStatePreview
    @JellyfinSpanishPreviews
    @Composable
    fun signInUnreachable() = JellyfinRoot(previewSignInUnreachable, Actions())

    @PreviewTest
    @JellyfinScreenStatePreview
    @Composable
    fun signInLocalNetworkDenied() = JellyfinRoot(previewSignInLocalNetwork, Actions(localNetworkAsked = true))

    // Signed in.

    @PreviewTest
    @JellyfinHomeLayoutPreviews
    @JellyfinFontScalePreviews
    @JellyfinSpanishPreviews
    @Composable
    fun signedIn() = JellyfinRoot(previewSignedIn, Actions())

    /** An administrator of a server with no libraries: the card above the server group. */
    @PreviewTest
    @JellyfinScreenStatePreview
    @JellyfinSpanishPreviews
    @Composable
    fun signedInNoLibraries() = JellyfinRoot(previewSignedInNoLibraries, Actions())

    @PreviewTest
    @JellyfinScreenStatePreview
    @Composable
    fun signedInChecking() =
        CompositionLocalProvider(LocalFlags provides Flags.of(SharedFlags.BingePromotion to true)) {
            JellyfinRoot(previewSignedInChecking, Actions())
        }

    /** The saved server has not answered yet: nothing of the home, only the wait, until it does or its problem replaces it. */
    @PreviewTest
    @JellyfinScreenStatePreview
    @Composable
    fun signedInWaiting() = JellyfinRoot(previewSignedInWaiting, Actions())

    @PreviewTest
    @JellyfinScreenStatePreview
    @Composable
    fun signedInFailed() =
        CompositionLocalProvider(LocalFlags provides Flags.of(SharedFlags.BingePromotion to true)) {
            JellyfinRoot(previewSignedInFailed, Actions())
        }

    @PreviewTest
    @JellyfinScreenStatePreview
    @JellyfinSpanishPreviews
    @Composable
    fun signedInCouldNotLoad() = JellyfinRoot(previewSignedInCouldNotLoad, Actions())

    @PreviewTest
    @JellyfinScreenStatePreview
    @JellyfinSpanishPreviews
    @Composable
    fun signedInClientCertificate() = JellyfinRoot(previewSignedInClientCertificate, Actions())

    @PreviewTest
    @JellyfinScreenStatePreview
    @JellyfinSpanishPreviews
    @Composable
    fun signedInLocalNetworkDenied() = JellyfinRoot(previewSignedInLocalNetworkDenied, Actions())

    @PreviewTest
    @JellyfinScreenStatePreview
    @Composable
    fun signedInLocalNetworkAsked() = JellyfinRoot(previewSignedInLocalNetworkDenied, Actions(localNetworkAsked = true))

    @PreviewTest
    @JellyfinScreenStatePreview
    @Composable
    fun signedInRetrying() = JellyfinRoot(previewSignedInRetrying, Actions())

    @PreviewTest
    @JellyfinFontScalePreviews
    @Composable
    fun signedInFailedLarge() = JellyfinRoot(previewSignedInFailed, Actions())

    @PreviewTest
    @JellyfinScreenStatePreview
    @Composable
    fun signedInHttps() = JellyfinRoot(previewSignedInHttps, Actions())

    @PreviewTest
    @JellyfinScreenStatePreview
    @JellyfinFontScalePreviews
    @JellyfinSpanishPreviews
    @Composable
    fun signedInAdmin() = JellyfinRoot(previewSignedInAdmin, Actions())

    /** The Live TV row, for an administrator whose policy lets them manage it. A user who is not an administrator is offered none. */
    @PreviewTest
    @JellyfinScreenStatePreview
    @Composable
    fun signedInAdminLiveTv() = JellyfinRoot(previewSignedInAdminLiveTv, Actions())

    /** The server card with three pills at once, on an unencrypted address: an administrator's, at every size and in Spanish. */
    @PreviewTest
    @JellyfinScreenStatePreview
    @JellyfinFontScalePreviews
    @JellyfinSpanishPreviews
    @Composable
    fun serverCardAlerts() = JellyfinRoot(previewSignedInAdminAlerts, Actions())

    /** The note under the server card for the titles with no TMDB id: plain, at every size and in Spanish. */
    @PreviewTest
    @JellyfinScreenStatePreview
    @JellyfinFontScalePreviews
    @JellyfinSpanishPreviews
    @Composable
    fun noTmdbIds() = JellyfinRoot(previewSignedInNoTmdb, Actions())

    /** The same note with the Binge flag on, which names Binge. */
    @PreviewTest
    @JellyfinScreenStatePreview
    @JellyfinSpanishPreviews
    @Composable
    fun noTmdbIdsPromoted() =
        CompositionLocalProvider(LocalFlags provides Flags.of(SharedFlags.BingePromotion to true)) {
            JellyfinRoot(previewSignedInNoTmdb, Actions())
        }

    /** The card before its figures arrive: a placeholder in each, and no Connected. */
    @PreviewTest
    @JellyfinScreenStatePreview
    @Composable
    fun serverCardLoading() = JellyfinRoot(previewSignedInCardLoading, Actions())

    /** An administrator's account, behind the avatar: the rows everyone else has inline on their home. */
    @PreviewTest
    @JellyfinScreenStatePreview
    @JellyfinFontScalePreviews
    @JellyfinSpanishPreviews
    @Composable
    fun account() = AccountScreen(previewSignedInAdmin, Actions(), onBack = {})

    /** The This app page, behind the home's This app row: what the app reports, feedback, and About. */
    @PreviewTest
    @JellyfinScreenStatePreview
    @JellyfinFontScalePreviews
    @JellyfinSpanishPreviews
    @Composable
    fun thisApp() = ThisAppScreen(Actions(), onBack = {})

    /** An administrator's This app page with the notifications switch off: only the switch. */
    @PreviewTest
    @JellyfinScreenStatePreview
    @JellyfinSpanishPreviews
    @Composable
    fun thisAppNotificationsOff() = ThisAppScreen(Actions(notifications = NotificationSettings()), onBack = {})

    /** With the switch on: the cadence, and the system warnings that apply, both here. */
    @PreviewTest
    @JellyfinScreenStatePreview
    @JellyfinFontScalePreviews
    @JellyfinSpanishPreviews
    @Composable
    fun thisAppNotificationsOn() =
        ThisAppScreen(
            Actions(notifications = NotificationSettings(enabled = true, intervalMinutes = 30, blocked = true, unrestricted = false)),
            onBack = {},
        )

    /** On, with nothing in Android's way: the switch and the cadence alone. */
    @PreviewTest
    @JellyfinScreenStatePreview
    @Composable
    fun thisAppNotificationsClear() = ThisAppScreen(Actions(notifications = NotificationSettings(enabled = true)), onBack = {})

    /** The Check every choices, drawn in place because a frame can't capture the sheet's own window. */
    @PreviewTest
    @JellyfinScreenStatePreview
    @JellyfinSpanishPreviews
    @Composable
    fun notificationsInterval() =
        Surface { IntervalChoices(NotificationSettings(enabled = true, intervalMinutes = 120), Actions(), close = {}) }

    /** The settings search before a query, with results (each with where it is) and with none. */
    @PreviewTest
    @JellyfinScreenStatePreview
    @JellyfinFontScalePreviews
    @JellyfinSpanishPreviews
    @Composable
    fun searchPrompt() = SearchPage()

    @PreviewTest
    @JellyfinScreenStatePreview
    @JellyfinFontScalePreviews
    @JellyfinSpanishPreviews
    @Composable
    fun searchResults() = SearchPage(initialQuery = "port")

    @PreviewTest
    @JellyfinScreenStatePreview
    @Composable
    fun searchNoMatch() = SearchPage(initialQuery = "zzz")

    /** The search before a query, with what the user opened last and its Clear. */
    @PreviewTest
    @JellyfinScreenStatePreview
    @JellyfinFontScalePreviews
    @JellyfinSpanishPreviews
    @Composable
    fun searchRecent() =
        SearchPage(
            recentPages = listOf(ConsolePage.Network, ConsolePage.Transcoding, ConsolePage.Playback),
        )

    /** The This app page with the tip jar priced, so its row shows. */
    @PreviewTest
    @JellyfinScreenStatePreview
    @JellyfinFontScalePreviews
    @JellyfinSpanishPreviews
    @Composable
    fun thisAppWithTipJar() = ThisAppScreen(Actions(), onBack = {}, tip = TipState.Ready(TIP_STEPS.associateWith { "£$it" }))

    /** The sign-out confirm's body: a screen frame can't capture the dialog's own window. */
    @PreviewTest
    @JellyfinScreenStatePreview
    @JellyfinFontScalePreviews
    @JellyfinSpanishPreviews
    @Composable
    fun signOutConfirm() = Surface { SignOutConfirm(Actions(), inWindow = false) }

    /** The confirm for taking back the agreement to plain HTTP, whose server is named in its body. */
    @PreviewTest
    @JellyfinScreenStatePreview
    @JellyfinFontScalePreviews
    @JellyfinSpanishPreviews
    @Composable
    fun takeBackConfirm() = Surface { TakeBackConfirm("media.example.com", Actions(), inWindow = false) }

    /** With the Binge flag on: the Library group shows what Binge can see. */
    @PreviewTest
    @JellyfinScreenStatePreview
    @JellyfinSpanishPreviews
    @Composable
    fun signedInPromoted() =
        CompositionLocalProvider(LocalFlags provides Flags.of(SharedFlags.BingePromotion to true)) {
            JellyfinRoot(previewSignedIn, Actions())
        }

    /** With the Binge flag on, signing out says what Binge stops doing. */
    @PreviewTest
    @JellyfinScreenStatePreview
    @Composable
    fun signOutConfirmPromoted() =
        CompositionLocalProvider(LocalFlags provides Flags.of(SharedFlags.BingePromotion to true)) {
            Surface { SignOutConfirm(Actions(), inWindow = false) }
        }

    // Forgot password: each step of the reset.

    @PreviewTest
    @JellyfinScreenStatePreview
    @JellyfinSpanishPreviews
    @JellyfinFontScalePreviews
    @Composable
    fun forgotContactAdmin() =
        Surface { ForgotPasswordOverlay(ForgotPasswordStep.Told(ForgotPasswordAction.ContactAdmin), null, inWindow = false) }

    @PreviewTest
    @JellyfinScreenStatePreview
    @JellyfinSpanishPreviews
    @Composable
    fun forgotInNetworkRequired() =
        Surface { ForgotPasswordOverlay(ForgotPasswordStep.Told(ForgotPasswordAction.InNetworkRequired), null, inWindow = false) }

    @PreviewTest
    @JellyfinScreenStatePreview
    @JellyfinSpanishPreviews
    @JellyfinFontScalePreviews
    @Composable
    fun forgotPin() =
        Surface { ForgotPasswordOverlay(ForgotPasswordStep.EnterPin("/config/data/passwordreset.json"), null, inWindow = false) }

    @PreviewTest
    @JellyfinScreenStatePreview
    @Composable
    fun forgotPinWrong() =
        Surface {
            ForgotPasswordOverlay(
                ForgotPasswordStep.EnterPin("/config/data/passwordreset.json", wrongPin = true),
                null,
                inWindow = false,
            )
        }

    @PreviewTest
    @JellyfinScreenStatePreview
    @JellyfinSpanishPreviews
    @Composable
    fun forgotFailed() = Surface { ForgotPasswordOverlay(ForgotPasswordStep.Failed, null, inWindow = false) }

    @PreviewTest
    @JellyfinScreenStatePreview
    @JellyfinSpanishPreviews
    @Composable
    fun forgotDone() = Surface { ForgotPasswordOverlay(ForgotPasswordStep.Done, null, inWindow = false) }
}

/** The shared search page over this app's real index, on the pinned bar the app opens it with, as an administrator on a current server sees it. */
@Composable
private fun SearchPage(
    initialQuery: String = "",
    recentPages: List<ConsolePage> = emptyList(),
) {
    val resources = LocalContext.current.resources
    val entries = remember(resources) { settingsIndex(resources) }
    val profile = previewSignedInAdmin.profile
    val account = previewSignedInAdmin.account
    SettingsSearchScreen(
        state =
            SettingsSearchState.Ready(
                available = { it.available(profile, account) },
                recent = entries.filter { it.target.page in recentPages && it.pageLabel == null }.distinctBy { it.target.page },
                viewer = Unit,
            ),
        entries = entries,
        icon = { it.target.icon },
        onOpen = { _, _ -> },
        onBack = {},
        onRetry = {},
        onClearRecent = {},
        initialQuery = initialQuery,
        pinnedBar = true,
    )
}
