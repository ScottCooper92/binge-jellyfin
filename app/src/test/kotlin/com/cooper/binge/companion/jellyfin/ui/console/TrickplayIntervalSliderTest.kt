package com.cooper.binge.companion.jellyfin.ui.console

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.binge.designsystem.testing.createTakeDownAndroidComposeRule
import com.cooper.binge.companion.jellyfin.server.TrickplaySettings
import com.cooper.binge.companion.jellyfin.theme.JellyfinTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** The trickplay interval as a slider in whole seconds: what a stored value reads as, and what a step saves. */
@RunWith(RobolectricTestRunner::class)
class TrickplayIntervalSliderTest {
    @get:Rule
    val compose = createTakeDownAndroidComposeRule<ComponentActivity>()

    private val saved = mutableListOf<Int>()

    /** Opens the interval sheet over a page whose interval is [millis]; [label] is the row's own text. */
    private fun open(
        millis: Int,
        label: String,
    ) {
        val state = TrickplayUiState.Ready(TrickplaySettings(intervalMillis = millis))
        compose.setContent { JellyfinTheme { TrickplayScreen(state, TrickplayActions(onInterval = { saved += it })) } }
        compose.onNodeWithText(label).performScrollTo().performClick()
    }

    @Test
    fun `a whole number of seconds reads as stored and a step saves the next second`() {
        open(10_000, "Every 10 s")
        compose.onAllNodesWithText("Every 10 s").assertCountEquals(2)
        assertTrue(saved.isEmpty())
        compose.onNodeWithContentDescription("Increase Interval").performClick()
        assertEquals(listOf(11_000), saved)
    }

    @Test
    fun `a stored interval between two seconds reads exactly as stored and is not rewritten on open`() {
        open(2_500, "Every 2500 ms")
        compose.onAllNodesWithText("Every 2500 ms").assertCountEquals(2)
        assertTrue(saved.isEmpty())
    }

    @Test
    fun `from between two seconds a step lands on the whole second above or below`() {
        open(2_500, "Every 2500 ms")
        compose.onNodeWithContentDescription("Increase Interval").performClick()
        assertEquals(listOf(3_000), saved)
    }

    @Test
    fun `from between two seconds a step down lands on the whole second below`() {
        open(2_500, "Every 2500 ms")
        compose.onNodeWithContentDescription("Decrease Interval").performClick()
        assertEquals(listOf(2_000), saved)
    }

    @Test
    fun `an interval beyond a minute reads as stored and is not rewritten on open`() {
        open(90_000, "Every 90 s")
        compose.onAllNodesWithText("Every 90 s").assertCountEquals(2)
        assertTrue(saved.isEmpty())
    }

    @Test
    fun `the slider has no open end, so the top is a minute and the step up from it is off`() {
        open(60_000, "Every 60 s")
        compose.onNodeWithContentDescription("Decrease Interval").performClick()
        assertEquals(listOf(59_000), saved)
    }
}
