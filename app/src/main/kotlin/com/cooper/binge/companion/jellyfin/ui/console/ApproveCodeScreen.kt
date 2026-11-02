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
import androidx.compose.ui.text.input.KeyboardType
import com.binge.designsystem.component.BingeFilledButton
import com.binge.designsystem.template.BingeScreenScaffold
import com.binge.designsystem.template.ScreenBar
import com.binge.designsystem.template.ScreenBody
import com.binge.designsystem.template.screenListPadding
import com.cooper.binge.companion.jellyfin.R
import com.binge.designsystem.R as DesR

/** What the approval page can ask of its view model, and of the console. */
data class ApproveActions(
    val onCode: (String) -> Unit = {},
    val onApprove: () -> Unit = {},
    val onBack: () -> Unit = {},
)

/** Lines the result takes, so a longer message never moves the button. */
private const val RESULT_LINES = 2

/**
 * Approving a Quick Connect code: the six digits another device shows, and Approve. The result says
 * what happened in place, under the field.
 */
@Composable
fun ApproveCodeScreen(
    state: ApproveUiState,
    actions: ApproveActions,
) {
    BingeScreenScaffold(title = stringResource(R.string.approve_title), bar = ScreenBar.Small, onBack = actions.onBack) { padding ->
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
                Text(stringResource(R.string.approve_body), style = MaterialTheme.typography.bodyMedium)
                ConsoleTextField(
                    value = state.code,
                    onValueChange = actions.onCode,
                    label = stringResource(R.string.approve_field),
                    isError = state.result != null && state.result != ApproveResult.Approved,
                    keyboardType = KeyboardType.NumberPassword,
                    onDone = actions.onApprove,
                )
                Text(
                    text = state.result?.let { resultText(it) }.orEmpty(),
                    style = MaterialTheme.typography.bodyMedium,
                    color =
                        if (state.result == ApproveResult.Approved) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                    minLines = RESULT_LINES,
                    maxLines = RESULT_LINES,
                )
                BingeFilledButton(
                    label = stringResource(R.string.approve_action),
                    onClick = actions.onApprove,
                    loading = state.approving,
                    enabled = state.complete,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun resultText(result: ApproveResult): String =
    stringResource(
        when (result) {
            ApproveResult.Approved -> R.string.approve_done
            ApproveResult.WrongCode -> R.string.approve_wrong
            ApproveResult.QuickConnectOff -> R.string.error_quick_connect_off
            ApproveResult.Unreachable -> R.string.error_unreachable
            ApproveResult.Failed -> R.string.error_generic
        },
    )
