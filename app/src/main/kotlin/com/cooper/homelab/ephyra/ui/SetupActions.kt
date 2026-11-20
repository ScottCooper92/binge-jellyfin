package com.cooper.homelab.ephyra.ui

import com.cooper.homelab.ephyra.server.DiscoveredServer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** What the user does on the address and sign-in steps, as calls on the connect flow. */
internal class SetupActions(
    private val setup: SetupFlow,
    private val state: MutableStateFlow<UiState.Setup>,
    private val scope: CoroutineScope,
    private val signedIn: () -> UiState.SignedIn?,
    private val discover: suspend () -> List<DiscoveredServer>,
) {
    private val flow get() = setup.flow

    fun onAddress(value: String) = flow.editAddress(value)

    fun allowCleartext(allowed: Boolean) = flow.allowCleartext(allowed)

    fun onUsername(value: String) = editForm(USERNAME_FIELD, value)

    fun onPassword(value: String) = editForm(PASSWORD_FIELD, value)

    private fun editForm(
        field: String,
        value: String,
    ) {
        if (flow.draft.value.server == null) return
        flow.editForm { with(field, value) }
    }

    /** Looks for servers on the local network once per visit to the server step. A server is one tap away; nothing found shows nothing. */
    fun discoverServers() {
        val current = state.value as? UiState.EnterServer ?: return
        if (current.discovering) return
        state.value = current.copy(discovering = true)
        scope.launch {
            val found = discover()
            state.update { (it as? UiState.EnterServer)?.copy(discovered = found, discovering = false) ?: it }
        }
    }

    fun connect() = setup.inspect()

    fun signInWithPassword() = flow.connect()

    fun tryDemo() = flow.tryDemo()

    fun localNetworkResult() = flow.localNetworkResult()

    /** Shows a code, then polls until the user approves it in a signed-in Jellyfin app, as Seerr's Plex PIN flow does. */
    fun startQuickConnect() {
        val draft = flow.draft.value
        if (draft.server == null) return
        setup.links.cancel()
        // What was typed for the password comes back if the user leaves Quick Connect for the form.
        if (draft.form.mode != SetupMode.QuickConnect) setup.typed = draft.form.values
        flow.selectMode(SetupMode.QuickConnect)
        setup.links.start()
    }

    /** Leaves Quick Connect for the password form, taking Quick Connect's code and failure with it. */
    fun cancelQuickConnect() {
        setup.links.cancel()
        if (flow.draft.value.form.mode != SetupMode.QuickConnect) return
        flow.selectMode(SetupMode.Password)
        flow.editForm { copy(values = setup.typed) }
    }

    /**
     * The server step again, with the address filled in: from the sign-in step, or from a saved server that cannot be
     * reached. A saved session stays saved until a new sign-in replaces it, so closing the app comes back to the gate.
     * With [recheck], from the sign-in step, the address is read again at once: the notice for a server that was not set up
     * says to finish that elsewhere, and this finds out whether it has been.
     */
    fun changeServer(recheck: Boolean) {
        setup.links.cancel()
        setup.typed = emptyMap()
        val saved = signedIn()
        val address =
            saved?.session?.baseUrl
                ?: (state.value as? UiState.Startup)?.baseUrl
                ?: flow.draft.value.server
                    ?.address
                    ?.url
                    ?.trimEnd('/')
                ?: ""
        state.value = UiState.Loading
        if (saved != null) {
            // The saved server is edited: it stays saved until a new sign-in replaces it.
            setup.leaveSavedSession()
            flow.beginEdit(address)
        } else {
            flow.editAddress(address)
            flow.changeServer()
        }
        // The flow may already have been at this step, in which case it has nothing new to say.
        setup.renderNow()
        if (recheck && address.isNotBlank()) connect()
    }
}
