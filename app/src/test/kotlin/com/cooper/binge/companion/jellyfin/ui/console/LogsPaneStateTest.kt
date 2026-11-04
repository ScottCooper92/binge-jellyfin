package com.cooper.binge.companion.jellyfin.ui.console

import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelected
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import com.binge.designsystem.LocalIsSinglePaneNav
import com.binge.designsystem.testing.createTakeDownAndroidComposeRule
import com.cooper.binge.companion.jellyfin.server.LogFile
import com.cooper.binge.companion.jellyfin.server.parseLogEntries
import com.cooper.binge.companion.jellyfin.server.redactLog
import com.cooper.binge.companion.jellyfin.theme.JellyfinTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w1200dp-h800dp")
class LogsPaneStateTest {
    @get:Rule
    val compose = createTakeDownAndroidComposeRule<ComponentActivity>()

    private val files = listOf(LogFile("server.log", 100, null), LogFile("older.log", 100, null))

    private fun entries(count: Int) =
        parseLogEntries(
            redactLog(
                (0 until count).joinToString("\n") {
                    "[2026-10-31 12:00:00 +00:00] [INF] [1] Main: Line $it"
                },
            ),
        )

    private var state by mutableStateOf(LogsUiState.Ready(files, OpenLog("server.log", LogContent.Ready(entries(2), false))))
    private var split by mutableStateOf(true)
    private var left = 0
    private var shared: Pair<String, String>? = null

    private fun show() = compose.setContent { Screen() }

    @Composable
    private fun Screen() {
        JellyfinTheme {
            CompositionLocalProvider(LocalPageSpansTwoPanes provides split, LocalIsSinglePaneNav provides !split) {
                BackHandler { left++ }
                LogsScreen(
                    state,
                    LogsActions(
                        onOpen = { state = state.copy(open = OpenLog(it, LogContent.Ready(entries(2), false))) },
                        onCloseLog = { state = state.copy(open = null) },
                        onQuery = { query -> state = state.copy(open = state.open?.copy(query = query)) },
                        onShare = { name, text -> shared = name to text },
                        onBack = { left++ },
                    ),
                )
            }
        }
    }

    @Test
    fun `the list stays beside the reader with its open row marked and a page title`() {
        show()
        compose.onNodeWithText("Logs").assertIsDisplayed()
        compose.onNodeWithText("older.log").assertIsDisplayed()
        compose.onAllNodesWithText("server.log").assertCountEquals(2)
        compose.onAllNodes(hasText("server.log") and isSelected()).assertCountEquals(1)
        compose.onNodeWithText("Line 1").assertIsDisplayed()
        compose.onNodeWithContentDescription("Share this log").performClick()
        assertEquals("server.log", shared?.first)
        assertEquals(state.open?.visible?.joinToString("\n") { it.text }, shared?.second)
    }

    @Test
    fun `without a selection the detail invites the user to open a log`() {
        state = state.copy(open = null)
        show()
        compose.onNodeWithText("older.log").assertIsDisplayed()
        compose.onNodeWithText("Select a log to read it.").assertIsDisplayed()
        compose.onNodeWithContentDescription("Share this log").assertDoesNotExist()
    }

    @Test
    fun `another file opens in the detail and Back closes it before leaving the page`() {
        show()
        compose.onNodeWithText("older.log").performClick()
        compose.onAllNodes(hasText("older.log") and isSelected()).assertCountEquals(1)
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.onNodeWithText("Select a log to read it.").assertIsDisplayed()
        assertEquals(0, left)
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()
        assertEquals(1, left)
    }

    @Test
    fun `the reader can load and fail without replacing the list or enabling Share`() {
        state = state.copy(open = OpenLog("server.log", LogContent.Loading))
        show()
        compose.onNodeWithText("older.log").assertIsDisplayed()
        compose.onNodeWithContentDescription("Share this log").assertIsNotEnabled()
        compose.runOnIdle { state = state.copy(open = state.open?.copy(content = LogContent.Failed(ConsoleFailure.Unreachable))) }
        compose.onNodeWithText("older.log").assertIsDisplayed()
        compose.onNodeWithText("Try again").assertIsDisplayed()
        compose.onNodeWithContentDescription("Share this log").assertIsNotEnabled()
    }

    @Test
    fun `long pressing the detail heading still reveals the full file name`() {
        show()
        compose.onAllNodesWithText("server.log").onLast().performTouchInput { longClick() }
        compose.onNodeWithText("Full log name").assertIsDisplayed()
        compose.onNodeWithText("OK").performClick()
        compose.onNodeWithText("Full log name").assertDoesNotExist()
    }

    @Test
    fun `narrowing and widening keeps the query and the reader's scroll position`() {
        state = state.copy(open = OpenLog("server.log", LogContent.Ready(entries(40), false)))
        show()
        compose.onNode(hasSetTextAction()).performTextReplacement("Line")
        compose.onAllNodes(hasScrollToIndexAction()).onLast().performScrollToIndex(10)
        compose.onNodeWithText("Line 10").assertIsDisplayed()
        compose.runOnIdle { split = false }
        compose.onNodeWithText("older.log").assertDoesNotExist()
        compose.onNodeWithText("Line 10").assertIsDisplayed()
        assertEquals("Line", state.open?.query)
        compose.runOnIdle { split = true }
        compose.onNodeWithText("older.log").assertIsDisplayed()
        compose.onNodeWithText("Line 10").assertIsDisplayed()
    }

    @Test
    fun `restoring after a resize keeps the reader where it was read to`() {
        state = state.copy(open = OpenLog("server.log", LogContent.Ready(entries(40), false), query = "Line"))
        val restoration = StateRestorationTester(compose)
        restoration.setContent { Screen() }
        compose.onAllNodes(hasScrollToIndexAction()).onLast().performScrollToIndex(10)
        compose.onNodeWithText("Line 10").assertIsDisplayed()
        compose.runOnIdle { split = false }
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithText("Line 10").assertIsDisplayed()
        compose.onNodeWithText("older.log").assertDoesNotExist()
        compose.runOnIdle { split = true }
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithText("older.log").assertIsDisplayed()
        compose.onNodeWithText("Line 10").assertIsDisplayed()
        assertEquals("Line", state.open?.query)
    }
}
