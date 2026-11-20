package com.cooper.homelab.ephyra.ui.nav

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.rememberNavBackStack
import com.binge.designsystem.component.BingeNavSuiteItem
import com.binge.designsystem.component.rememberBingeNavPresentation
import com.binge.designsystem.template.EmptyScreen
import com.cooper.homelab.core.uiandroid.shell.TabShell
import com.cooper.homelab.core.uiandroid.shell.TabShellState
import com.cooper.homelab.core.uiandroid.shell.rememberTabShellState
import com.cooper.homelab.ephyra.R
import kotlinx.serialization.Serializable

/**
 * The tabs of the client shell, in the order the bar shows them. The order must not change between launches: the selected tab
 * and each tab's saved state are kept by index. [Home] is the start tab, the one Back returns to. [Server] is the console, and
 * the tab a user who has opted out of the client keeps.
 *
 * A tap on the tab already showing pops it to its root and sends the tab on the design system's reselect signal. Each tab's root screen
 * scrolls to its top with `ScrollToTopOnTabReselect(EphyraTab.X, state)` when it lands: the Server home does, and Home, Libraries and
 * Search do as their screens arrive.
 */
enum class EphyraTab(
    val icon: ImageVector,
    val testTag: String,
) {
    Home(Icons.Filled.Home, "tab_home"),
    Libraries(Icons.Filled.VideoLibrary, "tab_libraries"),
    Search(Icons.Filled.Search, "tab_search"),
    Server(Icons.Filled.Dns, "tab_server"),
}

/** The root of each tab's own stack. Anything a tab opens goes on the app's stack above [ShellRoute], not on these. */
@Serializable
data object HomeTabRoot : NavKey

@Serializable
data object LibrariesTabRoot : NavKey

@Serializable
data object SearchTabRoot : NavKey

@Serializable
data object ServerTabRoot : NavKey

/** The shell's state: the selected tab, and a stack for each tab holding its root. It starts on [EphyraTab.Home]. */
@Composable
fun rememberEphyraTabs(): TabShellState<EphyraTab> {
    val home = rememberNavBackStack(HomeTabRoot)
    val libraries = rememberNavBackStack(LibrariesTabRoot)
    val search = rememberNavBackStack(SearchTabRoot)
    val server = rememberNavBackStack(ServerTabRoot)
    val stacks =
        remember(home, libraries, search, server) {
            mapOf(
                EphyraTab.Home to home,
                EphyraTab.Libraries to libraries,
                EphyraTab.Search to search,
                EphyraTab.Server to server,
            )
        }
    return rememberTabShellState(tabs = EphyraTab.entries, initial = EphyraTab.Home, stacks = stacks)
}

@Composable
private fun EphyraTab.label(): String =
    stringResource(
        when (this) {
            EphyraTab.Home -> R.string.shell_tab_home
            EphyraTab.Libraries -> R.string.shell_tab_libraries
            EphyraTab.Search -> R.string.shell_tab_search
            EphyraTab.Server -> R.string.shell_tab_server
        },
    )

/** The bar's destinations, labelled in the user's language. */
@Composable
internal fun ephyraTabItems(): List<BingeNavSuiteItem> =
    EphyraTab.entries.map { BingeNavSuiteItem(key = it, label = it.label(), icon = it.icon, testTag = it.testTag) }

/**
 * The tab bar over each tab's root. [server] is the console's home. A tab without a screen is a placeholder until its
 * screen lands. Only a root is drawn here: what a tab opens is an entry on the app's stack above the shell, so on a narrow window it
 * takes the whole window and the bar is hidden until Back pops it. Back at another tab's root returns to the start tab, and Back at
 * the start tab's root leaves the app. [backToStart] is off while something is open beside the home. [home] is the Home tab's screen, a
 * composable that takes the whole tab, as [server] does: until it is given, Home shows its placeholder. [libraries] is the same for the
 * Libraries tab, and [search] the Search tab.
 */
@Composable
internal fun EphyraTabShell(
    tabs: TabShellState<EphyraTab>,
    modifier: Modifier = Modifier,
    backToStart: Boolean = true,
    home: (@Composable () -> Unit)? = null,
    libraries: (@Composable () -> Unit)? = null,
    search: (@Composable () -> Unit)? = null,
    server: @Composable () -> Unit,
) {
    TabShell(
        state = tabs,
        items = ephyraTabItems(),
        // The design system's own choice: the floating bar, or the rail on a landscape tablet.
        presentation = rememberBingeNavPresentation(),
        modifier = modifier,
        backToStart = backToStart,
    ) { tab ->
        when (tab) {
            EphyraTab.Server -> server()
            EphyraTab.Home -> home?.invoke() ?: TabPlaceholder(tab)
            EphyraTab.Libraries -> libraries?.invoke() ?: TabPlaceholder(tab)
            EphyraTab.Search -> search?.invoke() ?: TabPlaceholder(tab)
            else -> TabPlaceholder(tab)
        }
    }
}

/** A tab whose screen has not landed. */
@Composable
internal fun TabPlaceholder(
    tab: EphyraTab,
    modifier: Modifier = Modifier,
) {
    Box(modifier.fillMaxSize()) {
        EmptyScreen(
            message = stringResource(R.string.shell_placeholder_message),
            title = tab.label(),
            icon = tab.icon,
        )
    }
}
