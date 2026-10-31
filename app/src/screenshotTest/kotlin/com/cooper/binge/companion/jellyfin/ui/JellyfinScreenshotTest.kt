package com.cooper.binge.companion.jellyfin.ui

import androidx.compose.runtime.Composable
import com.android.tools.screenshot.PreviewTest
import com.cooper.binge.companion.jellyfin.preview.JellyfinFontScalePreviews
import com.cooper.binge.companion.jellyfin.preview.JellyfinScreenPreviews
import com.cooper.binge.companion.jellyfin.preview.JellyfinScreenStatePreview
import com.cooper.binge.companion.jellyfin.preview.JellyfinSpanishPreviews

/** The phone screens: each step's layout across the matrix, then every other state on the phone cell alone. */
class JellyfinScreenshotTest {
    // The server step.

    @PreviewTest
    @JellyfinScreenPreviews
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

    // Sign-in.

    @PreviewTest
    @JellyfinScreenPreviews
    @JellyfinFontScalePreviews
    @JellyfinSpanishPreviews
    @Composable
    fun signIn() = JellyfinRoot(previewSignIn, Actions())

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
    fun quickConnectCode() = JellyfinRoot(previewQuickConnect, Actions())

    // Signed in.

    @PreviewTest
    @JellyfinScreenPreviews
    @JellyfinFontScalePreviews
    @JellyfinSpanishPreviews
    @Composable
    fun signedIn() = JellyfinRoot(previewSignedIn, Actions())

    @PreviewTest
    @JellyfinScreenStatePreview
    @Composable
    fun signedInChecking() = JellyfinRoot(previewSignedInChecking, Actions())

    @PreviewTest
    @JellyfinScreenStatePreview
    @Composable
    fun signedInFailed() = JellyfinRoot(previewSignedInFailed, Actions())

    @PreviewTest
    @JellyfinScreenStatePreview
    @Composable
    fun signedInHttps() = JellyfinRoot(previewSignedInHttps, Actions())
}
