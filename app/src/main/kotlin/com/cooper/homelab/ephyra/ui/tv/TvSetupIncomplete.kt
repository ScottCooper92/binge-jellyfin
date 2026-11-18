package com.cooper.homelab.ephyra.ui.tv

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import com.binge.designsystem.tv.component.TvButton
import com.binge.designsystem.tv.template.TvTwoPaneCopy
import com.binge.designsystem.tv.template.TvTwoPanePage
import com.binge.designsystem.tv.template.TvTwoPaneStyle
import com.binge.designsystem.tv.theme.TvButtonStyle
import com.cooper.homelab.ephyra.R
import com.cooper.homelab.ephyra.ui.Actions

/** A server that has not finished its first-run setup: the TV only says so, since it signs in and does not set a server up. */
@Composable
internal fun TvSetupIncompletePage(
    actions: Actions,
    modifier: Modifier,
) {
    BackHandler { actions.onChangeServer() }
    val entry = remember { FocusRequester() }
    LaunchedEffect(Unit) { entry.requestFocus() }
    TvTwoPanePage(
        title = stringResource(R.string.app_label),
        style = TvTwoPaneStyle.Board,
        entry = entry,
        modifier = modifier,
        copy = {
            TvTwoPaneCopy(
                headline = stringResource(R.string.setup_incomplete_title),
                body = stringResource(R.string.setup_incomplete_body),
            )
        },
        action = {
            TvButton(
                label = stringResource(R.string.setup_incomplete_check),
                onClick = actions.onRecheckServer,
                style = TvButtonStyle.Primary,
                modifier = Modifier.focusRequester(entry),
            )
            TvButton(label = stringResource(R.string.tv_change_server), onClick = actions.onChangeServer, style = TvButtonStyle.Text)
        },
    )
}
