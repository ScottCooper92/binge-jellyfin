package com.cooper.binge.companion.jellyfin.ui.console

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.cooper.binge.companion.jellyfin.theme.JellyfinTheme
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

    private companion object {
        const val SHORT_WINDOW = 320
    }
}
