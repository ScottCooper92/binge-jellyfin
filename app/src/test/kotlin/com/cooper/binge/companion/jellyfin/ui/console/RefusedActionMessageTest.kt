package com.cooper.binge.companion.jellyfin.ui.console

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import com.binge.designsystem.testing.createTakeDownAndroidComposeRule
import com.cooper.binge.companion.jellyfin.server.ApiKey
import com.cooper.binge.companion.jellyfin.server.NowPlaying
import com.cooper.binge.companion.jellyfin.server.PlayMethod
import com.cooper.binge.companion.jellyfin.server.PluginStatus
import com.cooper.binge.companion.jellyfin.server.ServerDevice
import com.cooper.binge.companion.jellyfin.server.ServerInfo
import com.cooper.binge.companion.jellyfin.server.ServerLibrary
import com.cooper.binge.companion.jellyfin.server.ServerPlugin
import com.cooper.binge.companion.jellyfin.server.ServerUser
import com.cooper.binge.companion.jellyfin.theme.JellyfinTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** An action the server refused is said in a message over the page, with the page's own sentence. */
@RunWith(RobolectricTestRunner::class)
class RefusedActionMessageTest {
    @get:Rule
    val compose = createTakeDownAndroidComposeRule<ComponentActivity>()

    private val info =
        ServerInfo("truenas", "10.11.6", "http://nas:8096", "X64", "", updateAvailable = false, restartPending = false, canRestart = true)

    @Test
    fun `a device change the server refused says so`() {
        val kodi = ServerDevice("k", "Kodi", null, "Kodi", "ana", null)
        compose.setContent {
            JellyfinTheme {
                DevicesScreen(
                    DevicesUiState.Ready(listOf(kodi), "me", actionFailed = ConsoleFailure.Refused),
                    DevicesActions(),
                )
            }
        }
        compose.onNodeWithText("Not changed. Something went wrong. Try again.").assertExists()
    }

    @Test
    fun `a stream the server wouldn't stop says so`() {
        val stream = NowPlaying("s", "ana", "TV", "Jellyfin", "Sintel", null, 0L, null, false, PlayMethod.DirectPlay, emptyList())
        compose.setContent {
            JellyfinTheme {
                NowPlayingScreen(
                    NowPlayingUiState.Ready(listOf(stream), stopFailed = ConsoleFailure.Unreachable),
                    true,
                    NowPlayingActions(),
                )
            }
        }
        compose.onNodeWithText("Not stopped. Couldn't reach your Jellyfin server", substring = true).assertExists()
    }

    @Test
    fun `a restart the server didn't take is a message, and one under way stays on the page`() {
        compose.setContent { JellyfinTheme { ServerScreen(ServerUiState.Ready(info, restart = Restart.Failed), ServerActions()) } }
        compose.onNodeWithText("The server didn't take the restart. Try again.").assertExists()
    }

    @Test
    fun `a restart under way is said on the page, not as a message that comes and goes`() {
        compose.setContent { JellyfinTheme { ServerScreen(ServerUiState.Ready(info, restart = Restart.Restarting), ServerActions()) } }
        compose.onNodeWithText("Restarting… This page carries on when the server is back.").assertExists()
        compose.onAllNodesWithText("The server didn't take the restart. Try again.").assertCountEquals(0)
    }

    @Test
    fun `an API key change the server refused says so`() {
        compose.setContent {
            JellyfinTheme {
                ApiKeysScreen(
                    ApiKeysUiState.Ready(listOf(ApiKey("0f3a9c2e", "Sonarr", null)), actionFailed = ConsoleFailure.Refused),
                    ApiKeysActions(),
                )
            }
        }
        compose.onNodeWithText("Not changed. Something went wrong. Try again.").assertExists()
    }

    @Test
    fun `a scan that didn't start says so`() {
        val libraries = listOf(ServerLibrary("m", "Movies", "movies", listOf("/media/movies")))
        compose.setContent {
            JellyfinTheme {
                LibrariesScreen(
                    LibrariesUiState.Ready(libraries, TaskBoard(emptyList()), actionFailed = ConsoleFailure.Refused),
                    LibrariesActions(),
                )
            }
        }
        compose.onNodeWithText("The scan didn't start. Something went wrong. Try again.").assertExists()
    }

    @Test
    fun `a plugin change the server refused says so, on the plugin's screen`() {
        val plugin = ServerPlugin("a", "AniDB", "1.2.3.4", "", PluginStatus.Active, canUninstall = true, hasSettings = false)
        compose.setContent {
            JellyfinTheme {
                PluginScreen(
                    PluginsUiState.Ready(listOf(plugin), actionFailed = ConsoleFailure.Refused),
                    "a",
                    PluginsActions(),
                    onBack = {},
                )
            }
        }
        compose.onNodeWithText("Not changed. Something went wrong. Try again.").assertExists()
    }

    @Test
    fun `the plugins list does not say a plugin's refusal, since its own screen does`() {
        val plugin = ServerPlugin("a", "AniDB", "1.2.3.4", "", PluginStatus.Active, canUninstall = true, hasSettings = false)
        compose.setContent {
            JellyfinTheme {
                PluginsScreen(
                    PluginsUiState.Ready(listOf(plugin), actionFailed = ConsoleFailure.Refused),
                    CatalogUiState.Loading,
                    RepositoriesUiState.Loading,
                    PluginsActions(),
                    CatalogActions(),
                    RepositoriesActions(),
                    onBack = {},
                )
            }
        }
        compose.onAllNodesWithText("Not changed. Something went wrong. Try again.").assertCountEquals(0)
    }

    @Test
    fun `a user change the server refused says so`() {
        val ana = ServerUser("ana", "Ana", true, null, kotlinx.serialization.json.JsonObject(emptyMap()))
        compose.setContent {
            JellyfinTheme {
                UsersScreen(UsersUiState.Ready(listOf(ana), emptyList(), me = "me", actionFailed = ConsoleFailure.Refused), UsersActions())
            }
        }
        compose.onNodeWithText("Not changed. Something went wrong. Try again.").assertExists()
    }

    @Test
    fun `a user added is said in a message`() {
        val ana = ServerUser("ana", "Ana", true, null, kotlinx.serialization.json.JsonObject(emptyMap()))
        compose.setContent {
            JellyfinTheme { UsersScreen(UsersUiState.Ready(listOf(ana), emptyList(), me = "me", done = UserDone.Created), UsersActions()) }
        }
        compose.onNodeWithText("User added.").assertExists()
    }

    private companion object {
        const val SNACKBAR_GONE_MILLIS = 10_000L
        const val SNACKBAR_SHOWN_MILLIS = 1_000L
    }
}
