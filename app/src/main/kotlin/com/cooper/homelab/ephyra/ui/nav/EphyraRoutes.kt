package com.cooper.homelab.ephyra.ui.nav

import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import com.cooper.homelab.ephyra.ui.console.ConsolePage
import com.cooper.homelab.ephyra.ui.console.coversWindow
import kotlinx.serialization.Serializable

/**
 * The app's screens, as Navigation 3 keys. Serializable so the back stack is saved across a rotation and a process death.
 * Every console page and item is a key above [HubRoute]; the helpers that open them are in `ConsoleStack.kt`.
 */
sealed interface EphyraRoute : NavKey

/** The start destination: the connection and the sign-in steps, and the loading screen while the session is worked out. */
@Serializable
data object HomeRoute : EphyraRoute

/** The signed-in home, which replaces [HomeRoute] at the root once the user is signed in. See [settleHome]. */
@Serializable
data object HubRoute : EphyraRoute

/**
 * One console page, above [HubRoute]. [opening] is its id: its view models are kept under it, so a page and the items
 * open in it share them, and the same page twice on the stack has a set each. [row] is the setting the page was opened on,
 * by its row key, for a search result: the page scrolls to it and washes it once. It is not part of the entry's identity.
 */
@Serializable
data class PageRoute(
    val page: ConsolePage,
    val opening: Int,
    val row: String? = null,
) : EphyraRoute

/** An item open in [page] (a task, a log, a user), above its [PageRoute], under the same [opening]. */
@Serializable
data class ItemRoute(
    val page: ConsolePage,
    val opening: Int,
    val item: String,
) : EphyraRoute

/**
 * The signed-in client, which replaces [HubRoute] at the root while the client is on (the user has not opted out, and the flag
 * offers it). Its entry holds the tab bar and each tab's root. The console's home is the Server tab's root, and its pages and
 * items are the keys above this one. With the client off there is no shell: [HubRoute] is the whole app. See [settleHome].
 */
@Serializable
data object ShellRoute : EphyraRoute

/**
 * Swaps the home root for the one that fits [signedIn] and [shell]: the sign-in steps, the console alone, or the client's shell.
 * An entry's pane role is fixed when it is built, so a change of layout is a change of key. Only a home root is swapped, so a
 * key opened over it stays, except when the client turns on or off: what was open belonged to the other layout, so it closes.
 * Leaving the signed-in home takes the stack back to [HomeRoute], so nothing opened for the server just left stays over its sign-in.
 */
fun NavBackStack<NavKey>.settleHome(
    signedIn: Boolean,
    shell: Boolean = false,
) {
    val home =
        when {
            !signedIn -> HomeRoute
            shell -> ShellRoute
            else -> HubRoute
        }
    val root = firstOrNull()
    val signedInRoot = root == HubRoute || root == ShellRoute
    when {
        signedInRoot && !signedIn -> {
            clear()
            add(HomeRoute)
        }
        signedInRoot && root != home -> {
            while (size > 1) removeAt(lastIndex)
            this[0] = home
        }
        root == HomeRoute && root != home -> this[0] = home
    }
}

/** Whether [this] is a page drawn beside the home: any page but the settings search, which covers the window. */
internal fun opensBesideHome(key: NavKey): Boolean = key is PageRoute && !key.page.coversWindow

/** The page the default pane shows (now playing), when it was opened on its own: it shows the same screen as the pane that stands in for it. */
internal fun isStandIn(key: NavKey): Boolean = key is PageRoute && key.page == ConsolePage.NowPlaying
