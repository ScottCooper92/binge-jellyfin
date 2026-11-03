package com.cooper.binge.companion.jellyfin.ui.console

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.unit.dp
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

    private companion object {
        const val SHORT_WINDOW = 320
    }
}
