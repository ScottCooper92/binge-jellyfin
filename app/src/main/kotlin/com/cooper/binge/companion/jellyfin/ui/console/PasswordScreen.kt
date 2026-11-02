package com.cooper.binge.companion.jellyfin.ui.console

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
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
                ConsoleTextField(
                    value = state.current,
                    onValueChange = actions.onCurrent,
                    label = stringResource(R.string.password_current),
                    isError = state.result == PasswordResult.WrongCurrent,
                    secret = true,
                    imeAction = ImeAction.Next,
                )
                ConsoleTextField(
                    value = state.new,
                    onValueChange = actions.onNew,
                    label = stringResource(R.string.password_new),
                    secret = true,
                    imeAction = ImeAction.Next,
                )
                ConsoleTextField(
                    value = state.confirm,
                    onValueChange = actions.onConfirm,
                    label = stringResource(R.string.password_confirm),
                    isError = state.result == PasswordResult.Mismatch,
                    secret = true,
                    onDone = actions.onChange,
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

private val PasswordResult.message: Int
    get() =
        when (this) {
            PasswordResult.Changed -> R.string.password_changed
            PasswordResult.WrongCurrent -> R.string.password_wrong
            PasswordResult.Mismatch -> R.string.password_mismatch
            PasswordResult.Refused -> R.string.error_generic
            PasswordResult.Unreachable -> R.string.error_unreachable
        }
