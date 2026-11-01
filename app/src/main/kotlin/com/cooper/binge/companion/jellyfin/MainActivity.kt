package com.cooper.binge.companion.jellyfin

import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.cooper.binge.companion.jellyfin.flags.LocalFlags
import com.cooper.binge.companion.jellyfin.theme.JellyfinTheme
import com.cooper.binge.companion.jellyfin.theme.JellyfinTvTheme
import com.cooper.binge.companion.jellyfin.ui.Actions
import com.cooper.binge.companion.jellyfin.ui.JellyfinRoot
import com.cooper.binge.companion.jellyfin.ui.JellyfinViewModel
import com.cooper.binge.companion.jellyfin.ui.UiState
import com.cooper.binge.companion.jellyfin.ui.console.ConsolePage
import com.cooper.binge.companion.jellyfin.ui.console.ConsolePageRoute
import com.cooper.binge.companion.jellyfin.ui.console.ConsolePageScope
import com.cooper.binge.companion.jellyfin.ui.console.ConsolePageStores
import com.cooper.binge.companion.jellyfin.ui.tv.TvJellyfinRoot
import com.cooper.binge.companion.jellyfin.ui.tv.isTelevision
import kotlinx.coroutines.launch

/** The app's own UI: connect to a server, sign in, and the signed-in settings. */
class MainActivity : ComponentActivity() {
    /** What runs once the user has answered the local-network permission, whatever they chose. */
    private var afterLocalNetwork: (() -> Unit)? = null

    private val localNetwork =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {
            afterLocalNetwork?.invoke()
            afterLocalNetwork = null
        }

    /**
     * Runs [then] with the local-network permission asked for where the platform has it (Android 17).
     * Refused, [then] still runs: the search finds nothing and the typed address is the way in.
     */
    private fun withLocalNetwork(then: () -> Unit) {
        val granted =
            Build.VERSION.SDK_INT < LOCAL_NETWORK_SDK ||
                checkSelfPermission(ACCESS_LOCAL_NETWORK) == PackageManager.PERMISSION_GRANTED
        if (granted) {
            then()
        } else {
            afterLocalNetwork = then
            localNetwork.launch(ACCESS_LOCAL_NETWORK)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val graph = (application as JellyfinApp).graph
        val factory =
            viewModelFactory {
                initializer {
                    JellyfinViewModel(
                        graph.sessions,
                        graph.connection,
                        graph.index,
                        discover = graph.discovery::find,
                    )
                }
            }
        // Fresh Remote Config values for the flags; until they land, the last ones activated are used.
        lifecycleScope.launch { graph.flags.refresh() }
        setContent {
            val viewModel: JellyfinViewModel = viewModel(factory = factory)
            val state by viewModel.state.collectAsStateWithLifecycle()
            // The console page open over its home, if any. Back closes it; leaving SignedIn closes it too.
            var page by rememberSaveable { mutableStateOf<ConsolePage?>(null) }
            // Counts openings, so each one gets a fresh store for its page's view model.
            var opened by rememberSaveable { mutableIntStateOf(0) }
            val pageStores: ConsolePageStores = viewModel()
            val home = rememberSaveableStateHolder()
            val signedIn = state is UiState.SignedIn
            LaunchedEffect(signedIn) { if (!signedIn) page = null }
            // A page the server or the user lost (Quick Connect turned off, admin rights taken away) closes,
            // once the refresh has read what they now allow.
            (state as? UiState.SignedIn)?.let { gated ->
                LaunchedEffect(gated.profile, gated.account) {
                    if (gated.account.isRead && page?.available(gated.profile, gated.account) == false) page = null
                }
            }
            val openPage = page.takeIf { signedIn }
            LaunchedEffect(openPage) { if (openPage == null) pageStores.close() }
            RefreshGatesWhenHomeReturns(openPage) { viewModel.refresh(gatesOnly = true) }
            BackHandler(enabled = openPage != null) { page = null }
            val actions =
                Actions(
                    onAddress = viewModel::onAddress,
                    onConnect = viewModel::connect,
                    onUsername = viewModel::onUsername,
                    onPassword = viewModel::onPassword,
                    onSignIn = viewModel::signInWithPassword,
                    onQuickConnect = viewModel::startQuickConnect,
                    onCancelQuickConnect = viewModel::cancelQuickConnect,
                    onChangeServer = viewModel::changeServer,
                    onRefresh = viewModel::refresh,
                    onAskSignOut = { viewModel.confirmSignOut(true) },
                    onCancelSignOut = { viewModel.confirmSignOut(false) },
                    onSignOut = viewModel::signOut,
                    onDiscover = { withLocalNetwork(viewModel::discoverServers) },
                    onChooseServer = { server ->
                        viewModel.onAddress(server.address)
                        viewModel.connect()
                    },
                    onOpenPage = {
                        opened++
                        page = it
                    },
                )
            val flags by graph.flags.flags.collectAsStateWithLifecycle()
            CompositionLocalProvider(LocalFlags provides flags) {
                if (LocalConfiguration.current.isTelevision()) {
                    JellyfinTvTheme { TvJellyfinRoot(state, actions) }
                } else {
                    JellyfinTheme {
                        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                            if (openPage == null) {
                                // The home leaves the composition while a page is open; this keeps its saved
                                // state (the list's scroll position) for when it comes back.
                                home.SaveableStateProvider(HOME_KEY) { JellyfinRoot(state, actions) }
                            } else {
                                val close = { page = null }
                                ConsolePageScope(pageStores.storeFor(opened)) {
                                    (state as? UiState.SignedIn)?.let { gated ->
                                        ConsolePageRoute(openPage, graph, gated, close, actions.onOpenPage)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private companion object {
        const val HOME_KEY = "home"

        /** Android 17, where reaching the local network became a permission of its own. */
        const val LOCAL_NETWORK_SDK = 37
        const val ACCESS_LOCAL_NETWORK = "android.permission.ACCESS_LOCAL_NETWORK"
    }
}

/**
 * A page may have changed a gate (Quick Connect turned off), and so may the server's admin while the
 * app was away: [refresh] reads them again whenever the home comes back, from a page or to the front.
 */
@Composable
private fun RefreshGatesWhenHomeReturns(
    openPage: ConsolePage?,
    refresh: () -> Unit,
) {
    var pageWasOpen by remember { mutableStateOf(false) }
    LaunchedEffect(openPage) {
        if (openPage == null && pageWasOpen) refresh()
        pageWasOpen = openPage != null
    }
    LifecycleResumeEffect(Unit) {
        refresh()
        onPauseOrDispose {}
    }
}
