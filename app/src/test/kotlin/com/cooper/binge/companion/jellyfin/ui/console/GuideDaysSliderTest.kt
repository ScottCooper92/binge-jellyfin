package com.cooper.binge.companion.jellyfin.ui.console

import androidx.activity.ComponentActivity
import androidx.compose.material3.Surface
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.binge.designsystem.testing.createTakeDownAndroidComposeRule
import com.cooper.binge.companion.jellyfin.server.LIVE_TV_GUIDE_DAYS
import com.cooper.binge.companion.jellyfin.theme.JellyfinTheme
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** The days of guide as a slider: what a stored length reads as, and what a move saves. */
@RunWith(RobolectricTestRunner::class)
class GuideDaysSliderTest {
    @get:Rule
    val compose = createTakeDownAndroidComposeRule<ComponentActivity>()

    private val saved = mutableListOf<Pair<String, JsonElement>>()

    private fun show(current: String) {
        val actions = LiveTvRecordingActions(onSet = { key, value -> saved += key to value })
        compose.setContent { JellyfinTheme { Surface { RecordingNumberForm(RecordingNumber.GuideDays, current, actions) } } }
    }

    private fun tap(description: String) = compose.onNodeWithContentDescription(description).performClick()

    @Test
    fun `a stored length reads as stored, and nothing is saved on open`() {
        show("7")
        compose.onNodeWithText("7 days").assertIsDisplayed()
        assertTrue(saved.isEmpty())
    }

    @Test
    fun `no length is the open end, which reads as automatic`() {
        show("")
        compose.onNodeWithText("Automatic").assertIsDisplayed()
        assertTrue(saved.isEmpty())
    }

    @Test
    fun `a length beyond the scale reads as stored and is not rewritten on open`() {
        show("20")
        compose.onNodeWithText("20 days").assertIsDisplayed()
        assertTrue(saved.isEmpty())
    }

    @Test
    fun `a step saves the number it lands on`() {
        show("7")
        tap("Increase Days of guide")
        assertEquals(listOf(LIVE_TV_GUIDE_DAYS to JsonPrimitive(8)), saved)
    }

    @Test
    fun `stepping down from automatic lands on 14 days`() {
        show("")
        tap("Decrease Days of guide")
        assertEquals(listOf(LIVE_TV_GUIDE_DAYS to JsonPrimitive(14)), saved)
    }

    @Test
    fun `the open end clears the length for the server to choose`() {
        show("14")
        tap("Increase Days of guide")
        assertEquals(listOf<Pair<String, JsonElement>>(LIVE_TV_GUIDE_DAYS to JsonNull), saved)
    }
}
