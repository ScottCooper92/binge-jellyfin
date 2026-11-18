package com.cooper.homelab.ephyra.ui.tv

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.ToggleOff
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.binge.designsystem.DecisionCopy
import com.binge.designsystem.DecisionPoint
import com.binge.designsystem.component.BingeLoadingIndicator
import com.binge.designsystem.template.ScreenAction
import com.binge.designsystem.tv.template.TvDecisionPage
import com.binge.designsystem.tv.template.TvPageHosting
import com.cooper.homelab.core.telemetry.AnalyticsConsent
import com.cooper.homelab.ephyra.R

/** The phone's consent gate on a television: the same question, before sign-in and the home. */
@Composable
internal fun TvConsentGate(
    consent: AnalyticsConsent?,
    onAnswer: (granted: Boolean) -> Unit,
    content: @Composable () -> Unit,
) {
    when (consent) {
        null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { BingeLoadingIndicator() }
        AnalyticsConsent.UNDECIDED -> TvConsentScreen(onChoice = onAnswer)
        AnalyticsConsent.GRANTED, AnalyticsConsent.DENIED -> content()
    }
}

/**
 * The usage data question on the design system's [TvDecisionPage]: the heading and reassurance beside a read-out points card,
 * and the two answers under it. Share is the primary and takes the arrival focus; Not now sits beside it, one
 * press away and reversible in Settings. Each point is a focusable read-out, so a line is never hidden from someone who is
 * about to agree to it.
 */
@Composable
internal fun TvConsentScreen(
    onChoice: (granted: Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    TvDecisionPage(
        copy =
            DecisionCopy(
                title = stringResource(R.string.tv_consent_title),
                subtitle = stringResource(R.string.tv_consent_subtitle),
                kicker = stringResource(R.string.tv_consent_kicker),
                note = stringResource(R.string.tv_consent_reassurance),
            ),
        points = tvConsentPoints(),
        accept = ScreenAction(stringResource(R.string.tv_consent_accept), onClick = { onChoice(true) }),
        decline = ScreenAction(stringResource(R.string.tv_consent_decline), onClick = { onChoice(false) }),
        modifier = modifier,
        // A gate before sign-in and the home, so the page places its own first focus on Share.
        hosting = TvPageHosting.PreShell,
    )
}

/** The three points, the last worded for a remote, which is a press and not a tap. */
@Composable
private fun tvConsentPoints(): List<DecisionPoint> =
    listOf(
        DecisionPoint(
            Icons.Filled.BarChart,
            stringResource(R.string.tv_consent_point_shared_title),
            stringResource(R.string.tv_consent_point_shared_detail),
        ),
        DecisionPoint(
            Icons.Filled.VisibilityOff,
            stringResource(R.string.tv_consent_point_never_title),
            stringResource(R.string.tv_consent_point_never_detail),
        ),
        DecisionPoint(
            Icons.Filled.ToggleOff,
            stringResource(R.string.tv_consent_point_change_title),
            stringResource(R.string.tv_consent_point_change_detail),
        ),
    )
