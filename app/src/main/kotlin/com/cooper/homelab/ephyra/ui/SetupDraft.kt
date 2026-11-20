package com.cooper.homelab.ephyra.ui

import androidx.lifecycle.SavedStateHandle
import com.cooper.homelab.core.presentation.connect.ConnectDraft
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * Where the user was on the address and sign-in steps when the process was killed: the address they typed, whether the sign-in
 * step was showing, and which tab it was on. Never an account name or a password: the form comes back empty.
 */
internal class SetupDraft(
    val address: String,
    val signingIn: Boolean,
    val quickConnect: Boolean = false,
)

/**
 * Keeps the [SetupDraft] in the view model's saved state, so a kill on the address step or the sign-in step reopens there. Saved
 * state can outlive the process on disk, so nothing here is kept once a session exists, and a Quick Connect wait is not kept at
 * all: its code is not here, so the sign-in step comes back on the Quick Connect tab, where Get a code is one tap away.
 */
internal class SetupDrafts(
    private val handle: SavedStateHandle,
) {
    /** The draft a killed process left, or null when there is none. */
    fun saved(): SetupDraft? {
        val address = handle.get<String>(ADDRESS)?.takeIf { it.isNotBlank() } ?: return null
        return SetupDraft(
            address,
            signingIn = handle.get<Boolean>(SIGNING_IN) == true,
            quickConnect = handle.get<Boolean>(QUICK_CONNECT) == true,
        )
    }

    /**
     * Puts [saved] back into [setup]: the address typed again, and the server read again when the sign-in step was showing, on the
     * tab it was on once the server has been read (and still offers Quick Connect).
     */
    fun restoreInto(setup: SetupFlow) {
        val draft = saved() ?: return
        setup.flow.editAddress(draft.address)
        if (draft.signingIn) {
            if (draft.quickConnect) setup.firstMode = SetupMode.QuickConnect
            setup.inspect()
        }
    }

    /** Writes each change of [draft] until the flow is cancelled; a null draft clears what was kept. */
    suspend fun keep(draft: Flow<ConnectDraft<*>?>) {
        draft
            .map { current ->
                current?.takeIf { it.address.isNotBlank() }?.let {
                    SetupDraft(
                        it.address,
                        signingIn = it.server != null,
                        quickConnect =
                            it.server != null && it.form.mode == SetupMode.QuickConnect,
                    )
                }
            }.distinctUntilChanged {
                a,
                b,
                ->
                a?.address == b?.address && a?.signingIn == b?.signingIn && a?.quickConnect == b?.quickConnect
            }.collect { write(it) }
    }

    private fun write(draft: SetupDraft?) {
        if (draft == null) {
            handle.remove<String>(ADDRESS)
            handle.remove<Boolean>(SIGNING_IN)
            handle.remove<Boolean>(QUICK_CONNECT)
        } else {
            handle[ADDRESS] = draft.address
            handle[SIGNING_IN] = draft.signingIn
            handle[QUICK_CONNECT] = draft.quickConnect
        }
    }

    private companion object {
        const val ADDRESS = "setup_address"
        const val SIGNING_IN = "setup_signing_in"
        const val QUICK_CONNECT = "setup_quick_connect"
    }
}
