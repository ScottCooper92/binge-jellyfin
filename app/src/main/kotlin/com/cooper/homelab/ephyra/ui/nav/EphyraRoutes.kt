package com.cooper.homelab.ephyra.ui.nav

import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/**
 * The app's screens, as Navigation 3 keys. Serializable so the back stack is saved across a rotation and a process death.
 * The console's own pages and items are still on their own stack, inside [HubRoute] (#616).
 */
sealed interface EphyraRoute : NavKey

/** The start destination: the connection and the sign-in steps, and the loading screen while the session is worked out. */
@Serializable
data object HomeRoute : EphyraRoute

/** The signed-in home, which replaces [HomeRoute] at the root once the user is signed in. See [settleHome]. */
@Serializable
data object HubRoute : EphyraRoute

/**
 * Swaps the home root for the one that fits [signedIn]. An entry's pane role is fixed when it is built, so a change of
 * layout is a change of key. Only a home root is swapped, so a key opened over it stays. Leaving the signed-in home
 * takes the stack back to [HomeRoute], so nothing opened for the server just left stays over its sign-in.
 */
fun NavBackStack<NavKey>.settleHome(signedIn: Boolean) {
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
