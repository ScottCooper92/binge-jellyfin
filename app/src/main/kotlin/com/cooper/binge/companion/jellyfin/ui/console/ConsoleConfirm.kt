package com.cooper.binge.companion.jellyfin.ui.console

import androidx.compose.runtime.Composable
import com.binge.designsystem.component.BingeConfirmDialog
import com.binge.designsystem.component.BingeConfirmDialogContent

/**
 * A console page's confirm or form dialog. On screen it's a [BingeConfirmDialog]. With [inWindow]
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
