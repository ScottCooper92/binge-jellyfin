package com.cooper.binge.companion.jellyfin.ui.console

import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import com.android.tools.screenshot.PreviewTest
import com.cooper.binge.companion.jellyfin.preview.JellyfinFontScalePreviews
import com.cooper.binge.companion.jellyfin.preview.JellyfinScreenPreviews
import com.cooper.binge.companion.jellyfin.preview.JellyfinScreenStatePreview
import com.cooper.binge.companion.jellyfin.preview.JellyfinSpanishPreviews
import com.cooper.binge.companion.jellyfin.server.Branding
import com.cooper.binge.companion.jellyfin.server.Country
import com.cooper.binge.companion.jellyfin.server.Culture
import com.cooper.binge.companion.jellyfin.server.ServerSettings

private val previewSettings =
    ServerSettingsUiState.Ready(
        settings = ServerSettings("truenas", "en", "GB", quickConnect = true),
        branding =
            Branding(
                loginDisclaimer = "Family only. Ask Scott for an account if you'd like one for the living room TV.",
                customCss = ".skinHeader { background: #101820; }\n.cardBox { border-radius: 12px; }\n",
                splashscreen = true,
            ),
        cultures = listOf(Culture("English", "en", listOf("eng")), Culture("French", "fr", listOf("fre"))),
        countries = listOf(Country("GB", "United Kingdom"), Country("US", "United States")),
    )

/** The server settings page across the matrix, without the 10.9 splash screen, each dialog, then each other state. */
class ServerSettingsScreenshotTest {
    @PreviewTest
    @JellyfinScreenPreviews
    @JellyfinFontScalePreviews
    @JellyfinSpanishPreviews
    @Composable
    fun settings() = ServerSettingsScreen(previewSettings, ServerSettingsActions())

    @PreviewTest
    @JellyfinScreenStatePreview
    @Composable
    fun beforeSplashscreens() =
        ServerSettingsScreen(
            previewSettings.copy(branding = Branding(), settings = ServerSettings(name = "truenas")),
            ServerSettingsActions(),
            splashscreenAvailable = false,
        )

    @PreviewTest
    @JellyfinScreenStatePreview
    @JellyfinSpanishPreviews
    @Composable
    fun editingName() = Surface { TextDialog(ServerText.Name, previewSettings, ServerSettingsActions(), inWindow = false) }

    @PreviewTest
    @JellyfinScreenStatePreview
    @Composable
    fun editingCss() = Surface { TextDialog(ServerText.CustomCss, previewSettings, ServerSettingsActions(), inWindow = false) }

    @PreviewTest
    @JellyfinScreenStatePreview
    @JellyfinSpanishPreviews
    @Composable
    fun notSaved() = ServerSettingsScreen(previewSettings.copy(saveFailed = ConsoleFailure.Unreachable), ServerSettingsActions())

    @PreviewTest
    @JellyfinScreenStatePreview
    @Composable
    fun loading() = ServerSettingsScreen(ServerSettingsUiState.Loading, ServerSettingsActions())

    @PreviewTest
    @JellyfinScreenStatePreview
    @Composable
    fun failed() = ServerSettingsScreen(ServerSettingsUiState.Failed(ConsoleFailure.Refused), ServerSettingsActions())
}
