package com.cooper.binge.companion.jellyfin.ui

import android.util.Log
import com.binge.companion.sdk.attempt
import com.cooper.binge.companion.jellyfin.server.Connection
import com.cooper.binge.companion.jellyfin.server.JellyfinClient
import com.cooper.binge.companion.jellyfin.server.JellyfinHttpException
import com.cooper.binge.companion.jellyfin.server.LocalNetworkPermission
import com.cooper.binge.companion.jellyfin.server.PublicServerInfo
import com.cooper.binge.companion.jellyfin.server.SignedIn
import com.cooper.binge.companion.jellyfin.server.authenticateByName
import com.cooper.binge.companion.jellyfin.server.completeStartup
import com.cooper.binge.companion.jellyfin.server.saveStartupRemoteAccess
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import okhttp3.HttpUrl

/**
 * The setup wizard's last step. Finish setup saves the remote-access choices, completes the server (which makes the Startup routes
 * administrator-only, so it is last), and signs in as the account the wizard made, through the app's one sign-in path: the token is
 * minted and saved by [signIn] as for any sign-in. The account's password is used once, here, and the step that held it is replaced.
 */
class StartupFinishStep internal constructor(
    private val connection: Connection,
    private val state: MutableStateFlow<UiState>,
    private val scope: CoroutineScope,
    private val localNetwork: LocalNetworkPermission,
    private val signIn: suspend (HttpUrl, PublicServerInfo, SignedIn) -> Unit,
) {
    fun onRemoteAccess(on: Boolean) = edit { copy(remoteAccess = on, portMapping = portMapping && on, error = null) }

    fun onPortMapping(on: Boolean) = edit { copy(portMapping = on && remoteAccess, error = null) }

    /** Saves remote access and completes the server, then signs in. Both calls can be made again if the first try stopped part way; a 401 from either means the server is already complete. */
    fun finish() {
        val wizard = state.value as? UiState.Startup ?: return
        val step = wizard.phase as? StartupPhase.Finish ?: return
        if (step.busy) return
        val url = JellyfinClient.parseBaseUrl(wizard.baseUrl) ?: return
        edit { copy(busy = true, error = null) }
        scope.launch {
            val client = connection.anonymous(url)
            val completed =
                attempt {
                    client.saveStartupRemoteAccess(step.remoteAccess, step.remoteAccess && step.portMapping)
                    client.completeStartup()
                }
            val failure = completed.exceptionOrNull()
            // Logged by type alone: nothing sent here belongs in a log.
            failure?.let { Log.i(TAG, "Setup could not be completed: ${it.javaClass.simpleName}") }
            // These routes are anonymous until Complete, so a 401 means the server is already complete: an earlier Complete took
            // effect and its reply was lost. Go on to the sign-in, as after a normal Complete.
            if (failure != null && !failure.isAlreadyComplete()) {
                val error = if (failure is JellyfinHttpException) SetupError.Generic else unreachableSetup(wizard.baseUrl, localNetwork)
                edit { copy(busy = false, error = error) }
                return@launch
            }
            signInAsNewAccount(url, wizard, step)
        }
    }

    /**
     * The server is set up now. Sign in as the account; if that fails, the user signs in by hand on the normal step, with the name
     * filled in, and the password is gone either way.
     */
    private suspend fun signInAsNewAccount(
        url: HttpUrl,
        wizard: UiState.Startup,
        step: StartupPhase.Finish,
    ) {
        val finished = wizard.server.copy(startupWizardCompleted = true)
        val signedIn = attempt { connection.anonymous(url).authenticateByName(step.name, step.password) }
        signedIn.exceptionOrNull()?.let { Log.i(TAG, "Sign-in after setup failed: ${it.javaClass.simpleName}") }
        signedIn.getOrNull()?.let { signIn(url, finished, it) } ?: state.update { current ->
            if (current !is UiState.Startup) return@update current
            UiState.SignIn(baseUrl = wizard.baseUrl, server = finished, username = step.name)
        }
        // The step is built anew, so Quick Connect's state is read again, as on the step reached from the server step.
        scope.loadQuickConnectOn(connection, state, url, wizard.baseUrl) {}
    }

    private fun Throwable.isAlreadyComplete() = this is JellyfinHttpException && code == HTTP_UNAUTHORIZED

    private fun edit(change: StartupPhase.Finish.() -> StartupPhase.Finish) =
        state.update { current ->
            val s = current as? UiState.Startup ?: return@update current
            s.copy(phase = (s.phase as? StartupPhase.Finish)?.change() ?: s.phase)
        }

    private companion object {
        const val TAG = "JellyfinStartup"
        const val HTTP_UNAUTHORIZED = 401
    }
}
