package com.cooper.binge.companion.jellyfin.ui.console

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.LayoutDirection
import com.binge.designsystem.component.BingePullToRefresh
import com.binge.designsystem.component.BingeTabRow
import com.binge.designsystem.component.ItemGroup
import com.binge.designsystem.component.ListItem
import com.binge.designsystem.component.ListRowSkeletonColumn
import com.binge.designsystem.layout.LayoutAnchors
import com.binge.designsystem.layout.layoutAnchor
import com.binge.designsystem.layout.layoutAnchorIf
import com.binge.designsystem.template.BingeScreenScaffold
import com.binge.designsystem.template.EmptyScreen
import com.binge.designsystem.template.ScreenBar
import com.binge.designsystem.template.ScreenBody
import com.binge.designsystem.template.screenListPadding
import com.cooper.binge.companion.jellyfin.R
import com.cooper.binge.companion.jellyfin.server.PluginStatus
import com.cooper.binge.companion.jellyfin.server.ServerPlugin
import kotlinx.coroutines.launch
import com.binge.designsystem.R as DesR

/** What the installed tab can ask of its view model, and of the console. */
data class PluginsActions(
    val onOpen: (ServerPlugin) -> Unit = {},
    val onToggle: (ServerPlugin, Boolean) -> Unit = { _, _ -> },
    val onAskUninstall: (ServerPlugin?) -> Unit = {},
    val onUninstall: () -> Unit = {},
    val onOpenSettings: (ServerPlugin) -> Unit = {},
    /** Goes to the Server page, where Restart is. */
    val onRestart: () -> Unit = {},
    val onRetry: () -> Unit = {},
    val onRefresh: () -> Unit = {},
    /** The plugin just uninstalled has had its screen closed. */
    val onUninstallSeen: () -> Unit = {},
)

internal const val REPOSITORIES_TAB = 2

/**
 * Each tab's list position, held above the tab switch so it outlives the list: switching tabs, opening a plugin or
 * package and coming back, and a reload that swaps the rows all leave the position where it was. Saved, so a
 * rotation keeps it too.
 */
class PluginsListStates(
    val installed: LazyListState,
    val catalog: LazyListState,
    val repositories: LazyListState,
)

@Composable
fun rememberPluginsListStates() =
    PluginsListStates(
        installed = rememberLazyListState(),
        catalog = rememberLazyListState(),
        repositories = rememberLazyListState(),
    )

/** The item key of an installed plugin on the stack, and of a catalog package. */
internal fun pluginItem(id: String) = "plugin:$id"

internal fun packageItem(name: String) = "package:$name"

private fun String?.plugin(): String? = this?.removePrefix("plugin:")?.takeIf { this.startsWith("plugin:") }

private fun String?.catalogPackage(): String? = this?.removePrefix("package:")?.takeIf { this.startsWith("package:") }

private val TABS = listOf(R.string.plugins_tab_installed, R.string.plugins_tab_catalog, R.string.plugins_tab_repositories)

/**
 * The plugins page: the plugins installed, the catalog the server's repositories offer, and the repositories
 * themselves, as three tabs. A tap on a plugin or a package opens its own screen ([PluginScreen], [PackageScreen]) above
 * this one on the stack. The one showing, where these tabs are beside it, is marked.
 */
