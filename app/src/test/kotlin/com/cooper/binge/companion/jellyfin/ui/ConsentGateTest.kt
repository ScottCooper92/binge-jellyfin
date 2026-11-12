package com.cooper.binge.companion.jellyfin.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.binge.designsystem.testing.createTakeDownAndroidComposeRule
import com.cooper.binge.companion.jellyfin.telemetry.AnalyticsConsent
import com.cooper.binge.companion.jellyfin.theme.JellyfinTheme
import com.cooper.binge.companion.jellyfin.ui.consent.ConsentGate
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** The usage data question stands in front of the app until it has an answer, and the settings reach the same answer after. */
@RunWith(RobolectricTestRunner::class)
class ConsentGateTest {
    @get:Rule
    val compose = createTakeDownAndroidComposeRule<ComponentActivity>()

    private fun gate(
        consent: AnalyticsConsent?,
        answers: MutableList<Boolean> = mutableListOf(),
    ) = compose.setContent { JellyfinTheme { ConsentGate(consent, { answers += it }) { Text("the app") } } }

    @Test
    fun `while the stored answer is read neither the question nor the app shows`() {
        gate(null)
        compose.onAllNodesWithText("Help make this app better").assertCountEquals(0)
        compose.onAllNodesWithText("the app").assertCountEquals(0)
    }

    @Test
    fun `an unanswered question is asked before the app`() {
        gate(AnalyticsConsent.UNDECIDED)
        compose.onNodeWithText("Help make this app better").assertExists()
        compose.onAllNodesWithText("the app").assertCountEquals(0)
    }

    @Test
    fun `sharing and declining each report their answer`() {
        val answers = mutableListOf<Boolean>()
        gate(AnalyticsConsent.UNDECIDED, answers)
        compose.onNodeWithText("Not now").performClick()
        compose.onNodeWithText("Share usage data").performClick()
        assertEquals(listOf(false, true), answers)
    }

    @Test
    fun `an answer, either one, lets the app show`() {
        gate(AnalyticsConsent.DENIED)
        compose.onNodeWithText("the app").assertExists()
        compose.onAllNodesWithText("Help make this app better").assertCountEquals(0)
    }

    @Test
    fun `the This app group reports each switch flipped`() {
        val calls = mutableListOf<String>()
        compose.setContent {
            JellyfinTheme {
                Column {
                    AppGroup(
                        AppSettings(shareUsageData = false, sendCrashReports = true),
                        Actions(onShareUsageData = { calls += "usage:$it" }, onSendCrashReports = { calls += "crash:$it" }),
                    )
                }
            }
        }
        compose.onNodeWithText("This app", ignoreCase = true).assertExists()
        compose.onNodeWithText("Share usage data").performClick()
        compose.onNodeWithText("Send crash reports").performClick()
        assertEquals(listOf("usage:true", "crash:false"), calls)
    }
}
