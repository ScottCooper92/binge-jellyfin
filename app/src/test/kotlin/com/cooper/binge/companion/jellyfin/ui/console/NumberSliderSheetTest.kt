package com.cooper.binge.companion.jellyfin.ui.console

import androidx.activity.ComponentActivity
import androidx.compose.material3.Surface
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.binge.designsystem.testing.assertNoTextClipped
import com.binge.designsystem.testing.createTakeDownAndroidComposeRule
import com.cooper.binge.companion.jellyfin.theme.JellyfinTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h900dp")
class NumberSliderSheetTest {
    @get:Rule
    val compose = createTakeDownAndroidComposeRule<ComponentActivity>()

    private val saved = mutableListOf<Int?>()

    private fun show(value: Int?) =
        compose.setContent {
            JellyfinTheme {
                Surface {
                    NumberSliderContent(
                        title = "Refresh metadata",
                        value = value,
                        range = 1..90,
                        format = { "Every $it days" },
                        openEndLabel = "Never",
                        onSave = { saved += it },
                    )
                }
            }
        }

    @Test
    fun `a stored value is shown as it is, and nothing is saved until the control is moved`() {
        show(30)
        compose.onNodeWithText("Every 30 days").assertIsDisplayed()
        assertTrue(saved.isEmpty())
    }

    @Test
    fun `a value beyond the range is shown as stored and survives untouched`() {
        show(120)
        compose.onNodeWithText("Every 120 days").assertIsDisplayed()
        assertTrue(saved.isEmpty())
    }

    @Test
    fun `no interval is the open end, never`() {
        show(null)
        compose.onNodeWithText("Never").assertIsDisplayed()
        assertTrue(saved.isEmpty())
    }

    @Test
    fun `a stepper tap saves the value it landed on, once`() {
        show(30)
        compose.onNodeWithContentDescription("Increase Refresh metadata").performClick()
        assertEquals(listOf<Int?>(31), saved)
        compose.onNodeWithText("Every 31 days").assertIsDisplayed()
    }

    @Test
    fun `stepping down from never lands on the last interval, and up from there is never again`() {
        show(null)
        compose.onNodeWithContentDescription("Decrease Refresh metadata").performClick()
        assertEquals(listOf<Int?>(90), saved)
        compose.onNodeWithContentDescription("Increase Refresh metadata").performClick()
        assertEquals(listOf(90, null), saved)
    }

    @Test
    @Config(fontScale = 2f)
    fun `at twice the font the value and the bounds are not cropped`() {
        show(30)
        assertTrue(compose.assertNoTextClipped() > 0)
    }
}
