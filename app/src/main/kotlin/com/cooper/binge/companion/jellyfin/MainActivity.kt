package com.cooper.binge.companion.jellyfin

import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.binge.companion.kit.isTelevision
import com.binge.companion.sdk.attempt
import com.cooper.binge.companion.jellyfin.flags.LocalFlags
import com.cooper.binge.companion.jellyfin.server.AndroidLocalNetworkPermission
import com.cooper.binge.companion.jellyfin.telemetry.ReportScreen
import com.cooper.binge.companion.jellyfin.theme.JellyfinTheme
import com.cooper.binge.companion.jellyfin.theme.JellyfinTvTheme
import com.cooper.binge.companion.jellyfin.ui.Actions
import com.cooper.binge.companion.jellyfin.ui.JellyfinRoot
import com.cooper.binge.companion.jellyfin.ui.JellyfinViewModel
import com.cooper.binge.companion.jellyfin.ui.ReviewPromptOnReturnToRoot
import com.cooper.binge.companion.jellyfin.ui.UiState
import com.cooper.binge.companion.jellyfin.ui.chooseServer
import com.cooper.binge.companion.jellyfin.ui.consent.ConsentGate
import com.cooper.binge.companion.jellyfin.ui.console.ConsoleBackStack
import com.cooper.binge.companion.jellyfin.ui.console.ConsoleItemRoute
import com.cooper.binge.companion.jellyfin.ui.console.ConsoleNavHost
import com.cooper.binge.companion.jellyfin.ui.console.ConsolePage
import com.cooper.binge.companion.jellyfin.ui.console.ConsolePageRoute
import com.cooper.binge.companion.jellyfin.ui.console.LocalUserPictures
import com.cooper.binge.companion.jellyfin.ui.localNetworkAnswered
import com.cooper.binge.companion.jellyfin.ui.rememberAppSettingsBinding
import com.cooper.binge.companion.jellyfin.ui.tryDemo
import com.cooper.binge.companion.jellyfin.ui.tv.TvConsentGate
import com.cooper.binge.companion.jellyfin.ui.tv.TvJellyfinRoot
import com.cooper.binge.companion.jellyfin.ui.tv.rememberTvPlayback
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

    /** This app's own Settings page, where the permission can be switched on. A device with no such page does nothing. */
    private fun openAppSettings() {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null))
        attempt { startActivity(intent) }
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
                        localNetwork = AndroidLocalNetworkPermission(applicationContext),
                        analytics = graph.analytics,
                        onSessionEnded = graph::clearAccountCaches,
                    )
                }
            }
        // Fresh Remote Config values for the flags; until they land, the last ones activated are used.
        lifecycleScope.launch { graph.flags.refresh() }
        setContent {
            val viewModel: JellyfinViewModel = viewModel(factory = factory)
            val state by viewModel.state.collectAsStateWithLifecycle()
            // The console pages open over the home, the last on top. A page that opens another stays under it, so
            // Back returns to the opener, and to the home from the first. Leaving SignedIn closes them all.
            var stack by rememberSaveable(stateSaver = ConsoleBackStack.Saver) { mutableStateOf(ConsoleBackStack()) }
            val signedIn = state is UiState.SignedIn
            // The permission is asked for once; after that the way to it is the app's settings, as the prompt may not come back.
            var askedLocalNetwork by rememberSaveable { mutableStateOf(false) }
            LaunchedEffect(signedIn) { if (!signedIn) stack = stack.clear() }
            // A page the server or the user lost (Quick Connect turned off, admin rights taken away) closes,
            // once the refresh has read what they now allow; Back skips it.
            (state as? UiState.SignedIn)?.let { gated ->
                LaunchedEffect(gated.profile, gated.account) {
                    if (gated.account.isRead) stack = stack.keeping { it.available(gated.profile, gated.account) }
                }
            }
            val openPage = stack.top.takeIf { signedIn }
            ReportScreen(state, openPage?.page, graph.analytics)
            RefreshGatesWhenHomeReturns(openPage?.opening, stack.openings) { viewModel.refresh(gatesOnly = true) }
            val actions =
                Actions(
                    onAddress = viewModel::onAddress,
                    onConnect = viewModel::connect,
                    onTryDemo = viewModel::tryDemo,
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
                    onChooseServer = viewModel::chooseServer,
                    onOpenPage = { stack = stack.open(it) },
                    onAllowLocalNetwork = {
                        if (askedLocalNetwork) {
                            openAppSettings()
                        } else {
                            askedLocalNetwork = true
                            withLocalNetwork(viewModel::localNetworkAnswered)
                        }
                    },
                    localNetworkAsked = askedLocalNetwork,
                )
            // Beside a page the home stays on screen, so what it opens replaces the page that is open.
            // Now playing is what shows beside it while none is open.
            val homeActions =
                actions.copy(
                    onOpenPage = { stack = stack.openFromHome(it) },
                    selectedPage = stack.entries.firstOrNull()?.page ?: ConsolePage.NowPlaying,
                )
            val flags by graph.flags.flags.collectAsStateWithLifecycle()
            // What the user has said about usage data and crash reports; the stored answer is null until it is read.
            val telemetry = rememberAppSettingsBinding(graph.telemetryPrefs)
            CompositionLocalProvider(LocalFlags provides flags, LocalUserPictures provides graph.userPictures) {
                if (LocalConfiguration.current.isTelevision()) {
                    JellyfinTvTheme {
                        TvConsentGate(telemetry.consent, telemetry.answerConsent) {
                            TvJellyfinRoot(state, telemetry.applyTo(actions), playback = rememberTvPlayback(graph.connection, state))
                        }
                    }
                } else {
                    JellyfinTheme {
                        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                            ConsentGate(telemetry.consent, telemetry.answerConsent) {
                                ConsoleHost(state, stack, { stack = it }, graph, telemetry.applyTo(actions), telemetry.applyTo(homeActions))
                            }
                        }
                    }
                }
            }
        }
    }

    private companion object {
        /** Android 17, where reaching the local network became a permission of its own. */
        const val LOCAL_NETWORK_SDK = 37
        const val ACCESS_LOCAL_NETWORK = "android.permission.ACCESS_LOCAL_NETWORK"
    }
}

