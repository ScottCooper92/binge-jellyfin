package com.cooper.homelab.ephyra.ui.nav

import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import com.cooper.homelab.ephyra.ui.console.ConsolePage
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
 * Swaps the home root for the one that fits [signedIn]. An entry's pane role is fixed when it is built, so a change of
 * layout is a change of key. Only a home root is swapped, so a key opened over it stays. Leaving the signed-in home
 * takes the stack back to [HomeRoute], so nothing opened for the server just left stays over its sign-in. That happens only once
 * the session is [resolved]: not signed in and resolved is a sign-out or a refused token, whereas after a process death the state
 * starts at Loading, nobody is signed out yet, and the restored stack is kept until the first answer settles it.
 */
fun NavBackStack<NavKey>.settleHome(
    signedIn: Boolean,
    resolved: Boolean = true,
) {
    if (!resolved) return
    val home = if (signedIn) HubRoute else HomeRoute
    val root = firstOrNull()
    when {
        root == HubRoute && !signedIn -> {
            clear()
            add(HomeRoute)
        }
        (root == HomeRoute || root == HubRoute) && root != home -> this[0] = home
    }
}
