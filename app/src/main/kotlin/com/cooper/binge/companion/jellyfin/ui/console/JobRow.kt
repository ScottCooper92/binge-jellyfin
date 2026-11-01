package com.cooper.binge.companion.jellyfin.ui.console

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import com.binge.designsystem.component.ExpressiveIconButton
import com.binge.designsystem.component.ListItem
import com.binge.designsystem.theme.BingeSentiment
import com.binge.designsystem.theme.accent
import com.binge.designsystem.theme.fill
import com.cooper.binge.companion.jellyfin.R
import com.binge.designsystem.R as DesR

/** What a job row shows and does. [onStop] is null for a job that can't be stopped, such as one library's scan. */
data class JobRow(
    val icon: ImageVector,
    val label: String,
    val idleDetail: String,
    val running: Boolean,
    val percent: Int? = null,
    val busy: Boolean = false,
    val outcome: JobOutcome? = null,
    val runLabel: Int = R.string.job_run,
    val onRun: () -> Unit = {},
    val onStop: (() -> Unit)? = null,
    /** Opens the job, when it has a page of its own; null leaves the row as plain text. */
    val onOpen: (() -> Unit)? = null,
)

/**
 * A job as a list row, after binge-seerr's: run it, or stop it while it runs, in place at the row's end.
 * While it runs the detail says how far it has got. When a run ends, its outcome shows for a moment:
 * a check, or an error in the error colour, with the row's icon tinted to match.
 */
@Composable
internal fun JobRow.item(): ListItem =
    ListItem(
        icon = icon,
        iconTint =
            when (outcome) {
                JobOutcome.Succeeded -> BingeSentiment.Positive.fill()
                JobOutcome.Failed -> BingeSentiment.Negative.fill()
                null -> null
            },
        label = label,
        detail =
            when {
                outcome == JobOutcome.Succeeded -> stringResource(R.string.job_done)
                outcome == JobOutcome.Failed -> stringResource(R.string.job_failed)
                running -> percent?.let { stringResource(R.string.job_running_percent, it) } ?: stringResource(R.string.job_running)
                else -> idleDetail
            },
        detailColor = if (outcome == JobOutcome.Failed) BingeSentiment.Negative.accent() else null,
        clickable = onOpen != null,
        onClick = onOpen ?: {},
        trailingContent = { JobAction(this) },
    )

/** Run, Stop, or the outcome, in one box at the row's end, so the glyph stays put from one to the next. */
@Composable
private fun JobAction(row: JobRow) {
    Box(modifier = Modifier.size(dimensionResource(DesR.dimen.button_tonal_size)), contentAlignment = Alignment.Center) {
        val stop = row.onStop
        when {
            row.outcome != null -> {
                val ok = row.outcome == JobOutcome.Succeeded
                Icon(
                    imageVector = if (ok) Icons.Filled.Check else Icons.Filled.Error,
                    contentDescription = stringResource(if (ok) R.string.job_done else R.string.job_failed),
                    tint = if (ok) LocalContentColor.current else BingeSentiment.Negative.fill(),
                )
            }
            row.running && stop != null ->
                ExpressiveIconButton(
                    onClick = stop,
                    icon = Icons.Filled.Stop,
                    contentDescription = stringResource(R.string.job_stop),
                    loading = row.busy,
                )
            else ->
                // A job that can't be stopped spins in its Run button while it runs.
                ExpressiveIconButton(
                    onClick = row.onRun,
                    icon = Icons.Filled.PlayArrow,
                    contentDescription = stringResource(row.runLabel),
                    loading = row.busy || row.running,
                )
        }
    }
}
