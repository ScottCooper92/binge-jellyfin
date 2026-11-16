package com.cooper.binge.companion.jellyfin.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.binge.designsystem.component.BingeFilledButton
import com.binge.designsystem.component.BingeOutlinedButton
import com.cooper.binge.companion.jellyfin.R

/** A server that has not finished its first-run setup has no account to sign in to, so this stands where the sign-in would. */
@Composable
internal fun SetupIncomplete(actions: Actions) {
    Text(stringResource(R.string.setup_incomplete_title), style = MaterialTheme.typography.headlineSmall)
    Text(stringResource(R.string.setup_incomplete_body), style = MaterialTheme.typography.bodyMedium)
    BingeFilledButton(
        label = stringResource(R.string.startup_open),
        onClick = actions.startup.onOpen,
        modifier = Modifier.fillMaxWidth(),
    )
    BingeOutlinedButton(
        label = stringResource(R.string.setup_incomplete_check),
        onClick = actions.onRecheckServer,
        modifier = Modifier.fillMaxWidth(),
    )
}
