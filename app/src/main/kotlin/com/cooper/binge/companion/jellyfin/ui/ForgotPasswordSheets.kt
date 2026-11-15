package com.cooper.binge.companion.jellyfin.ui

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import com.binge.designsystem.component.TextEntrySurface
import com.cooper.binge.companion.jellyfin.R
import com.cooper.binge.companion.jellyfin.server.ForgotPasswordAction
import com.cooper.binge.companion.jellyfin.ui.console.ConsoleMessage
import com.cooper.binge.companion.jellyfin.ui.console.ConsoleSheet

/**
 * What a forgotten password has reached: a message for a cause and its next step (the server won't reset it here, did not answer, or
 * has removed the password), or the sheet that takes the PIN from the file the server wrote. Nothing is shown for no step, or while the
 * server is being asked, which the button's own spinner says. With [inWindow] false it draws in place, for a frame.
 */
@Composable
internal fun ForgotPasswordOverlay(
    step: ForgotPasswordStep?,
    flow: ForgotPasswordFlow?,
    inWindow: Boolean = true,
) {
    val dismiss = { flow?.dismiss() ?: Unit }
    when (step) {
        null, ForgotPasswordStep.Asking -> Unit
        is ForgotPasswordStep.Told ->
            ConsoleMessage(
                title =
                    stringResource(
                        if (step.action ==
                            ForgotPasswordAction.InNetworkRequired
                        ) {
                            R.string.forgot_network_title
                        } else {
                            R.string.forgot_admin_title
                        },
                    ),
                message =
                    stringResource(
                        if (step.action ==
                            ForgotPasswordAction.InNetworkRequired
                        ) {
                            R.string.forgot_network_body
                        } else {
                            R.string.forgot_admin_body
                        },
                    ),
                onDismiss = dismiss,
                inWindow = inWindow,
            )
        ForgotPasswordStep.Failed ->
            ConsoleMessage(
                title = stringResource(R.string.forgot_failed_title),
                message = stringResource(R.string.forgot_failed_body),
                onDismiss = dismiss,
                inWindow = inWindow,
            )
        ForgotPasswordStep.Done ->
            ConsoleMessage(
                title = stringResource(R.string.forgot_done_title),
                message = stringResource(R.string.forgot_done_body),
                onDismiss = dismiss,
                inWindow = inWindow,
            )
        is ForgotPasswordStep.EnterPin -> ConsoleSheet(onDismissRequest = dismiss, inWindow = inWindow) { PinForm(step, flow) }
    }
}

/** The PIN, typed in the design system's text-entry surface, with where the server wrote it. */
@Composable
internal fun PinForm(
    step: ForgotPasswordStep.EnterPin,
    flow: ForgotPasswordFlow?,
) {
    var pin by rememberSaveable { mutableStateOf("") }
    TextEntrySurface(
        title = stringResource(R.string.forgot_pin_title),
        value = pin,
        onValueChange = { pin = it },
        onSubmit = { flow?.submitPin(pin) },
        onCancel = { flow?.dismiss() },
        submitLabel = stringResource(R.string.forgot_pin_submit),
        hint = stringResource(R.string.forgot_pin_hint),
        isSubmitting = step.busy,
        error = stringResource(R.string.forgot_pin_wrong).takeIf { step.wrongPin },
        minLines = 1,
        maxLines = 1,
        keyboardOptions =
            KeyboardOptions(
                keyboardType = KeyboardType.Ascii,
                capitalization = KeyboardCapitalization.Characters,
                autoCorrectEnabled = false,
            ),
        header = { PinFileLine(step.pinFile) },
    )
}

@Composable
private fun ColumnScope.PinFileLine(pinFile: String?) {
    Text(
        text =
            if (pinFile == null) {
                stringResource(R.string.forgot_pin_where_unknown)
            } else {
                stringResource(R.string.forgot_pin_where, pinFile)
            },
        style = MaterialTheme.typography.bodyMedium,
    )
}
