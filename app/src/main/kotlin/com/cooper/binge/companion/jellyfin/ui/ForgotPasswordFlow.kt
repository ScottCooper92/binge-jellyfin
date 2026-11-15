package com.cooper.binge.companion.jellyfin.ui

import android.util.Log
import com.binge.companion.sdk.attempt
import com.cooper.binge.companion.jellyfin.server.Connection
import com.cooper.binge.companion.jellyfin.server.ForgotPasswordAction
import com.cooper.binge.companion.jellyfin.server.JellyfinClient
import com.cooper.binge.companion.jellyfin.server.forgotPassword
import com.cooper.binge.companion.jellyfin.server.redeemPasswordPin
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Where a password reset is, on the sign-in step. Null on the step is no reset. */
sealed interface ForgotPasswordStep {
    /** The server is being asked. */
    data object Asking : ForgotPasswordStep

    /** The server will not reset it here: [action] is [ForgotPasswordAction.ContactAdmin] or [ForgotPasswordAction.InNetworkRequired]. */
    data class Told(
        val action: ForgotPasswordAction,
    ) : ForgotPasswordStep

    /** The server wrote a PIN to [pinFile]; the user types it. */
    data class EnterPin(
        val pinFile: String?,
        val busy: Boolean = false,
        val wrongPin: Boolean = false,
    ) : ForgotPasswordStep

    /** The server did not answer, or answered with something that is not Jellyfin. */
    data object Failed : ForgotPasswordStep

    /** The PIN was right: the account has no password now. */
    data object Done : ForgotPasswordStep
}

/**
 * The forgotten-password steps of the sign-in step. Only the user's name goes to the server, and the PIN the user read
 * from the server's own file; nothing here touches the access token, which does not exist yet.
 */
class ForgotPasswordFlow(
    private val scope: CoroutineScope,
    private val connection: Connection,
    private val state: MutableStateFlow<UiState>,
) {
    /** Asks the server to start a reset for the typed username. One at a time. */
    fun start() {
        val current = state.value as? UiState.SignIn ?: return
        if (current.forgot != null || current.username.isBlank()) return
        val base = current.baseUrl
        val name = current.username.trim()
        state.value = current.copy(forgot = ForgotPasswordStep.Asking)
        scope.launch {
            val answer = attempt { anonymous(base).forgotPassword(name) }
            answer.onFailure { Log.i(TAG, "Forgot password was not answered", it) }
            move(
                answer.fold(
                    onSuccess = {
                        if (it.action ==
                            ForgotPasswordAction.PinCode
                        ) {
                            ForgotPasswordStep.EnterPin(it.pinFile)
                        } else {
                            ForgotPasswordStep.Told(it.action)
                        }
                    },
                    onFailure = { ForgotPasswordStep.Failed },
                ),
            )
        }
    }

    /** Sends the PIN the user read from the file. A wrong or expired one keeps the sheet open, with its message. */
    fun submitPin(pin: String) {
        val current = state.value as? UiState.SignIn ?: return
        val step = current.forgot as? ForgotPasswordStep.EnterPin ?: return
        if (step.busy || pin.isBlank()) return
        state.value = current.copy(forgot = step.copy(busy = true, wrongPin = false))
        scope.launch {
            val redeemed = attempt { anonymous(current.baseUrl).redeemPasswordPin(pin.trim()) }
            redeemed.onFailure { Log.i(TAG, "The password PIN was not answered", it) }
            state.update {
                val signIn = it as? UiState.SignIn ?: return@update it
                when {
                    redeemed.isFailure -> signIn.copy(forgot = ForgotPasswordStep.Failed)
                    redeemed.getOrNull() == true -> signIn.copy(forgot = ForgotPasswordStep.Done, password = "", passwordError = null)
                    else -> signIn.copy(forgot = step.copy(busy = false, wrongPin = true))
                }
            }
        }
    }

    /** Closes whatever step is showing. */
    fun dismiss() = move(null)

    private suspend fun anonymous(baseUrl: String): JellyfinClient = connection.anonymous(JellyfinClient.parseBaseUrl(baseUrl) ?: error("bad base"))

    private fun move(step: ForgotPasswordStep?) = state.update { (it as? UiState.SignIn)?.copy(forgot = step) ?: it }

    private companion object {
        const val TAG = "JellyfinSetup"
    }
}
