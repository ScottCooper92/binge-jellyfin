package com.cooper.binge.companion.jellyfin.ui.console

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.OpenInBrowser
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import com.binge.designsystem.ErrorKind
import com.binge.designsystem.component.BingeOutlinedButton
import com.binge.designsystem.component.BingeTabRow
import com.binge.designsystem.component.ItemGroup
import com.binge.designsystem.component.ListItem
import com.binge.designsystem.component.ListRowSkeletonColumn
import com.binge.designsystem.template.BingeScreenScaffold
import com.binge.designsystem.template.EmptyScreen
import com.binge.designsystem.template.ErrorScreen
import com.binge.designsystem.template.ScreenBar
import com.binge.designsystem.template.ScreenBody
import com.binge.designsystem.template.screenListPadding
import com.cooper.binge.companion.jellyfin.R
import com.cooper.binge.companion.jellyfin.server.PluginStatus
import com.cooper.binge.companion.jellyfin.server.ServerPlugin
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

/** Lines a failed change's message takes, so it never moves the list. */
private const val STATUS_LINES = 2

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
) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
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
    BingeScreenScaffold(
        title = detail ?: stringResource(R.string.plugins_title),
        bar = ScreenBar.Small,
        onBack = if (detail != null) leaveDetail else onBack,
    ) { padding ->
        ScreenBody(padding) { _ ->
            val inner = padding.screenListPadding()
            Column(Modifier.fillMaxSize()) {
                if (detail == null) {
                    BingeTabRow(tabs = TABS.map { stringResource(it) }, selectedIndex = tab, onSelect = { tab = it })
                }
                when (tab) {
                    0 -> InstalledTab(installed, plugin, actions, inner)
                    1 -> CatalogTab(catalog, pkg, catalogActions, inner)
                    else -> RepositoriesTab(repositories, repositoryActions, inner)
                }
            }
        }
    }
}

/** A failure that replaced the page: its words, and Try again. */
@Composable
internal fun PluginsFailed(
    failure: ConsoleFailure,
    onRetry: () -> Unit,
) {
    val kind = if (failure == ConsoleFailure.Unreachable) ErrorKind.Network else ErrorKind.Server
    ErrorScreen(kind = kind, onRetry = onRetry, message = stringResource(failure.message))
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
) {
    when (state) {
        PluginsUiState.Loading -> PluginsLoading(inner)
        is PluginsUiState.Failed -> PluginsFailed(state.failure, actions.onRetry)
        is PluginsUiState.Ready ->
            when {
                open != null -> PluginPage(open, state, actions, inner)
                state.plugins.isEmpty() ->
                    EmptyScreen(
                        message = stringResource(R.string.plugins_empty),
                        title = stringResource(R.string.plugins_empty_title),
                    )
                else -> InstalledList(state, actions, inner)
            }
    }
}

@Composable
private fun InstalledList(
    state: PluginsUiState.Ready,
    actions: PluginsActions,
    inner: PaddingValues,
) {
    LazyColumn(contentPadding = inner, verticalArrangement = Arrangement.spacedBy(dimensionResource(DesR.dimen.padding_l))) {
        if (state.restartNeeded) {
            item {
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
        item { ItemGroup(title = null, rows = state.plugins.map { it.row(state, actions) }) }
        item { FailureLine(state.actionFailed) }
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
                            toggled = plugin.enabled,
                            loading = busy,
                            onClick = { actions.onToggle(plugin, !plugin.enabled) },
                        ).takeIf { plugin.status.canToggle },
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
        item { FailureLine(state.actionFailed) }
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

/** The last change that didn't go through, in a fixed height so it never moves the rows. */
@Composable
internal fun FailureLine(failure: ConsoleFailure?) {
    Text(
        text = failure?.let { stringResource(R.string.plugins_not_changed, stringResource(it.message)) }.orEmpty(),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.error,
        minLines = STATUS_LINES,
        maxLines = STATUS_LINES,
    )
}
