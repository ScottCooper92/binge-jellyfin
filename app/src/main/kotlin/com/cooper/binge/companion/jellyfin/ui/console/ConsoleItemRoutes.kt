package com.cooper.binge.companion.jellyfin.ui.console

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.cooper.binge.companion.jellyfin.AppGraph
import com.cooper.binge.companion.jellyfin.server.JellyfinAccount
import com.cooper.binge.companion.jellyfin.server.JellyfinServerProfile
import com.cooper.binge.companion.jellyfin.server.JellyfinVersion

/** One task's screen, with the page's view model: the one its list shows, whose triggers it changes. */
@Composable
internal fun TaskItemRoute(
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

/** One log's screen, with the page's view model: the one its list shows, which reads the log. */
@Composable
internal fun LogItemRoute(
    graph: AppGraph,
    name: String,
    onBack: () -> Unit,
) {
    val logs: LogsViewModel = viewModel(factory = viewModelFactory { initializer { LogsViewModel(graph.connection) } })
    val state by logs.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    LogScreen(
        state,
        name,
        LogsActions(
            onRead = logs::open,
            onQuery = logs::setQuery,
            onLevel = logs::setLevel,
            onCopy = { text -> copyLogEntry(context, text) },
            onShare = { shared, text -> context.startActivity(shareLogIntent(context, shared, text)) },
            onRetry = logs::load,
            onRetryOpen = logs::retryOpen,
            onBack = onBack,
        ),
    )
}

/** The screen of an item open in a page: stacked above the page's own, under the same opening, sharing its view models. */
@Composable
internal fun ConsoleItemRoute(
    entry: ConsoleEntry,
    graph: AppGraph,
    server: JellyfinVersion?,
    profile: JellyfinServerProfile,
    account: JellyfinAccount,
    openSubItem: (String) -> Unit,
    close: () -> Unit,
) {
    val item = entry.item ?: return
    when (entry.page) {
        ConsolePage.Tasks -> TaskItemRoute(graph, item, close)
        ConsolePage.Logs -> LogItemRoute(graph, item, close)
        ConsolePage.Users -> UserItemRoute(graph, profile, item, openSubItem, close)
        ConsolePage.HomeLayout -> HomeLayoutLibraryRoute(graph, profile, item, close)
        ConsolePage.LibraryDisplay -> LibraryDisplayListRoute(graph, item, close)
        ConsolePage.MetadataDefaults -> MetadataItemRoute(graph.connection, item, openSubItem, close)
        ConsolePage.Libraries -> LibraryItemRoute(graph.connection, profile, item, openSubItem, close)
        ConsolePage.NowPlaying -> NowPlayingItemRoute(graph, account, item, close)
        ConsolePage.Plugins ->
            if (item.startsWith("package:")) {
                PackageItemRoute(graph, server, item.removePrefix("package:"), close)
            } else {
                PluginItemRoute(graph, item.removePrefix("plugin:"), close)
            }
        else -> Unit
    }
}

/** The actions of the home screen page's screens, over the page's view model. */
internal fun homeLayoutActions(
    home: HomeLayoutViewModel,
    onOpen: (String) -> Unit = {},
    onBack: () -> Unit,
) = HomeLayoutActions(
    onOpen = onOpen,
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
)

/** One library's screen on the home screen page, with the page's view model. */
@Composable
internal fun HomeLayoutLibraryRoute(
    graph: AppGraph,
    profile: JellyfinServerProfile,
    libraryId: String,
    onBack: () -> Unit,
) {
    val home: HomeLayoutViewModel =
        viewModel(factory = viewModelFactory { initializer { HomeLayoutViewModel(graph.connection, profile) } })
    val state by home.state.collectAsStateWithLifecycle()
    HomeLayoutLibraryScreen(state, libraryId, homeLayoutActions(home, onBack = onBack))
}
