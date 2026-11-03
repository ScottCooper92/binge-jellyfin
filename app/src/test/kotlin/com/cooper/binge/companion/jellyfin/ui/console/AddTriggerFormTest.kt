package com.cooper.binge.companion.jellyfin.ui.console

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.unit.dp
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
    val compose = createAndroidComposeRule<ComponentActivity>()

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
            compose.activityRule.scenario.recreate()
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
    fun `a twelve hour time is the minutes since midnight, with 12 a m as midnight and 12 p m as noon`() {
        assertEquals(0, twelveHourMinutes("12", "00", afternoon = false))
        assertEquals(12 * 60, twelveHourMinutes("12", "0", afternoon = true))
        assertEquals(15 * 60, twelveHourMinutes("3", "00", afternoon = true))
        assertEquals(23 * 60 + 59, twelveHourMinutes("11", "59", afternoon = true))
        assertEquals(60 + 5, twelveHourMinutes("1", "5", afternoon = false))
        listOf("" to "00", "0" to "00", "13" to "00", "3" to "60", "3" to "").forEach { (h, m) ->
            assertEquals("$h:$m", null, twelveHourMinutes(h, m, afternoon = false))
        }
    }

    @Test
    fun `the p m chip makes the daily trigger an afternoon one, and an hour out of range stops Add`() {
        val added = mutableListOf<TaskTrigger>()
        compose.setContent { JellyfinTheme { AddTriggerForm(TasksActions(onAddTrigger = { added += it }), is24Hour = false) } }
        compose.onNodeWithText("p.m.").performClick()
        compose.onNodeWithText("Add").performClick()
        assertEquals(listOf(TaskTrigger.daily(15 * TICKS_PER_HOUR)), added)
        compose.onNodeWithText("3").performTextReplacement("13")
        compose.onNodeWithText("Use an hour from 1 to 12 and minutes from 0 to 59.").assertIsDisplayed()
        compose.onNodeWithText("Add").assertIsNotEnabled()
        compose.onNodeWithText("13").performTextReplacement("")
        compose.onNodeWithText("Use an hour from 1 to 12 and minutes from 0 to 59.").assertIsDisplayed()
        compose.onNodeWithText("Add").assertIsNotEnabled()
    }

    @Test
    fun `a twenty four hour device keeps the Material time input, which has no period to clip`() {
        compose.setContent { JellyfinTheme { AddTriggerForm(TasksActions(), is24Hour = true) } }
        compose.onNodeWithText("p.m.").assertDoesNotExist()
        compose.onNodeWithTag(TRIGGER_TIME_TAG).assertIsDisplayed()
    }

    private companion object {
        const val SHORT_WINDOW = 320
    }
}
