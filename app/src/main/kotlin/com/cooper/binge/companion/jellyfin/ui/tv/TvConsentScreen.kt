package com.cooper.binge.companion.jellyfin.ui.tv

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.binge.designsystem.DecisionCopy
import com.binge.designsystem.DecisionPoint
import com.binge.designsystem.template.ScreenAction
import com.binge.designsystem.tv.template.TvDecisionPage
import com.binge.designsystem.tv.template.TvPageHosting
import com.cooper.binge.companion.jellyfin.R
import com.cooper.binge.companion.jellyfin.telemetry.AnalyticsConsent
import com.cooper.binge.companion.jellyfin.ui.consent.ConsentLoading
import com.cooper.binge.companion.jellyfin.ui.consent.ConsentPoints

/** The phone's consent gate on a television: the same question, before sign-in and the home. */
@Composable
internal fun TvConsentGate(
    consent: AnalyticsConsent?,
    onAnswer: (granted: Boolean) -> Unit,
    content: @Composable () -> Unit,
) {
    when (consent) {
        null -> ConsentLoading()
        AnalyticsConsent.UNDECIDED -> TvConsentScreen(onChoice = onAnswer)
        AnalyticsConsent.GRANTED, AnalyticsConsent.DENIED -> content()
    }
}

/**
 * The usage data question on the design system's [TvDecisionPage]: the heading and reassurance beside a read-out points card,
 * and the two answers under it. Share usage data is the primary and takes the arrival focus; Not now sits beside it, one
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
                title = stringResource(R.string.consent_title),
                subtitle = stringResource(R.string.consent_subtitle),
                kicker = stringResource(R.string.consent_kicker),
                note = stringResource(R.string.tv_consent_reassurance),
            ),
        points = tvConsentPoints(),
        accept = ScreenAction(stringResource(R.string.consent_accept), onClick = { onChoice(true) }),
        decline = ScreenAction(stringResource(R.string.consent_decline), onClick = { onChoice(false) }),
        modifier = modifier,
        // A gate before sign-in and the home, so the page places its own first focus on Share usage data.
        hosting = TvPageHosting.PreShell,
    )
}

/** The phone's three points, the last reworded because a remote is a press, not a tap. */
@Composable
private fun tvConsentPoints(): List<DecisionPoint> =
    ConsentPoints.mapIndexed { index, point ->
        val title = if (index == ConsentPoints.lastIndex) R.string.tv_consent_point_change_title else point.titleRes
        DecisionPoint(icon = point.icon, title = stringResource(title), detail = stringResource(point.detailRes))
    }
