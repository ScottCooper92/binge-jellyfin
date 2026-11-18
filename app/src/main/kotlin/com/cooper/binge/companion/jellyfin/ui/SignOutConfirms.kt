package com.cooper.binge.companion.jellyfin.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.binge.designsystem.component.BingeConfirmDialog
import com.binge.designsystem.component.BingeConfirmDialogContent
import com.cooper.binge.companion.jellyfin.R
import com.cooper.binge.companion.jellyfin.auth.JellyfinSession
import com.cooper.binge.companion.jellyfin.flags.LocalFlags
import com.cooper.binge.companion.jellyfin.server.AddressPolicy
import com.cooper.binge.companion.jellyfin.server.insecureHost

/** Shows or hides the sign-out confirm. */
fun JellyfinViewModel.confirmSignOut(showing: Boolean) = updateSignedIn { it.copy(confirmingSignOut = showing) }

/**
 * Shows or hides the confirm for taking back the plain-HTTP agreement, which the session's server card offers. Only a session
 * that holds one has it: nothing shows for any other.
 */
fun JellyfinViewModel.confirmTakeBack(showing: Boolean) =
    updateSignedIn { if (!showing || it.consentHost != null) it.copy(confirmingTakeBack = showing) else it }

/**
 * The confirm for taking back the agreement to plain HTTP to [host]. Taking it back is a sign-out, since the app can no longer reach the
 * server, so it says so and confirms with the sign-out. [inWindow] false draws its body alone, as [SignOutConfirm] does.
 */
@Composable
internal fun TakeBackConfirm(
    host: String,
    actions: Actions,
    inWindow: Boolean = true,
) {
    val title = stringResource(R.string.take_back_title)
    val message = stringResource(R.string.take_back_body, host)
    val confirm = stringResource(R.string.take_back_confirm)
    if (inWindow) {
        BingeConfirmDialog(title, message, confirm, actions.onSignOut, actions.onCancelTakeBack, destructive = true)
    } else {
        BingeConfirmDialogContent(title, message, confirm, actions.onSignOut, actions.onCancelTakeBack, destructive = true)
    }
}

/**
 * The sign-out confirm. [inWindow] false draws its body alone, without the dialog window a screen
 * frame can't capture, so a `@PreviewTest` can show it.
 */
@Composable
internal fun SignOutConfirm(
    actions: Actions,
    inWindow: Boolean = true,
) {
    val title = stringResource(R.string.sign_out_title)
    val message = stringResource(if (LocalFlags.current.bingePromotion) R.string.sign_out_body else R.string.sign_out_body_plain)
    val confirm = stringResource(R.string.settings_sign_out)
    if (inWindow) {
        BingeConfirmDialog(title, message, confirm, actions.onSignOut, actions.onCancelSignOut, destructive = true)
    } else {
        BingeConfirmDialogContent(title, message, confirm, actions.onSignOut, actions.onCancelSignOut, destructive = true)
    }
}

/** The state of a session just signed in or restored: it carries the public host whose plain-HTTP agreement it holds, if any. */
internal fun signedInState(
    policy: AddressPolicy,
    session: JellyfinSession,
) = UiState.SignedIn(session, consentHost = policy.insecureHost(session.baseUrl))
