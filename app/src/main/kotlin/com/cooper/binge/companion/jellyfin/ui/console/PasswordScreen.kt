package com.cooper.binge.companion.jellyfin.ui.console

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import com.binge.designsystem.component.BingeFilledButton
import com.binge.designsystem.template.BingeScreenScaffold
import com.binge.designsystem.template.ScreenBar
import com.binge.designsystem.template.ScreenBody
import com.binge.designsystem.template.screenListPadding
import com.cooper.binge.companion.jellyfin.R
import com.binge.designsystem.R as DesR

/** What the password page can ask of its view model, and of the console. */
data class PasswordActions(
    val onCurrent: (String) -> Unit = {},
    val onNew: (String) -> Unit = {},
    val onConfirm: (String) -> Unit = {},
    val onChange: () -> Unit = {},
    val onBack: () -> Unit = {},
)

/** Lines the result takes, so a longer message never moves the button. */
private const val RESULT_LINES = 2

/**
 * Changing the user's password: the current one, the new one twice, and Change. It says up front
 * that other devices are signed out, and the result says what happened in place, over the button.
 */
@Composable
fun PasswordScreen(
    state: PasswordUiState,
    actions: PasswordActions,
) {
    val focus = LocalFocusManager.current
    BingeScreenScaffold(title = stringResource(R.string.password_title), bar = ScreenBar.Small, onBack = actions.onBack) { padding ->
        ScreenBody(padding) { _ ->
            Column(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(padding.screenListPadding())
                        .imePadding(),
                verticalArrangement = Arrangement.spacedBy(dimensionResource(DesR.dimen.padding_l)),
            ) {
                Text(stringResource(R.string.password_body), style = MaterialTheme.typography.bodyMedium)
                val next: () -> Unit = { focus.moveFocus(FocusDirection.Down) }
                PasswordField(
                    state.current,
                    actions.onCurrent,
                    R.string.password_current,
                    state.result == PasswordResult.WrongCurrent,
                    next,
                )
                PasswordField(state.new, actions.onNew, R.string.password_new, isError = false, onIme = next)
                PasswordField(
                    state.confirm,
                    actions.onConfirm,
                    R.string.password_confirm,
                    state.result == PasswordResult.Mismatch,
                    actions.onChange,
                )
                Text(
                    text = state.result?.let { stringResource(it.message) }.orEmpty(),
                    style = MaterialTheme.typography.bodyMedium,
                    color =
                        if (state.result == PasswordResult.Changed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                    minLines = RESULT_LINES,
                    maxLines = RESULT_LINES,
                )
                BingeFilledButton(
                    label = stringResource(R.string.password_action),
                    onClick = actions.onChange,
                    loading = state.changing,
                    enabled = state.complete,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun PasswordField(
    value: String,
    onValueChange: (String) -> Unit,
    label: Int,
    isError: Boolean,
    onIme: () -> Unit,
) {
    // The confirmation is the last field: its key submits.
    val last = label == R.string.password_confirm
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(stringResource(label)) },
        singleLine = true,
        isError = isError,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = if (last) ImeAction.Done else ImeAction.Next),
        keyboardActions = KeyboardActions(onNext = { onIme() }, onDone = { onIme() }),
        modifier = Modifier.fillMaxWidth(),
    )
}

private val PasswordResult.message: Int
    get() =
        when (this) {
            PasswordResult.Changed -> R.string.password_changed
            PasswordResult.WrongCurrent -> R.string.password_wrong
            PasswordResult.Mismatch -> R.string.password_mismatch
            PasswordResult.Refused -> R.string.error_generic
            PasswordResult.Unreachable -> R.string.error_unreachable
        }
