package com.cooper.binge.companion.jellyfin.ui.console

import android.content.ActivityNotFoundException
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.cooper.binge.companion.jellyfin.AppGraph
import com.cooper.binge.companion.jellyfin.server.JellyfinVersion

/**
 * The plugins page, with a view model for each tab. A plugin's settings page is Jellyfin web's own, opened in
 * the browser at the server's address, where the web client signs the user in: the address carries no token.
 * Kept out of `ConsoleRoutes.kt`, which is at its function limit.
 */
@Composable
internal fun PluginsRoute(
    graph: AppGraph,
    server: JellyfinVersion?,
    onOpenServer: () -> Unit,
    onBack: () -> Unit,
) {
    val installed: PluginsViewModel = viewModel(factory = viewModelFactory { initializer { PluginsViewModel(graph.connection) } })
    val catalog: CatalogViewModel = viewModel(factory = viewModelFactory { initializer { CatalogViewModel(graph.connection, server) } })
    val repositories: RepositoriesViewModel =
        viewModel(factory = viewModelFactory { initializer { RepositoriesViewModel(graph.connection) } })
    val installedState by installed.state.collectAsStateWithLifecycle()
    val catalogState by catalog.state.collectAsStateWithLifecycle()
    val repositoriesState by repositories.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val settingsUrl = (installedState as? PluginsUiState.Ready)?.settingsUrl
    LaunchedEffect(settingsUrl) {
        if (settingsUrl != null) {
            try {
                context.startActivity(Intent(Intent.ACTION_VIEW, settingsUrl.toUri()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            } catch (_: ActivityNotFoundException) {
                // No browser to open it with: nothing to show, and nothing was sent anywhere.
            }
            installed.settingsOpened()
        }
    }
    PluginsScreen(
        installed = installedState,
        catalog = catalogState,
        repositories = repositoriesState,
        actions =
            PluginsActions(
                onOpen = installed::open,
                onToggle = installed::setEnabled,
                onAskUninstall = installed::askUninstall,
                onUninstall = installed::uninstall,
                onOpenSettings = installed::openSettings,
                onRestart = onOpenServer,
                onRetry = installed::load,
            ),
        catalogActions =
            CatalogActions(
                onOpen = catalog::open,
                onInstall = catalog::install,
                onCancel = catalog::cancel,
                onRetry = catalog::load,
            ),
        repositoryActions =
            RepositoriesActions(
                onAskAdd = repositories::askAdd,
                onAdd = repositories::add,
                onAskRemove = repositories::askRemove,
                onRemove = repositories::remove,
                onRetry = repositories::load,
            ),
        onBack = onBack,
    )
}
