package com.cooper.binge.companion.jellyfin.ui.console

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.OpenInBrowser
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import com.binge.designsystem.component.BingeOutlinedButton
import com.binge.designsystem.component.ItemGroup
import com.binge.designsystem.component.ListItem
import com.binge.designsystem.template.BingeScreenScaffold
import com.binge.designsystem.template.ScreenBar
import com.binge.designsystem.template.ScreenBody
import com.binge.designsystem.template.screenListPadding
import com.cooper.binge.companion.jellyfin.R
import com.cooper.binge.companion.jellyfin.server.ServerPlugin
import com.binge.designsystem.R as DesR

/**
 * One plugin: what it is, on or off, its settings page, and Uninstall, last, red, behind a confirm. A screen of its own
 * above the tabs on the stack, so Back returns to them. A plugin that is uninstalled or no longer listed closes it.
 */
@Composable
fun PluginScreen(
    state: PluginsUiState,
    pluginId: String,
    actions: PluginsActions,
    onBack: () -> Unit,
) {
    val ready = state as? PluginsUiState.Ready
    val plugin = ready?.plugins?.firstOrNull { it.id == pluginId }
    val gone = ready != null && (plugin == null || ready.uninstalled == pluginId)
    LaunchedEffect(gone) {
        if (gone) {
            onBack()
            actions.onUninstallSeen()
        }
    }
    val host = remember { SnackbarHostState() }
    val listState = rememberLazyListState()
    BingeScreenScaffold(
        title = plugin?.name ?: stringResource(R.string.plugins_title),
        bar = ScreenBar.Small,
        onBack = onBack,
        snackbarHostState = host,
    ) { padding ->
        ScreenBody(padding) { _ ->
            val inner = padding.screenListPadding()
            when {
                state is PluginsUiState.Loading -> PluginsLoading(inner)
                state is PluginsUiState.Failed -> ConsoleFailureScreen(state.failure, actions.onRetry)
                ready != null && plugin != null -> PluginPage(plugin, ready, actions, inner, listState)
            }
            RefusedMessage(ready?.actionFailed, host, R.string.plugins_not_changed)
        }
    }
}

/** One plugin: what it is, on or off, its settings page, and Uninstall, last, red, behind a confirm. */
@Composable
internal fun PluginPage(
    plugin: ServerPlugin,
    state: PluginsUiState.Ready,
    actions: PluginsActions,
    inner: PaddingValues,
    listState: LazyListState,
) {
    val busy = plugin.id in state.busy
    LazyColumn(
        state = listState,
        contentPadding = inner,
        verticalArrangement = Arrangement.spacedBy(dimensionResource(DesR.dimen.padding_l)),
    ) {
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
