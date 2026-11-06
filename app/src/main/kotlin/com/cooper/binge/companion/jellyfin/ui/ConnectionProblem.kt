package com.cooper.binge.companion.jellyfin.ui

import androidx.annotation.StringRes
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
 * The saved server did not answer, answered with something the app could not use, or is on the local network and Android
 * has not let the app reach it: the home gives way to this, with the three ways out. Try again heals it in place (the
 * button shows a spinner and swallows taps while it is trying, and the problem is asked about again on its own while it
 * shows), editing the server details goes back to the server step with the address filled in, and signing out forgets the
 * server. A missing local-network permission swaps Try again for the way to grant it. A token the server refuses never
 * reaches here: the app goes to sign-in instead.
 *
 * Three ways out, one of them destructive, so the stacked actions rather than the two-button row.
 */
@Composable
internal fun ConnectionProblem(
    state: UiState.SignedIn,
    actions: Actions,
    modifier: Modifier = Modifier,
) {
    val health = state.health
    ConnectionAutoRetry(health, state.refreshing, actions.onRefresh)
    MessageScreen(
        modifier = modifier,
        headline = stringResource(connectionHeadline(health)),
        body = stringResource(connectionBody(health)),
        icon = if (health == ConnectionHealth.CouldNotLoad) Icons.Outlined.HourglassEmpty else Icons.Outlined.CloudOff,
        actions = {
            if (health == ConnectionHealth.LocalNetworkDenied) {
                BingeFilledButton(
                    label =
                        stringResource(
                            if (actions.localNetworkAsked) R.string.connection_open_settings else R.string.connection_allow_local_network,
                        ),
                    onClick = actions.onAllowLocalNetwork,
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                BingeFilledButton(
                    label = stringResource(R.string.connection_retry),
                    onClick = actions.onRefresh,
                    loading = state.refreshing,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            BingeOutlinedButton(
                label = stringResource(R.string.connection_edit_server),
                onClick = actions.onChangeServer,
                modifier = Modifier.fillMaxWidth(),
            )
            SignOutButton(actions)
        },
    )
}

/** What the problem is called, on the phone and on the television. */
@StringRes
internal fun connectionHeadline(health: ConnectionHealth): Int =
    when (health) {
        ConnectionHealth.CouldNotLoad -> R.string.connection_couldnt_load_headline
        ConnectionHealth.LocalNetworkDenied -> R.string.connection_local_network_headline
        else -> R.string.connection_unreachable_headline
    }

/** What to do about it, on the phone and on the television. */
@StringRes
internal fun connectionBody(health: ConnectionHealth): Int =
    when (health) {
        ConnectionHealth.CouldNotLoad -> R.string.connection_couldnt_load_body
        ConnectionHealth.LocalNetworkDenied -> R.string.connection_local_network_body
        else -> R.string.connection_unreachable_body
    }
