package com.cooper.binge.companion.jellyfin.ui.console

import androidx.compose.runtime.Composable
import com.android.tools.screenshot.PreviewTest
import com.cooper.binge.companion.jellyfin.preview.JellyfinFontScalePreviews
import com.cooper.binge.companion.jellyfin.preview.JellyfinScreenPreviews
import com.cooper.binge.companion.jellyfin.preview.JellyfinScreenStatePreview
import com.cooper.binge.companion.jellyfin.preview.JellyfinSpanishPreviews

private val previewFilled = PasswordUiState(current = "old-secret", new = "new-secret", confirm = "new-secret")

/** Changing the password: the filled form across the matrix, then each result on the phone cell. */
class PasswordScreenshotTest {
    @PreviewTest
    @JellyfinScreenPreviews
    @JellyfinFontScalePreviews
    @JellyfinSpanishPreviews
    @Composable
    fun filled() = PasswordScreen(previewFilled, PasswordActions())

    @PreviewTest
    @JellyfinScreenStatePreview
    @Composable
    fun empty() = PasswordScreen(PasswordUiState(), PasswordActions())

    @PreviewTest
    @JellyfinScreenStatePreview
    @JellyfinSpanishPreviews
    @Composable
    fun changed() = PasswordScreen(PasswordUiState(result = PasswordResult.Changed), PasswordActions())

    @PreviewTest
    @JellyfinScreenStatePreview
    @JellyfinSpanishPreviews
    @Composable
    fun wrongCurrent() = PasswordScreen(previewFilled.copy(result = PasswordResult.WrongCurrent), PasswordActions())

    @PreviewTest
    @JellyfinScreenStatePreview
    @Composable
    fun mismatch() = PasswordScreen(previewFilled.copy(confirm = "new-secert", result = PasswordResult.Mismatch), PasswordActions())
}
