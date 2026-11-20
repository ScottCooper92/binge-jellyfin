package com.cooper.homelab.ephyra

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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.rememberNavBackStack
import com.binge.companion.kit.isTelevision
import com.binge.companion.sdk.attempt
import com.cooper.homelab.core.server.network.AndroidLocalNetworkPermission
import com.cooper.homelab.core.uiandroid.consent.ConsentGate
import com.cooper.homelab.core.uiandroid.flags.LocalFlags
import com.cooper.homelab.core.uiandroid.review.ReviewPromptOnReturnToRoot
import com.cooper.homelab.core.uiandroid.shell.RestartOnGateChange
import com.cooper.homelab.core.uiandroid.shell.RestartOnSessionEnd
import com.cooper.homelab.ephyra.browse.ItemKind
import com.cooper.homelab.ephyra.flags.LocalClientEnabled
import com.cooper.homelab.ephyra.playback.PlayItem
import com.cooper.homelab.ephyra.telemetry.ReportScreen
import com.cooper.homelab.ephyra.theme.JellyfinTheme
import com.cooper.homelab.ephyra.theme.JellyfinTvTheme
import com.cooper.homelab.ephyra.ui.Actions
import com.cooper.homelab.ephyra.ui.JellyfinRoot
import com.cooper.homelab.ephyra.ui.JellyfinViewModel
import com.cooper.homelab.ephyra.ui.UiState
import com.cooper.homelab.ephyra.ui.chooseServer
import com.cooper.homelab.ephyra.ui.client.CollectionPage
import com.cooper.homelab.ephyra.ui.client.EpisodePage
import com.cooper.homelab.ephyra.ui.client.HomeTab
import com.cooper.homelab.ephyra.ui.client.LibrariesTab
import com.cooper.homelab.ephyra.ui.client.LibraryGridTab
import com.cooper.homelab.ephyra.ui.client.MoviePage
import com.cooper.homelab.ephyra.ui.client.PersonPage
import com.cooper.homelab.ephyra.ui.client.PlayStartHost
import com.cooper.homelab.ephyra.ui.client.PlayerPage
import com.cooper.homelab.ephyra.ui.client.SearchTab
import com.cooper.homelab.ephyra.ui.client.SeasonPage
import com.cooper.homelab.ephyra.ui.client.SeriesPage
import com.cooper.homelab.ephyra.ui.client.TitlePlaceholder
import com.cooper.homelab.ephyra.ui.confirmSignOut
import com.cooper.homelab.ephyra.ui.confirmTakeBack
import com.cooper.homelab.ephyra.ui.connected
import com.cooper.homelab.ephyra.ui.console.ConsoleItemRoute
import com.cooper.homelab.ephyra.ui.console.ConsolePage
import com.cooper.homelab.ephyra.ui.console.ConsolePageRoute
import com.cooper.homelab.ephyra.ui.console.LocalUserPictures
import com.cooper.homelab.ephyra.ui.localNetworkAnswered
import com.cooper.homelab.ephyra.ui.nav.CollectionRoute
import com.cooper.homelab.ephyra.ui.nav.EphyraNavHost
import com.cooper.homelab.ephyra.ui.nav.EphyraTab
import com.cooper.homelab.ephyra.ui.nav.EpisodeRoute
import com.cooper.homelab.ephyra.ui.nav.HomeRoute
import com.cooper.homelab.ephyra.ui.nav.LibraryRoute
import com.cooper.homelab.ephyra.ui.nav.MovieRoute
import com.cooper.homelab.ephyra.ui.nav.PersonRoute
import com.cooper.homelab.ephyra.ui.nav.SeasonRoute
import com.cooper.homelab.ephyra.ui.nav.SeriesRoute
import com.cooper.homelab.ephyra.ui.nav.TitleRoute
import com.cooper.homelab.ephyra.ui.nav.closeTop
import com.cooper.homelab.ephyra.ui.nav.entries
import com.cooper.homelab.ephyra.ui.nav.keeping
import com.cooper.homelab.ephyra.ui.nav.openFromHome
import com.cooper.homelab.ephyra.ui.nav.openItem
import com.cooper.homelab.ephyra.ui.nav.openLibrary
import com.cooper.homelab.ephyra.ui.nav.openPage
import com.cooper.homelab.ephyra.ui.nav.openPageItem
import com.cooper.homelab.ephyra.ui.nav.openPerson
import com.cooper.homelab.ephyra.ui.nav.openPlayer
import com.cooper.homelab.ephyra.ui.nav.openSearchHit
import com.cooper.homelab.ephyra.ui.nav.openSubItem
import com.cooper.homelab.ephyra.ui.nav.openTitle
import com.cooper.homelab.ephyra.ui.nav.openings
import com.cooper.homelab.ephyra.ui.nav.rememberEphyraTabs
import com.cooper.homelab.ephyra.ui.nav.top
import com.cooper.homelab.ephyra.ui.pickPublicUser
import com.cooper.homelab.ephyra.ui.rememberAppSettingsBinding
import com.cooper.homelab.ephyra.ui.rememberNotificationBinding
import com.cooper.homelab.ephyra.ui.tv.TvConsentGate
import com.cooper.homelab.ephyra.ui.tv.TvJellyfinRoot
import com.cooper.homelab.ephyra.ui.tv.rememberTvPlayback
import com.cooper.homelab.player.api.PlayTitle
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

    /** The view model's factory: the graph's objects, and the permission check the connection failures read. */
    private fun jellyfinViewModelFactory(graph: AppGraph) =
        viewModelFactory {
            initializer {
                JellyfinViewModel(
                    graph.sessions,
                    graph.connection,
                    graph.index,
                    dispatcher = graph.ioDispatcher,
                    cipher = graph.linkCipher,
                    discover = graph.discovery::find,
                    localNetwork = AndroidLocalNetworkPermission(applicationContext),
                    analytics = graph.analytics,
                    onSessionEnded = graph::clearAccountCaches,
                    cleartext = graph.cleartextConsent,
                    linkStash = graph.linkStash,
                    savedState = createSavedStateHandle(),
                )
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Swaps the launch theme for Theme.Jellyfin. No keep-on condition: the splash never holds the first frame.
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val graph = (application as JellyfinApp).graph
        val factory = jellyfinViewModelFactory(graph)
        // Fresh Remote Config values for the flags; until they land, the last ones activated are used.
        lifecycleScope.launch { graph.flags.refresh() }
        setContent {
            val viewModel: JellyfinViewModel = viewModel(factory = factory)
            val state by viewModel.state.collectAsStateWithLifecycle()
            // The app's screens: the sign-in steps, then the home with the console pages open over it, the last on top. A
            // page that opens another stays under it, so Back returns to the opener, and to the home from the first.
            // Leaving SignedIn takes the stack back to the sign-in steps.
            val backStack = rememberNavBackStack(HomeRoute)
            // The permission is asked for once; after that the way to it is the app's settings, as the prompt may not come back.
            var askedLocalNetwork by rememberSaveable { mutableStateOf(false) }
            // A page the server or the user lost (Quick Connect turned off, admin rights taken away) closes,
            // once the refresh has read what they now allow; Back skips it.
            (state as? UiState.SignedIn)?.let { gated ->
                LaunchedEffect(gated.profile, gated.account) {
                    if (gated.account.isRead) backStack.keeping { it.available(gated.profile, gated.account) }
                }
            }
            val openPage = backStack.top.takeIf { state is UiState.SignedIn }
            ReportScreen(state, openPage?.page, graph.analytics)
            RefreshGatesWhenHomeReturns(openPage?.opening, backStack.openings()) { viewModel.refresh(gatesOnly = true) }
            val actions =
                viewModel.signInActions().copy(
                    onAddress = viewModel::onAddress,
                    onConnect = viewModel::connect,
                    onAllowCleartext = viewModel::allowCleartext,
                    onTryDemo = viewModel::tryDemo,
                    onChangeServer = viewModel::changeServer,
                    onRecheckServer = { viewModel.changeServer(recheck = true) },
                    startup = viewModel.startup.actions(),
                    onRefresh = viewModel::refresh,
                    onAskSignOut = { viewModel.confirmSignOut(true) },
                    onCancelSignOut = { viewModel.confirmSignOut(false) },
                    onAskTakeBack = { viewModel.confirmTakeBack(true) },
                    onCancelTakeBack = { viewModel.confirmTakeBack(false) },
                    onSignOut = viewModel::signOut,
                    onDiscover = { withLocalNetwork(viewModel::discoverServers) },
                    onChooseServer = viewModel::chooseServer,
                    onOpenPage = { backStack.openPage(it) },
                    onOpenRow = { page, row -> backStack.openPage(page, row) },
                    onOpenResult = { page, row ->
                        backStack.closeTop()
                        backStack.openFromHome(page, row)
                    },
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
                    onOpenPage = { backStack.openFromHome(it) },
                    onOpenRow = { page, row -> backStack.openFromHome(page, row) },
                    selectedPage = backStack.entries().firstOrNull()?.page ?: ConsolePage.NowPlaying,
                )
            val flags by graph.flags.flags.collectAsStateWithLifecycle()
            // What the user has said about usage data and crash reports; the stored answer is null until it is read.
            val telemetry = rememberAppSettingsBinding(graph.telemetryPrefs, graph.clientGate)
            // The telemetry choices, and an administrator's notifications, which no one else is shown.
            val appActions = rememberNotificationBinding(graph, (state as? UiState.SignedIn)?.isAdministrator == true, telemetry::applyTo)
            val client by graph.clientGate.resolved.collectAsStateWithLifecycle()
            CompositionLocalProvider(
                LocalFlags provides flags,
                LocalClientEnabled provides (client ?: graph.clientGate.fallback),
                LocalUserPictures provides graph.userPictures,
            ) {
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
                                ConsoleHost(state, backStack, graph, appActions(actions), appActions(homeActions), client)
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

/** A library's grid, for the user the session belongs to: a title opens its page above it, and Back closes it. */
@Composable
private fun LibraryRouteScreen(
    state: UiState,
    route: LibraryRoute,
    graph: AppGraph,
    backStack: NavBackStack<NavKey>,
) {
    (state as? UiState.SignedIn)?.let { signed ->
        LibraryGridTab(
            graph,
            signed.profile,
            route.libraryId,
            onBack = { backStack.closeTop() },
            onOpen = { kind, id -> backStack.openTitle(kind, id) },
        )
    }
}

/** The Search tab, for the user the session belongs to: a result opens its page above the bar. */
@Composable
private fun SearchTabRoute(
    state: UiState,
    graph: AppGraph,
    backStack: NavBackStack<NavKey>,
) {
    (state as? UiState.SignedIn)?.let { signed ->
        SearchTab(graph, signed.profile, onOpen = { kind, id -> backStack.openSearchHit(kind, id) })
    }
}

/** The app's screens: the sign-in steps, then the home, and the pages and items open over it, on [backStack]. */
@Composable
internal fun ConsoleHost(
    state: UiState,
    backStack: NavBackStack<NavKey>,
    graph: AppGraph,
    actions: Actions,
    homeActions: Actions,
    clientResolved: Boolean?,
) {
    val signedIn = state is UiState.SignedIn
    // The client's shell shows while the gate is on. Until the gate is read, the flag alone decides, right for anyone who has not opted out.
    val tabs = rememberEphyraTabs()
    val clientOn = clientResolved ?: graph.clientGate.fallback
    // Turning the client off or on starts over from the start tab, with no screen left over from the other layout.
    RestartOnGateChange(clientResolved, tabs, backStack, offTab = EphyraTab.Server)
    // A session that ends takes what the Server tab parked with it, so the next account does not find it.
    RestartOnSessionEnd(state.connected(), tabs, backStack)
    // At the root when nothing is open over it: no page and no library.
    ReviewPromptOnReturnToRoot(connected = state.connected(), atRoot = backStack.size <= 1, reviewPrompt = graph.reviewPrompt)
    EphyraNavHost(
        backStack = backStack,
        signedIn = signedIn,
        tabs = tabs.takeIf { clientOn },
        resolved = state !is UiState.Loading,
        onBack = { backStack.closeTop() },
        signedOut = { JellyfinRoot(state, homeActions) },
        home = { JellyfinRoot(state, homeActions) },
        // Home's rows come from the user's own server, so the tab needs a session. A card opens its title page and Continue watching
        // resumes it through PlayStartHost.
        homeTab =
            (state as? UiState.SignedIn)?.let { signed ->
                {
                    PlayStartHost(graph, signed.session.baseUrl, onPlayer = { id, title -> backStack.openPlayer(id, title) }) { play ->
                        HomeTab(
                            graph,
                            signed.profile,
                            onOpen = { kind, id -> backStack.openTitle(kind, id) },
                            onResume = { id, title -> play(PlayItem(id, PlayTitle(title))) },
                        )
                    }
                }
            },
        librariesTab = {
            (state as? UiState.SignedIn)?.let { LibrariesTab(graph, it.profile, onOpen = { id -> backStack.openLibrary(id) }) }
        },
        searchTab = { SearchTabRoute(state, graph, backStack) },
        library = { route -> LibraryRouteScreen(state, route, graph, backStack) },
        title = { route -> TitlePageRoute(route, state as? UiState.SignedIn, graph, backStack) },
        player = { route -> PlayerPage(graph, route, onBack = { backStack.closeTop() }) },
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
                    {
                        backStack.openFromHome(ConsolePage.NowPlaying)
                        backStack.openItem(it)
                    },
                )
            }
        },
        page = { entry ->
            (state as? UiState.SignedIn)?.let { gated ->
                ConsolePageRoute(
                    entry.page,
                    graph,
                    gated,
                    { backStack.closeTop() },
                    actions.onOpenPage,
                    actions,
                    { backStack.openItem(it) },
                    { page, item -> backStack.openPageItem(page, item) },
                    row = entry.row,
                )
            }
        },
        item = { entry ->
            (state as? UiState.SignedIn)?.let {
                ConsoleItemRoute(
                    entry,
                    graph,
                    it.profile.version,
                    it.profile,
                    it.account,
                    { item -> backStack.openSubItem(item) },
                ) {
                    backStack.closeTop()
                }
            }
        },
    )
}

/**
 * The page for a title route. A film's, a collection's, a series', a season's, an episode's or a person's page needs the user's session and the
 * server's version; every other kind keeps the placeholder until its screen lands.
 */
@Composable
private fun TitlePageRoute(
    route: TitleRoute,
    signed: UiState.SignedIn?,
    graph: AppGraph,
    backStack: NavBackStack<NavKey>,
) {
    when {
        route is MovieRoute && signed != null ->
            MoviePage(
                graph,
                signed.profile,
                route.itemId,
                signed.session.baseUrl,
                onBack = { backStack.closeTop() },
                onPlay = { sessionId, title -> backStack.openPlayer(sessionId, title) },
                onOpenTitle = { kind, id -> backStack.openTitle(kind, id) },
                onOpenPerson = { id -> backStack.openPerson(id) },
            )
        route is CollectionRoute && signed != null ->
            CollectionPage(
                graph,
                signed.profile,
                route.itemId,
                onBack = { backStack.closeTop() },
                onOpenTitle = { kind, id -> backStack.openTitle(kind, id) },
            )
        route is PersonRoute && signed != null ->
            PersonPage(
                graph,
                signed.profile,
                route.itemId,
                onBack = { backStack.closeTop() },
                onOpenTitle = { kind, id -> backStack.openTitle(kind, id) },
            )
        route is SeriesRoute && signed != null ->
            SeriesPage(
                graph,
                signed.profile,
                route.itemId,
                signed.session.baseUrl,
                onBack = { backStack.closeTop() },
                onPlay = { sessionId, title -> backStack.openPlayer(sessionId, title) },
                onOpenTitle = { kind, id -> backStack.openTitle(kind, id) },
                onOpenSeason = { id -> backStack.openTitle(ItemKind.Season, id) },
                onOpenEpisode = { id -> backStack.openTitle(ItemKind.Episode, id) },
                onOpenPerson = { id -> backStack.openPerson(id) },
            )
        route is EpisodeRoute && signed != null ->
            EpisodePage(
                graph,
                signed.profile,
                route.itemId,
                signed.session.baseUrl,
                onBack = { backStack.closeTop() },
                onPlay = { sessionId, title -> backStack.openPlayer(sessionId, title) },
                onOpenEpisode = { id -> backStack.openTitle(ItemKind.Episode, id) },
                onOpenSeries = { id -> backStack.openTitle(ItemKind.Series, id) },
                onOpenPerson = { id -> backStack.openPerson(id) },
            )
        route is SeasonRoute && signed != null ->
            SeasonPage(
                graph,
                signed.profile,
                route.itemId,
                signed.session.baseUrl,
                onBack = { backStack.closeTop() },
                onPlay = { sessionId, title -> backStack.openPlayer(sessionId, title) },
                onOpenSeries = { id -> backStack.openTitle(ItemKind.Series, id) },
                onOpenEpisode = { id -> backStack.openTitle(ItemKind.Episode, id) },
            )
        else -> TitlePlaceholder(onBack = { backStack.closeTop() })
    }
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

/** What the sign-in step asks of the view model: the account, its password, the picks and resets around it, and Quick Connect. */
private fun JellyfinViewModel.signInActions() =
    Actions(
        onUsername = ::onUsername,
        onPassword = ::onPassword,
        onPickUser = ::pickPublicUser,
        forgotPassword = forgotPassword,
        onSignIn = ::signInWithPassword,
        onQuickConnect = ::startQuickConnect,
        onCancelQuickConnect = ::cancelQuickConnect,
        onSelectMethod = actions::selectMethod,
        onCancelCode = actions::cancelCode,
    )
