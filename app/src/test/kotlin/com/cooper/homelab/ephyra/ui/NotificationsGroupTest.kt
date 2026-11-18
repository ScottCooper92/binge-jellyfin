package com.cooper.homelab.ephyra.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.binge.designsystem.testing.createTakeDownAndroidComposeRule
import com.cooper.homelab.ephyra.theme.JellyfinTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The Notifications group on the This app page: an administrator's alone, and each row only while it means something. */
@RunWith(RobolectricTestRunner::class)
class NotificationsGroupTest {
    @get:Rule
    val compose = createTakeDownAndroidComposeRule<ComponentActivity>()

    private fun show(
        settings: NotificationSettings?,
        actions: Actions = Actions(),
    ) = compose.setContent { JellyfinTheme { ThisAppScreen(actions.copy(notifications = settings), onBack = {}) } }

    @Test
    fun `a user who is not an administrator is shown no such group`() {
        show(null)
        compose.onAllNodesWithText("Server alerts").assertCountEquals(0)
        compose.onAllNodesWithText("Check every").assertCountEquals(0)
    }

    @Test
    fun `off, only the switch shows`() {
        show(NotificationSettings(enabled = false, blocked = true, unrestricted = false))
        compose.onNodeWithText("Server alerts").assertExists()
        compose.onAllNodesWithText("Check every").assertCountEquals(0)
        compose.onAllNodesWithText("Android won't show alerts").assertCountEquals(0)
        compose.onAllNodesWithText("Allow background checks").assertCountEquals(0)
    }

    @Test
    fun `on, it shows the cadence, and the warnings only while they apply`() {
        show(NotificationSettings(enabled = true, intervalMinutes = 120, blocked = false, unrestricted = true))
        compose.onNodeWithText("Check every").assertExists()
        compose.onNodeWithText("2 hours").assertExists()
        compose.onAllNodesWithText("Android won't show alerts").assertCountEquals(0)
        compose.onAllNodesWithText("Allow background checks").assertCountEquals(0)
    }

    @Test
    fun `on, with Android in the way, both warnings show`() {
        show(NotificationSettings(enabled = true, blocked = true, unrestricted = false))
        compose.onNodeWithText("Android won't show alerts").assertExists()
        compose.onNodeWithText("Allow background checks").assertExists()
    }

    @Test
    @Config(qualifiers = "w411dp-h891dp")
    fun `the switch asks to turn on, and then off`() {
        val asked = mutableListOf<Boolean>()
        show(NotificationSettings(enabled = false), Actions(onNotificationsEnabled = { asked += it }))
        compose.onNodeWithText("Server alerts").performClick()
        assertEquals(listOf(true), asked)
    }

    @Test
    fun `a chosen interval is passed on`() {
        val chosen = mutableListOf<Int>()
        compose.setContent {
            JellyfinTheme {
                IntervalChoices(NotificationSettings(enabled = true), Actions(onNotificationInterval = { chosen += it }), close = {})
            }
        }
        compose.onNodeWithText("6 hours").performClick()
        assertEquals(listOf(360), chosen)
    }
}
