package com.cooper.binge.companion.jellyfin.ui.console

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.binge.designsystem.component.BingeConfirmDialog
import com.binge.designsystem.component.BingeConfirmDialogContent
import com.binge.designsystem.component.BingeMessageDialog
import com.binge.designsystem.component.BingeMessageDialogContent
import com.binge.designsystem.R as DesR

/**
 * A console page's confirm dialog. Anything that takes input is a sheet
 * ([ConsoleSheet]). On screen it's a [BingeConfirmDialog]. With [inWindow]
 * false it draws [BingeConfirmDialogContent], the same body without the dialog window, which a screen
 * frame can't capture, so a `@PreviewTest` can show it.
 */
@Composable
internal fun ConsoleConfirm(
    title: String,
    message: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismissRequest: () -> Unit,
    destructive: Boolean = false,
    inWindow: Boolean = true,
    extraContent: (@Composable () -> Unit)? = null,
) {
    if (inWindow) {
        BingeConfirmDialog(
            title = title,
            message = message,
            confirmLabel = confirmLabel,
            onConfirm = onConfirm,
            onDismissRequest = onDismissRequest,
            destructive = destructive,
            extraContent = extraContent,
        )
    } else {
        BingeConfirmDialogContent(
            title = title,
            message = message,
            confirmLabel = confirmLabel,
            onConfirm = onConfirm,
            onDismissRequest = onDismissRequest,
            destructive = destructive,
            extraContent = extraContent,
        )
    }
}

/**
 * A console page's message dialog: why something failed and what to do about it, with one button that acknowledges it (the rule is
 * in `CLAUDE.md` > Messages). A short result, or a refusal with nothing to act on, is a snackbar instead. With [inWindow] false it
 * draws [BingeMessageDialogContent], the same body without the dialog window, which a screen frame can't capture.
 *
 * With [dismissible] false only the button closes it, not Back or a tap outside: for something shown once that a stray tap must
 * not lose (an API key), whose [confirmLabel] then says what closing means. [extraContent] sits under the message, in the same
 * scroll, for what the message is about and the user acts on (that key and its Copy button).
 */
@Composable
internal fun ConsoleMessage(
    title: String,
    message: String,
    onDismiss: () -> Unit,
    inWindow: Boolean = true,
    confirmLabel: String = stringResource(DesR.string.action_ok),
    dismissible: Boolean = true,
    extraContent: (@Composable () -> Unit)? = null,
) {
    if (inWindow) {
        BingeMessageDialog(
            title = title,
            message = message,
            onDismiss = onDismiss,
            confirmLabel = confirmLabel,
            dismissible = dismissible,
            extraContent = extraContent,
        )
    } else {
        BingeMessageDialogContent(
            title = title,
            message = message,
            onDismiss = onDismiss,
            confirmLabel = confirmLabel,
            extraContent = extraContent,
        )
    }
}
