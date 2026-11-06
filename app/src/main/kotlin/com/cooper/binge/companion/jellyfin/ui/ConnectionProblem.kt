package com.cooper.binge.companion.jellyfin.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.HourglassEmpty
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.binge.designsystem.component.BingeFilledButton
import com.binge.designsystem.component.BingeOutlinedButton
import com.binge.designsystem.template.MessageScreen
import com.cooper.binge.companion.jellyfin.R

/**
 * The saved server did not answer, or answered with something the app could not use: the home gives way to this, with
 * the three ways out. Try again heals it in place (the button shows a spinner and swallows taps while it is trying), editing
 * the server details goes back to the server step with the address filled in, and signing out forgets the server. A
 * token the server refuses never reaches here: the app goes to sign-in instead.
 *
 * Three ways out, one of them destructive, so the stacked actions rather than the two-button row.
 */
@Composable
internal fun ConnectionProblem(
    state: UiState.SignedIn,
    actions: Actions,
    modifier: Modifier = Modifier,
) {
    val couldNotLoad = state.health == ConnectionHealth.CouldNotLoad
    MessageScreen(
        modifier = modifier,
        headline =
            stringResource(
                if (couldNotLoad) R.string.connection_couldnt_load_headline else R.string.connection_unreachable_headline,
            ),
        body = stringResource(if (couldNotLoad) R.string.connection_couldnt_load_body else R.string.connection_unreachable_body),
        icon = if (couldNotLoad) Icons.Outlined.HourglassEmpty else Icons.Outlined.CloudOff,
        actions = {
            BingeFilledButton(
                label = stringResource(R.string.connection_retry),
                onClick = actions.onRefresh,
                loading = state.refreshing,
                modifier = Modifier.fillMaxWidth(),
            )
            BingeOutlinedButton(
                label = stringResource(R.string.connection_edit_server),
                onClick = actions.onChangeServer,
                modifier = Modifier.fillMaxWidth(),
            )
            SignOutButton(actions)
        },
    )
}
