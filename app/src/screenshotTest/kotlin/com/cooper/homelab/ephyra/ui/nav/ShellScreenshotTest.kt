package com.cooper.homelab.ephyra.ui.nav

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import com.android.tools.screenshot.PreviewTest
import com.cooper.homelab.core.uiandroid.shell.TabShellState
import com.cooper.homelab.ephyra.preview.JellyfinScreenStatePreview
import com.cooper.homelab.ephyra.preview.JellyfinSpanishPreviews
import com.cooper.homelab.ephyra.preview.JellyfinWidePreview
import com.cooper.homelab.ephyra.ui.console.ConsolePage

/**
 * The client's shell: the bar over a phone's tab roots, the rail beside a landscape tablet's, and on the tablet the Server tab
 * drawing the home beside the page and its item. The three new tabs are placeholders until their screens land.
 */
class ShellScreenshotTest {
    @Composable
    private fun Shell(
        selected: EphyraTab,
        open: NavBackStack<NavKey>.() -> Unit = {},
    ) = Surface {
        val tabs =
            remember {
                TabShellState(
                    tabs = EphyraTab.entries,
                    selectedIndex = mutableIntStateOf(EphyraTab.entries.indexOf(selected)),
                    stacks =
                        mapOf(
                            EphyraTab.Home to NavBackStack<NavKey>(HomeTabRoot),
                            EphyraTab.Libraries to NavBackStack<NavKey>(LibrariesTabRoot),
                            EphyraTab.Search to NavBackStack<NavKey>(SearchTabRoot),
                            EphyraTab.Server to NavBackStack<NavKey>(ServerTabRoot),
                        ),
                )
            }
        EphyraNavHost(
            backStack = remember { NavBackStack<NavKey>(ShellRoute).apply(open) },
            signedIn = true,
            tabs = tabs,
            onBack = {},
            signedOut = {},
            // The rail overlays the content, and the design system's own scaffolds inset for it; this bare text does not.
            home = {
                Column(Modifier.padding(start = 112.dp)) {
                    Text("Console home")
                    Text("Tasks")
                }
            },
            defaultPage = { Text("Now playing") },
            page = { entry -> Text(entry.page.name) },
            item = { entry -> Text("${entry.page.name} ${entry.item}") },
        )
    }

    @PreviewTest
    @JellyfinScreenStatePreview
    @Composable
    fun homeTabPhone() = Shell(EphyraTab.Home)

    @PreviewTest
    @JellyfinSpanishPreviews
    @Composable
    fun homeTabPhoneSpanish() = Shell(EphyraTab.Home)

    @PreviewTest
    @JellyfinScreenStatePreview
    @Composable
    fun serverTabPhone() = Shell(EphyraTab.Server)

    @PreviewTest
    @JellyfinWidePreview
    @Composable
    fun homeTabTablet() = Shell(EphyraTab.Home)

    @PreviewTest
    @JellyfinWidePreview
    @Composable
    fun serverTabTabletDefaultPage() = Shell(EphyraTab.Server)

    @PreviewTest
    @JellyfinWidePreview
    @Composable
    fun serverTabTabletPageOpen() = Shell(EphyraTab.Server) { openFromHome(ConsolePage.Activity) }

    @PreviewTest
    @JellyfinWidePreview
    @Composable
    fun serverTabTabletItemOpen() =
        Shell(EphyraTab.Server) {
            openFromHome(ConsolePage.Tasks)
            openItem("scan")
        }
}
