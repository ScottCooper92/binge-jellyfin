package com.cooper.binge.companion.jellyfin.ui.console

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import com.binge.designsystem.testing.createTakeDownAndroidComposeRule
import com.cooper.binge.companion.jellyfin.server.LIVE_TV_PRE_PADDING
import com.cooper.binge.companion.jellyfin.theme.JellyfinTheme
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class RecordingPaddingSliderTest {
    @get:Rule
    val compose = createTakeDownAndroidComposeRule<ComponentActivity>()

    private val sent = mutableListOf<Pair<String, JsonElement>>()

    private fun show(current: Int) {
        compose.setContent {
            JellyfinTheme {
                RecordingPaddingSlider(
                    RecordingNumber.PrePadding,
                    current,
                    LiveTvRecordingActions(onSet = { key, value -> sent += key to value }),
                )
            }
        }
    }

    @Test
    fun `opening preserves an off-step value and a stepper saves seconds once`() {
        show(7)
        compose.onNodeWithText("7 s").assertExists()
        assertTrue(sent.isEmpty())
        compose.onNodeWithContentDescription("Increase Start early").performClick()
        assertEquals(listOf(LIVE_TV_PRE_PADDING to JsonPrimitive(10)), sent)
        compose.onNodeWithText("10 s").assertExists()
    }

    @Test
    fun `an out-of-range stored value is kept until the slider moves`() {
        show(300)
        compose.onNodeWithText("300 s").assertExists()
        assertTrue(sent.isEmpty())
        compose.onNodeWithContentDescription("Decrease Start early").performClick()
        assertEquals(listOf(LIVE_TV_PRE_PADDING to JsonPrimitive(115)), sent)
    }

    @Test
    fun `a drag shows its value without saving until release`() {
        show(0)
        val slider = compose.onNodeWithContentDescription("Start early")
        slider.performTouchInput {
            down(center)
            moveTo(centerRight)
        }
        assertTrue(sent.isEmpty())
        slider.performTouchInput { up() }
        assertEquals(1, sent.size)
    }
}