/** The console: the home, and the pages and items open over it, on [stack], whose changes [onStack] takes. */
@Composable
private fun ConsoleHost(
    state: UiState,
    stack: ConsoleBackStack,
    onStack: (ConsoleBackStack) -> Unit,
    graph: AppGraph,
    actions: Actions,
    homeActions: Actions,
) {
    val signedIn = state is UiState.SignedIn
    // A tap acts on the stack as it is then, not as it was when the screen under it was composed.
    val latest by rememberUpdatedState(stack)
    ReviewPromptOnReturnToRoot(
        signedIn = signedIn,
        atRoot = stack.entries.isEmpty(),
        onReturnedToRoot = graph.reviewPrompt::maybeAsk,
    )
    ConsoleNavHost(
        stack = if (signedIn) stack else ConsoleBackStack(),
        signedIn = signedIn,
        onBack = { onStack(latest.back()) },
        home = { JellyfinRoot(state, homeActions) },
        defaultPage = {
            (state as? UiState.SignedIn)?.let { gated ->
                // A stream tapped here has no page to open over, so the page opens, and the stream over it.
                ConsolePageRoute(
                    ConsolePage.NowPlaying,
                    graph,
                    gated,
                    {},
                    actions.onOpenPage,
                    actions,
                    { onStack(latest.openFromHome(ConsolePage.NowPlaying).openItem(it)) },
                )
            }
        },
        page = { entry ->
            (state as? UiState.SignedIn)?.let { gated ->
                ConsolePageRoute(
                    entry.page,
                    graph,
                    gated,
                    { onStack(latest.back()) },
                    actions.onOpenPage,
                    actions,
                    { onStack(latest.openItem(it)) },
                    { page, item -> onStack(latest.openPageItem(page, item)) },
                )
            }
        },
        item = { entry ->
            (state as? UiState.SignedIn)?.let {
                ConsoleItemRoute(entry, graph, it.profile.version, it.profile, it.account, { item -> onStack(latest.openSubItem(item)) }) {
                    onStack(latest.back())
                }
            }
        },
    )
}

/**
 * A page may have changed a gate (Quick Connect turned off), and so may the server's admin while the
 * app was away: [refresh] reads them again whenever a page closes ([top] was open and is no longer one of
 * the [openings]), so also when the home beside it opens another page, and whenever the app comes to the front.
 */
@Composable
private fun RefreshGatesWhenHomeReturns(
    top: Int?,
    openings: Set<Int>,
    refresh: () -> Unit,
) {
    var previousTop by remember { mutableStateOf<Int?>(null) }
    LaunchedEffect(top) {
        if (previousTop != null && previousTop !in openings) refresh()
        previousTop = top
    }
    LifecycleResumeEffect(Unit) {
        refresh()
        onPauseOrDispose {}
    }
}
