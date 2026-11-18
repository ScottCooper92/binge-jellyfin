package com.cooper.homelab.ephyra.ui.console

import androidx.compose.runtime.Composable
import com.android.tools.screenshot.PreviewTest
import com.cooper.homelab.ephyra.preview.JellyfinFontScalePreviews
import com.cooper.homelab.ephyra.preview.JellyfinScreenStatePreview
import com.cooper.homelab.ephyra.preview.JellyfinSpanishPreviews

private val previewFilled = PasswordUiState(current = "old-secret", new = "new-secret", confirm = "new-secret")

/** Changing the password: the filled form across the matrix, then each result on the phone cell. */
class PasswordScreenshotTest {
    @PreviewTest
    @JellyfinScreenStatePreview
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
