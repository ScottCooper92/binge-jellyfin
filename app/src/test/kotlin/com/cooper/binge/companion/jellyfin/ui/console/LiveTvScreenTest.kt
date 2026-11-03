package com.cooper.binge.companion.jellyfin.ui.console

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.binge.designsystem.testing.createTakeDownAndroidComposeRule
import com.cooper.binge.companion.jellyfin.server.GuideProviderSummary
import com.cooper.binge.companion.jellyfin.server.LiveTvSummary
import com.cooper.binge.companion.jellyfin.server.TunerHostSummary
import com.cooper.binge.companion.jellyfin.theme.JellyfinTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h2000dp")
class LiveTvScreenTest {
    @get:Rule
    val compose = createTakeDownAndroidComposeRule<ComponentActivity>()

    private val summary =
        LiveTvSummary(
            tuners = listOf(TunerHostSummary("t1", "IPTV", "m3u", "https://iptv.example:8443")),
            providers = listOf(GuideProviderSummary("g1", "SchedulesDirect", "alice")),
            guideEndMillis = NOW + DAY,
        )

    private fun show(
        state: LiveTvUiState,
        actions: LiveTvActions = LiveTvActions(),
    ) = compose.setContent { JellyfinTheme { LiveTvScreen(state, actions, now = NOW) } }

    @Test
    fun `the tuners and guide data are listed, each by its kind, and a tuner's account is not on the page`() {
        show(LiveTvUiState.Ready(summary))
        compose.onNodeWithText("IPTV").assertIsDisplayed()
        compose.onNodeWithText("M3U playlist", substring = true).assertIsDisplayed()
        compose.onNodeWithText("alice").assertIsDisplayed()
        compose.onNodeWithText("Schedules Direct").assertIsDisplayed()
        compose.onNodeWithText("Guide runs until").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `with nothing set up the page says so for each`() {
        show(LiveTvUiState.Ready(LiveTvSummary()))
        compose.onNodeWithText("No tuners").assertIsDisplayed()
        compose.onNodeWithText("No guide data").assertIsDisplayed()
    }

    @Test
    fun `Refresh guide opens Scheduled tasks where the user can open them`() {
        var opened = 0
        show(LiveTvUiState.Ready(summary), LiveTvActions(onOpenTasks = { opened++ }))
        compose.onNodeWithText("Refresh guide").performScrollTo().performClick()
        assertEquals(1, opened)
    }

    @Test
    fun `Refresh guide is not offered where the user cannot open Scheduled tasks`() {
        show(LiveTvUiState.Ready(summary), LiveTvActions(onOpenTasks = null))
        compose.onNodeWithText("Refresh guide").assertDoesNotExist()
    }

    @Test
    fun `Recording opens the recording page where the user may change it, and is not offered where they may not`() {
        var opened = 0
        show(LiveTvUiState.Ready(summary), LiveTvActions(onOpenRecording = { opened++ }))
        compose.onNodeWithText("Folders, padding and the files saved with a recording").performScrollTo().performClick()
        assertEquals(1, opened)
    }

    @Test
    fun `Recording is not offered to a manager who is not an administrator`() {
        show(LiveTvUiState.Ready(summary), LiveTvActions(onOpenRecording = null))
        compose.onNodeWithText("Folders, padding and the files saved with a recording").assertDoesNotExist()
    }

    @Test
    fun `a failure offers Retry`() {
        var retried = 0
        show(LiveTvUiState.Failed(ConsoleFailure.Unreachable), LiveTvActions(onRetry = { retried++ }))
        compose.onNodeWithText("Try again", substring = true).performClick()
        assertEquals(1, retried)
    }

    private companion object {
        const val NOW = 1_800_000_000_000L
        const val DAY = 86_400_000L
    }
}
