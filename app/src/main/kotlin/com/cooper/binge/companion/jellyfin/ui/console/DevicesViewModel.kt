package com.cooper.binge.companion.jellyfin.ui.console

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cooper.binge.companion.jellyfin.server.Connection
import com.cooper.binge.companion.jellyfin.server.JellyfinClient
import com.cooper.binge.companion.jellyfin.server.ServerDevice
import com.cooper.binge.companion.jellyfin.server.devices
import com.cooper.binge.companion.jellyfin.server.removeDevice
import com.cooper.binge.companion.jellyfin.server.renameDevice
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface DevicesUiState {
    data object Loading : DevicesUiState

    data class Failed(
        val failure: ConsoleFailure,
    ) : DevicesUiState

    /**
     * The devices, most recently seen first, and [thisDevice], the id this app signs in under. [renaming]
     * and [removing] are the device being renamed or asked about removing; [busy] those with a change in
     * flight; [actionFailed] the last change that didn't go through.
     */
    data class Ready(
        val devices: List<ServerDevice>,
        val thisDevice: String,
        val renaming: ServerDevice? = null,
        val removing: ServerDevice? = null,
        val busy: Set<String> = emptySet(),
        val actionFailed: ConsoleFailure? = null,
    ) : DevicesUiState
}

/**
 * The devices signed in to the server, renamed or removed. Admin only. This app's own device can't be
 * removed from here: that would sign this app out under the admin's feet, so its row says why instead.
 */
class DevicesViewModel(
    private val connection: Connection,
) : ViewModel() {
    private val _state = MutableStateFlow<DevicesUiState>(DevicesUiState.Loading)
    val state: StateFlow<DevicesUiState> = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        _state.value = DevicesUiState.Loading
        viewModelScope.launch {
            val thisDevice = connection.identity().deviceId
            _state.value =
                connection
                    .consoleCall(TAG) { _, client -> client.devices() }
                    .fold(
                        onSuccess = { DevicesUiState.Ready(it, thisDevice) },
                        onFailure = { DevicesUiState.Failed(it.consoleFailure()) },
                    )
        }
    }

    fun askRename(device: ServerDevice?) = update { copy(renaming = device, actionFailed = null) }

    fun askRemove(device: ServerDevice?) = update { copy(removing = device?.takeUnless { it.id == thisDevice }, actionFailed = null) }

    /** Saves [name] as the device's name; blank goes back to the name the device gave. */
    fun rename(name: String) {
        val device = (_state.value as? DevicesUiState.Ready)?.renaming ?: return
        val trimmed = name.trim()
        update { copy(renaming = null, busy = busy + device.id) }
        change(device) { client -> client.renameDevice(device.id, trimmed) }
    }

    fun remove() {
        val ready = _state.value as? DevicesUiState.Ready ?: return
        val device = ready.removing?.takeUnless { it.id == ready.thisDevice } ?: return
        update { copy(removing = null, busy = busy + device.id) }
        change(device) { client -> client.removeDevice(device.id) }
    }

    /** Runs a change, then reads the list again, so the row shows what the server now holds. */
    private fun change(
        device: ServerDevice,
        call: suspend (JellyfinClient) -> Unit,
    ) {
        viewModelScope.launch {
            connection
                .consoleCall(TAG) { _, client ->
                    call(client)
                    client.devices()
                }.onSuccess { devices -> update { copy(devices = devices, busy = busy - device.id) } }
                .onFailure { error -> update { copy(busy = busy - device.id, actionFailed = error.consoleFailure()) } }
        }
    }

    private fun update(change: DevicesUiState.Ready.() -> DevicesUiState.Ready) =
        _state.update { (it as? DevicesUiState.Ready)?.change() ?: it }

    private companion object {
        const val TAG = "JellyfinDevices"
    }
}