@Composable
fun PluginsScreen(
    installed: PluginsUiState,
    catalog: CatalogUiState,
    repositories: RepositoriesUiState,
    actions: PluginsActions,
    catalogActions: CatalogActions,
    repositoryActions: RepositoriesActions,
    onBack: () -> Unit,
    /** The tab it opens on: the installed plugins, unless a frame opens another. */
    initialTab: Int = 0,
    listStates: PluginsListStates = rememberPluginsListStates(),
    openItem: String? = LocalOpenItem.current,
) {
    var tab by rememberSaveable { mutableIntStateOf(initialTab) }
    // The pager and the tab row are one control: a swipe settles on a page and sets the tab, a tap on a tab animates the pager.
    val pager = rememberPagerState(initialPage = tab) { TABS.size }
    val scope = rememberCoroutineScope()
    LaunchedEffect(pager) { snapshotFlow { pager.settledPage }.collect { tab = it } }
    val host = remember { SnackbarHostState() }
    BingeScreenScaffold(
        title = stringResource(R.string.plugins_title),
        bar = ScreenBar.Small,
        onBack = onBack,
        snackbarHostState = host,
        bottomBar = {
            // Only the Repositories tab creates anything; the other tabs have no footer.
            CreateFooter(
                label = stringResource(R.string.repositories_add),
                icon = Icons.Outlined.Add,
                onClick = { repositoryActions.onAskAdd(true) },
                visible = tab == REPOSITORIES_TAB && repositories !is RepositoriesUiState.Failed,
                enabled = repositories is RepositoriesUiState.Ready,
                loading = (repositories as? RepositoriesUiState.Ready)?.saving == true,
            )
        },
    ) { padding ->
        ScreenBody(padding) { _ ->
            val inner = padding.screenListPadding()
            // The lists sit under the tab row, which has taken the top inset.
            val content = inner.belowTabRow(LocalLayoutDirection.current)
            // What a plugin or a package changed is said by its own screen; the list says what its own tabs did.
            RefusedMessage((repositories as? RepositoriesUiState.Ready)?.actionFailed, host, R.string.plugins_not_changed)
            RefusedMessage((installed as? PluginsUiState.Ready)?.refreshFailed, host, R.string.console_not_refreshed)
            RefusedMessage((repositories as? RepositoriesUiState.Ready)?.refreshFailed, host, R.string.console_not_refreshed)
            Column(Modifier.fillMaxSize()) {
                BingeTabRow(
                    tabs = TABS.map { stringResource(it) },
                    selectedIndex = pager.currentPage,
                    // The pager alone writes `tab`, when it settles: a tap a swipe interrupts lands where the swipe did.
                    onSelect = { page -> scope.launch { pager.animateScrollToPage(page) } },
                    modifier = Modifier.padding(top = inner.calculateTopPadding()),
                )
                HorizontalPager(
                    state = pager,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    verticalAlignment = Alignment.Top,
                ) { page ->
                    PluginsPage(
                        page,
                        PluginsStates(installed, catalog, repositories),
                        PluginsHandlers(actions, catalogActions, repositoryActions),
                        content,
                        listStates,
                        openItem,
                    )
                }
            }
        }
    }
}

/** The three tabs' states, together. */
private class PluginsStates(
    val installed: PluginsUiState,
    val catalog: CatalogUiState,
    val repositories: RepositoriesUiState,
)

/** What the three tabs can ask, together. */
private class PluginsHandlers(
    val installed: PluginsActions,
    val catalog: CatalogActions,
    val repositories: RepositoriesActions,
)

/** Under the tab row the bar is already above, so the lists keep the sides and the bottom only. */
@Composable
private fun PaddingValues.belowTabRow(direction: LayoutDirection) =
    PaddingValues(
        start = calculateStartPadding(direction),
        top = dimensionResource(DesR.dimen.padding_m),
        end = calculateEndPadding(direction),
        bottom = calculateBottomPadding(),
    )

/** One tab's page, by index. */
@Composable
private fun PluginsPage(
    page: Int,
    states: PluginsStates,
    handlers: PluginsHandlers,
    content: PaddingValues,
    listStates: PluginsListStates,
    openItem: String?,
) {
    // Full height, so a page lays out as it did before the pager: a list fills it and a message sits where it always did.
    Box(Modifier.fillMaxSize()) {
        when (page) {
            0 -> InstalledTab(states.installed, handlers.installed, content, listStates.installed, openItem.plugin())
            1 -> CatalogTab(states.catalog, handlers.catalog, content, listStates.catalog, openItem.catalogPackage())
            else -> RepositoriesTab(states.repositories, handlers.repositories, content, listStates.repositories)
        }
    }
}

