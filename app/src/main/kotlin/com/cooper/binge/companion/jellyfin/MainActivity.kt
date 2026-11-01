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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.cooper.binge.companion.jellyfin.theme.JellyfinTheme
import com.cooper.binge.companion.jellyfin.theme.JellyfinTvTheme
import com.cooper.binge.companion.jellyfin.ui.Actions
import com.cooper.binge.companion.jellyfin.ui.JellyfinRoot
import com.cooper.binge.companion.jellyfin.ui.JellyfinViewModel
import com.cooper.binge.companion.jellyfin.ui.UiState
import com.cooper.binge.companion.jellyfin.ui.console.ActivityActions
import com.cooper.binge.companion.jellyfin.ui.console.ActivityScreen
import com.cooper.binge.companion.jellyfin.ui.console.ActivityViewModel
import com.cooper.binge.companion.jellyfin.ui.console.ApproveActions
import com.cooper.binge.companion.jellyfin.ui.console.ApproveCodeScreen
import com.cooper.binge.companion.jellyfin.ui.console.ApproveCodeViewModel
import com.cooper.binge.companion.jellyfin.ui.console.ConsolePage
import com.cooper.binge.companion.jellyfin.ui.console.ConsolePageScope
import com.cooper.binge.companion.jellyfin.ui.console.ConsolePageStores
import com.cooper.binge.companion.jellyfin.ui.console.DevicesActions
import com.cooper.binge.companion.jellyfin.ui.console.DevicesScreen
import com.cooper.binge.companion.jellyfin.ui.console.DevicesViewModel
import com.cooper.binge.companion.jellyfin.ui.console.LanguagesActions
import com.cooper.binge.companion.jellyfin.ui.console.LanguagesScreen
import com.cooper.binge.companion.jellyfin.ui.console.LanguagesViewModel
import com.cooper.binge.companion.jellyfin.ui.console.LibrariesActions
import com.cooper.binge.companion.jellyfin.ui.console.LibrariesScreen
import com.cooper.binge.companion.jellyfin.ui.console.LibrariesViewModel
import com.cooper.binge.companion.jellyfin.ui.console.NowPlayingActions
import com.cooper.binge.companion.jellyfin.ui.console.NowPlayingScreen
import com.cooper.binge.companion.jellyfin.ui.console.NowPlayingViewModel
import com.cooper.binge.companion.jellyfin.ui.console.PasswordActions
import com.cooper.binge.companion.jellyfin.ui.console.PasswordScreen
import com.cooper.binge.companion.jellyfin.ui.console.PasswordViewModel
import com.cooper.binge.companion.jellyfin.ui.console.ServerActions
import com.cooper.binge.companion.jellyfin.ui.console.ServerScreen
import com.cooper.binge.companion.jellyfin.ui.console.ServerViewModel
import com.cooper.binge.companion.jellyfin.ui.console.TasksActions
import com.cooper.binge.companion.jellyfin.ui.console.TasksScreen
import com.cooper.binge.companion.jellyfin.ui.console.TasksViewModel
import com.cooper.binge.companion.jellyfin.ui.console.UsersActions
import com.cooper.binge.companion.jellyfin.ui.console.UsersScreen
import com.cooper.binge.companion.jellyfin.ui.console.UsersViewModel
import com.cooper.binge.companion.jellyfin.ui.tv.TvJellyfinRoot
import com.cooper.binge.companion.jellyfin.ui.tv.isTelevision

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
            val openPage = page.takeIf { signedIn }
            LaunchedEffect(openPage) { if (openPage == null) pageStores.close() }
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
                                when (openPage) {
                                    ConsolePage.ApproveCode -> ApproveCodeRoute(graph, close)
                                    ConsolePage.Languages -> LanguagesRoute(graph, close)
                                    ConsolePage.Password -> PasswordRoute(graph, close)
                                    ConsolePage.NowPlaying ->
                                        NowPlayingRoute(
                                            graph,
                                            (state as? UiState.SignedIn)?.isAdministrator == true,
                                            close,
                                        )
                                    ConsolePage.Libraries -> LibrariesRoute(graph, close)
                                    ConsolePage.Tasks -> TasksRoute(graph, close)
                                    ConsolePage.Activity -> ActivityRoute(graph, close)
                                    ConsolePage.Devices -> DevicesRoute(graph, close)
                                    ConsolePage.Users -> UsersRoute(graph, close)
                                    ConsolePage.Server -> ServerRoute(graph, close)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    /** The approval page with its own view model, kept while the page is open and new for each opening. */
    @Composable
    private fun ApproveCodeRoute(
        graph: AppGraph,
        onBack: () -> Unit,
    ) {
        val approve: ApproveCodeViewModel =
            viewModel(factory = viewModelFactory { initializer { ApproveCodeViewModel(graph.connection) } })
        val state by approve.state.collectAsStateWithLifecycle()
        ApproveCodeScreen(state, ApproveActions(onCode = approve::onCode, onApprove = approve::approve, onBack = onBack))
    }

    /** The languages page with its own view model, which reads the user's configuration when it opens. */
    @Composable
    private fun LanguagesRoute(
        graph: AppGraph,
        onBack: () -> Unit,
    ) {
        val languages: LanguagesViewModel =
            viewModel(factory = viewModelFactory { initializer { LanguagesViewModel(graph.connection) } })
        val state by languages.state.collectAsStateWithLifecycle()
        LanguagesScreen(
            state,
            LanguagesActions(
                onAudio = languages::setAudio,
                onSubtitles = languages::setSubtitles,
                onSubtitleMode = languages::setSubtitleMode,
                onRetry = languages::load,
                onBack = onBack,
            ),
        )
    }

    /** The password page with a view model per opening, so the form is empty each time it opens. */
    @Composable
    private fun PasswordRoute(
        graph: AppGraph,
        onBack: () -> Unit,
    ) {
        val password: PasswordViewModel =
            viewModel(factory = viewModelFactory { initializer { PasswordViewModel(graph.connection) } })
        val state by password.state.collectAsStateWithLifecycle()
        PasswordScreen(
            state,
            PasswordActions(
                onCurrent = password::onCurrent,
                onNew = password::onNew,
                onConfirm = password::onConfirm,
                onChange = password::change,
                onBack = onBack,
            ),
        )
    }

    /** The libraries page, whose event socket closes with the page. */
    @Composable
    private fun LibrariesRoute(
        graph: AppGraph,
        onBack: () -> Unit,
    ) {
        val libraries: LibrariesViewModel = viewModel(factory = viewModelFactory { initializer { LibrariesViewModel(graph.connection) } })
        val state by libraries.state.collectAsStateWithLifecycle()
        LibrariesScreen(
            state,
            LibrariesActions(
                onScan = libraries::scan,
                onScanEverything = libraries::scanEverything,
                onStopEverything = libraries::stopEverything,
                onRetry = libraries::load,
                onBack = onBack,
            ),
        )
    }

    /** The scheduled tasks page, whose event socket closes with the page. */
    @Composable
    private fun TasksRoute(
        graph: AppGraph,
        onBack: () -> Unit,
    ) {
        val tasks: TasksViewModel = viewModel(factory = viewModelFactory { initializer { TasksViewModel(graph.connection) } })
        val state by tasks.state.collectAsStateWithLifecycle()
        TasksScreen(state, TasksActions(onRun = tasks::run, onStop = tasks::stop, onRetry = tasks::load, onBack = onBack))
    }

    /** The activity page, read a page at a time. */
    @Composable
    private fun ActivityRoute(
        graph: AppGraph,
        onBack: () -> Unit,
    ) {
        val activity: ActivityViewModel = viewModel(factory = viewModelFactory { initializer { ActivityViewModel(graph.connection) } })
        val state by activity.state.collectAsStateWithLifecycle()
        ActivityScreen(
            state,
            ActivityActions(onRefresh = activity::refresh, onLoadMore = activity::loadMore, onRetry = activity::load, onBack = onBack),
        )
    }

    /** The devices page. */
    @Composable
    private fun DevicesRoute(
        graph: AppGraph,
        onBack: () -> Unit,
    ) {
        val devices: DevicesViewModel = viewModel(factory = viewModelFactory { initializer { DevicesViewModel(graph.connection) } })
        val state by devices.state.collectAsStateWithLifecycle()
        DevicesScreen(
            state,
            DevicesActions(
                onAskRename = devices::askRename,
                onRename = devices::rename,
                onAskRemove = devices::askRemove,
                onRemove = devices::remove,
                onRetry = devices::load,
                onBack = onBack,
            ),
        )
    }

    /** The users page, with each user's own page inside it. */
    @Composable
    private fun UsersRoute(
        graph: AppGraph,
        onBack: () -> Unit,
    ) {
        val users: UsersViewModel = viewModel(factory = viewModelFactory { initializer { UsersViewModel(graph.connection) } })
        val state by users.state.collectAsStateWithLifecycle()
        UsersScreen(
            state,
            UsersActions(
                onOpen = users::open,
                onShow = users::show,
                onAdministrator = users::setAdministrator,
                onCanSignIn = users::setCanSignIn,
                onAllLibraries = users::setAllLibraries,
                onLibrary = users::setLibrary,
                onCreate = users::create,
                onSetPassword = users::setPassword,
                onDelete = users::delete,
                onRetry = users::load,
                onBack = onBack,
            ),
        )
    }

    /** The server page. */
    @Composable
    private fun ServerRoute(
        graph: AppGraph,
        onBack: () -> Unit,
    ) {
        val server: ServerViewModel = viewModel(factory = viewModelFactory { initializer { ServerViewModel(graph.connection) } })
        val state by server.state.collectAsStateWithLifecycle()
        ServerScreen(
            state,
            ServerActions(onConfirm = server::confirm, onRestart = server::restart, onRetry = server::load, onBack = onBack),
        )
    }

    /** The now-playing page with its own view model, whose event socket closes with the page. */
    @Composable
    private fun NowPlayingRoute(
        graph: AppGraph,
        administrator: Boolean,
        onBack: () -> Unit,
    ) {
        val nowPlaying: NowPlayingViewModel =
            viewModel(factory = viewModelFactory { initializer { NowPlayingViewModel(graph.connection) } })
        val state by nowPlaying.state.collectAsStateWithLifecycle()
        NowPlayingScreen(
            state,
            administrator,
            NowPlayingActions(
                onStop = nowPlaying::askStop,
                onConfirmStop = nowPlaying::stop,
                onCancelStop = nowPlaying::cancelStop,
                onRetry = nowPlaying::load,
                onBack = onBack,
            ),
        )
    }

    private companion object {
        const val HOME_KEY = "home"

        /** Android 17, where reaching the local network became a permission of its own. */
        const val LOCAL_NETWORK_SDK = 37
        const val ACCESS_LOCAL_NETWORK = "android.permission.ACCESS_LOCAL_NETWORK"
    }
}
