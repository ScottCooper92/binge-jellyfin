package com.cooper.binge.companion.jellyfin.ui.console

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cooper.binge.companion.jellyfin.server.Connection
import com.cooper.binge.companion.jellyfin.server.JellyfinHttpException
import com.cooper.binge.companion.jellyfin.server.changePassword
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** How a change went. */
enum class PasswordResult { Changed, WrongCurrent, Mismatch, Refused, Unreachable }

data class PasswordUiState(
    val current: String = "",
    val new: String = "",
    val confirm: String = "",
    val changing: Boolean = false,
    val result: PasswordResult? = null,
) {
    /** Change waits for the new password, twice. The current one may be empty: Jellyfin allows no password. */
    val complete: Boolean get() = new.isNotEmpty() && confirm.isNotEmpty()
}

/**
 * Changing the user's own password: the current one, the new one twice. The one console form with an
 * explicit submit, since a password is never saved a keystroke at a time.
 */
class PasswordViewModel(
    private val connection: Connection,
) : ViewModel() {
    private val _state = MutableStateFlow(PasswordUiState())
    val state: StateFlow<PasswordUiState> = _state.asStateFlow()

    fun onCurrent(value: String) = _state.update { it.copy(current = value, result = null) }

    fun onNew(value: String) = _state.update { it.copy(new = value, result = null) }

    fun onConfirm(value: String) = _state.update { it.copy(confirm = value, result = null) }

    fun change() {
        val form = _state.value
        if (!form.complete || form.changing) return
        if (form.new != form.confirm) {
            _state.value = form.copy(result = PasswordResult.Mismatch)
            return
        }
        _state.value = form.copy(changing = true, result = null)
        viewModelScope.launch {
            val result =
                connection
                    .consoleCall(TAG) { session, client -> client.changePassword(session.userId, form.current, form.new) }
                    .fold(onSuccess = { PasswordResult.Changed }, onFailure = { it.passwordResult() })
            _state.update {
                // A changed password leaves nothing in the form; a refused one keeps it to correct.
                if (result == PasswordResult.Changed) PasswordUiState(result = result) else it.copy(changing = false, result = result)
            }
        }
    }

    private companion object {
        const val TAG = "JellyfinPassword"
    }
}

private const val HTTP_FORBIDDEN = 403

private fun Throwable.passwordResult(): PasswordResult =
    when {
        this is JellyfinHttpException && code == HTTP_FORBIDDEN -> PasswordResult.WrongCurrent
        consoleFailure() == ConsoleFailure.Refused -> PasswordResult.Refused
        else -> PasswordResult.Unreachable
    }
