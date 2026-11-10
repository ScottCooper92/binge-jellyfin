package com.cooper.binge.companion.jellyfin.ui.console

import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import com.binge.designsystem.layout.LayoutAnchors
import com.binge.designsystem.testing.assertSkeletonReservesGeometry
import com.binge.designsystem.testing.createTakeDownAndroidComposeRule
import com.cooper.binge.companion.jellyfin.FakeJellyfin
import com.cooper.binge.companion.jellyfin.server.AdvancedSettings
import com.cooper.binge.companion.jellyfin.server.Branding
import com.cooper.binge.companion.jellyfin.server.Culture
import com.cooper.binge.companion.jellyfin.server.HomeLayout
import com.cooper.binge.companion.jellyfin.server.JellyfinServerProfile
import com.cooper.binge.companion.jellyfin.server.LIBRARY_MONITOR_DELAY
import com.cooper.binge.companion.jellyfin.server.PlaybackPreferences
import com.cooper.binge.companion.jellyfin.server.ServerSettings
import com.cooper.binge.companion.jellyfin.server.SubtitleMode
import com.cooper.binge.companion.jellyfin.server.TrickplaySettings
import com.cooper.binge.companion.jellyfin.server.UserView
import com.cooper.binge.companion.jellyfin.server.networkKeys
import com.cooper.binge.companion.jellyfin.server.networkSettings
import com.cooper.binge.companion.jellyfin.theme.JellyfinTheme
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * A settings page under `FormScreen` loads as a skeleton that holds its first group where the settled page puts it: the form's
 * own content inset around it and the page's intro sentence above it, so nothing jumps when the settings arrive.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp")
class SettingsSkeletonGeometryTest {
    @get:Rule
    val compose = createTakeDownAndroidComposeRule<ComponentActivity>()

    private fun ComposeContentTestRule.holdsFirstGroup(page: @Composable (resolved: Boolean) -> Unit) =
        assertSkeletonReservesGeometry(
            listOf(LayoutAnchors.section(LayoutAnchors.Collection.FIRST_ITEM)),
            checkStartEdge = true,
        ) { resolved -> JellyfinTheme { page(resolved) } }

    @Test
    fun `trickplay`() =
        compose.holdsFirstGroup { resolved ->
            TrickplayScreen(
                state = if (resolved) TrickplayUiState.Ready(TrickplaySettings()) else TrickplayUiState.Loading,
                actions = TrickplayActions(),
            )
        }

    @Test
    fun `advanced`() =
        compose.holdsFirstGroup { resolved ->
            AdvancedScreen(
                state =
                    if (resolved) {
                        AdvancedUiState.Ready(AdvancedSettings(present = setOf(LIBRARY_MONITOR_DELAY)))
                    } else {
                        AdvancedUiState.Loading
                    },
                actions = AdvancedActions(),
            )
        }

    @Test
    fun `home layout`() =
        compose.holdsFirstGroup { resolved ->
            HomeLayoutScreen(
                state =
                    if (resolved) {
                        HomeLayoutUiState.Ready(listOf(UserView("a", "Movies", "movies")), HomeLayout())
                    } else {
                        HomeLayoutUiState.Loading
                    },
                actions = HomeLayoutActions(),
            )
        }

    @Test
    fun `network`() {
        val keys = networkKeys(JellyfinServerProfile.from("10.10.7"))
        val settings = Json.parseToJsonElement(FakeJellyfin().also { it.close() }.networkConfiguration).jsonObject.networkSettings(keys)
        compose.holdsFirstGroup { resolved ->
            NetworkScreen(
                state = if (resolved) NetworkUiState.Ready(settings, keys) else NetworkUiState.Loading,
                actions = NetworkActions(),
                appUrl = "http://nas:8096",
            )
        }
    }

    @Test
    fun `playback`() =
        compose.holdsFirstGroup { resolved ->
            PlaybackScreen(
                state =
                    if (resolved) {
                        PlaybackUiState.Ready(
                            PlaybackPreferences("eng", "", SubtitleMode.Default),
                            listOf(Culture("English", "en", listOf("eng"))),
                        )
                    } else {
                        PlaybackUiState.Loading
                    },
                actions = PlaybackActions(),
            )
        }

    @Test
    fun `profile picture`() =
        compose.holdsFirstGroup { resolved ->
            ProfilePictureScreen(
                state = if (resolved) ProfilePictureUiState.Ready() else ProfilePictureUiState.Loading,
                actions = ProfilePictureActions(),
            )
        }

    @Test
    fun `server settings`() =
        compose.holdsFirstGroup { resolved ->
            ServerSettingsScreen(
                state =
                    if (resolved) {
                        ServerSettingsUiState.Ready(ServerSettings("truenas", "en", "GB"), Branding())
                    } else {
                        ServerSettingsUiState.Loading
                    },
                actions = ServerSettingsActions(),
            )
        }
}
