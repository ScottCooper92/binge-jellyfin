package com.cooper.binge.companion.jellyfin.ui.consent

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Lock
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.binge.designsystem.DecisionCopy
import com.binge.designsystem.component.BingeLoadingIndicator
import com.binge.designsystem.template.DecisionScreen
import com.binge.designsystem.template.ScreenAction
import com.cooper.binge.companion.jellyfin.R
import com.cooper.binge.companion.jellyfin.telemetry.AnalyticsConsent

/**
 * Shows [content] once usage data has an answer, and the question until then. [consent] is null while the stored answer is
 * being read, and [onAnswer] records the user's choice.
 */
@Composable
fun ConsentGate(
    consent: AnalyticsConsent?,
    onAnswer: (granted: Boolean) -> Unit,
    content: @Composable () -> Unit,
) {
    when (consent) {
        null -> ConsentLoading()
        AnalyticsConsent.UNDECIDED -> ConsentScreen(onChoice = onAnswer)
        AnalyticsConsent.GRANTED, AnalyticsConsent.DENIED -> content()
    }
}

/** What stands in for the question while the stored answer is read, so the app never flashes its home first. */
@Composable
internal fun ConsentLoading(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) { BingeLoadingIndicator() }
}

/**
 * The usage data question, laid out as Binge's own analytics step so the apps ask it the same way: what it is for, what it
 * never touches, how to turn it off, and the two answers. The layout is the design system's [DecisionScreen]; what is this
 * app's own is the copy.
 */
@Composable
fun ConsentScreen(onChoice: (granted: Boolean) -> Unit) {
    DecisionScreen(
        copy =
            DecisionCopy(
                title = stringResource(R.string.consent_title),
                subtitle = stringResource(R.string.consent_subtitle),
                kicker = stringResource(R.string.consent_kicker),
            ),
        points = consentPoints(),
        accept = ScreenAction(stringResource(R.string.consent_accept), { onChoice(true) }),
        decline = ScreenAction(stringResource(R.string.consent_decline), { onChoice(false) }),
        hero = Icons.Filled.BarChart,
        heroBadge = Icons.Filled.Lock,
    )
}
