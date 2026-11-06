package com.cooper.binge.companion.jellyfin.ui.console

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performSemanticsAction
import com.binge.designsystem.testing.createTakeDownAndroidComposeRule
import com.cooper.binge.companion.jellyfin.server.ApiKey
import com.cooper.binge.companion.jellyfin.server.ServerDevice
import com.cooper.binge.companion.jellyfin.server.ServerTask
import com.cooper.binge.companion.jellyfin.server.TaskTrigger
import com.cooper.binge.companion.jellyfin.theme.JellyfinTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * A console list keeps where the user scrolled to when its rows are swapped for the skeleton and back, as a reload or a
 * retry does: the list state is held above the loading, failed and ready branches, not in the one that is replaced.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp")
class ConsoleListPositionTest {
    @get:Rule
    val compose = createTakeDownAndroidComposeRule<ComponentActivity>()

    private val devices = (0 until ROWS).map { ServerDevice("id$it", "Device $it", null, "App", "sam", 0L) }
    private val keys = (0 until ROWS).map { ApiKey("secret-key-0000$it", "App $it", 0L) }

    private val triggers = (0 until ROWS).map { TaskTrigger(type = "IntervalTrigger", intervalTicks = (it + 1) * TICKS_PER_MINUTE) }
    private val task =
        ServerTask("t1", "ScanLibrary", "Scan Media Library", "Scans the library.", "Library", false, false, null, null, triggers)

    private var tasksState by mutableStateOf<TasksUiState>(TasksUiState.Ready(TaskBoard(listOf(task))))
    private var devicesState by mutableStateOf<DevicesUiState>(DevicesUiState.Ready(devices, thisDevice = ""))
    private var keysState by mutableStateOf<ApiKeysUiState>(ApiKeysUiState.Ready(keys))

    /** The page's list: the one scrollable that has more to scroll to. */
    private val list =
        SemanticsMatcher("scrolls vertically") {
            it.config.contains(SemanticsProperties.VerticalScrollAxisRange) &&
                it.config[SemanticsProperties.VerticalScrollAxisRange].maxValue() > 0f
        }

    /** The list's scroll offset, as the semantics tree reports it. */
    private fun offset(): Float =
        compose
            .onAllNodes(list)
            .onFirst()
            .fetchSemanticsNode()
            .config[SemanticsProperties.VerticalScrollAxisRange]
            .value()

    private fun scrollDown(): Float {
        compose.onAllNodes(list).onFirst().performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, SCROLL_PX) }
        compose.waitForIdle()
        return offset().also { assertTrue("the list should have scrolled, but is at $it", it > 0f) }
    }

    @Test
    fun `the devices list keeps its place through a reload that shows the skeleton`() {
        compose.setContent { JellyfinTheme { DevicesScreen(devicesState, DevicesActions()) } }
        val scrolled = scrollDown()
        devicesState = DevicesUiState.Loading
        compose.waitForIdle()
        devicesState = DevicesUiState.Ready(devices.map { it.copy() }, thisDevice = "")
        compose.waitForIdle()
        assertEquals(scrolled, offset(), 0.5f)
    }

    @Test
    fun `the API keys list keeps its place through a reload that shows the skeleton`() {
        compose.setContent { JellyfinTheme { ApiKeysScreen(keysState, ApiKeysActions()) } }
        val scrolled = scrollDown()
        keysState = ApiKeysUiState.Loading
        compose.waitForIdle()
        keysState = ApiKeysUiState.Ready(keys.toList())
        compose.waitForIdle()
        assertEquals(scrolled, offset(), 0.5f)
    }

    @Test
    fun `a task's triggers keep their place through a reload that shows the skeleton`() {
        compose.setContent { JellyfinTheme { TaskScreen(tasksState, "t1", TasksActions()) } }
        val scrolled = scrollDown()
        tasksState = TasksUiState.Loading
        compose.waitForIdle()
        tasksState = TasksUiState.Ready(TaskBoard(listOf(task.copy())))
        compose.waitForIdle()
        assertEquals(scrolled, offset(), 0.5f)
    }

    private companion object {
        const val ROWS = 60
        const val SCROLL_PX = 900f
        const val TICKS_PER_MINUTE = 600_000_000L
    }
}
