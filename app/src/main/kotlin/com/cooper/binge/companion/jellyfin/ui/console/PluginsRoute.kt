package com.cooper.binge.companion.jellyfin.ui.console

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.cooper.binge.companion.jellyfin.AppGraph
import com.cooper.binge.companion.jellyfin.server.JellyfinVersion
import com.cooper.binge.companion.jellyfin.ui.openInBrowser

/**
 * The plugins page, with a view model for each tab. A plugin or a package opens as an entry above it on the stack
 * ([openItem]), and its screen ([PluginItemRoute], [PackageItemRoute]) reads these same view models.
 * Kept out of `ConsoleRoutes.kt`, which is at its function limit.
 */
@Composable
internal fun PluginsRoute(
    graph: AppGraph,
    server: JellyfinVersion?,
    onOpenServer: () -> Unit,
    openItem: (String) -> Unit,
    onBack: () -> Unit,
) {
    val installed: PluginsViewModel = viewModel(factory = viewModelFactory { initializer { PluginsViewModel(graph.connection) } })
    val catalog: CatalogViewModel = viewModel(factory = viewModelFactory { initializer { CatalogViewModel(graph.connection, server) } })
    val repositories: RepositoriesViewModel =
        viewModel(factory = viewModelFactory { initializer { RepositoriesViewModel(graph.connection) } })
    val installedState by installed.state.collectAsStateWithLifecycle()
    val catalogState by catalog.state.collectAsStateWithLifecycle()
    val repositoriesState by repositories.state.collectAsStateWithLifecycle()
    // An install that finished while a package's screen was over this page is read back when the page is shown again.
    val installsDone = (catalogState as? CatalogUiState.Ready)?.outcomes?.count { it.value == InstallOutcome.Done } ?: 0
    LaunchedEffect(installsDone) {
        if (installsDone > 0) installed.refresh()
    }
    PluginsScreen(
        installed = installedState,
        catalog = catalogState,
        repositories = repositoriesState,
        actions =
            PluginsActions(
                onOpen = {
                    installed.itemOpened()
                    openItem(pluginItem(it.id))
                },
                onRestart = onOpenServer,
                onRetry = installed::load,
                onRefresh = installed::pull,
            ),
        catalogActions =
            CatalogActions(
                onOpen = {
                    catalog.itemOpened()
                    openItem(packageItem(it))
                },
                onRetry = catalog::load,
            ),
        repositoryActions =
            RepositoriesActions(
                onAskAdd = repositories::askAdd,
                onAdd = repositories::add,
                onAskRemove = repositories::askRemove,
                onRemove = repositories::remove,
                onRetry = repositories::load,
                onRefresh = repositories::refresh,
            ),
        onBack = onBack,
    )
}

/**
 * One plugin's screen, with the page's view model. A plugin's settings page is Jellyfin web's own, opened in the
 * browser at the server's address, where the web client signs the user in: the address carries no token.
 */
@Composable
internal fun PluginItemRoute(
    graph: AppGraph,
    pluginId: String,
    onBack: () -> Unit,
) {
    val installed: PluginsViewModel = viewModel(factory = viewModelFactory { initializer { PluginsViewModel(graph.connection) } })
    val state by installed.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    // Opened from this screen, so asked for here: the page behind it may not be composed to hear it.
    val settingsUrl = (state as? PluginsUiState.Ready)?.settingsUrl
    LaunchedEffect(settingsUrl) {
        if (settingsUrl != null) {
            context.openInBrowser(settingsUrl)
            installed.settingsOpened()
        }
    }
    PluginScreen(
        state,
        pluginId,
        PluginsActions(
            onToggle = installed::setEnabled,
            onAskUninstall = installed::askUninstall,
            onUninstall = installed::uninstall,
            onOpenSettings = installed::openSettings,
            onRetry = installed::load,
            onUninstallSeen = installed::uninstallSeen,
        ),
        onBack,
    )
}

/** One catalog package's screen, with the page's view model. */
@Composable
internal fun PackageItemRoute(
    graph: AppGraph,
    server: JellyfinVersion?,
    name: String,
    onBack: () -> Unit,
) {
    val catalog: CatalogViewModel = viewModel(factory = viewModelFactory { initializer { CatalogViewModel(graph.connection, server) } })
    val state by catalog.state.collectAsStateWithLifecycle()
    PackageScreen(
        state,
        name,
        CatalogActions(onInstall = catalog::install, onCancel = catalog::cancel, onRetry = catalog::load),
        onBack,
    )
}
