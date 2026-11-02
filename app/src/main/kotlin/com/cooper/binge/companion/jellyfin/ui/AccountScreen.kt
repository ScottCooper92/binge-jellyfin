package com.cooper.binge.companion.jellyfin.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.Password
import androidx.compose.material.icons.outlined.QrCode2
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.binge.designsystem.component.BingeInitialsAvatar
import com.binge.designsystem.component.BingeTextButton
import com.binge.designsystem.component.ItemGroup
import com.binge.designsystem.component.ListItem
import com.binge.designsystem.template.BingeScreenScaffold
import com.binge.designsystem.template.ScreenBar
import com.binge.designsystem.template.ScreenBody
import com.binge.designsystem.template.screenListPadding
import com.cooper.binge.companion.jellyfin.R
import com.cooper.binge.companion.jellyfin.ui.console.ConsolePage
import com.binge.designsystem.R as DesR

/**
 * An administrator's own account, behind the avatar in the top bar: Approve a code, Playback, the Home screen, their
 * password, and Sign out last, red, behind a confirm that opens on Cancel. Everyone else has these rows inline on their
 * home, from the same [AccountGroup] and [SignOutButton], so the two cannot drift.
 */
@Composable
fun AccountScreen(
    state: UiState.SignedIn,
    actions: Actions,
    onBack: () -> Unit,
) {
    BingeScreenScaffold(title = stringResource(R.string.console_account_title), bar = ScreenBar.Small, onBack = onBack) { padding ->
        ScreenBody(padding) { _ ->
            LazyColumn(
                contentPadding = padding.screenListPadding(),
                verticalArrangement = Arrangement.spacedBy(dimensionResource(DesR.dimen.padding_l)),
            ) {
                item { AccountGroup(state, actions, titled = false) }
                item { SignOutButton(actions) }
            }
        }
    }
    if (state.confirmingSignOut) SignOutConfirm(actions)
}

/** The sign-out button, red, and last wherever it is: it asks first. */
@Composable
internal fun SignOutButton(actions: Actions) {
    BingeTextButton(
        label = stringResource(R.string.settings_sign_out),
        onClick = actions.onAskSignOut,
        leadingIcon = Icons.AutoMirrored.Outlined.Logout,
        destructive = true,
        modifier = Modifier.fillMaxWidth(),
    )
}

/** The avatar in the top bar that opens an administrator's account. */
@Composable
internal fun AccountAction(
    state: UiState.SignedIn,
    actions: Actions,
) {
    val label = stringResource(R.string.console_account_title)
    IconButton(onClick = { actions.onOpenPage(ConsolePage.Account) }, modifier = Modifier.semantics { contentDescription = label }) {
        BingeInitialsAvatar(name = state.session.userName)
    }
}

/**
 * The console's pages for the signed-in user's own account, for everyone. An administrator reaches them in the
 * account page and anyone else on their home, so the one group is shared and the two cannot drift. [titled] is
 * false where the page's own title already says it.
 */
@Composable
internal fun AccountGroup(
    state: UiState.SignedIn,
    actions: Actions,
    titled: Boolean = true,
) {
    ItemGroup(
        title = if (titled) stringResource(R.string.console_account_title) else null,
        rows =
            listOfNotNull(
                ListItem(
                    icon = Icons.Outlined.QrCode2,
                    label = stringResource(R.string.approve_title),
                    detail = stringResource(R.string.console_approve_detail),
                    onClick = { actions.onOpenPage(ConsolePage.ApproveCode) },
                ).takeIf { ConsolePage.ApproveCode.available(state.profile, state.account) },
                ListItem(
                    icon = Icons.Outlined.Translate,
                    label = stringResource(R.string.playback_title),
                    detail = stringResource(R.string.console_playback_detail),
                    onClick = { actions.onOpenPage(ConsolePage.Playback) },
                ).takeIf { ConsolePage.Playback.available(state.profile, state.account) },
                ListItem(
                    icon = Icons.Outlined.Dashboard,
                    label = stringResource(R.string.home_layout_title),
                    detail = stringResource(R.string.console_home_layout_detail),
                    onClick = { actions.onOpenPage(ConsolePage.HomeLayout) },
                ).takeIf { ConsolePage.HomeLayout.available(state.profile, state.account) },
                ListItem(
                    icon = Icons.Outlined.Password,
                    label = stringResource(R.string.password_title),
                    onClick = { actions.onOpenPage(ConsolePage.Password) },
                ).takeIf { ConsolePage.Password.available(state.profile, state.account) },
            ),
    )
}
