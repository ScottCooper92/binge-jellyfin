package com.cooper.binge.companion.jellyfin.ui.console

import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import com.android.tools.screenshot.PreviewTest
import com.cooper.binge.companion.jellyfin.preview.JellyfinFontScalePreviews
import com.cooper.binge.companion.jellyfin.preview.JellyfinScreenPreviews
import com.cooper.binge.companion.jellyfin.preview.JellyfinScreenStatePreview
import com.cooper.binge.companion.jellyfin.preview.JellyfinSpanishPreviews
import com.cooper.binge.companion.jellyfin.server.ApiKey

private const val NOW = 1_793_534_400_000L // 31 Oct 2026, 12:00 UTC
private const val DAY = 24 * 60 * 60 * 1000L

private val previewKeys =
    listOf(
        ApiKey("0f3a9c2e7b1d4a68b5c0e91d2f7a3b4c", "Home Assistant", NOW - 2 * DAY),
        ApiKey("9d81e4a7c3f2406bb1d5e8a07c92f6d3", "Sonarr", NOW - 20 * DAY),
        ApiKey("c47b0e91a2d3485fa6e1d90b3c58f27a", "", NOW - 90 * DAY),
    )

private val previewReady = ApiKeysUiState.Ready(previewKeys)

/** API keys: the list across the matrix, then the dialogs (new, shown once, revoke), a change failed, empty and the read states. */
class ApiKeysScreenshotTest {
    @PreviewTest
    @JellyfinScreenPreviews
    @JellyfinFontScalePreviews
    @JellyfinSpanishPreviews
    @Composable
    fun apiKeys() = ApiKeysScreen(previewReady, ApiKeysActions(), now = NOW)

    @PreviewTest
    @JellyfinScreenStatePreview
    @JellyfinSpanishPreviews
    @Composable
    fun naming() = Surface { NewKeyDialog(ApiKeysActions(), inWindow = false) }

    @PreviewTest
    @JellyfinScreenStatePreview
    @JellyfinSpanishPreviews
    @Composable
    fun created() = Surface { CreatedKeyDialog(previewKeys.first(), ApiKeysActions(), inWindow = false) }

    @PreviewTest
    @JellyfinScreenStatePreview
    @JellyfinSpanishPreviews
    @Composable
    fun revoking() = Surface { RevokeDialog(previewKeys[1], ApiKeysActions(), inWindow = false) }

    @PreviewTest
    @JellyfinScreenStatePreview
    @Composable
    fun failed() = ApiKeysScreen(previewReady.copy(actionFailed = ConsoleFailure.Unreachable), ApiKeysActions(), now = NOW)

    @PreviewTest
    @JellyfinScreenStatePreview
    @JellyfinSpanishPreviews
    @Composable
    fun empty() = ApiKeysScreen(ApiKeysUiState.Ready(emptyList()), ApiKeysActions(), now = NOW)

    @PreviewTest
    @JellyfinScreenStatePreview
    @Composable
    fun loading() = ApiKeysScreen(ApiKeysUiState.Loading, ApiKeysActions())

    @PreviewTest
    @JellyfinScreenStatePreview
    @Composable
    fun readFailed() = ApiKeysScreen(ApiKeysUiState.Failed(ConsoleFailure.Refused), ApiKeysActions())
}
