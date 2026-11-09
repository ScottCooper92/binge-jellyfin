package com.cooper.binge.companion.jellyfin.ui.console

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cooper.binge.companion.jellyfin.server.ChannelMappingOptions
import com.cooper.binge.companion.jellyfin.server.Connection
import com.cooper.binge.companion.jellyfin.server.TunerChannel
import com.cooper.binge.companion.jellyfin.server.channelMappingOptions
import com.cooper.binge.companion.jellyfin.server.setChannelMapping
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface ChannelMappingsUiState {
    data object Loading : ChannelMappingsUiState

    data class Failed(
        val failure: ConsoleFailure,
    ) : ChannelMappingsUiState

    /**
     * The channels and how they are paired, the provider channel whose tuner channels are being chosen ([selected]), what the
     * choice is narrowed to ([query]), and the last pairing the server refused ([refused]).
     */
    data class Ready(
        val options: ChannelMappingOptions,
        val selected: String? = null,
        val query: String = "",
        val refused: ConsoleFailure? = null,
    ) : ChannelMappingsUiState {
        /** The tuner channels whose name contains [query], in the order the server listed them. */
        val matching: List<TunerChannel> get() =
            options.tunerChannels.filter { query.isBlank() || it.name.contains(query.trim(), ignoreCase = true) }
    }
}

/**
 * Pairs a guide data provider's channels with the tuner's. A pairing is saved as it is made: the row shows it at once, and goes back to
 * what the server held if the server did not keep it. The server keeps the pairing on the tuner channel, one provider channel each.
 */
class ChannelMappingsViewModel(
    private val connection: Connection,
    private val providerId: String,
) : ViewModel() {
    private val _state = MutableStateFlow<ChannelMappingsUiState>(ChannelMappingsUiState.Loading)
    val state: StateFlow<ChannelMappingsUiState> = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        _state.value = ChannelMappingsUiState.Loading
        viewModelScope.launch {
            _state.value =
                connection
                    .consoleCall(TAG) { _, client -> client.channelMappingOptions(providerId) }
                    .fold(
                        onSuccess = { ChannelMappingsUiState.Ready(it) },
                        onFailure = { ChannelMappingsUiState.Failed(it.consoleFailure()) },
                    )
        }
    }

    /** Opens the choice of tuner channels for the provider channel [providerChannelId], or closes it for null. The search starts empty. */
    fun select(providerChannelId: String?) = update { copy(selected = providerChannelId, query = "") }

    fun search(query: String) = update { copy(query = query) }

    /** Pairs the tuner channel [tunerChannelId] with the selected provider channel and saves it. */
    fun pick(tunerChannelId: String) {
        val ready = _state.value as? ChannelMappingsUiState.Ready ?: return
        val providerChannelId = ready.selected ?: return
        val before = ready.options.tunerChannels.firstOrNull { it.id == tunerChannelId } ?: return
        if (before.providerChannelId == providerChannelId) return
        update { copy(options = options.paired(tunerChannelId, providerChannelId), refused = null) }
        viewModelScope.launch {
            connection
                .consoleCall(TAG) { _, client -> client.setChannelMapping(providerId, tunerChannelId, providerChannelId) }
                .onFailure { error ->
                    // Back to what the server held, for this tuner channel only: another pairing may have been made since.
                    update { copy(options = revert(options, before), refused = error.consoleFailure()) }
                }
        }
    }

    private fun revert(
        options: ChannelMappingOptions,
        before: TunerChannel,
    ): ChannelMappingOptions = options.copy(tunerChannels = options.tunerChannels.map { if (it.id == before.id) before else it })

    private fun update(change: ChannelMappingsUiState.Ready.() -> ChannelMappingsUiState.Ready) =
        _state.update { (it as? ChannelMappingsUiState.Ready)?.change() ?: it }

    private companion object {
        const val TAG = "JellyfinChannelMappings"
    }
}
