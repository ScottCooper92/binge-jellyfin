package com.cooper.homelab.ephyra.ui.search

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.binge.designsystem.component.BingeSnackbarHost
import com.cooper.homelab.core.presentation.search.SearchAccess
import com.cooper.homelab.core.presentation.search.SettingsSearchViewModel
import com.cooper.homelab.core.uiandroid.search.SettingsSearchScreen
import com.cooper.homelab.ephyra.AppGraph
import com.cooper.homelab.ephyra.server.JellyfinAccount
import com.cooper.homelab.ephyra.server.JellyfinServerProfile
import com.cooper.homelab.ephyra.server.serverFailure
import com.cooper.homelab.ephyra.ui.Actions
import com.cooper.homelab.ephyra.ui.console.ConsolePage

/**
 * The search route: the shared search page over the entries the user may open, with the ones they last opened from it kept on this
 * device ([LocalRecentSearches]). Opening a result remembers it before the page opens, and [open] gets the page and the row to scroll to.
 * The page has already read the profile and the account, so what the user may open is known at once. Behind a debug switch
 * ([AppGraph.searchTogglesEnabled]) a result for an on/off setting carries its switch, through [actions], the page's own.
 */
@Composable
internal fun SettingsSearchRoute(
    graph: AppGraph,
    profile: JellyfinServerProfile,
    account: JellyfinAccount,
    actions: Actions,
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
                loadAvailability = {
                    // The Transcoding page draws many rows for some acceleration types only: for an administrator, read which, once.
                    val encoding = if (ConsolePage.Transcoding.available(profile, account)) graph.connection.encodingForSearch() else null
                    SearchAccess({ entry -> entry.available(profile, account) && entry.drawnFor(encoding, profile) }, Unit)
                },
                recents = graph.recentSearches,
                classify = { error -> error.serverFailure() },
            )
        }
    val state by search.state.collectAsStateWithLifecycle()
    var switches by remember { mutableStateOf<SearchSwitchesViewModel?>(null) }
    // Once each time the search comes to the front (this composition is new), however many switches it shows: a page opened over the
    // search and closed is read, not written back over.
    LaunchedEffect(switches) { switches?.refresh() }
    val host = remember { SnackbarHostState() }
    Box(Modifier.fillMaxSize()) {
        SettingsSearchScreen(
            state = state,
            entries = entries,
            icon = { it.target.icon },
            onOpen = { entry, _ ->
                search.remember(entry)
                // The page reads the configuration as it opens: what the search still owes the server goes first.
                switches?.leaving()
                open(entry.target.page, entry.row)
            },
            onBack = onBack,
            onRetry = search::retry,
            onClearRecent = search::clearRecent,
            toggle =
                if (graph.searchTogglesEnabled) {
                    { entry, _ -> configSwitchToggle(entry.row, graph, profile, account) { switches = it } ?: searchToggle(entry, actions) }
                } else {
                    null
                },
            pinnedBar = true,
        )
        // What the pages say of a save, said here too, since the user does not leave the search to flip a switch.
        SearchServerMessages(switches, host)
        BingeSnackbarHost(host, Modifier.align(Alignment.BottomCenter).windowInsetsPadding(WindowInsets.safeDrawing))
    }
}
