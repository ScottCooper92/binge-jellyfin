package com.cooper.homelab.ephyra.ui

import com.binge.companion.sdk.attempt
import com.cooper.homelab.core.server.network.LocalNetworkPermission
import com.cooper.homelab.ephyra.server.Connection
import com.cooper.homelab.ephyra.server.JellyfinClient
import com.cooper.homelab.ephyra.server.JellyfinHttpException
import com.cooper.homelab.ephyra.server.noted
import com.cooper.homelab.ephyra.server.saveStartupUser
import com.cooper.homelab.ephyra.server.startupFirstUserName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import okhttp3.HttpUrl

/**
 * The setup wizard's account step. The password is sent once, to the server the user chose, over the anonymous client (the route is
 * anonymous until setup is complete); it is held only in [StartupPhase.Account] until then, and goes on to [StartupPhase.Finish] only for the sign-in that ends setup.
 */
class StartupAccountStep internal constructor(
    private val connection: Connection,
    private val state: MutableStateFlow<UiState>,
    private val scope: CoroutineScope,
    private val localNetwork: LocalNetworkPermission,
) {
    /** Fills the name the server suggests, unless the user has already typed one. */
    internal fun suggestName(url: HttpUrl) {
        scope.launch {
            val suggested = attempt { connection.anonymous(url).startupFirstUserName() }.getOrNull() ?: return@launch
            edit { if (name.isBlank()) copy(name = suggested) else this }
        }
    }

    fun onName(name: String) = edit { copy(name = name, error = null, reason = null) }

    fun onPassword(password: String) = edit { copy(password = password, error = null, reason = null) }

    fun onConfirmation(confirmation: String) = edit { copy(confirmation = confirmation, error = null, reason = null) }

    /** Makes the account. Nothing is sent while the name is blank, the password is empty, or the two passwords differ. */
    fun save() {
        val wizard = state.value as? UiState.Startup ?: return
        val step = wizard.phase as? StartupPhase.Account ?: return
        if (!step.canSave) return
        val url = JellyfinClient.parseBaseUrl(wizard.baseUrl) ?: return
        edit { copy(busy = true, error = null, reason = null) }
        scope.launch {
            val saved = attempt { connection.anonymous(url).saveStartupUser(step.name.trim(), step.password) }
            // The exception is logged by its type and status alone: a message could carry what was sent.
            saved.exceptionOrNull()?.let { connection.logger.noted(TAG, "setup account failed", it) }
            state.update { current ->
                val s = current as? UiState.Startup ?: return@update current
                if (saved.isSuccess) {
                    s.copy(phase = StartupPhase.Finish(step.name.trim(), step.password))
                } else {
                    val refusal = saved.exceptionOrNull() as? JellyfinHttpException
                    val error = if (refusal != null) SetupError.Generic else unreachableSetup(s.baseUrl, localNetwork)
                    val account = s.phase as? StartupPhase.Account
                    s.copy(phase = account?.copy(busy = false, error = error, reason = refusal?.reason) ?: s.phase)
                }
            }
        }
    }

    private fun edit(change: StartupPhase.Account.() -> StartupPhase.Account) =
        state.update { current ->
            val s = current as? UiState.Startup ?: return@update current
            s.copy(phase = (s.phase as? StartupPhase.Account)?.change() ?: s.phase)
        }

    private companion object {
        const val TAG = "JellyfinStartup"
    }
}
