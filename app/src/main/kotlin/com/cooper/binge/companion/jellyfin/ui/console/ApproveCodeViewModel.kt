package com.cooper.binge.companion.jellyfin.ui.console

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cooper.binge.companion.jellyfin.server.Connection
import com.cooper.binge.companion.jellyfin.server.JellyfinHttpException
import com.cooper.binge.companion.jellyfin.server.authorizeQuickConnect
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.IOException

/** How an approval went. */
enum class ApproveResult { Approved, WrongCode, QuickConnectOff, Unreachable, Failed }

data class ApproveUiState(
    val code: String = "",
    val approving: Boolean = false,
    val result: ApproveResult? = null,
) {
    /** Jellyfin's codes are six digits; Approve waits for all six. */
    val complete: Boolean get() = code.length == CODE_LENGTH
}

internal const val CODE_LENGTH = 6

/**
 * Approving a Quick Connect code another device shows (a TV, a browser), which signs that device in
 * as the user. For everyone: Jellyfin only asks that the caller is signed in.
 */
class ApproveCodeViewModel(
    private val connection: Connection,
) : ViewModel() {
    private val _state = MutableStateFlow(ApproveUiState())
    val state: StateFlow<ApproveUiState> = _state.asStateFlow()

    /** Keeps digits only, at most six; a new code clears the last result. */
    fun onCode(value: String) = _state.update { it.copy(code = value.filter(Char::isDigit).take(CODE_LENGTH), result = null) }

    fun approve() {
        val current = _state.value
        if (!current.complete || current.approving) return
        _state.value = current.copy(approving = true, result = null)
        viewModelScope.launch {
            val result =
                try {
                    val client = connection.signedIn()?.second
                    if (client == null) {
                        ApproveResult.Unreachable
                    } else {
                        client.authorizeQuickConnect(current.code)
                        ApproveResult.Approved
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: JellyfinHttpException) {
                    Log.i(TAG, "Approval refused", e)
                    when (e.code) {
                        HTTP_NOT_FOUND -> ApproveResult.WrongCode
                        HTTP_UNAUTHORIZED -> ApproveResult.QuickConnectOff
                        else -> ApproveResult.Failed
                    }
                } catch (e: IOException) {
                    Log.i(TAG, "Approval failed", e)
                    ApproveResult.Unreachable
                }
            // An approved code is spent, so the field empties for the next one.
            _state.update {
                it.copy(approving = false, result = result, code = if (result == ApproveResult.Approved) "" else it.code)
            }
        }
    }

    private companion object {
        const val TAG = "JellyfinApprove"
        const val HTTP_UNAUTHORIZED = 401
        const val HTTP_NOT_FOUND = 404
    }
}
