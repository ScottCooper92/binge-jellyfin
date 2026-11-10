package com.cooper.binge.companion.jellyfin.ui.console

import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import com.binge.designsystem.layout.LayoutAnchors
import com.binge.designsystem.testing.assertSkeletonReservesGeometry
import com.binge.designsystem.testing.createTakeDownAndroidComposeRule
import com.cooper.binge.companion.jellyfin.server.ApiKey
import com.cooper.binge.companion.jellyfin.server.LiveTvSummary
import com.cooper.binge.companion.jellyfin.server.NowPlaying
import com.cooper.binge.companion.jellyfin.server.PlayMethod
import com.cooper.binge.companion.jellyfin.server.ServerDevice
import com.cooper.binge.companion.jellyfin.server.ServerTask
import com.cooper.binge.companion.jellyfin.server.ServerUser
import com.cooper.binge.companion.jellyfin.server.TunerHostSummary
import com.cooper.binge.companion.jellyfin.theme.JellyfinTheme
import kotlinx.serialization.json.JsonObject
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * A list page that opens with an intro loads as a skeleton that holds its first group where the settled list puts it: the intro
 * sentence is in the skeleton too, so nothing jumps when the list arrives.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp")
class ListSkeletonGeometryTest {
    @get:Rule
    val compose = createTakeDownAndroidComposeRule<ComponentActivity>()

    private val now = 1_793_534_400_000L

    private fun ComposeContentTestRule.holdsFirstGroup(page: @Composable (resolved: Boolean) -> Unit) =
        assertSkeletonReservesGeometry(
            listOf(LayoutAnchors.section(LayoutAnchors.Collection.FIRST_ITEM)),
            checkStartEdge = true,
        ) { resolved -> JellyfinTheme { page(resolved) } }

    @Test
    fun `api keys`() =
        compose.holdsFirstGroup { resolved ->
            ApiKeysScreen(
                state = if (resolved) ApiKeysUiState.Ready(listOf(ApiKey("k1", "Home Assistant", now))) else ApiKeysUiState.Loading,
                actions = ApiKeysActions(),
                now = now,
            )
        }

    @Test
    fun `devices`() =
        compose.holdsFirstGroup { resolved ->
            DevicesScreen(
                state =
                    if (resolved) {
                        DevicesUiState.Ready(listOf(ServerDevice("tv", "Living room", null, "Jellyfin", "Ana", now)), thisDevice = "me")
                    } else {
                        DevicesUiState.Loading
                    },
                actions = DevicesActions(),
                now = now,
            )
        }

    @Test
    fun `live tv`() =
        compose.holdsFirstGroup { resolved ->
            LiveTvScreen(
                state =
                    if (resolved) {
                        LiveTvUiState.Ready(
                            LiveTvSummary(tuners = listOf(TunerHostSummary("t1", "HDHomeRun", "hdhomerun", "http://tuner"))),
                        )
                    } else {
                        LiveTvUiState.Loading
                    },
                actions = LiveTvActions(),
                now = now,
            )
        }

    @Test
    fun `now playing`() {
        val stream =
            NowPlaying("tv", "Ana", "Living room", "Jellyfin", "Severance", null, 0L, 1L, false, PlayMethod.DirectPlay, emptyList())
        compose.holdsFirstGroup { resolved ->
            NowPlayingScreen(
                state = if (resolved) NowPlayingUiState.Ready(listOf(stream)) else NowPlayingUiState.Loading,
                administrator = true,
                actions = NowPlayingActions(),
            )
        }
    }

    @Test
    fun `tasks`() {
        val task = ServerTask("scan", "RefreshLibrary", "Scan Media Library", "", "Library", false, false, null, null)
        compose.holdsFirstGroup { resolved ->
            TasksScreen(
                state = if (resolved) TasksUiState.Ready(TaskBoard(listOf(task))) else TasksUiState.Loading,
                actions = TasksActions(),
                now = now,
            )
        }
    }

    @Test
    fun `users`() =
        compose.holdsFirstGroup { resolved ->
            UsersScreen(
                state =
                    if (resolved) {
                        UsersUiState.Ready(listOf(ServerUser("ana", "Ana", true, now, JsonObject(emptyMap()))), emptyList(), me = "me")
                    } else {
                        UsersUiState.Loading
                    },
                actions = UsersActions(),
                now = now,
            )
        }
}
