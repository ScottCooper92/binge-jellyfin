package com.cooper.binge.companion.jellyfin.ui.console

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.binge.designsystem.component.BingeFilterChip
import com.binge.designsystem.component.WrappingRow
import com.cooper.binge.companion.jellyfin.R
import com.binge.designsystem.R as DesR

private const val HALF_DAY_HOURS = 12
private const val MINUTES_PER_HOUR_VALUE = 60
private const val MAX_FIELD_DIGITS = 2

/** The widest a time field grows: two digits, however large the font. */
private val FIELD_MAX_WIDTH = 160.dp

/**
 * A time of day typed as a 12-hour clock: [hour] 1 to 12, [minute] 0 to 59, and whether it is after noon. The minutes since
 * midnight, or null while either field is not a number in range. 12 a.m. is midnight and 12 p.m. is noon.
 */
internal fun twelveHourMinutes(
    hour: String,
    minute: String,
    afternoon: Boolean,
): Int? {
    val h = hour.toIntOrNull()?.takeIf { it in 1..HALF_DAY_HOURS } ?: return null
    val m = minute.toIntOrNull()?.takeIf { it in 0 until MINUTES_PER_HOUR_VALUE } ?: return null
    return (h % HALF_DAY_HOURS + if (afternoon) HALF_DAY_HOURS else 0) * MINUTES_PER_HOUR_VALUE + m
}

/**
 * A 12-hour time of day: hour and minute fields, and a.m. and p.m. as chips under them. Material's own time input draws
 * the period as a fixed-size toggle whose labels the font scale clips, and it cannot be made larger; chips wrap and grow
 * with the font. A 24-hour device keeps Material's input, which has no toggle.
 */
@Composable
internal fun TwelveHourTimeInput(
    hour: String,
    minute: String,
    afternoon: Boolean,
    onHour: (String) -> Unit,
    onMinute: (String) -> Unit,
    onAfternoon: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val valid = twelveHourMinutes(hour, minute, afternoon) != null
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(dimensionResource(DesR.dimen.padding_s)),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(dimensionResource(DesR.dimen.padding_s)), verticalAlignment = Alignment.Top) {
            ConsoleTextField(
                value = hour,
                onValueChange = { onHour(it.filter(Char::isDigit).take(MAX_FIELD_DIGITS)) },
                label = stringResource(R.string.tasks_trigger_hour),
                isError = hour.isNotEmpty() && hour.toIntOrNull()?.let { it in 1..HALF_DAY_HOURS } != true,
                keyboardType = KeyboardType.Number,
                modifier = Modifier.widthIn(max = FIELD_MAX_WIDTH).weight(1f, fill = false),
            )
            Text(":", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(top = 12.dp))
            ConsoleTextField(
                value = minute,
                onValueChange = { onMinute(it.filter(Char::isDigit).take(MAX_FIELD_DIGITS)) },
                label = stringResource(R.string.tasks_trigger_minute),
                isError = minute.isNotEmpty() && minute.toIntOrNull()?.let { it in 0 until MINUTES_PER_HOUR_VALUE } != true,
                keyboardType = KeyboardType.Number,
                modifier = Modifier.widthIn(max = FIELD_MAX_WIDTH).weight(1f, fill = false),
            )
        }
        WrappingRow {
            BingeFilterChip(label = stringResource(R.string.tasks_trigger_am), selected = !afternoon, onClick = { onAfternoon(false) })
            BingeFilterChip(label = stringResource(R.string.tasks_trigger_pm), selected = afternoon, onClick = { onAfternoon(true) })
        }
        if (!valid && hour.isNotEmpty() && minute.isNotEmpty()) {
            Text(
                stringResource(R.string.tasks_trigger_time_invalid),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}
