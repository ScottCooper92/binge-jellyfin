package com.cooper.binge.companion.jellyfin.ui.tv

import androidx.compose.runtime.Composable
import com.android.tools.screenshot.PreviewTest
import com.cooper.binge.companion.jellyfin.preview.JellyfinTvScreenPreviews
import com.cooper.binge.companion.jellyfin.preview.JellyfinTvStressPreviews
import com.cooper.binge.companion.jellyfin.ui.Actions
import com.cooper.binge.companion.jellyfin.ui.previewEnterServerFound
import com.cooper.binge.companion.jellyfin.ui.previewEnterServerSearching
import com.cooper.binge.companion.jellyfin.ui.previewQuickConnect
import com.cooper.binge.companion.jellyfin.ui.previewQuickConnectGetting
import com.cooper.binge.companion.jellyfin.ui.previewQuickConnectOff
import com.cooper.binge.companion.jellyfin.ui.previewSignIn
import com.cooper.binge.companion.jellyfin.ui.previewSignInWrongPassword
import com.cooper.binge.companion.jellyfin.ui.previewSignedIn
import com.cooper.binge.companion.jellyfin.ui.previewSignedInChecking
import com.cooper.binge.companion.jellyfin.ui.previewSignedInFailed

/** The television's screens: each step's main state in Spanish and at large fonts too, every other state once. */
class TvJellyfinScreenshotTest {
    @PreviewTest
    @JellyfinTvScreenPreviews
    @JellyfinTvStressPreviews
    @Composable
    fun serverFound() = TvJellyfinRoot(previewEnterServerFound, Actions())

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
    fun signedInChecking() = TvJellyfinRoot(previewSignedInChecking, Actions())

    @PreviewTest
    @JellyfinTvScreenPreviews
    @Composable
    fun signedInFailed() = TvJellyfinRoot(previewSignedInFailed, Actions())
}
