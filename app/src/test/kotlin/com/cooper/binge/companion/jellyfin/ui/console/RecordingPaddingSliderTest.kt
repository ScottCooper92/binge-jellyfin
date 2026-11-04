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
    fun `opening preserves an off-step value and a stepper saves whole minutes as seconds once`() {
        show(7)
        compose.onNodeWithText("7 s").assertExists()
        assertTrue(sent.isEmpty())
        compose.onNodeWithContentDescription("Increase Start early").performClick()
        assertEquals(listOf(LIVE_TV_PRE_PADDING to JsonPrimitive(60)), sent)
        compose.onNodeWithText("1 min").assertExists()
    }

    @Test
    fun `an out-of-range stored value is kept until the slider moves`() {
        show(9000)
        compose.onNodeWithText("150 min").assertExists()
        assertTrue(sent.isEmpty())
        compose.onNodeWithContentDescription("Decrease Start early").performClick()
        assertEquals(listOf(LIVE_TV_PRE_PADDING to JsonPrimitive(7140)), sent)
    }

    @Test
    fun `a stored value inside the old range survives a nudge`() {
        show(300)
        compose.onNodeWithText("5 min").assertExists()
        compose.onNodeWithContentDescription("Increase Start early").performClick()
        assertEquals(listOf(LIVE_TV_PRE_PADDING to JsonPrimitive(360)), sent)
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
