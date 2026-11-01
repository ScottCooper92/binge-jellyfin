package com.cooper.binge.companion.jellyfin.ui.console

import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import com.android.tools.screenshot.PreviewTest
import com.cooper.binge.companion.jellyfin.preview.JellyfinFontScalePreviews
import com.cooper.binge.companion.jellyfin.preview.JellyfinScreenPreviews
import com.cooper.binge.companion.jellyfin.preview.JellyfinScreenStatePreview
import com.cooper.binge.companion.jellyfin.preview.JellyfinSpanishPreviews
import com.cooper.binge.companion.jellyfin.server.ServerDevice

private const val NOW = 1_793_534_400_000L // 31 Oct 2026, 12:00 UTC
private const val HOUR = 60 * 60 * 1000L

private val previewDevices =
    listOf(
        ServerDevice("tv", "Living room", "Living room", "Jellyfin Android TV", "Ana", NOW - HOUR / 4),
        ServerDevice("me", "Pixel 10 Pro XL", null, "Binge Jellyfin", "Claude", NOW - HOUR / 60),
        ServerDevice("web", "Firefox", null, "Jellyfin Web", "sam", NOW - 3 * HOUR),
        ServerDevice("kodi", "Den box", null, "Kodi", "sam", NOW - 50 * HOUR),
    )

private val previewReady = DevicesUiState.Ready(previewDevices, thisDevice = "me")

/** Devices: the list across the matrix, then renaming, removing, a change in flight and failed, and the read states. */
class DevicesScreenshotTest {
    @PreviewTest
    @JellyfinScreenPreviews
    @JellyfinFontScalePreviews
    @JellyfinSpanishPreviews
    @Composable
    fun devices() = DevicesScreen(previewReady, DevicesActions(), now = NOW)

    @PreviewTest
    @JellyfinScreenStatePreview
    @JellyfinSpanishPreviews
    @Composable
    fun renaming() = Surface { RenameDialog(previewDevices.first(), DevicesActions(), inWindow = false) }

    @PreviewTest
    @JellyfinScreenStatePreview
    @JellyfinSpanishPreviews
    @Composable
    fun removing() = Surface { RemoveDialog(previewDevices[2], DevicesActions(), inWindow = false) }

    @PreviewTest
    @JellyfinScreenStatePreview
    @Composable
    fun changing() =
        DevicesScreen(previewReady.copy(busy = setOf("kodi"), actionFailed = ConsoleFailure.Unreachable), DevicesActions(), now = NOW)

    @PreviewTest
    @JellyfinScreenStatePreview
    @Composable
    fun loading() = DevicesScreen(DevicesUiState.Loading, DevicesActions())

    @PreviewTest
    @JellyfinScreenStatePreview
    @Composable
    fun failed() = DevicesScreen(DevicesUiState.Failed(ConsoleFailure.Refused), DevicesActions())
}
