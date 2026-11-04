package com.cooper.binge.companion.jellyfin.ui.console

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import com.binge.designsystem.testing.createTakeDownAndroidComposeRule
import com.cooper.binge.companion.jellyfin.server.AccessSchedule
import com.cooper.binge.companion.jellyfin.server.asAccessSchedule
import com.cooper.binge.companion.jellyfin.server.json
import com.cooper.binge.companion.jellyfin.theme.JellyfinTheme
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
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

    @Test fun `editing retains minute precision and replaces the old row without a duplicate`() {
        val original = AccessSchedule("Weekday", 9.5, 17.75)
        var changed: (List<JsonElement>) -> List<JsonElement> = { it }
        compose.setContent { JellyfinTheme { ScheduleForm(UsersActions(onList = { _, change -> changed = change }), {}, original = original, is24Hour = true) } }
        compose.onAllNodes(hasSetTextAction() and hasText("Minute"))[0].performTextReplacement("45")
        compose.onNodeWithText("Save").performClick()
        val expected = AccessSchedule("Weekday", 9.75, 17.75)
        val unrelated = AccessSchedule("Sunday", 0.0, 24.0)
        assertEquals(listOf(expected, unrelated), changed(listOf(original.json(), unrelated.json())).map { it.asAccessSchedule() })
        assertEquals(listOf(expected), changed(listOf(original.json(), expected.json())).map { it.asAccessSchedule() })
    }

    @Test fun `until must be later and midnight means the end of the same day`() {
        var changed: (List<JsonElement>) -> List<JsonElement> = { it }
        compose.setContent { JellyfinTheme { ScheduleForm(UsersActions(onList = { _, change -> changed = change }), {}, startStep = 1, startHour = 9, is24Hour = true) } }
        compose.onAllNodes(hasSetTextAction() and hasText("Hour"))[1].performTextReplacement("08")
        compose.onNodeWithText("Add").assertIsNotEnabled()
        compose.onAllNodes(hasSetTextAction() and hasText("Hour"))[1].performTextReplacement("09")
        compose.onNodeWithText("Add").assertIsNotEnabled()
        compose.onAllNodes(hasSetTextAction() and hasText("Hour"))[1].performTextReplacement("00")
        compose.onNodeWithText("Add").performClick()
        assertEquals(AccessSchedule("Everyday", 9.0, 24.0), changed(emptyList()).single().asAccessSchedule())
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
