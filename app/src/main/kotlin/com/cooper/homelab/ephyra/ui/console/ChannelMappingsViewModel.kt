package com.cooper.homelab.ephyra.ui.console

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cooper.homelab.ephyra.server.ChannelMappingOptions
import com.cooper.homelab.ephyra.server.Connection
import com.cooper.homelab.ephyra.server.TunerChannel
import com.cooper.homelab.ephyra.server.channelMappingOptions
import com.cooper.homelab.ephyra.server.setChannelMapping
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
     * The channels and how they are paired, the provider channel whose tuner channels are being chosen ([selected]), and the last pairing the server refused ([refused]).
     */
    data class Ready(
        val options: ChannelMappingOptions,
        val selected: String? = null,
        val refused: ConsoleFailure? = null,
    ) : ChannelMappingsUiState
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

    /** Opens the choice of tuner channels for the provider channel [providerChannelId], or closes it for null. */
    fun select(providerChannelId: String?) = update { copy(selected = providerChannelId) }

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
