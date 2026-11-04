package com.cooper.binge.companion.jellyfin.ui.console

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.binge.companion.sdk.attempt
import com.cooper.binge.companion.jellyfin.AppGraph
import com.cooper.binge.companion.jellyfin.server.JellyfinAccount
import com.cooper.binge.companion.jellyfin.server.JellyfinServerProfile
import com.cooper.binge.companion.jellyfin.ui.AccountScreen
import com.cooper.binge.companion.jellyfin.ui.Actions
import com.cooper.binge.companion.jellyfin.ui.UiState
import kotlinx.coroutines.launch

/** The open console page, with its own view model from the page's store. */
@Composable
internal fun ConsolePageRoute(
    page: ConsolePage,
    graph: AppGraph,
    signedIn: UiState.SignedIn,
    close: () -> Unit,
    open: (ConsolePage) -> Unit = {},
    actions: Actions = Actions(),
    openItem: (String) -> Unit = {},
) {
    when (page) {
        ConsolePage.ApproveCode -> ApproveCodeRoute(graph, close)
        ConsolePage.Playback -> PlaybackRoute(graph, close)
        ConsolePage.HomeLayout -> HomeLayoutRoute(graph, signedIn.profile, close)
        ConsolePage.Password -> PasswordRoute(graph, close)
        ConsolePage.ProfilePicture -> ProfilePictureRoute(graph, signedIn.profile.hasUserImageRoute, close)
        ConsolePage.Account -> AccountScreen(signedIn, actions, close)
        ConsolePage.NowPlaying ->
            NowPlayingRoute(
                graph,
                signedIn.account,
                close,
            )
        ConsolePage.Libraries -> LibrariesRoute(graph, signedIn.profile, close)
        ConsolePage.Tasks -> TasksRoute(graph, openItem, close)
        ConsolePage.Activity -> ActivityRoute(graph, close)
        ConsolePage.Logs -> LogsRoute(graph, close)
        ConsolePage.ApiKeys -> ApiKeysRoute(graph, close)
        ConsolePage.Devices -> DevicesRoute(graph, close)
        ConsolePage.Users -> UsersRoute(graph, signedIn.profile, close)
        ConsolePage.ServerSettings -> ServerSettingsRoute(graph, signedIn.profile, close)
        ConsolePage.Network -> NetworkRoute(graph, signedIn.profile, { open(ConsolePage.Server) }, close)
        ConsolePage.Advanced ->
            AdvancedRoute(
                graph,
                AdvancedGates(signedIn.profile.hasAdvancedPerformance, signedIn.profile.hasCacheAndLegacyAuth),
                { open(ConsolePage.Server) },
                close,
            )
        ConsolePage.Plugins -> PluginsRoute(graph, signedIn.profile.version, { open(ConsolePage.Server) }, close)
        ConsolePage.Trickplay -> TrickplayRoute(graph, signedIn.profile.hasKeyFrameOnlyTrickplay, close)
        ConsolePage.LibraryDisplay -> LibraryDisplayRoute(graph, signedIn.profile, close)
        ConsolePage.MetadataDefaults -> MetadataDefaultsRoute(graph, close)
        ConsolePage.Backups -> BackupsRoute(graph, signedIn.profile.version, close)
        ConsolePage.Transcoding -> TranscodingRoute(graph, signedIn.profile, close)
        ConsolePage.LiveTv ->
            LiveTvRoute(
                graph,
                signedIn.account,
                {
                    open(ConsolePage.Tasks)
                }.takeIf { ConsolePage.Tasks.available(signedIn.profile, signedIn.account) },
                { open(ConsolePage.LiveTvRecording) }.takeIf { ConsolePage.LiveTvRecording.available(signedIn.profile, signedIn.account) },
                close,
            )
        ConsolePage.LiveTvRecording -> LiveTvRecordingRoute(graph, close)
        ConsolePage.Server -> ServerRoute(graph, close)
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

/** The playback page with its own view model, which reads the user's configuration when it opens. */
@Composable
private fun PlaybackRoute(
    graph: AppGraph,
    onBack: () -> Unit,
) {
    val playback: PlaybackViewModel =
        viewModel(factory = viewModelFactory { initializer { PlaybackViewModel(graph.connection) } })
    val state by playback.state.collectAsStateWithLifecycle()
    PlaybackScreen(
        state,
        PlaybackActions(
            onAudio = playback::setAudio,
            onSubtitles = playback::setSubtitles,
            onSubtitleMode = playback::setSubtitleMode,
            onPlayDefaultAudioTrack = playback::setPlayDefaultAudioTrack,
            onRememberAudio = playback::setRememberAudio,
            onRememberSubtitles = playback::setRememberSubtitles,
            onAutoplayNextEpisode = playback::setAutoplayNextEpisode,
            onRetry = playback::load,
            onRetrySave = playback::retrySave,
            onBack = onBack,
        ),
    )
}

/** The home screen page with its own view model, asking the server its own release's way. */
@Composable
private fun HomeLayoutRoute(
    graph: AppGraph,
    profile: JellyfinServerProfile,
    onBack: () -> Unit,
) {
    val home: HomeLayoutViewModel =
        viewModel(factory = viewModelFactory { initializer { HomeLayoutViewModel(graph.connection, profile) } })
    val state by home.state.collectAsStateWithLifecycle()
    PageItemDepth(open = (state as? HomeLayoutUiState.Ready)?.openLibrary != null) {
        HomeLayoutScreen(
            state,
            HomeLayoutActions(
                onOpen = home::open,
                onInMyMedia = home::setInMyMedia,
                onInLatest = home::setInLatest,
                onGrouped = home::setGrouped,
                onMove = home::move,
                onHidePlayedInLatest = home::setHidePlayedInLatest,
                onDisplayMissingEpisodes = home::setDisplayMissingEpisodes,
                onDisplayCollectionsView = home::setDisplayCollectionsView,
                onRetry = home::load,
                onRetrySave = home::retrySave,
                onBack = onBack,
            ),
        )
    }
}

/** The profile picture page: the system photo picker feeds the view model the picked file's bytes. */
@Composable
private fun ProfilePictureRoute(
    graph: AppGraph,
    newRoute: Boolean,
    onBack: () -> Unit,
) {
    val picture: ProfilePictureViewModel =
        viewModel(factory = viewModelFactory { initializer { ProfilePictureViewModel(graph.connection, newRoute) } })
    val state by picture.state.collectAsStateWithLifecycle()
    val resolver = LocalContext.current.applicationContext.contentResolver
    val picker =
        rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
            // Nothing picked is the user backing out, not a failure.
            if (uri != null) picture.choose { attempt { resolver.openInputStream(uri)?.use { it.readBytes() } }.getOrNull() }
        }
    ProfilePictureScreen(
        state,
        ProfilePictureActions(
            onChoose = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
            onAskRemove = picture::askRemove,
            onRemove = picture::remove,
            onRetry = picture::load,
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

/**
 * The scheduled tasks page, whose event socket closes with the page. A task opens as an entry above it on the stack
 * ([openItem]), and its screen ([TaskItemRoute]) reads this same view model.
 */
@Composable
private fun TasksRoute(
    graph: AppGraph,
    openItem: (String) -> Unit,
    onBack: () -> Unit,
) {
    val tasks: TasksViewModel = viewModel(factory = viewModelFactory { initializer { TasksViewModel(graph.connection) } })
    val state by tasks.state.collectAsStateWithLifecycle()
    TasksScreen(
        state,
        TasksActions(
            onRun = tasks::run,
            onStop = tasks::stop,
            onOpen = {
                tasks.opened()
                openItem(it.id)
            },
            onRetry = tasks::load,
            onRefresh = tasks::refresh,
            onBack = onBack,
        ),
    )
}

/** One task's screen, with the page's view model: the one its list shows, whose triggers it changes. */
@Composable
private fun TaskItemRoute(
    graph: AppGraph,
    taskId: String,
    onBack: () -> Unit,
) {
    val tasks: TasksViewModel = viewModel(factory = viewModelFactory { initializer { TasksViewModel(graph.connection) } })
    val state by tasks.state.collectAsStateWithLifecycle()
    TaskScreen(
        state,
        taskId,
        TasksActions(
            onAskAdd = tasks::askAdd,
            onAddTrigger = { tasks.addTrigger(taskId, it) },
            onRemoveTrigger = { tasks.removeTrigger(taskId, it) },
            onRetry = tasks::load,
            onBack = onBack,
        ),
    )
}

/** The screen of an item open in a page: stacked above the page's own, under the same opening, sharing its view models. */
@Composable
internal fun ConsoleItemRoute(
    entry: ConsoleEntry,
    graph: AppGraph,
    close: () -> Unit,
) {
    val item = entry.item ?: return
    when (entry.page) {
        ConsolePage.Tasks -> TaskItemRoute(graph, item, close)
        else -> Unit
    }
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
            onRefresh = devices::refresh,
            onBack = onBack,
        ),
    )
}

/** The server settings page, with the splash screen switch only on a server that has one. */
@Composable
private fun ServerSettingsRoute(
    graph: AppGraph,
    profile: JellyfinServerProfile,
    onBack: () -> Unit,
) {
    val settings: ServerSettingsViewModel =
        viewModel(factory = viewModelFactory { initializer { ServerSettingsViewModel(graph.connection) } })
    val state by settings.state.collectAsStateWithLifecycle()
    val splash by settings.splashImage.state.collectAsStateWithLifecycle()
    val resolver = LocalContext.current.contentResolver
    val picker =
        rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
            // Nothing picked is the user backing out, not a failure.
            if (uri != null) settings.splashImage.choose { attempt { resolver.openInputStream(uri)?.use { it.readBytes() } }.getOrNull() }
        }
    ServerSettingsScreen(
        state,
        ServerSettingsActions(
            onEdit = settings::edit,
            onSaveText = settings::saveText,
            onMetadataLanguage = settings::setMetadataLanguage,
            onMetadataCountry = settings::setMetadataCountry,
            onQuickConnect = settings::setQuickConnect,
            onSplashscreen = settings::setSplashscreen,
            onOpenSplash = settings.splashImage::open,
            onChooseSplash = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
            onAskRemoveSplash = settings.splashImage::askRemove,
            onRemoveSplash = settings.splashImage::remove,
            onRetry = settings::load,
            onRetrySave = settings::retrySave,
            onBack = onBack,
        ),
        splashscreenAvailable = profile.hasSplashscreen,
        splashImageKnown = profile.servesSplashscreenLocation,
        splash = splash,
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
        ServerActions(
            onConfirm = server::confirm,
            onRestart = server::restart,
            onConfirmShutdown = server::confirmShutdown,
            onShutdown = server::shutdown,
            onDismissRestart = server::dismissRestart,
            onRetry = server::load,
            onRefresh = server::refresh,
            onBack = onBack,
        ),
    )
}

/** The now-playing page with its own view model, whose event socket closes with the page. */
@Composable
private fun NowPlayingRoute(
    graph: AppGraph,
    account: JellyfinAccount,
    onBack: () -> Unit,
) {
    val nowPlaying: NowPlayingViewModel =
        viewModel(factory = viewModelFactory { initializer { NowPlayingViewModel(graph.connection, account) } })
    val state by nowPlaying.state.collectAsStateWithLifecycle()
    PageItemDepth(open = (state as? NowPlayingUiState.Ready)?.selectedSession != null) {
        NowPlayingScreen(
            state,
            account.isAdministrator,
            NowPlayingActions(
                onSelect = nowPlaying::select,
                onDeselect = nowPlaying::deselect,
                onPlayPause = nowPlaying.remote::playPause,
                onSkip = nowPlaying.remote::skip,
                onSeekBy = nowPlaying.remote::seekBy,
                onSeekTo = nowPlaying.remote::seekTo,
                onVolume = nowPlaying.remote::setVolume,
                onToggleMute = nowPlaying.remote::toggleMute,
                onAskMessage = nowPlaying.remote::askMessage,
                onCancelMessage = nowPlaying.remote::cancelMessage,
                onSendMessage = nowPlaying.remote::sendMessage,
                onStop = nowPlaying::askStop,
                onConfirmStop = nowPlaying::stop,
                onCancelStop = nowPlaying::cancelStop,
                onRetry = nowPlaying::load,
                onBack = onBack,
            ),
        )
    }
}

/** The logs page, with the share sheet it opens for a log. */
@Composable
private fun LogsRoute(
    graph: AppGraph,
    onBack: () -> Unit,
) {
    val logs: LogsViewModel = viewModel(factory = viewModelFactory { initializer { LogsViewModel(graph.connection) } })
    val state by logs.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    PageItemDepth(open = (state as? LogsUiState.Ready)?.open != null) {
        LogsScreen(
            state,
            LogsActions(
                onOpen = logs::open,
                onCloseLog = logs::closeLog,
                onQuery = logs::setQuery,
                onLevel = logs::setLevel,
                onCopy = { text -> copyLogEntry(context, text) },
                onShare = { name, text -> context.startActivity(shareLogIntent(context, name, text)) },
                onEditRetention = logs::editRetention,
                onSaveRetention = logs::saveRetention,
                onRetrySave = logs::retrySave,
                onRetry = logs::load,
                onRefresh = logs::refresh,
                onRetryOpen = logs::retryOpen,
                onBack = onBack,
            ),
        )
    }
}
