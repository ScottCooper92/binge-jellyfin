package com.cooper.binge.companion.jellyfin.ui.console

import androidx.compose.runtime.Composable
import com.android.tools.screenshot.PreviewTest
import com.cooper.binge.companion.jellyfin.preview.JellyfinFontScalePreviews
import com.cooper.binge.companion.jellyfin.preview.JellyfinScreenPreviews
import com.cooper.binge.companion.jellyfin.preview.JellyfinScreenStatePreview
import com.cooper.binge.companion.jellyfin.preview.JellyfinSpanishPreviews

/** Approving a Quick Connect code: the page across the matrix, then each result on the phone cell. */
class ApproveCodeScreenshotTest {
    @PreviewTest
    @JellyfinScreenPreviews
    @JellyfinFontScalePreviews
    @JellyfinSpanishPreviews
    @Composable
    fun typed() = ApproveCodeScreen(ApproveUiState(code = "482913"), ApproveActions())

    @PreviewTest
    @JellyfinScreenStatePreview
    @Composable
    fun empty() = ApproveCodeScreen(ApproveUiState(), ApproveActions())

    @PreviewTest
    @JellyfinScreenStatePreview
    @JellyfinSpanishPreviews
    @Composable
    fun approved() = ApproveCodeScreen(ApproveUiState(result = ApproveResult.Approved), ApproveActions())

    @PreviewTest
    @JellyfinScreenStatePreview
    @JellyfinSpanishPreviews
    @Composable
    fun wrongCode() = ApproveCodeScreen(ApproveUiState(code = "111111", result = ApproveResult.WrongCode), ApproveActions())

    @PreviewTest
    @JellyfinScreenStatePreview
    @Composable
    fun failed() = ApproveCodeScreen(ApproveUiState(code = "111111", result = ApproveResult.Failed), ApproveActions())
}
