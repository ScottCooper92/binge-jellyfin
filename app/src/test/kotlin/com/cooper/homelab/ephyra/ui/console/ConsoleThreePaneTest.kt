package com.cooper.homelab.ephyra.ui.console

import androidx.activity.ComponentActivity
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import com.binge.designsystem.testing.createTakeDownAndroidComposeRule
import com.cooper.homelab.ephyra.ui.nav.EphyraNavHost
import com.cooper.homelab.ephyra.ui.nav.closeTop
import com.cooper.homelab.ephyra.ui.nav.openFromHome
import com.cooper.homelab.ephyra.ui.nav.rememberConsoleBackStack
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** A landscape tablet: the home, a page's list and what it opened are three panes, a third of the window each. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w1200dp-h800dp")
class ConsoleThreePaneTest {
    @get:Rule
    val compose = createTakeDownAndroidComposeRule<ComponentActivity>()

    private fun host() {
        compose.setContent {
            val stack = rememberConsoleBackStack()
            EphyraNavHost(
                backStack = stack,
                signedIn = true,
                onBack = { stack.closeTop() },
                signedOut = {},
                home = { Column { Text("home", Modifier.clickable { stack.openFromHome(ConsolePage.Activity) }) } },
                defaultPage = { Text("default page") },
                page = { Text("the page") },
            )
        }
    }

    @Test
    fun `the default page fills the pane beside the home`() {
        host()
        compose.onNodeWithText("home").assertIsDisplayed()
        compose.onNodeWithText("default page").assertIsDisplayed()
    }
}
