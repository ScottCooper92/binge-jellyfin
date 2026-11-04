package com.cooper.binge.companion.jellyfin.ui.console

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.binge.designsystem.testing.createTakeDownAndroidComposeRule
import com.cooper.binge.companion.jellyfin.server.ServerLibrary
import com.cooper.binge.companion.jellyfin.server.ServerTask
import com.cooper.binge.companion.jellyfin.server.ServerUser
import com.cooper.binge.companion.jellyfin.theme.JellyfinTheme
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * A page's create action is its footer's primary button: there from the first frame but unusable while the list loads,
 * working once it is ready (the empty list included), and gone when a failure replaces the page.
 */
@RunWith(RobolectricTestRunner::class)
class CreateFooterTest {
    @get:Rule
    val compose = createTakeDownAndroidComposeRule<ComponentActivity>()

    @Test
    fun `API keys has New key in the footer, disabled while loading, working once ready, and gone on a failure`() {
        var asked = 0
        var state by mutableStateOf<ApiKeysUiState>(ApiKeysUiState.Loading)
        compose.setContent { JellyfinTheme { ApiKeysScreen(state, ApiKeysActions(onAskCreate = { asked++ })) } }
        compose.onNodeWithText("New key").assertIsNotEnabled()
        state = ApiKeysUiState.Ready(emptyList())
        compose.waitForIdle()
        compose.onNodeWithText("New key").assertIsEnabled().performClick()
        assertEquals(1, asked)
        state = ApiKeysUiState.Failed(ConsoleFailure.Unreachable)
        compose.waitForIdle()
        compose.onAllNodesWithText("New key").assertCountEquals(0)
    }

    @Test
    fun `Libraries has Add library in the footer, once, including when there are none`() {
        var adding = false
        var state by mutableStateOf<LibrariesUiState>(LibrariesUiState.Loading)
        compose.setContent { JellyfinTheme { LibrariesScreen(state, LibrariesActions(onAskAdd = { adding = it })) } }
        compose.onNodeWithText("Add library").assertIsNotEnabled()
        state = LibrariesUiState.Ready(emptyList(), TaskBoard(emptyList()))
        compose.waitForIdle()
        // The empty state no longer carries its own Add: the footer is the one way in, so there is exactly one.
        compose.onAllNodesWithText("Add library").assertCountEquals(1)
        compose.onNodeWithText("Add library").assertIsEnabled().performClick()
        assertEquals(true, adding)
        state = LibrariesUiState.Failed(ConsoleFailure.Refused)
        compose.waitForIdle()
        compose.onAllNodesWithText("Add library").assertCountEquals(0)
    }

    @Test
    fun `Users has Add user in the footer on the list only`() {
        val ana = ServerUser("ana", "Ana", false, null, JsonObject(emptyMap()))
        val libraries = listOf(ServerLibrary("m", "Movies", "movies", emptyList()))
        var shown = emptyList<UserDialog?>()
        var state by mutableStateOf<UsersUiState>(UsersUiState.Loading)
        compose.setContent { JellyfinTheme { UsersScreen(state, UsersActions(onShow = { shown = shown + it })) } }
        compose.onNodeWithText("Add a user").assertIsNotEnabled()
        state = UsersUiState.Ready(listOf(ana), libraries, me = "me")
        compose.waitForIdle()
        compose.onNodeWithText("Add a user").assertIsEnabled().performClick()
        assertEquals(listOf<UserDialog?>(UserDialog.Create), shown)
        // A user's own page has nothing to create.
        state = UsersUiState.Ready(listOf(ana), libraries, me = "me", open = "ana")
        compose.waitForIdle()
        compose.onAllNodesWithText("Add a user").assertCountEquals(0)
        state = UsersUiState.Failed(ConsoleFailure.Unreachable)
        compose.waitForIdle()
        compose.onAllNodesWithText("Add a user").assertCountEquals(0)
    }

    @Test
    fun `the Plugins page has Add repository in the footer on the Repositories tab only`() {
        var adding = false
        var repositories by mutableStateOf<RepositoriesUiState>(RepositoriesUiState.Loading)
        compose.setContent {
            JellyfinTheme {
                PluginsScreen(
                    PluginsUiState.Ready(emptyList()),
                    CatalogUiState.Ready(emptyList()),
                    repositories,
                    PluginsActions(),
                    CatalogActions(),
                    RepositoriesActions(onAskAdd = { adding = it }),
                    onBack = {},
                    initialTab = REPOSITORIES_TAB,
                )
            }
        }
        compose.onNodeWithText("Add repository").assertIsNotEnabled()
        repositories = RepositoriesUiState.Ready(emptyList())
        compose.waitForIdle()
        // One Add, in the footer: the empty state doesn't carry a second.
        compose.onAllNodesWithText("Add repository").assertCountEquals(1)
        compose.onNodeWithText("Add repository").assertIsEnabled().performClick()
        assertEquals(true, adding)
        repositories = RepositoriesUiState.Failed(ConsoleFailure.Unreachable)
        compose.waitForIdle()
        compose.onAllNodesWithText("Add repository").assertCountEquals(0)
        // Another tab has none.
        compose.onNodeWithText("Installed").performClick()
        compose.onAllNodesWithText("Add repository").assertCountEquals(0)
    }

    @Test
    fun `a task's screen has Add trigger in the footer, and the task list has none`() {
        var adding = false
        val task = ServerTask("t", "k", "Scan", "", "Library", false, false, null, null)
        val state = TasksUiState.Ready(TaskBoard(listOf(task)))
        compose.setContent {
            JellyfinTheme {
                TasksScreen(state, TasksActions())
                TaskScreen(state, "t", TasksActions(onAskAdd = { adding = it }))
            }
        }
        compose.waitForIdle()
        // The task has no triggers: its empty state doesn't carry a second Add, and the list has none at all.
        compose.onAllNodesWithText("Add trigger").assertCountEquals(1)
        compose.onNodeWithText("Add trigger").assertIsEnabled().performClick()
        assertEquals(true, adding)
    }
}
