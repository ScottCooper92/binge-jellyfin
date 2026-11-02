package com.cooper.binge.companion.jellyfin.ui.console

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.res.stringResource
import com.binge.designsystem.SaveStatus
import com.binge.designsystem.template.FormSaveStatus
import com.cooper.binge.companion.jellyfin.R

/** This page's saves as the design system's [FormSaveStatus]: a failed write is Failed, with [onRetry] sending it again. */
internal fun SaveProgress.formStatus(onRetry: () -> Unit): FormSaveStatus =
    when {
        failed -> FormSaveStatus.Failed(onRetry)
        status == SaveStatus.Saving -> FormSaveStatus.Saving
        status == SaveStatus.Saved -> FormSaveStatus.Saved
        else -> FormSaveStatus.Idle
    }

/**
 * Says so when the server refused a write, which put the change back: Retry would only be refused again, so this is a
 * message and no more. [failure] goes back to null with the page's next change, so a second refusal says so again.
 */
@Composable
internal fun RefusedMessage(
    failure: ConsoleFailure?,
    host: SnackbarHostState,
) {
    val message = failure?.let { stringResource(R.string.settings_not_saved, stringResource(it.message)) }
    LaunchedEffect(message) { if (message != null) host.showSnackbar(message) }
}
