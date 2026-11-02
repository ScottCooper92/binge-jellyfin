package com.cooper.binge.companion.jellyfin.ui.console

import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import com.android.tools.screenshot.PreviewTest
import com.binge.designsystem.SaveStatus
import com.cooper.binge.companion.jellyfin.preview.JellyfinFontScalePreviews
import com.cooper.binge.companion.jellyfin.preview.JellyfinScreenPreviews
import com.cooper.binge.companion.jellyfin.preview.JellyfinScreenStateLightPreview
import com.cooper.binge.companion.jellyfin.preview.JellyfinScreenStatePreview
import com.cooper.binge.companion.jellyfin.preview.JellyfinSpanishPreviews
import com.cooper.binge.companion.jellyfin.server.AdvancedSettings
import com.cooper.binge.companion.jellyfin.server.CastReceiver

private val everyKey =
    setOf(
        "ActivityLogRetentionDays",
        "LibraryMonitorDelay",
        "LibraryUpdateDuration",
        "LibraryScanFanoutConcurrency",
        "ParallelImageEncodingLimit",
        "ImageExtractionTimeoutMs",
        "EnableSlowResponseWarning",
        "SlowResponseThresholdMs",
        "EnableMetrics",
        "AllowClientLogUpload",
        "CorsHosts",
        "InactiveSessionThreshold",
        "CastReceiverApplications",
        "CacheSize",
        "EnableLegacyAuthorization",
    )

private val previewSettings =
    AdvancedSettings(
        present = everyKey,
        activityLogRetentionDays = 30,
        libraryMonitorDelay = 60,
        libraryUpdateDuration = 30,
        slowResponseWarning = true,
        slowResponseThresholdMs = 500,
        corsHosts = listOf("*"),
        castReceivers = listOf(CastReceiver("CC1AD845", "Default Media Receiver")),
        cacheSize = 800,
        legacyAuthorization = true,
    )

private val previewReady = AdvancedUiState.Ready(previewSettings)

/** Advanced: the page across the matrix, an older server's, each sheet, then each other state. */
class AdvancedScreenshotTest {
    @PreviewTest
    @JellyfinScreenPreviews
    @JellyfinFontScalePreviews
    @JellyfinSpanishPreviews
    @Composable
    fun advanced() = AdvancedScreen(previewReady, AdvancedActions())

    @PreviewTest
    @JellyfinScreenStatePreview
    @Composable
    fun olderServer() =
        AdvancedScreen(
            AdvancedUiState.Ready(previewSettings.copy(present = everyKey - "CacheSize" - "EnableLegacyAuthorization")),
            AdvancedActions(),
            AdvancedGates(cacheAndLegacyAuth = false),
        )

    @PreviewTest
    @JellyfinScreenStatePreview
    @Composable
    fun oldestServer() =
        AdvancedScreen(
            AdvancedUiState.Ready(previewSettings),
            AdvancedActions(),
            AdvancedGates(performance = false, cacheAndLegacyAuth = false),
        )

    @PreviewTest
    @JellyfinScreenStatePreview
    @JellyfinSpanishPreviews
    @JellyfinScreenStateLightPreview
    @JellyfinFontScalePreviews
    @Composable
    fun number() = Surface { NumberSheet(AdvancedNumber.RetentionDays, previewSettings, AdvancedActions(), inWindow = false) }

    @PreviewTest
    @JellyfinScreenStatePreview
    @Composable
    fun hosts() = Surface { ListSheet(AdvancedList.CorsHosts, previewSettings, AdvancedActions(), inWindow = false) }

    @PreviewTest
    @JellyfinScreenStatePreview
    @Composable
    fun castReceivers() = Surface { ListSheet(AdvancedList.CastReceivers, previewSettings, AdvancedActions(), inWindow = false) }

    @PreviewTest
    @JellyfinScreenStatePreview
    @Composable
    fun saving() = AdvancedScreen(previewReady.copy(save = SaveProgress(SaveStatus.Saving)), AdvancedActions())

    @PreviewTest
    @JellyfinScreenStatePreview
    @Composable
    fun unsent() = AdvancedScreen(previewReady.copy(save = SaveProgress(SaveStatus.Idle, failed = true)), AdvancedActions())

    @PreviewTest
    @JellyfinScreenStatePreview
    @Composable
    fun loading() = AdvancedScreen(AdvancedUiState.Loading, AdvancedActions())

    @PreviewTest
    @JellyfinScreenStatePreview
    @Composable
    fun failed() = AdvancedScreen(AdvancedUiState.Failed(ConsoleFailure.Refused), AdvancedActions())
}
