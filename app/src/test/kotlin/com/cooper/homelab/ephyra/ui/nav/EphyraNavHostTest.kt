package com.cooper.homelab.ephyra.ui.nav

import androidx.activity.ComponentActivity
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.rememberNavBackStack
import com.binge.designsystem.testing.createTakeDownAndroidComposeRule
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** The app's one nav host, driven for real: the home is settled by the sign-in, and the stack survives the Activity being made again. */
@RunWith(RobolectricTestRunner::class)
class EphyraNavHostTest {
    @get:Rule
    val compose = createTakeDownAndroidComposeRule<ComponentActivity>()

    private var signedIn by mutableStateOf(false)
    private var stack: NavBackStack<NavKey>? = null

    private fun host() {
        compose.setContent {
            val backStack = rememberNavBackStack(HomeRoute)
            stack = backStack
            EphyraNavHost(
                backStack = backStack,
                signedIn = signedIn,
                onBack = {},
                signedOut = { Text("sign in") },
                home = { Column { Text("console", Modifier.clickable { }) } },
                defaultPage = {},
                page = {},
            )
        }
    }

    @Test
    fun `signed out shows the sign-in steps, and signing in settles the home to the console`() {
        host()
        compose.onNodeWithText("sign in").assertIsDisplayed()
        assertEquals(listOf<NavKey>(HomeRoute), stack?.toList())
        compose.runOnUiThread { signedIn = true }
        compose.waitForIdle()
        compose.onNodeWithText("console").assertIsDisplayed()
        assertEquals(listOf<NavKey>(HubRoute), stack?.toList())
    }

    @Test
    fun `signing out returns to the sign-in steps and clears what was open over the home`() {
        signedIn = true
        host()
        compose.waitForIdle()
        // A key opened over the home belongs to the server just left.
        compose.runOnUiThread { stack?.add(HomeRoute) }
        compose.runOnUiThread { signedIn = false }
        compose.waitForIdle()
        assertEquals(listOf<NavKey>(HomeRoute), stack?.toList())
        compose.onNodeWithText("sign in").assertIsDisplayed()
    }

    @Test
    fun `the stack survives the Activity being made again`() {
        signedIn = true
        host()
        compose.waitForIdle()
        compose.onNodeWithText("console").assertIsDisplayed()
        compose.scenario.recreate()
        host()
        compose.waitForIdle()
        assertEquals(listOf<NavKey>(HubRoute), stack?.toList())
        compose.onNodeWithText("console").assertIsDisplayed()
    }

    @Test
    fun `a home root is the only thing settled, so a key opened over it stays`() {
        val back = NavBackStack<NavKey>(HomeRoute)
        back.settleHome(signedIn = true)
        assertEquals(listOf<NavKey>(HubRoute), back.toList())
        back.add(HomeRoute)
        back.settleHome(signedIn = true)
        assertEquals(listOf<NavKey>(HubRoute, HomeRoute), back.toList())
    }
}
