package com.cooper.binge.companion.jellyfin.ui.console

import androidx.annotation.StringRes
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.res.stringResource
import com.cooper.binge.companion.jellyfin.R

/**
 * Says so when the server refused a write, which put the change back: Retry would only be refused again, so this is a
 * message and no more. [failure] goes back to null with the page's next change, so a second refusal says so again.
 * [text] is the sentence, with the failure's own reason as its one argument; a page whose change is not a setting
 * ("Triggers not changed") gives its own.
 */
@Composable
internal fun RefusedMessage(
    failure: ConsoleFailure?,
    host: SnackbarHostState,
    @StringRes text: Int = R.string.settings_not_saved,
) {
    SnackbarMessage(failure?.let { stringResource(text, stringResource(it.message)) }, host)
}

/** Shows [message] in a snackbar when there is one, and again when it changes. A page's own sentence for what went wrong. */
@Composable
internal fun SnackbarMessage(
    message: String?,
    host: SnackbarHostState,
) {
    LaunchedEffect(message) { if (message != null) host.showSnackbar(message) }
}
