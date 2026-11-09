package com.cooper.binge.companion.jellyfin.ui.console

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.binge.designsystem.testing.assertNoTextClipped
import com.binge.designsystem.testing.createTakeDownAndroidComposeRule
import com.cooper.binge.companion.jellyfin.server.ActivityEntry
import com.cooper.binge.companion.jellyfin.server.ActivityFilter
import com.cooper.binge.companion.jellyfin.server.ActivityTimePreset
import com.cooper.binge.companion.jellyfin.server.ActivityWho
import com.cooper.binge.companion.jellyfin.server.Severity
import com.cooper.binge.companion.jellyfin.theme.JellyfinTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h2000dp")
class ActivityFiltersScreenTest {
    @get:Rule
    val compose = createTakeDownAndroidComposeRule<ComponentActivity>()

    private val entry = ActivityEntry(1, "alice signed in", "192.168.1.5", "SessionStarted", 1_700_000_000_000L, Severity.Information)
    private val applied =
        ActivityFilter(
            fromMillis = 1_699_000_000_000L,
            preset = ActivityTimePreset.Last7Days,
            who = ActivityWho.Users,
            username = "alice",
            severity = Severity.Warning,
            type = "VideoPlayback",
        )

    private fun show(
        state: ActivityUiState = ActivityUiState.Ready(listOf(entry), 1),
        filters: ActivityFilters = ActivityFilters(),
        actions: ActivityFilterActions = ActivityFilterActions(),
        extended: Boolean = true,
    ) = compose.setContent {
        JellyfinTheme { ActivityScreen(state, ActivityActions(), filters = filters, filterActions = actions, extendedFilters = extended) }
    }

    @Test
    fun `the filter button opens the sheet`() {
        var opened = false
        show(actions = ActivityFilterActions(onOpen = { opened = true }))
        compose.onNodeWithContentDescription("Filters").performClick()
        assertTrue(opened)
    }

    @Test
    fun `the active filters are chips above the list, and the button says how many`() {
        show(filters = ActivityFilters(applied = applied, draft = applied))
        compose.onNodeWithText("Last 7 days").assertIsDisplayed()
        compose.onNodeWithText("alice").assertIsDisplayed()
        compose.onNodeWithText("Warnings").assertIsDisplayed()
        compose.onNodeWithText("Video playback").assertIsDisplayed()
        compose.onNodeWithContentDescription("Filters, 4 active").assertIsDisplayed()
    }

    @Test
    fun `a chip clears its filter`() {
        val cleared = mutableListOf<ActivityFilterKind>()
        show(
            filters = ActivityFilters(applied = applied, draft = applied),
            actions = ActivityFilterActions(onClear = { cleared += it }),
        )
        compose.onNodeWithText("Warnings").performClick()
        assertEquals(listOf(ActivityFilterKind.Severity), cleared)
    }

    @Test
    fun `a filter that matches nothing is not the empty log, and offers to clear them`() {
        var cleared = false
        show(
            state = ActivityUiState.Ready(emptyList(), 0),
            filters = ActivityFilters(applied = applied, draft = applied),
            actions = ActivityFilterActions(onClearAll = { cleared = true }),
        )
        compose.onNodeWithText("Nothing matches these filters").assertIsDisplayed()
        compose.onNodeWithText("Clear filters").performClick()
        assertTrue(cleared)
    }

    @Test
    fun `an empty log with no filter keeps its own empty state`() {
        show(state = ActivityUiState.Ready(emptyList(), 0))
        compose.onNodeWithText("Nothing has happened on this server yet.").assertIsDisplayed()
    }

    @Test
    @Config(fontScale = 2f)
    fun `at twice the font the chips and rows are not cropped`() {
        show(filters = ActivityFilters(applied = applied, draft = applied))
        assertTrue(compose.assertNoTextClipped() > 0)
    }
}