/** The page's rows as they load: the shape of the list, not a spinner. */
@Composable
internal fun PluginsLoading(
    inner: PaddingValues,
    intro: String? = null,
) {
    if (intro == null) {
        ListRowSkeletonColumn(contentPadding = inner, count = LOADING_ROWS, description = stringResource(R.string.plugins_loading))
    } else {
        ConsoleLoading(inner, LOADING_ROWS, intro = intro, formInset = false)
    }
}

private const val LOADING_ROWS = 6

@Composable
private fun InstalledTab(
    state: PluginsUiState,
    actions: PluginsActions,
    inner: PaddingValues,
    listState: LazyListState,
    openPlugin: String?,
) {
    when (state) {
        PluginsUiState.Loading -> PluginsLoading(inner)
        is PluginsUiState.Failed -> ConsoleFailureScreen(state.failure, actions.onRetry)
        is PluginsUiState.Ready ->
            when {
                state.plugins.isEmpty() ->
                    EmptyScreen(
                        message = stringResource(R.string.plugins_empty),
                        title = stringResource(R.string.plugins_empty_title),
                    )
                else ->
                    BingePullToRefresh(
                        isRefreshing = state.refreshing,
                        onRefresh = actions.onRefresh,
                        indicatorTopInset = inner.calculateTopPadding(),
                    ) { InstalledList(state, actions, inner, listState, openPlugin) }
            }
    }
}

@Composable
private fun InstalledList(
    state: PluginsUiState.Ready,
    actions: PluginsActions,
    inner: PaddingValues,
    listState: LazyListState,
    openPlugin: String?,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        state = listState,
        contentPadding = inner,
        verticalArrangement = Arrangement.spacedBy(dimensionResource(DesR.dimen.padding_l)),
    ) {
        if (state.restartNeeded) {
            item(key = "restart") {
                ItemGroup(
                    title = null,
                    modifier = Modifier.layoutAnchor(LayoutAnchors.section(LayoutAnchors.Collection.FIRST_ITEM)),
                    rows =
                        listOf(
                            ListItem(
                                icon = Icons.Outlined.RestartAlt,
                                label = stringResource(R.string.plugins_restart_needed),
                                detail = stringResource(R.string.plugins_restart_detail),
                                supportingMaxLines = 3,
                                onClick = actions.onRestart,
                            ),
                        ),
                )
            }
        }
        item(key = "plugins") {
            ItemGroup(
                title = null,
                rows = state.plugins.map { it.row(state, actions, it.id == openPlugin) },
                modifier = Modifier.layoutAnchorIf(!state.restartNeeded, LayoutAnchors.section(LayoutAnchors.Collection.FIRST_ITEM)),
            )
        }
    }
}

@Composable
private fun ServerPlugin.row(
    state: PluginsUiState.Ready,
    actions: PluginsActions,
    open: Boolean,
): ListItem =
    ListItem(
        icon = Icons.Outlined.Extension,
        label = name,
        detail = listOf(version, statusLabel(status)).filter { it.isNotBlank() }.joinToString(" · "),
        loading = id in state.busy,
        selected = open,
        onClick = { actions.onOpen(this) },
    )

@Composable
internal fun statusLabel(status: PluginStatus): String =
    stringResource(
        when (status) {
            PluginStatus.Active -> R.string.plugin_status_active
            PluginStatus.Restart -> R.string.plugin_status_restart
            PluginStatus.Disabled -> R.string.plugin_status_disabled
            PluginStatus.NotSupported -> R.string.plugin_status_not_supported
            PluginStatus.Malfunctioned -> R.string.plugin_status_malfunctioned
            PluginStatus.Superseded -> R.string.plugin_status_superseded
            PluginStatus.Deleted -> R.string.plugin_status_deleted
            PluginStatus.Unknown -> R.string.plugin_status_unknown
        },
    )
