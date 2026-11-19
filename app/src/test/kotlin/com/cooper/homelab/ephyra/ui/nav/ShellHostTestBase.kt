package com.cooper.homelab.ephyra.ui.nav

import androidx.activity.ComponentActivity
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.rememberNavBackStack
import com.binge.designsystem.testing.createTakeDownAndroidComposeRule
import com.cooper.homelab.core.uiandroid.shell.RestartOnGateChange
import com.cooper.homelab.core.uiandroid.shell.RestartOnSessionEnd
import com.cooper.homelab.core.uiandroid.shell.TabShellState
import com.cooper.homelab.ephyra.theme.JellyfinTheme
import com.cooper.homelab.ephyra.ui.console.ConsolePage
import org.junit.Rule

/**
 * The app's one nav host with the client's shell, driven for real: the signed-in home settles to [ShellRoute] while the gate is on,
 * the console's home is the Server tab, and the pages open as keys above the shell. Subclasses set the window.
 */
abstract class ShellHostTestBase {
    @get:Rule
    val compose = createTakeDownAndroidComposeRule<ComponentActivity>()

    /** The gate's resolved value: null until it is read, then whether the client shows. */
    protected var gate by mutableStateOf<Boolean?>(true)

    /** The session: null while it is worked out, false when signed out or the token is refused, true while signed in. */
    protected var connected by mutableStateOf<Boolean?>(true)
    protected var stack: NavBackStack<NavKey>? = null
    protected var tabs: TabShellState<EphyraTab>? = null

    protected fun host(homeTab: (@Composable () -> Unit)? = null) {
        compose.setContent {
            JellyfinTheme {
                val backStack = rememberNavBackStack(HomeRoute)
                val shell = rememberEphyraTabs()
                stack = backStack
                tabs = shell
                RestartOnGateChange(gate, shell, backStack, offTab = EphyraTab.Server)
                RestartOnSessionEnd(connected, shell, backStack)
                EphyraNavHost(
                    backStack = backStack,
                    signedIn = connected == true,
                    resolved = connected != null,
                    tabs = shell.takeIf { gate ?: true },
                    homeTab = homeTab,
                    onBack = { backStack.closeTop() },
                    signedOut = { Text("sign in") },
                    home = {
                        // The rail overlays the content; the design system's own scaffolds inset for it, a bare Text does not.
                        Column(Modifier.padding(start = 112.dp)) {
                            Text("console home")
                            Text("open logs", Modifier.clickable { backStack.openFromHome(ConsolePage.Logs) })
                            Text("open tasks", Modifier.clickable { backStack.openFromHome(ConsolePage.Tasks) })
                        }
                    },
                    defaultPage = { Text("default page") },
                    page = { Text("page ${it.page.name}") },
                    item = { Text("item ${it.item}") },
                )
            }
        }
        compose.waitForIdle()
    }

    protected fun tap(tag: String) {
        compose.onNodeWithTag(tag).performClick()
        compose.waitForIdle()
    }

    protected fun click(text: String) {
        compose.onNodeWithText(text).performClick()
        compose.waitForIdle()
    }

    protected fun assertShown(text: String) = compose.onNodeWithText(text).assertIsDisplayed()

    protected fun assertAbsent(text: String) = compose.onNodeWithText(text).assertDoesNotExist()

    protected fun assertBar(shown: Boolean) {
        val node = compose.onNodeWithTag("tab_server")
        if (shown) node.assertIsDisplayed() else node.assertDoesNotExist()
    }

    protected fun keys(): List<NavKey> = stack?.toList().orEmpty()

    /** Back as the system sends it: true when the app has nothing to close and leaves. */
    protected fun pressBack(): Boolean {
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()
        return compose.activity.isFinishing
    }

    protected fun pageKeys(): List<PageRoute> = keys().filterIsInstance<PageRoute>()
}
