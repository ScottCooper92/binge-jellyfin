package com.cooper.binge.companion.jellyfin.ui.consent

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.ToggleOff
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import com.binge.designsystem.DecisionPoint
import com.cooper.binge.companion.jellyfin.R

/** One of the three things the consent screen says, shared by the phone and the television. */
internal class ConsentPoint(
    val icon: ImageVector,
    @StringRes val titleRes: Int,
    @StringRes val detailRes: Int,
)

internal val ConsentPoints =
    listOf(
        ConsentPoint(Icons.Filled.BarChart, R.string.consent_point_shared_title, R.string.consent_point_shared_detail),
        ConsentPoint(Icons.Filled.VisibilityOff, R.string.consent_point_never_title, R.string.consent_point_never_detail),
        ConsentPoint(Icons.Filled.ToggleOff, R.string.consent_point_change_title, R.string.consent_point_change_detail),
    )

/** The three points as the design system's decision screen takes them: this app's copy under each icon. */
@Composable
internal fun consentPoints(): List<DecisionPoint> =
    ConsentPoints.map { point ->
        DecisionPoint(icon = point.icon, title = stringResource(point.titleRes), detail = stringResource(point.detailRes))
    }
