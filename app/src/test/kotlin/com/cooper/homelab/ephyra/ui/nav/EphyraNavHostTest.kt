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
import com.cooper.homelab.ephyra.ui.console.ConsolePage
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
    private var resolved by mutableStateOf(true)
    private var stack: NavBackStack<NavKey>? = null

    private fun host() {
        compose.setContent {
            val backStack = rememberNavBackStack(HomeRoute)
            stack = backStack
            EphyraNavHost(
                backStack = backStack,
                signedIn = signedIn,
                resolved = resolved,
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

    private fun openPageAndItem(): List<NavKey> {
        compose.runOnUiThread {
            stack?.openPage(ConsolePage.Tasks)
            stack?.openItem("scan")
        }
        compose.waitForIdle()
        return stack?.toList().orEmpty()
    }

    private fun recreateWhileLoading() {
        compose.scenario.recreate()
        // After a process death the view model is new and its state starts at Loading: nobody is signed in yet.
        signedIn = false
        resolved = false
        host()
        compose.waitForIdle()
    }

    @Test
    fun `a restored stack is kept while the session is still loading, and when it resolves as signed in`() {
        signedIn = true
        host()
        compose.waitForIdle()
        val open = openPageAndItem()
        assertEquals(3, open.size)
        recreateWhileLoading()
        assertEquals(open, stack?.toList())
        compose.runOnUiThread {
            signedIn = true
            resolved = true
        }
        compose.waitForIdle()
        assertEquals(open, stack?.toList())
    }

    @Test
    fun `a session that resolves as signed out, as with a refused token, still clears the restored stack`() {
        signedIn = true
        host()
        compose.waitForIdle()
        openPageAndItem()
        recreateWhileLoading()
        compose.runOnUiThread { resolved = true }
        compose.waitForIdle()
        assertEquals(listOf<NavKey>(HomeRoute), stack?.toList())
        compose.onNodeWithText("sign in").assertIsDisplayed()
    }

    @Test
    fun `settling while the session is unresolved changes nothing`() {
        val back = NavBackStack<NavKey>(HubRoute, PageRoute(ConsolePage.Logs, 1))
        back.settleHome(signedIn = false, resolved = false)
        assertEquals(listOf<NavKey>(HubRoute, PageRoute(ConsolePage.Logs, 1)), back.toList())
        back.settleHome(signedIn = false)
        assertEquals(listOf<NavKey>(HomeRoute), back.toList())
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
