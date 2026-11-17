package com.cooper.binge.companion.jellyfin.ui.search

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cooper.binge.companion.jellyfin.AppGraph
import com.cooper.binge.companion.jellyfin.server.JellyfinAccount
import com.cooper.binge.companion.jellyfin.server.JellyfinServerProfile
import com.cooper.binge.companion.jellyfin.server.serverFailure
import com.cooper.binge.companion.jellyfin.ui.console.ConsolePage
import com.cooper.homelab.core.presentation.search.SettingsSearchViewModel
import com.cooper.homelab.core.uiandroid.search.SettingsSearchScreen

/**
 * The search route: the shared search page over the entries the user may open, with the ones they last opened from it kept on this
 * device ([LocalRecentSearches]). Opening a result remembers it before the page opens, and [open] gets the page and the row to scroll to.
 * The page has already read the profile and the account, so what the user may open is known at once.
 */
@Composable
internal fun SettingsSearchRoute(
    graph: AppGraph,
    profile: JellyfinServerProfile,
    account: JellyfinAccount,
    open: (ConsolePage, String?) -> Unit,
    onBack: () -> Unit,
) {
    val resources = LocalContext.current.resources
    val scope = rememberCoroutineScope()
    val entries = remember(resources) { settingsIndex(resources) }
    val search =
        remember(profile, account, entries) {
            SettingsSearchViewModel(
                scope = scope,
                appScope = graph.appScope,
                dispatcher = graph.ioDispatcher,
                entries = entries,
                loadAvailability = { { entry -> entry.available(profile, account) } },
                recents = graph.recentSearches,
                classify = { error -> error.serverFailure() },
            )
        }
    val state by search.state.collectAsStateWithLifecycle()
    SettingsSearchScreen(
        state = state,
        entries = entries,
        icon = { it.target.icon },
        onOpen = { entry ->
            search.remember(entry)
            open(entry.target.page, entry.row)
        },
        onBack = onBack,
        onRetry = search::retry,
        onClearRecent = search::clearRecent,
    )
}
