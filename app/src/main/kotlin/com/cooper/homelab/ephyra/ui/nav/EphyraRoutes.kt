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
 * One library's titles, above [ShellRoute]: opened from the Libraries tab, it covers the bar on every width and Back closes it. It
 * carries the library's id alone, so it survives a process death; the screen reads the rest from the server.
 */
@Serializable
data class LibraryRoute(
    val libraryId: String,
) : EphyraRoute

/**
 * A title's page, above [ShellRoute]: opened from a card or a tile, it covers the bar or rail on every width and Back closes it. A key
 * carries the item's id alone and no screen state, so it survives a process death and a hand-off from another app can build a back
 * stack of them. Its screen is the host's `title` slot; the key names only which kind of page it is.
 */
sealed interface TitleRoute : EphyraRoute {
    val itemId: String
}

@Serializable
data class MovieRoute(
    override val itemId: String,
) : TitleRoute

@Serializable
data class SeriesRoute(
    override val itemId: String,
) : TitleRoute

@Serializable
data class SeasonRoute(
    override val itemId: String,
) : TitleRoute

@Serializable
data class EpisodeRoute(
    override val itemId: String,
) : TitleRoute

/** A collection (a box set) of movies. */
@Serializable
data class CollectionRoute(
    override val itemId: String,
) : TitleRoute

/** A person: an actor, a director, a writer. */
@Serializable
data class PersonRoute(
    override val itemId: String,
) : TitleRoute

/**
 * A play, above the title page it began from: the player covers the window, bar and all. It carries the sitting's id and the title's
 * name alone, so it survives a process death; a sitting that is not known then shows as one that is gone.
 */
@Serializable
data class PlayerRoute(
    val sessionId: String,
    val title: String,
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
 * That happens only once the session is [resolved]: not signed in and resolved is a sign-out or a refused token, whereas after a
 * process death the state starts at Loading, nobody is signed out yet, and the restored stack is kept until the first answer settles it.
 */
fun NavBackStack<NavKey>.settleHome(
    signedIn: Boolean,
    shell: Boolean = false,
    resolved: Boolean = true,
) {
    if (!resolved) return
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
