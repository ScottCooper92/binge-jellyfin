package com.cooper.binge.companion.jellyfin.ui.console

import androidx.annotation.StringRes
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import com.cooper.binge.companion.jellyfin.R

/**
 * The longest server reason a snackbar carries. "Not saved. " and a reason of this length is about 50 characters: two lines at
 * 1.3x font on a narrow phone. A longer reason is a cause the user has to read, so it gets a message dialog.
 */
internal const val LONG_REASON_CHARS = 40

/**
 * Says so when the server refused a write, which put the change back: Retry would only be refused again, so this is a
 * message and no more. [failure] goes back to null with the page's next change, so a second refusal says so again.
 * [text] is the sentence, with the failure's own reason as its one argument; a page whose change is not a setting
 * ("Triggers not changed") gives its own.
 *
 * A short sentence is a snackbar. A refusal whose server reason is longer than [LONG_REASON_CHARS] is a cause the user must
 * read, and a snackbar would cut it off: it opens a message dialog instead, titled with the sentence's own lead ("Not saved"),
 * the reason as its body and one OK.
 */
@Composable
internal fun RefusedMessage(
    failure: ConsoleFailure?,
    host: SnackbarHostState,
    @StringRes text: Int = R.string.settings_not_saved,
) {
    val reason = failure?.reason?.takeIf { it.length > LONG_REASON_CHARS }
    if (reason == null) {
        SnackbarMessage(failure?.let { stringResource(text, it.text()) }, host)
    } else {
        // The sentence's lead, "Not saved", is what comes before the reason in every sentence a page gives.
        var open by rememberSaveable(reason) { mutableStateOf(true) }
        if (open) RefusedDialog(stringResource(text, "").trim().trimEnd('.'), reason, onDismiss = { open = false })
    }
}

/** A refusal's long reason, as a message dialog: what happened, why, and OK. [inWindow] false draws its body for a frame. */
@Composable
internal fun RefusedDialog(
    title: String,
    reason: String,
    onDismiss: () -> Unit,
    inWindow: Boolean = true,
) = ConsoleMessage(title = title, message = reason, onDismiss = onDismiss, inWindow = inWindow)

/** Shows [message] in a snackbar when there is one, and again when it changes. A page's own sentence for what went wrong. */
@Composable
internal fun SnackbarMessage(
    message: String?,
    host: SnackbarHostState,
) {
    LaunchedEffect(message) { if (message != null) host.showSnackbar(message) }
}
