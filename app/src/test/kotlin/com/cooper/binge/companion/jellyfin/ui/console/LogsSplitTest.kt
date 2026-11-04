package com.cooper.binge.companion.jellyfin.ui.console

import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.isSelected
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import com.binge.designsystem.testing.createTakeDownAndroidComposeRule
import com.cooper.binge.companion.jellyfin.server.LogFile
import com.cooper.binge.companion.jellyfin.server.LogLevel
import com.cooper.binge.companion.jellyfin.theme.JellyfinTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** The logs page given two panes' width: the files and the open log's end side by side. */
@RunWith(RobolectricTestRunner::class)
class LogsSplitTest {
    @get:Rule
    val compose = createTakeDownAndroidComposeRule<ComponentActivity>()

    private val files = listOf(LogFile("log_20261104.log", 2048, null), LogFile("log_20261103.log", 4096, null))

    private fun show(
        open: OpenLog?,
        split: Boolean,
    ) = compose.setContent {
        JellyfinTheme {
            CompositionLocalProvider(LocalPageSpansTwoPanes provides split) {
                LogsScreen(LogsUiState.Ready(files, open = open), LogsActions())
            }
        }
    }

    private val reading = OpenLog("log_20261104.log", LogContent.Failed(ConsoleFailure.Unreachable), level = LogLevel.Debug)

    @Test
    fun `beside the files the open log shows under the page's own title, its file marked`() {
        show(reading, split = true)
        compose.onNodeWithText("Logs").assertIsDisplayed()
        compose.onNodeWithText("log_20261103.log").assertIsDisplayed()
        // The open file is in the list, marked, and again as the heading of what is read.
        compose.onAllNodesWithText("log_20261104.log").assertCountEquals(2)
        compose.onAllNodes(isSelected()).assertCountEquals(1)
    }

    @Test
    fun `beside the files with nothing open the detail says to pick a log`() {
        show(null, split = true)
        compose.onNodeWithText("log_20261103.log").assertIsDisplayed()
        compose.onNodeWithText("Select a log to read it.").assertIsDisplayed()
    }

    @Test
    fun `on its own an open log replaces the files, titled with its name`() {
        show(reading, split = false)
        compose.onNodeWithText("log_20261104.log").assertIsDisplayed()
        compose.onAllNodesWithText("log_20261103.log").assertCountEquals(0)
        compose.onAllNodesWithText("Select a log to read it.").assertCountEquals(0)
    }
}
