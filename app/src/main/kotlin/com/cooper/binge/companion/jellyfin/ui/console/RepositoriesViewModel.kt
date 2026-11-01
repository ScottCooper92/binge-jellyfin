package com.cooper.binge.companion.jellyfin.ui.console

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cooper.binge.companion.jellyfin.server.Connection
import com.cooper.binge.companion.jellyfin.server.JellyfinClient
import com.cooper.binge.companion.jellyfin.server.PluginRepository
import com.cooper.binge.companion.jellyfin.server.repositories
import com.cooper.binge.companion.jellyfin.server.saveRepositories
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

sealed interface RepositoriesUiState {
    data object Loading : RepositoriesUiState

    data class Failed(
        val failure: ConsoleFailure,
    ) : RepositoriesUiState

    /**
     * The repositories. [adding] is the new repository sheet; [removing] the one asked about removing;
     * [saving] a change in flight; [actionFailed] the last change that didn't go through.
     */
    data class Ready(
        val repositories: List<PluginRepository>,
        val adding: Boolean = false,
        val removing: PluginRepository? = null,
        val saving: Boolean = false,
        val actionFailed: ConsoleFailure? = null,
    ) : RepositoriesUiState
}

/** [text] as an address a repository can be fetched from: a web address, http or https. Null if it isn't one. */
fun repositoryUrl(text: String): String? =
    text
        .trim()
        .toHttpUrlOrNull()
        ?.toString()

/**
 * The repositories the server fetches its plugin catalog from: add one, or remove one behind a confirm.
 * Admin only. A repository can serve code the server runs once the plugin is installed, so the sheet that
 * adds one says so. The server takes the whole list, so each change sends the list with that one change.
 */
class RepositoriesViewModel(
    private val connection: Connection,
) : ViewModel() {
    private val _state = MutableStateFlow<RepositoriesUiState>(RepositoriesUiState.Loading)
    val state: StateFlow<RepositoriesUiState> = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        _state.value = RepositoriesUiState.Loading
        viewModelScope.launch {
            _state.value =
                connection
                    .consoleCall(TAG) { _, client -> client.repositories() }
                    .fold(
                        onSuccess = { RepositoriesUiState.Ready(it) },
                        onFailure = { RepositoriesUiState.Failed(it.consoleFailure()) },
                    )
        }
    }

    fun askAdd(on: Boolean) = update { copy(adding = on, actionFailed = null) }

    fun askRemove(repository: PluginRepository?) = update { copy(removing = repository, actionFailed = null) }

    /** Adds a repository called [name] at [url]; an address that isn't a web address is refused before it is sent. */
    fun add(
        name: String,
        url: String,
    ) {
        val address = repositoryUrl(url) ?: return
        val label = name.trim().ifBlank { return }
        change(closing = { copy(adding = false) }) { it + PluginRepository(label, address, enabled = true) }
    }

    fun remove() {
        val target = (_state.value as? RepositoriesUiState.Ready)?.removing ?: return
        change(closing = { copy(removing = null) }) { list -> list.filterNot { it.url == target.url && it.name == target.name } }
    }

    private fun change(
        closing: RepositoriesUiState.Ready.() -> RepositoriesUiState.Ready,
        list: (List<PluginRepository>) -> List<PluginRepository>,
    ) {
        val ready = _state.value as? RepositoriesUiState.Ready ?: return
        if (ready.saving) return
        update { closing().copy(saving = true, actionFailed = null) }
        viewModelScope.launch {
            connection
                .consoleCall(TAG) { _, client -> saveAndRead(client, list(ready.repositories)) }
                .onSuccess { saved -> update { copy(repositories = saved, saving = false) } }
                .onFailure { error -> update { copy(saving = false, actionFailed = error.consoleFailure()) } }
        }
    }

    private suspend fun saveAndRead(
        client: JellyfinClient,
        repositories: List<PluginRepository>,
    ): List<PluginRepository> {
        client.saveRepositories(repositories)
        return client.repositories()
    }

    private fun update(change: RepositoriesUiState.Ready.() -> RepositoriesUiState.Ready) =
        _state.update { (it as? RepositoriesUiState.Ready)?.change() ?: it }

    private companion object {
        const val TAG = "JellyfinRepositories"
    }
}
