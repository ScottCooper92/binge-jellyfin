package com.cooper.binge.companion.jellyfin.ui.console

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.binge.designsystem.testing.createTakeDownAndroidComposeRule
import com.cooper.binge.companion.jellyfin.server.POLICY_BITRATE_LIMIT
import com.cooper.binge.companion.jellyfin.server.POLICY_LOCKOUT
import com.cooper.binge.companion.jellyfin.server.POLICY_MAX_SESSIONS
import com.cooper.binge.companion.jellyfin.theme.JellyfinTheme
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** The three numbers of a user's policy as sliders: what a stored value reads as, and what a move saves. */
@RunWith(RobolectricTestRunner::class)
class UserPolicySlidersTest {
    @get:Rule
    val compose = createTakeDownAndroidComposeRule<ComponentActivity>()

    private val saved = mutableListOf<Pair<String, Int>>()

    private fun show(
        picker: PolicyPicker,
        policy: String,
    ) {
        val sent: JsonObject = Json.parseToJsonElement(policy).jsonObject
        val actions = UsersActions(onPolicy = { key, value -> saved += key to (value as JsonPrimitive).content.toInt() })
        compose.setContent { JellyfinTheme { PolicyPickerSheet(picker, sent, UserChoices(), actions) {} } }
    }

    private fun tap(description: String) = compose.onNodeWithContentDescription(description).performClick()

    @Test
    fun `no session limit is the open end, and nothing is saved on open`() {
        show(PolicyPicker.Sessions, """{"MaxActiveSessions":0}""")
        compose.onNodeWithText("No limit").assertIsDisplayed()
        assertTrue(saved.isEmpty())
    }

    @Test
    fun `a session count saves the number a step lands on, and stepping down from no limit lands on 20`() {
        show(PolicyPicker.Sessions, """{"MaxActiveSessions":3}""")
        tap("Increase Sessions at once")
        assertEquals(listOf(POLICY_MAX_SESSIONS to 4), saved)
        saved.clear()
    }

    @Test
    fun `from no limit a step down saves 20 sessions`() {
        show(PolicyPicker.Sessions, """{}""")
        tap("Decrease Sessions at once")
        assertEquals(listOf(POLICY_MAX_SESSIONS to 20), saved)
    }

    @Test
    fun `the open end of the sessions slider saves 0`() {
        show(PolicyPicker.Sessions, """{"MaxActiveSessions":20}""")
        tap("Increase Sessions at once")
        assertEquals(listOf(POLICY_MAX_SESSIONS to 0), saved)
    }

    @Test
    fun `a whole megabit reads as stored, and a step saves in bits`() {
        show(PolicyPicker.Bitrate, """{"RemoteClientBitrateLimit":8000000}""")
        compose.onNodeWithText("8 Mbps").assertIsDisplayed()
        tap("Decrease Bitrate limit away from home")
        assertEquals(listOf(POLICY_BITRATE_LIMIT to 7_000_000), saved)
    }

    @Test
    fun `a bitrate between two megabits reads exactly as stored and is not rewritten on open`() {
        show(PolicyPicker.Bitrate, """{"RemoteClientBitrateLimit":2500000}""")
        compose.onNodeWithText("2.5 Mbps").assertIsDisplayed()
        assertTrue(saved.isEmpty())
    }

    @Test
    fun `from between two megabits a step lands on the next whole one above or below`() {
        show(PolicyPicker.Bitrate, """{"RemoteClientBitrateLimit":2500000}""")
        tap("Increase Bitrate limit away from home")
        assertEquals(listOf(POLICY_BITRATE_LIMIT to 3_000_000), saved)
    }

    @Test
    fun `from between two megabits a step down lands on the whole one below`() {
        show(PolicyPicker.Bitrate, """{"RemoteClientBitrateLimit":2500000}""")
        tap("Decrease Bitrate limit away from home")
        assertEquals(listOf(POLICY_BITRATE_LIMIT to 2_000_000), saved)
    }

    @Test
    fun `a bitrate beyond the scale reads as stored, and no limit is the open end`() {
        show(PolicyPicker.Bitrate, """{"RemoteClientBitrateLimit":120000000}""")
        compose.onNodeWithText("120 Mbps").assertIsDisplayed()
        assertTrue(saved.isEmpty())
    }

    @Test
    fun `no bitrate limit is the open end, and a step down from it saves 100 megabits`() {
        show(PolicyPicker.Bitrate, """{"RemoteClientBitrateLimit":0}""")
        compose.onNodeWithText("No limit").assertIsDisplayed()
        tap("Decrease Bitrate limit away from home")
        assertEquals(listOf(POLICY_BITRATE_LIMIT to 100_000_000), saved)
    }

    @Test
    fun `a lockout count reads as stored, and a step saves the count`() {
        show(PolicyPicker.Lockout, """{"LoginAttemptsBeforeLockout":5}""")
        compose.onNodeWithText("After 5 failed sign-ins").assertIsDisplayed()
        tap("Increase Lock out after failed sign-ins")
        assertEquals(listOf(POLICY_LOCKOUT to 6), saved)
    }

    @Test
    fun `never locking out is the open end, saved as -1`() {
        show(PolicyPicker.Lockout, """{"LoginAttemptsBeforeLockout":-1}""")
        compose.onNodeWithText("Never lock out").assertIsDisplayed()
        assertTrue(saved.isEmpty())
        tap("Decrease Lock out after failed sign-ins")
        assertEquals(listOf(POLICY_LOCKOUT to 20), saved)
        saved.clear()
    }

    @Test
    fun `the open end of the lockout slider saves -1`() {
        show(PolicyPicker.Lockout, """{"LoginAttemptsBeforeLockout":20}""")
        tap("Increase Lock out after failed sign-ins")
        assertEquals(listOf(POLICY_LOCKOUT to -1), saved)
    }

    @Test
    fun `a stored 0 still reads as the server's default, and is not rewritten on open`() {
        show(PolicyPicker.Lockout, """{"LoginAttemptsBeforeLockout":0}""")
        compose.onNodeWithText("Server default").assertIsDisplayed()
        assertTrue(saved.isEmpty())
    }
}
