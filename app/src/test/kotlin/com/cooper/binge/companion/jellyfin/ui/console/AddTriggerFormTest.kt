package com.cooper.binge.companion.jellyfin.ui.console

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.unit.dp
import com.binge.designsystem.testing.createTakeDownAndroidComposeRule
import com.cooper.binge.companion.jellyfin.server.TICKS_PER_MINUTE
import com.cooper.binge.companion.jellyfin.server.TICKS_PER_HOUR
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

    /** The x-centre of the time-of-day fields and of the time limit's readout below them, in pixels, as drawn. */
    private fun centres(kind: TriggerKind): Pair<Float, Float> {
        compose.setContent { JellyfinTheme { AddTriggerForm(TasksActions(), kind) } }
        val time = compose.onNodeWithTag(TRIGGER_TIME_TAG).getUnclippedBoundsInRoot()
        val limit = compose.onNodeWithText("No limit").getUnclippedBoundsInRoot()
        return (time.left + time.right).value / 2 to (limit.left + limit.right).value / 2
    }

    @Test
    fun `the time fields share an axis with the time limit on the daily and weekly forms`() {
        listOf(TriggerKind.Daily, TriggerKind.Weekly).forEach { kind ->
            val (time, limit) = centres(kind)
            assertEquals("$kind", limit, time, 1f)
            compose.scenario.recreate()
        }
    }

    @Test
    @Config(fontScale = 2f)
    fun `and at twice the font size`() {
        val (time, limit) = centres(TriggerKind.Daily)
        assertEquals(limit, time, 1f)
    }

    @Test
    @Config(fontScale = 1.5f)
    fun `and at one and a half times`() {
        val (time, limit) = centres(TriggerKind.Weekly)
        assertEquals(limit, time, 1f)
    }

    @Test
    fun `the p m chip makes the daily trigger an afternoon one, and an hour out of range stops Add`() {
        val added = mutableListOf<TaskTrigger>()
        compose.setContent { JellyfinTheme { AddTriggerForm(TasksActions(onAddTrigger = { added += it }), is24Hour = false) } }
        compose.onNodeWithText("p.m.").performClick()
        compose.onNodeWithText("Add").performClick()
        assertEquals(listOf(TaskTrigger.daily(15 * TICKS_PER_HOUR)), added)
        compose.onNodeWithText("03").performTextReplacement("13")
        compose.onNodeWithText("Enter a valid time.").assertIsDisplayed()
        compose.onNodeWithText("Add").assertIsNotEnabled()
        compose.onNodeWithText("13").performTextReplacement("")
        compose.onNodeWithText("Enter a valid time.").assertIsDisplayed()
        compose.onNodeWithText("Add").assertIsNotEnabled()
    }

    @Test
    fun `the hour and minute fields are labelled for TalkBack, and the chosen period is announced as selected`() {
        compose.setContent { JellyfinTheme { AddTriggerForm(TasksActions(), is24Hour = false) } }
        // A field's label is part of the node a screen reader reads, so each field is named, not just "edit box".
        compose.onNode(hasSetTextAction() and hasText("Hour")).assertIsDisplayed()
        compose.onNode(hasSetTextAction() and hasText("Minute")).assertIsDisplayed()
        compose.onNodeWithText("a.m.").assertIsSelected()
        compose.onNodeWithText("p.m.").assertIsNotSelected()
        compose.onNodeWithText("p.m.").performClick()
        compose.onNodeWithText("p.m.").assertIsSelected()
        compose.onNodeWithText("a.m.").assertIsNotSelected()
    }

    @Test
    fun `a twenty four hour device uses numeric fields without a period`() {
        compose.setContent { JellyfinTheme { AddTriggerForm(TasksActions(), is24Hour = true) } }
        compose.onNodeWithText("p.m.").assertDoesNotExist()
        compose.onNodeWithTag(TRIGGER_TIME_TAG).assertIsDisplayed()
    }

    @Test
    fun `opening either clock format leaves both fields unfocused`() {
        listOf(false, true).forEach { is24 ->
            compose.setContent { JellyfinTheme { AddTriggerForm(TasksActions(), is24Hour = is24) } }
            compose.onNode(hasSetTextAction() and hasText("Hour")).assertIsNotFocused()
            compose.onNode(hasSetTextAction() and hasText("Minute")).assertIsNotFocused()
            compose.scenario.recreate()
        }
    }

    @Test
    fun `an interval slider draft becomes the same minutes in the added trigger`() {
        val added = mutableListOf<TaskTrigger>()
        compose.setContent { JellyfinTheme { AddTriggerForm(TasksActions(onAddTrigger = { added += it }), TriggerKind.Interval) } }
        compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsActions.SetProgress))[0]
            .performSemanticsAction(SemanticsActions.SetProgress) { it(45f) }
        compose.onNodeWithText("45 min").assertIsDisplayed()
        compose.onNodeWithText("Add").performClick()
        assertEquals(listOf(TaskTrigger.interval(45 * TICKS_PER_MINUTE)), added)
    }

    @Test
    @Config(fontScale = 2f)
    fun `the last weekday can scroll into view and is the day added`() {
        val added = mutableListOf<TaskTrigger>()
        compose.setContent { JellyfinTheme { AddTriggerForm(TasksActions(onAddTrigger = { added += it }), TriggerKind.Weekly, is24Hour = true) } }
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
