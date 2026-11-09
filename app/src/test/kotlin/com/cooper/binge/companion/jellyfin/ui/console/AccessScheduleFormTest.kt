package com.cooper.binge.companion.jellyfin.ui.console

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.binge.designsystem.testing.createTakeDownAndroidComposeRule
import com.cooper.binge.companion.jellyfin.server.AccessSchedule
import com.cooper.binge.companion.jellyfin.server.asAccessSchedule
import com.cooper.binge.companion.jellyfin.server.json
import com.cooper.binge.companion.jellyfin.theme.JellyfinTheme
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h2000dp")
class AccessScheduleFormTest {
    @get:Rule val compose = createTakeDownAndroidComposeRule<ComponentActivity>()

    @Test fun `common day groups appear ahead of individual days`() {
        compose.setContent { JellyfinTheme { ScheduleForm(UsersActions(), {}) } }
        val every = compose.onNodeWithText("Every day").getUnclippedBoundsInRoot().top
        val weekdays = compose.onNodeWithText("Weekdays").getUnclippedBoundsInRoot().top
        val weekends = compose.onNodeWithText("Weekends").getUnclippedBoundsInRoot().top
        val sunday = compose.onNodeWithText("Sunday").getUnclippedBoundsInRoot().top
        assertTrue(every < weekdays && weekdays < weekends && weekends < sunday)
    }

    private fun saved(original: AccessSchedule): List<AccessSchedule> {
        var changed: (List<JsonElement>) -> List<JsonElement> = { it }
        compose.setContent {
            JellyfinTheme {
                ScheduleForm(UsersActions(onList = { _, change -> changed = change }), {}, original = original, is24Hour = true)
            }
        }
        compose.onNodeWithText("Save").performClick()
        return changed(listOf(original.json(), AccessSchedule("Sunday", 0.0, 24.0).json())).mapNotNull { it.asAccessSchedule() }
    }

    @Test fun `editing shows both times in the clock format and keeps minute precision in the replaced row`() {
        val original = AccessSchedule("Weekday", 9.5, 17.75)
        assertEquals(listOf(original, AccessSchedule("Sunday", 0.0, 24.0)), saved(original))
        compose.onNodeWithText("09:30").assertExists()
        compose.onNodeWithText("17:45").assertExists()
    }

    @Test fun `a time row opens the picker, and Cancel closes it without a change`() {
        compose.setContent { JellyfinTheme { ScheduleForm(UsersActions(), {}, startStep = 1, startHour = 9, is24Hour = true) } }
        compose.onNodeWithText("OK").assertDoesNotExist()
        compose.onNodeWithText("From when?").performClick()
        compose.onNodeWithText("OK").assertExists()
        // The form has a Cancel of its own; the dialog's is the one inside the dialog window.
        compose.onNode(hasText("Cancel") and hasAnyAncestor(isDialog())).performClick()
        compose.onNodeWithText("OK").assertDoesNotExist()
        compose.onNodeWithText("09:00").assertExists()
    }

    @Test fun `until must be later than from, so Save is off otherwise`() {
        compose.setContent {
            JellyfinTheme { ScheduleForm(UsersActions(), {}, original = AccessSchedule("Weekday", 17.0, 9.0), is24Hour = true) }
        }
        compose.onNodeWithText("Save").assertIsNotEnabled()
    }

    @Test fun `midnight as until means the end of the same day`() {
        val original = AccessSchedule("Weekday", 9.0, 24.0)
        assertEquals(listOf(original, AccessSchedule("Sunday", 0.0, 24.0)), saved(original))
        compose.onNodeWithText("00:00").assertExists()
    }

    @Test fun `an edit keeps unknown server fields and never adds a missing original`() {
        val original = AccessSchedule("Weekday", 9.0, 17.0)
        val updated = original.copy(startHour = 9.5)
        val json = Json.parseToJsonElement("""{"DayOfWeek":"Weekday","StartHour":9,"EndHour":17,"Id":7,"FutureSetting":true}""")
        val changed = listOf(json).withSchedule(original, updated).single().jsonObject
        assertEquals(updated, changed.asAccessSchedule())
        assertEquals(json.jsonObject["Id"], changed["Id"])
        assertEquals(json.jsonObject["FutureSetting"], changed["FutureSetting"])
        assertEquals(emptyList<JsonElement>(), emptyList<JsonElement>().withSchedule(original, updated))
        assertEquals(listOf(original.json()), listOf(original.json()).withSchedule(null, original))
    }
}
