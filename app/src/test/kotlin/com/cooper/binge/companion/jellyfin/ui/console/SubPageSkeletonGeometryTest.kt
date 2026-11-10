package com.cooper.binge.companion.jellyfin.ui.console

import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import com.binge.designsystem.layout.LayoutAnchors
import com.binge.designsystem.testing.assertSkeletonReservesGeometry
import com.binge.designsystem.testing.createTakeDownAndroidComposeRule
import com.cooper.binge.companion.jellyfin.server.HomeLayout
import com.cooper.binge.companion.jellyfin.server.JellyfinServerProfile
import com.cooper.binge.companion.jellyfin.server.LogFile
import com.cooper.binge.companion.jellyfin.server.NowPlaying
import com.cooper.binge.companion.jellyfin.server.PlayMethod
import com.cooper.binge.companion.jellyfin.server.ServerTask
import com.cooper.binge.companion.jellyfin.server.ServerUser
import com.cooper.binge.companion.jellyfin.server.TaskTrigger
import com.cooper.binge.companion.jellyfin.server.UserView
import com.cooper.binge.companion.jellyfin.theme.JellyfinTheme
import kotlinx.serialization.json.JsonObject
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The sub-pages that open on one item of a list (a library, a user, a task, a stream) hold their first group where it settles. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp")
class SubPageSkeletonGeometryTest {
    @get:Rule
    val compose = createTakeDownAndroidComposeRule<ComponentActivity>()

    private val now = 1_793_534_400_000L

    private fun ComposeContentTestRule.holdsFirstGroup(page: @Composable (resolved: Boolean) -> Unit) =
        assertSkeletonReservesGeometry(
            listOf(LayoutAnchors.section(LayoutAnchors.Collection.FIRST_ITEM)),
            checkStartEdge = true,
        ) { resolved -> JellyfinTheme { page(resolved) } }

    private val sam = ServerUser("sam", "Sam", true, now, JsonObject(emptyMap()))

    @Test
    fun `a library's home screen page`() =
        compose.holdsFirstGroup { resolved ->
            HomeLayoutLibraryScreen(
                state =
                    if (resolved) {
                        HomeLayoutUiState.Ready(listOf(UserView("a", "Movies", "movies")), HomeLayout())
                    } else {
                        HomeLayoutUiState.Loading
                    },
                libraryId = "a",
                actions = HomeLayoutActions(),
            )
        }

    @Test
    fun `a user's page`() =
        compose.holdsFirstGroup { resolved ->
            UserScreen(
                state = if (resolved) UsersUiState.Ready(listOf(sam), emptyList(), me = "me") else UsersUiState.Loading,
                userId = "sam",
                actions = UsersActions(),
            )
        }

    @Test
    fun `a section of a user's settings`() =
        compose.holdsFirstGroup { resolved ->
            UserSectionScreen(
                state = if (resolved) UsersUiState.Ready(listOf(sam), emptyList(), me = "me") else UsersUiState.Loading,
                userId = "sam",
                section = UserSection.Playback,
                choices = UserChoices(),
                profile = JellyfinServerProfile.from("10.11.6"),
                actions = UsersActions(),
            )
        }

    @Test
    fun `a task's triggers`() {
        val task =
            ServerTask(
                "scan",
                "RefreshLibrary",
                "Scan Media Library",
                "",
                "Library",
                false,
                false,
                null,
                null,
                listOf(TaskTrigger("StartupTrigger")),
            )
        compose.holdsFirstGroup { resolved ->
            TaskScreen(
                state = if (resolved) TasksUiState.Ready(TaskBoard(listOf(task))) else TasksUiState.Loading,
                taskId = "scan",
                actions = TasksActions(),
            )
        }
    }

    @Test
    fun `the log files, with the retention row above them`() =
        compose.holdsFirstGroup { resolved ->
            LogsScreen(
                state =
                    if (resolved) {
                        LogsUiState.Ready(listOf(LogFile("log_20261031.log", 2048L, now)), retention = 3)
                    } else {
                        LogsUiState.Loading
                    },
                actions = LogsActions(),
            )
        }

    @Test
    fun `a stream's page`() {
        val stream =
            NowPlaying("tv", "Ana", "Living room", "Jellyfin", "Severance", null, 0L, 1L, false, PlayMethod.DirectPlay, emptyList())
        compose.holdsFirstGroup { resolved ->
            NowPlayingSessionScreen(
                state = if (resolved) NowPlayingUiState.Ready(listOf(stream)) else NowPlayingUiState.Loading,
                sessionId = "tv",
                actions = NowPlayingActions(),
            )
        }
    }
}
