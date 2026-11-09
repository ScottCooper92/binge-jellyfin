package com.cooper.binge.companion.jellyfin.ui.console

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.unit.dp
import com.binge.designsystem.testing.createTakeDownAndroidComposeRule
import com.cooper.binge.companion.jellyfin.server.TICKS_PER_HOUR
import com.cooper.binge.companion.jellyfin.server.TICKS_PER_MINUTE
import com.cooper.binge.companion.jellyfin.server.TaskTrigger
import com.cooper.binge.companion.jellyfin.server.TriggerKind
import com.cooper.binge.companion.jellyfin.theme.JellyfinTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The new trigger form where there is little room, as with the keyboard up. */
@RunWith(RobolectricTestRunner::class)
class AddTriggerFormTest {
    @get:Rule
    val compose = createTakeDownAndroidComposeRule<ComponentActivity>()

    @Test
    fun `the Add button stays in view when the weekly form is taller than the room`() {
        compose.setContent { JellyfinTheme { Box(Modifier.height(SHORT_WINDOW.dp)) { AddTriggerForm(TasksActions()) } } }
        compose.onNodeWithText("Weekly").performClick()
        compose.onNodeWithText("Add").assertIsDisplayed()
    }

    private fun trigger(limitHours: Int?): TaskTrigger {
        val added = mutableListOf<TaskTrigger>()
        compose.setContent { JellyfinTheme { AddTriggerForm(TasksActions(onAddTrigger = { added += it }), TriggerKind.Startup) } }
        compose.onNodeWithText("No limit").assertIsDisplayed()
        if (limitHours != null) {
            compose.onNode(SemanticsMatcher.keyIsDefined(SemanticsActions.SetProgress)).performSemanticsAction(
                SemanticsActions.SetProgress,
            ) { it(limitHours.toFloat()) }
            compose.onNodeWithText("$limitHours hours").assertIsDisplayed()
        }
        compose.onNodeWithText("Add").performClick()
        return added.single()
    }

    @Test
    fun `a time limit left at No limit sends none`() {
        assertEquals(TaskTrigger.startup(), trigger(limitHours = null))
    }

    @Test
    fun `three hours on the slider is 108000000000 ticks`() {
        assertEquals(108_000_000_000L, trigger(limitHours = 3).maxRuntimeTicks)
    }

    @Test
    fun `the daily trigger shows its time in the phone's clock format, and Add sends it`() {
        val added = mutableListOf<TaskTrigger>()
        compose.setContent { JellyfinTheme { AddTriggerForm(TasksActions(onAddTrigger = { added += it }), is24Hour = true) } }
        compose.onNodeWithTag(TRIGGER_TIME_TAG).assertIsDisplayed()
        compose.onNodeWithText("03:00").assertIsDisplayed()
        compose.onNodeWithText("Add").performClick()
        assertEquals(listOf(TaskTrigger.daily(3 * TICKS_PER_HOUR)), added)
    }

    @Test
    fun `a twelve hour phone reads the time with a period, and the row opens the picker`() {
        compose.setContent { JellyfinTheme { AddTriggerForm(TasksActions(), is24Hour = false) } }
        compose.onNodeWithText("OK").assertDoesNotExist()
        compose.onNodeWithText("Time").performClick()
        compose.onNodeWithText("OK").assertIsDisplayed()
        compose.onNodeWithText("Cancel").performClick()
        compose.onNodeWithText("OK").assertDoesNotExist()
    }

    @Test
    @Config(fontScale = 2f)
    fun `the time row is in view at twice the font size`() {
        compose.setContent { JellyfinTheme { AddTriggerForm(TasksActions(), TriggerKind.Weekly, is24Hour = true) } }
        compose.onNodeWithTag(TRIGGER_TIME_TAG).assertIsDisplayed()
    }

    @Test
    fun `the interval and startup kinds have no time row`() {
        compose.setContent { JellyfinTheme { AddTriggerForm(TasksActions(), TriggerKind.Interval) } }
        compose.onNodeWithTag(TRIGGER_TIME_TAG).assertDoesNotExist()
    }

    @Test
    fun `an interval slider draft becomes the same minutes in the added trigger`() {
        val added = mutableListOf<TaskTrigger>()
        compose.setContent { JellyfinTheme { AddTriggerForm(TasksActions(onAddTrigger = { added += it }), TriggerKind.Interval) } }
        compose
            .onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsActions.SetProgress))[0]
            .performSemanticsAction(SemanticsActions.SetProgress) { it(45f) }
        compose.onNodeWithText("45 min").assertIsDisplayed()
        compose.onNodeWithText("Add").performClick()
        assertEquals(listOf(TaskTrigger.interval(45 * TICKS_PER_MINUTE)), added)
    }

    @Test
    @Config(fontScale = 2f)
    fun `the last weekday can scroll into view and is the day added`() {
        val added = mutableListOf<TaskTrigger>()
        compose.setContent {
            JellyfinTheme {
                AddTriggerForm(
                    TasksActions(onAddTrigger = { added += it }),
                    TriggerKind.Weekly,
                    is24Hour = true,
                )
            }
        }
        // The horizontal row is the second scroll container after the whole form.
        compose.onAllNodes(hasScrollAction())[1].performScrollToNode(hasText("Sun"))
        compose.onNodeWithText("Sun").assertIsDisplayed().performClick()
        compose.onNodeWithText("Add").performClick()
        assertEquals("Sunday", added.single().dayOfWeek)
    }

    private companion object {
        const val SHORT_WINDOW = 320
    }
}
