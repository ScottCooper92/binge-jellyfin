package com.cooper.binge.companion.jellyfin.ui.console

import androidx.compose.runtime.Composable
import com.binge.designsystem.component.BingeConfirmDialog
import com.binge.designsystem.component.BingeConfirmDialogContent
import com.binge.designsystem.component.BingeMessageDialog
import com.binge.designsystem.component.BingeMessageDialogContent

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
 * A console page's message dialog: why something failed and what to do about it, with one OK that acknowledges it (the rule is in
 * `CLAUDE.md` > Messages). A short result, or a refusal with nothing to act on, is a snackbar instead. With [inWindow] false it
 * draws [BingeMessageDialogContent], the same body without the dialog window, which a screen frame can't capture.
 */
@Composable
internal fun ConsoleMessage(
    title: String,
    message: String,
    onDismiss: () -> Unit,
    inWindow: Boolean = true,
) {
    if (inWindow) {
        BingeMessageDialog(title = title, message = message, onDismiss = onDismiss)
    } else {
        BingeMessageDialogContent(title = title, message = message, onDismiss = onDismiss)
    }
}
