package com.cooper.binge.companion.jellyfin.ui.console

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
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

    private companion object {
        const val SHORT_WINDOW = 320
    }
}
