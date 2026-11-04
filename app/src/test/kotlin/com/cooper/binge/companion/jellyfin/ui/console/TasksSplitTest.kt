package com.cooper.binge.companion.jellyfin.ui.console

import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import com.binge.designsystem.testing.createTakeDownAndroidComposeRule
import com.cooper.binge.companion.jellyfin.server.ServerTask
import com.cooper.binge.companion.jellyfin.theme.JellyfinTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** The scheduled tasks page given two panes' width: the list and the open task's triggers side by side. */
@RunWith(RobolectricTestRunner::class)
class TasksSplitTest {
    @get:Rule
    val compose = createTakeDownAndroidComposeRule<ComponentActivity>()

    private fun task(
        id: String,
        name: String,
    ) = ServerTask(id, "Key$id", name, "About $name.", "Library", running = false, stopping = false, percent = null, lastRun = null)

    private val board = TaskBoard(listOf(task("a", "Scan Media Library"), task("b", "Clean cache")))

    private fun show(
        state: TasksUiState.Ready,
        split: Boolean,
    ) = compose.setContent {
        JellyfinTheme { CompositionLocalProvider(LocalPageSpansTwoPanes provides split) { TasksScreen(state, TasksActions()) } }
    }

    @Test
    fun `beside the list the open task shows its triggers under the page's own title`() {
        show(TasksUiState.Ready(board, open = "a"), split = true)
        compose.onNodeWithText("Scheduled tasks").assertIsDisplayed()
        // The list keeps both tasks; the task's name is also the heading of its triggers.
        compose.onNodeWithText("Clean cache").assertIsDisplayed()
        compose.onAllNodesWithText("Scan Media Library").assertCountEquals(2)
        compose.onNodeWithText("Add trigger").assertIsDisplayed()
    }

    @Test
    fun `beside the list with nothing open the detail says to pick a task`() {
        show(TasksUiState.Ready(board), split = true)
        compose.onNodeWithText("Clean cache").assertIsDisplayed()
        compose.onNodeWithText("Select a task to see its triggers.").assertIsDisplayed()
    }

    @Test
    fun `on its own an open task replaces the list, titled with its name`() {
        show(TasksUiState.Ready(board, open = "a"), split = false)
        compose.onNodeWithText("Scan Media Library").assertIsDisplayed()
        compose.onAllNodesWithText("Clean cache").assertCountEquals(0)
        compose.onAllNodes(hasText("Select a task to see its triggers.")).assertCountEquals(0)
    }
}
