package com.cooper.binge.companion.jellyfin.ui.console

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cooper.binge.companion.jellyfin.server.ApiKey
import com.cooper.binge.companion.jellyfin.server.Connection
import com.cooper.binge.companion.jellyfin.server.apiKeys
import com.cooper.binge.companion.jellyfin.server.createApiKey
import com.cooper.binge.companion.jellyfin.server.revokeApiKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface ApiKeysUiState {
    data object Loading : ApiKeysUiState

    data class Failed(
        val failure: ConsoleFailure,
    ) : ApiKeysUiState

    /**
     * The keys, newest first. [naming] is the new-key dialog open; [created] the key just made, shown once
     * and gone when dismissed; [removing] the key asked about revoking; [busy] a change in flight;
     * [actionFailed] the last change that didn't go through.
     */
    data class Ready(
        val keys: List<ApiKey>,
        val naming: Boolean = false,
        val created: ApiKey? = null,
        val removing: ApiKey? = null,
        val busy: Boolean = false,
        val actionFailed: ConsoleFailure? = null,
    ) : ApiKeysUiState
}

/**
 * The server's API keys: listed with the last four characters of each, made for an app name and shown in
 * full once, and revoked. Admin only. A key lives in this state while the page is open and nowhere else:
 * nothing here saves, logs or sends it anywhere but the user's own server.
 */
class ApiKeysViewModel(
    private val connection: Connection,
) : ViewModel() {
    private val _state = MutableStateFlow<ApiKeysUiState>(ApiKeysUiState.Loading)
    val state: StateFlow<ApiKeysUiState> = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        _state.value = ApiKeysUiState.Loading
        viewModelScope.launch {
            _state.value =
                connection
                    .consoleCall(TAG) { _, client -> client.apiKeys() }
                    .fold(
                        onSuccess = { ApiKeysUiState.Ready(it) },
                        onFailure = { ApiKeysUiState.Failed(it.consoleFailure()) },
                    )
        }
    }

    fun askCreate(on: Boolean) = update { copy(naming = on, actionFailed = null) }

    /** Makes a key for [app] (a blank name asks for nothing), then shows it, once. */
    fun create(app: String) {
        val name = app.trim()
        if (name.isEmpty()) return
        update { copy(naming = false, busy = true) }
        viewModelScope.launch {
            connection
                .consoleCall(TAG) { _, client -> client.createApiKey(name) }
                .onSuccess { (made, keys) -> update { copy(keys = keys, created = made, busy = false) } }
                .onFailure { error -> update { copy(busy = false, actionFailed = error.consoleFailure()) } }
        }
    }

    /** The new key was seen: it is dropped from memory, and can't be shown again. */
    fun dismissCreated() = update { copy(created = null) }

    fun askRemove(key: ApiKey?) = update { copy(removing = key, actionFailed = null) }

    fun remove() {
        val key = (_state.value as? ApiKeysUiState.Ready)?.removing ?: return
        update { copy(removing = null, busy = true) }
        viewModelScope.launch {
            connection
                .consoleCall(TAG) { _, client ->
                    client.revokeApiKey(key.key)
                    client.apiKeys()
                }.onSuccess { keys -> update { copy(keys = keys, busy = false) } }
                .onFailure { error -> update { copy(busy = false, actionFailed = error.consoleFailure()) } }
        }
    }

    private fun update(change: ApiKeysUiState.Ready.() -> ApiKeysUiState.Ready) =
        _state.update { (it as? ApiKeysUiState.Ready)?.change() ?: it }

    private companion object {
        const val TAG = "JellyfinApiKeys"
    }
}
