package com.cooper.binge.companion.jellyfin.ui.console

import androidx.activity.compose.BackHandler
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
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.OpenInBrowser
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import com.binge.designsystem.component.BingeOutlinedButton
import com.binge.designsystem.component.BingeTabRow
import com.binge.designsystem.component.ItemGroup
import com.binge.designsystem.component.ListItem
import com.binge.designsystem.component.ListRowSkeletonColumn
import com.binge.designsystem.template.BingeScreenScaffold
import com.binge.designsystem.template.EmptyScreen
import com.binge.designsystem.template.ScreenBar
import com.binge.designsystem.template.ScreenBody
import com.binge.designsystem.template.screenListPadding
import com.cooper.binge.companion.jellyfin.R
import com.cooper.binge.companion.jellyfin.server.CatalogPackage
import com.cooper.binge.companion.jellyfin.server.PluginStatus
import com.cooper.binge.companion.jellyfin.server.ServerPlugin
import kotlinx.coroutines.launch
import com.binge.designsystem.R as DesR

/** What the installed tab can ask of its view model, and of the console. */
data class PluginsActions(
    val onOpen: (ServerPlugin?) -> Unit = {},
    val onToggle: (ServerPlugin, Boolean) -> Unit = { _, _ -> },
    val onAskUninstall: (ServerPlugin?) -> Unit = {},
    val onUninstall: () -> Unit = {},
    val onOpenSettings: (ServerPlugin) -> Unit = {},
    /** Goes to the Server page, where Restart is. */
    val onRestart: () -> Unit = {},
    val onRetry: () -> Unit = {},
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
fun rememberPluginsListStates() = PluginsListStates(rememberLazyListState(), rememberLazyListState(), rememberLazyListState())

private val TABS = listOf(R.string.plugins_tab_installed, R.string.plugins_tab_catalog, R.string.plugins_tab_repositories)

/**
 * The plugins page: the plugins installed, the catalog the server's repositories offer, and the repositories
 * themselves, as three tabs. Opening a plugin or a catalog package shows its own page, and Back returns to its list.
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
) {
    var tab by rememberSaveable { mutableIntStateOf(initialTab) }
    // The pager and the tab row are one control: a swipe settles on a page and sets the tab, a tap on a tab animates the pager.
    val pager = rememberPagerState(initialPage = tab) { TABS.size }
    val scope = rememberCoroutineScope()
    LaunchedEffect(pager) { snapshotFlow { pager.settledPage }.collect { tab = it } }
    val plugin = (installed as? PluginsUiState.Ready)?.let { ready -> ready.plugins.firstOrNull { it.id == ready.open } }
    val pkg = (catalog as? CatalogUiState.Ready)?.let { ready -> ready.packages.firstOrNull { it.name == ready.open } }
    val detail =
        if (tab == 0) {
            plugin?.name
        } else if (tab == 1) {
            pkg?.name
        } else {
            null
        }
    val leaveDetail: () -> Unit = { if (tab == 0) actions.onOpen(null) else catalogActions.onOpen(null) }
    BackHandler(enabled = detail != null, onBack = leaveDetail)
    val host = remember { SnackbarHostState() }
    BingeScreenScaffold(
        title = detail ?: stringResource(R.string.plugins_title),
        bar = ScreenBar.Small,
        onBack = if (detail != null) leaveDetail else onBack,
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
            val direction = LocalLayoutDirection.current
            // Under the tab row the bar is already above, so the lists keep the sides and the bottom only.
            val content =
                if (detail == null) {
                    PaddingValues(
                        start = inner.calculateStartPadding(direction),
                        top = dimensionResource(DesR.dimen.padding_m),
                        end = inner.calculateEndPadding(direction),
                        bottom = inner.calculateBottomPadding(),
                    )
                } else {
                    inner
                }
            // A change the server didn't take is said once, by the tab that made it. One message per
            // state, so switching tabs doesn't change a key and say an old refusal again.
            RefusedMessage((installed as? PluginsUiState.Ready)?.actionFailed, host, R.string.plugins_not_changed)
            RefusedMessage((catalog as? CatalogUiState.Ready)?.actionFailed, host, R.string.plugins_not_changed)
            RefusedMessage((repositories as? RepositoriesUiState.Ready)?.actionFailed, host, R.string.plugins_not_changed)
            Column(Modifier.fillMaxSize()) {
                if (detail == null) {
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
                            installed,
                            catalog,
                            repositories,
                            actions,
                            catalogActions,
                            repositoryActions,
                            content,
                            listStates,
                            plugin,
                            pkg,
                        )
                    }
                } else {
                    PluginsPage(
                        tab,
                        installed,
                        catalog,
                        repositories,
                        actions,
                        catalogActions,
                        repositoryActions,
                        content,
                        listStates,
                        plugin,
                        pkg,
                    )
                }
            }
        }
    }
}

/** One tab's page, by index. */
@Composable
private fun PluginsPage(
    page: Int,
    installed: PluginsUiState,
    catalog: CatalogUiState,
    repositories: RepositoriesUiState,
    actions: PluginsActions,
    catalogActions: CatalogActions,
    repositoryActions: RepositoriesActions,
    content: PaddingValues,
    listStates: PluginsListStates,
    plugin: ServerPlugin?,
    pkg: CatalogPackage?,
) {
    // Full height, so a page lays out as it did before the pager: a list fills it and a message sits where it always did.
    Box(Modifier.fillMaxSize()) {
        when (page) {
            0 -> InstalledTab(installed, plugin, actions, content, listStates.installed)
            1 -> CatalogTab(catalog, pkg, catalogActions, content, listStates.catalog)
            else -> RepositoriesTab(repositories, repositoryActions, content, listStates.repositories)
        }
    }
}

/** The page's rows as they load: the shape of the list, not a spinner. */
@Composable
internal fun PluginsLoading(inner: PaddingValues) {
    ListRowSkeletonColumn(contentPadding = inner, count = LOADING_ROWS, description = stringResource(R.string.plugins_loading))
}

private const val LOADING_ROWS = 6

@Composable
private fun InstalledTab(
    state: PluginsUiState,
    open: ServerPlugin?,
    actions: PluginsActions,
    inner: PaddingValues,
    listState: LazyListState,
) {
    when (state) {
        PluginsUiState.Loading -> PluginsLoading(inner)
        is PluginsUiState.Failed -> ConsoleFailureScreen(state.failure, actions.onRetry)
        is PluginsUiState.Ready ->
            when {
                open != null -> PluginPage(open, state, actions, inner)
                state.plugins.isEmpty() ->
                    EmptyScreen(
                        message = stringResource(R.string.plugins_empty),
                        title = stringResource(R.string.plugins_empty_title),
                    )
                else -> InstalledList(state, actions, inner, listState)
            }
    }
}

@Composable
private fun InstalledList(
    state: PluginsUiState.Ready,
    actions: PluginsActions,
    inner: PaddingValues,
    listState: LazyListState,
) {
    LazyColumn(
        state = listState,
        contentPadding = inner,
        verticalArrangement = Arrangement.spacedBy(dimensionResource(DesR.dimen.padding_l)),
    ) {
        if (state.restartNeeded) {
            item(key = "restart") {
                ItemGroup(
                    title = null,
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
        item(key = "plugins") { ItemGroup(title = null, rows = state.plugins.map { it.row(state, actions) }) }
    }
}

@Composable
private fun ServerPlugin.row(
    state: PluginsUiState.Ready,
    actions: PluginsActions,
): ListItem =
    ListItem(
        icon = Icons.Outlined.Extension,
        label = name,
        detail = listOf(version, statusLabel(status)).filter { it.isNotBlank() }.joinToString(" · "),
        loading = id in state.busy,
        onClick = { actions.onOpen(this) },
    )

@Composable
private fun statusLabel(status: PluginStatus): String =
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

/** One plugin: what it is, on or off, its settings page, and Uninstall, last, red, behind a confirm. */
@Composable
private fun PluginPage(
    plugin: ServerPlugin,
    state: PluginsUiState.Ready,
    actions: PluginsActions,
    inner: PaddingValues,
) {
    val busy = plugin.id in state.busy
    LazyColumn(contentPadding = inner, verticalArrangement = Arrangement.spacedBy(dimensionResource(DesR.dimen.padding_l))) {
        if (plugin.description.isNotBlank()) item { Text(plugin.description, style = MaterialTheme.typography.bodyMedium) }
        item {
            ItemGroup(
                title = null,
                rows =
                    listOfNotNull(
                        ListItem(
                            icon = Icons.Outlined.Extension,
                            label = stringResource(R.string.plugins_enabled),
                            detail = listOf(plugin.version, statusLabel(plugin.status)).joinToString(" · "),
                            toggled = state.isEnabled(plugin),
                            loading = busy,
                            onClick = { actions.onToggle(plugin, !state.isEnabled(plugin)) },
                        ).takeIf { plugin.status.canToggle },
                        // One that can't be switched still says where it stands, and why it can't.
                        ListItem(
                            icon = Icons.Outlined.Info,
                            label = statusLabel(plugin.status),
                            detail = plugin.version,
                            clickable = false,
                        ).takeIf { !plugin.status.canToggle },
                        ListItem(
                            icon = Icons.Outlined.OpenInBrowser,
                            label = stringResource(R.string.plugins_settings),
                            detail = stringResource(R.string.plugins_settings_detail),
                            supportingMaxLines = 3,
                            onClick = { actions.onOpenSettings(plugin) },
                        ).takeIf { plugin.hasSettings },
                    ),
            )
        }
        if (plugin.canUninstall) {
            item {
                BingeOutlinedButton(
                    label = stringResource(R.string.plugins_uninstall),
                    onClick = { actions.onAskUninstall(plugin) },
                    destructive = true,
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
    state.confirming?.let { UninstallDialog(it, actions) }
}

/** Uninstalling a plugin, after its name, as a confirm that opens on Cancel. [inWindow] false draws its body for a frame. */
@Composable
internal fun UninstallDialog(
    plugin: ServerPlugin,
    actions: PluginsActions,
    inWindow: Boolean = true,
) {
    ConsoleConfirm(
        title = stringResource(R.string.plugins_uninstall_title, plugin.name),
        message = stringResource(R.string.plugins_uninstall_body),
        confirmLabel = stringResource(R.string.plugins_uninstall),
        onConfirm = actions.onUninstall,
        onDismissRequest = { actions.onAskUninstall(null) },
        destructive = true,
        inWindow = inWindow,
    )
}
