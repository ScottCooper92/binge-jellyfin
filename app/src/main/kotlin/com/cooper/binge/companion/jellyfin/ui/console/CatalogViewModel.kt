package com.cooper.binge.companion.jellyfin.ui.console

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.binge.companion.sdk.attempt
import com.cooper.binge.companion.jellyfin.server.CatalogPackage
import com.cooper.binge.companion.jellyfin.server.Connection
import com.cooper.binge.companion.jellyfin.server.InstallEvent
import com.cooper.binge.companion.jellyfin.server.JellyfinVersion
import com.cooper.binge.companion.jellyfin.server.cancelInstall
import com.cooper.binge.companion.jellyfin.server.installEvents
import com.cooper.binge.companion.jellyfin.server.installPackage
import com.cooper.binge.companion.jellyfin.server.packages
import com.cooper.binge.companion.jellyfin.server.plugins
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** How an install this page watched ended, shown on the package for a moment. */
enum class InstallOutcome { Done, Failed, Cancelled }

sealed interface CatalogUiState {
    data object Loading : CatalogUiState

    data class Failed(
        val failure: ConsoleFailure,
    ) : CatalogUiState

    /**
     * The catalog and what this page has asked of it. [installing] maps a package's name to the id the server
     * gave its install, once it has (null until the server says), which Cancel needs. [outcomes] is how each
     * install ended. [installed] is the names of the plugins the server already has. [open] is the name of
     * the package whose page is showing.
     */
    data class Ready(
        val packages: List<CatalogPackage>,
        val installed: Set<String> = emptySet(),
        val open: String? = null,
        val installing: Map<String, String?> = emptyMap(),
        val outcomes: Map<String, InstallOutcome> = emptyMap(),
        val actionFailed: ConsoleFailure? = null,
        /** The server's release, so a package page offers the version built for it. */
        val server: JellyfinVersion? = null,
    ) : CatalogUiState
}

/**
 * The plugins the server's repositories offer. Install takes the newest version built for this server's
 * release or older (`targetAbi`), and the server downloads it in the background: its progress comes over the
 * page's own event socket, which closes with the page, and Cancel stops it with the id the server announced.
 * An install takes effect when the server restarts. Admin only.
 */
class CatalogViewModel(
    private val connection: Connection,
    private val server: JellyfinVersion?,
) : ViewModel() {
    private val _state = MutableStateFlow<CatalogUiState>(CatalogUiState.Loading)
    val state: StateFlow<CatalogUiState> = _state.asStateFlow()

    private var events: Job? = null

    init {
        load()
    }

    fun load() {
        events?.cancel()
        _state.value = CatalogUiState.Loading
        events =
            viewModelScope.launch {
                connection
                    .consoleCall(TAG) { _, client ->
                        val names =
                            attempt { client.plugins() }
                                .getOrNull()
                                .orEmpty()
                                .map { it.name }
                                .toSet()
                        Triple(client.packages(), names, client)
                    }.onFailure { _state.value = CatalogUiState.Failed(it.consoleFailure()) }
                    .onSuccess { (packages, installed, client) ->
                        _state.value = CatalogUiState.Ready(packages, installed, server = server)
                        client.installEvents().collect { following(it) }
                    }
            }
    }

    /** Opens the package called [name], or goes back to the list for null. */
    fun open(name: String?) = update { copy(open = name, actionFailed = null) }

    fun install(pkg: CatalogPackage) {
        val version = pkg.installable(server) ?: return
        update { copy(installing = installing + (pkg.name to null), outcomes = outcomes - pkg.name, actionFailed = null) }
        viewModelScope.launch {
            connection
                .consoleCall(TAG) { _, client -> client.installPackage(pkg, version) }
                .onFailure { error -> update { copy(installing = installing - pkg.name, actionFailed = error.consoleFailure()) } }
        }
    }

    /** Cancels [pkg]'s install, once the server has said which install it is. */
    fun cancel(pkg: CatalogPackage) {
        val id = (_state.value as? CatalogUiState.Ready)?.installing?.get(pkg.name) ?: return
        viewModelScope.launch {
            connection
                .consoleCall(TAG) { _, client -> client.cancelInstall(id) }
                .onFailure { error -> update { copy(actionFailed = error.consoleFailure()) } }
        }
    }

    private fun following(event: InstallEvent) =
        update {
            when (event) {
                is InstallEvent.Started -> if (event.name in installing) copy(installing = installing + (event.name to event.id)) else this
                is InstallEvent.Done ->
                    copy(
                        installing = installing - event.name,
                        installed = installed + event.name,
                        outcomes =
                            outcomes + (event.name to InstallOutcome.Done),
                    )
                is InstallEvent.Failed ->
                    copy(installing = installing - event.name, outcomes = outcomes + (event.name to InstallOutcome.Failed))
                is InstallEvent.Cancelled ->
                    copy(installing = installing - event.name, outcomes = outcomes + (event.name to InstallOutcome.Cancelled))
            }
        }

    private fun update(change: CatalogUiState.Ready.() -> CatalogUiState.Ready) =
        _state.update {
            (it as? CatalogUiState.Ready)?.change()
                ?: it
        }

    private companion object {
        const val TAG = "JellyfinCatalog"
    }
}
