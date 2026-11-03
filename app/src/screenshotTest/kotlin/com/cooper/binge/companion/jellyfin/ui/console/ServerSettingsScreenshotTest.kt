package com.cooper.binge.companion.jellyfin.ui.console

import android.graphics.Bitmap
import android.graphics.Color
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import com.android.tools.screenshot.PreviewTest
import com.binge.designsystem.SaveStatus
import com.cooper.binge.companion.jellyfin.preview.JellyfinFontScalePreviews
import com.cooper.binge.companion.jellyfin.preview.JellyfinScreenPreviews
import com.cooper.binge.companion.jellyfin.preview.JellyfinScreenStateLightPreview
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
                customSplashscreen = true,
            ),
        cultures = listOf(Culture("English", "en", listOf("eng")), Culture("French", "fr", listOf("fre"))),
        countries = listOf(Country("GB", "United Kingdom"), Country("US", "United States")),
    )

/** A wide picture with a bright disc, so a frame shows how the preview crops. */
private val previewSplash: Bitmap =
    Bitmap.createBitmap(SPLASH_WIDTH, SPLASH_HEIGHT, Bitmap.Config.ARGB_8888).apply {
        eraseColor(Color.rgb(48, 64, 112))
        for (x in 0 until SPLASH_WIDTH) {
            for (y in 0 until SPLASH_HEIGHT) {
                val dx = x - SPLASH_WIDTH / 2
                val dy = y - SPLASH_HEIGHT / 2
                if (dx * dx + dy * dy < SPLASH_DISC * SPLASH_DISC) setPixel(x, y, Color.rgb(240, 200, 80))
            }
        }
    }

private const val SPLASH_WIDTH = 320
private const val SPLASH_HEIGHT = 180
private const val SPLASH_DISC = 50

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
    @JellyfinScreenStateLightPreview
    @JellyfinFontScalePreviews
    @Composable
    fun editingName() = Surface { TextSheet(ServerText.Name, previewSettings, ServerSettingsActions(), inWindow = false) }

    @PreviewTest
    @JellyfinScreenStatePreview
    @JellyfinSpanishPreviews
    @JellyfinScreenStateLightPreview
    @JellyfinFontScalePreviews
    @Composable
    fun editingNameFailed() =
        Surface {
            TextSheet(
                ServerText.Name,
                previewSettings.copy(sheetFailed = ConsoleFailure.Refused),
                ServerSettingsActions(),
                inWindow = false,
            )
        }

    @PreviewTest
    @JellyfinScreenStatePreview
    @Composable
    fun editingNameSubmitting() =
        Surface {
            TextSheet(
                ServerText.Name,
                previewSettings.copy(submitting = ServerText.Name),
                ServerSettingsActions(),
                inWindow = false,
            )
        }

    @PreviewTest
    @JellyfinScreenStatePreview
    @JellyfinScreenStateLightPreview
    @JellyfinFontScalePreviews
    @Composable
    fun editingCss() = Surface { TextSheet(ServerText.CustomCss, previewSettings, ServerSettingsActions(), inWindow = false) }

    @PreviewTest
    @JellyfinScreenStatePreview
    @JellyfinSpanishPreviews
    @Composable
    fun saving() = ServerSettingsScreen(previewSettings.copy(serverSave = SaveProgress(SaveStatus.Saving)), ServerSettingsActions())

    @PreviewTest
    @JellyfinScreenStatePreview
    @JellyfinSpanishPreviews
    @Composable
    fun notSaved() =
        ServerSettingsScreen(
            previewSettings.copy(
                saveFailed = ConsoleFailure.Unreachable,
                serverSave = SaveProgress(failed = true),
            ),
            ServerSettingsActions(),
        )

    @PreviewTest
    @JellyfinScreenStatePreview
    @Composable
    fun loading() = ServerSettingsScreen(ServerSettingsUiState.Loading, ServerSettingsActions())

    @PreviewTest
    @JellyfinScreenStatePreview
    @Composable
    fun failed() = ServerSettingsScreen(ServerSettingsUiState.Failed(ConsoleFailure.Refused), ServerSettingsActions())

    @PreviewTest
    @JellyfinScreenStatePreview
    @JellyfinFontScalePreviews
    @JellyfinSpanishPreviews
    @Composable
    fun splashCustom() =
        Surface {
            SplashSheet(
                custom = true,
                state = SplashUiState(open = true, picture = previewSplash),
                actions = ServerSettingsActions(),
                inWindow = false,
            )
        }

    @PreviewTest
    @JellyfinScreenStatePreview
    @Composable
    fun splashDefaultNoPreview() =
        Surface { SplashSheet(custom = false, state = SplashUiState(open = true), actions = ServerSettingsActions(), inWindow = false) }

    @PreviewTest
    @JellyfinScreenStatePreview
    @JellyfinSpanishPreviews
    @Composable
    fun splashUnknown() =
        Surface {
            SplashSheet(
                custom = false,
                state = SplashUiState(open = true),
                actions = ServerSettingsActions(),
                inWindow = false,
                known = false,
            )
        }

    @PreviewTest
    @JellyfinScreenStatePreview
    @Composable
    fun splashRefused() =
        Surface {
            SplashSheet(
                custom = true,
                state =
                    SplashUiState(
                        open = true,
                        picture = previewSplash,
                        refused = ConsoleFailure.refused("User is not allowed to update the image."),
                    ),
                actions = ServerSettingsActions(),
                inWindow = false,
            )
        }

    @PreviewTest
    @JellyfinScreenStatePreview
    @JellyfinSpanishPreviews
    @Composable
    fun splashRemoving() = Surface { RemoveSplashDialog(ServerSettingsActions(), inWindow = false) }
}
