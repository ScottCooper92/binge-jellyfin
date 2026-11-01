package com.cooper.binge.companion.jellyfin.ui.console

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cooper.binge.companion.jellyfin.server.Connection
import com.cooper.binge.companion.jellyfin.server.JellyfinClient
import com.cooper.binge.companion.jellyfin.server.PluginStatus
import com.cooper.binge.companion.jellyfin.server.ServerPlugin
import com.cooper.binge.companion.jellyfin.server.disablePlugin
import com.cooper.binge.companion.jellyfin.server.enablePlugin
import com.cooper.binge.companion.jellyfin.server.pluginSettingsPage
import com.cooper.binge.companion.jellyfin.server.pluginSettingsUrl
import com.cooper.binge.companion.jellyfin.server.plugins
import com.cooper.binge.companion.jellyfin.server.uninstallPlugin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface PluginsUiState {
    data object Loading : PluginsUiState

    data class Failed(
        val failure: ConsoleFailure,
    ) : PluginsUiState

    /**
     * The installed plugins. [open] is the id of the one whose page is showing; [confirming] the one asked
     * about uninstalling; [busy] those with a change in flight; [settingsUrl] a plugin settings page ready to
     * open in the browser, until [PluginsViewModel.settingsOpened]; [actionFailed] the last change that
     * didn't go through; [sentEnabled] the last on/off sent for each plugin, which the switch shows while that
     * change waits for a restart.
     */
    data class Ready(
        val plugins: List<ServerPlugin>,
        val open: String? = null,
        val confirming: ServerPlugin? = null,
        val busy: Set<String> = emptySet(),
        val settingsUrl: String? = null,
        val actionFailed: ConsoleFailure? = null,
        val sentEnabled: Map<String, Boolean> = emptyMap(),
    ) : PluginsUiState {
        /**
         * Whether [plugin] is switched on. Once a change is waiting for a restart the server reports `Restart`
         * with no direction, so the switch shows what was last sent for it, until the restart settles it.
         */
        fun isEnabled(plugin: ServerPlugin): Boolean =
            if (plugin.status == PluginStatus.Restart) sentEnabled[plugin.id] ?: plugin.enabled else plugin.enabled

        /** Some plugin is waiting for the server to restart, so the admin is pointed at Restart. */
        val restartNeeded: Boolean get() = plugins.any { it.status == PluginStatus.Restart }
    }
}

/**
 * The server's installed plugins: each switched on or off, opened for its settings page (Jellyfin web's, in
 * the browser), or uninstalled behind a confirm. Admin only. A change takes effect when the server restarts,
 * and the page says so. The access token is never in the settings address: the web client signs in itself.
 */
class PluginsViewModel(
    private val connection: Connection,
) : ViewModel() {
    private val _state = MutableStateFlow<PluginsUiState>(PluginsUiState.Loading)
    val state: StateFlow<PluginsUiState> = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        _state.value = PluginsUiState.Loading
        viewModelScope.launch {
            _state.value =
                connection
                    .consoleCall(TAG) { _, client -> client.plugins() }
                    .fold(
                        onSuccess = { PluginsUiState.Ready(it) },
                        onFailure = { PluginsUiState.Failed(it.consoleFailure()) },
                    )
        }
    }

    /** Opens [plugin]'s page, or goes back to the list for null. */
    fun open(plugin: ServerPlugin?) = update { copy(open = plugin?.id, confirming = null, actionFailed = null) }

    fun setEnabled(
        plugin: ServerPlugin,
        on: Boolean,
    ) = change(plugin, sent = on) { client -> if (on) client.enablePlugin(plugin) else client.disablePlugin(plugin) }

    fun askUninstall(plugin: ServerPlugin?) = update { copy(confirming = plugin, actionFailed = null) }

    fun uninstall() {
        val plugin = (_state.value as? PluginsUiState.Ready)?.confirming ?: return
        update { copy(confirming = null) }
        change(plugin, thenClose = true) { client -> client.uninstallPlugin(plugin) }
    }

    /** Looks up [plugin]'s settings page and, when it has one, hands its address to the screen to open. */
    fun openSettings(plugin: ServerPlugin) {
        viewModelScope.launch {
            connection
                .consoleCall(TAG) { _, client -> client.pluginSettingsPage(plugin)?.let { client.pluginSettingsUrl(it) } }
                .onSuccess { url -> update { copy(settingsUrl = url, actionFailed = if (url == null) ConsoleFailure.Refused else null) } }
                .onFailure { error -> update { copy(actionFailed = error.consoleFailure()) } }
        }
    }

    /** The settings address was opened, or there was nothing to open it with. */
    fun settingsOpened() = update { copy(settingsUrl = null) }

    /** Runs a change, then reads the plugins again, so the page shows what the server now holds. */
    private fun change(
        plugin: ServerPlugin,
        thenClose: Boolean = false,
        sent: Boolean? = null,
        call: suspend (JellyfinClient) -> Unit,
    ) {
        update { copy(busy = busy + plugin.id, actionFailed = null) }
        viewModelScope.launch {
            connection
                .consoleCall(TAG) { _, client ->
                    call(client)
                    client.plugins()
                }.onSuccess { plugins ->
                    update {
                        val remembered = if (sent == null) sentEnabled else sentEnabled + (plugin.id to sent)
                        val waiting = plugins.filter { it.status == PluginStatus.Restart }.map { it.id }.toSet()
                        copy(
                            plugins = plugins,
                            busy = busy - plugin.id,
                            open = if (thenClose) null else open,
                            sentEnabled = remembered.filterKeys { it in waiting },
                        )
                    }
                }.onFailure { error -> update { copy(busy = busy - plugin.id, actionFailed = error.consoleFailure()) } }
        }
    }

    private fun update(change: PluginsUiState.Ready.() -> PluginsUiState.Ready) =
        _state.update { (it as? PluginsUiState.Ready)?.change() ?: it }

    private companion object {
        const val TAG = "JellyfinPlugins"
    }
}
