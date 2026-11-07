package com.cooper.binge.companion.jellyfin.ui.console

import androidx.activity.ComponentActivity
import androidx.compose.material3.Text
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import com.binge.designsystem.testing.createTakeDownAndroidComposeRule
import com.cooper.binge.companion.jellyfin.theme.JellyfinTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** A form taller than its sheet scrolls its fields and keeps Cancel and the submit button in reach. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h480dp")
class ConsoleFieldsFormTest {
    @get:Rule
    val compose = createTakeDownAndroidComposeRule<ComponentActivity>()

    @Test
    fun `the buttons stay docked and the fields scroll when the form is taller than the window`() {
        compose.setContent {
            JellyfinTheme {
                ConsoleFieldsForm(title = "Edit", body = "A form", submitLabel = "Save", onSubmit = {}, onCancel = {}) {
                    repeat(FIELDS) { Text("Field $it") }
                }
            }
        }
        compose.onNodeWithText("Save").assertIsDisplayed()
        compose.onNodeWithText("Cancel").assertIsDisplayed()
        compose.onNodeWithText("Field ${FIELDS - 1}").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Save").assertIsDisplayed()
    }

    private companion object {
        const val FIELDS = 60
    }
}
