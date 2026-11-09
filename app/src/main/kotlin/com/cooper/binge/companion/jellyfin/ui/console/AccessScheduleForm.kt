package com.cooper.binge.companion.jellyfin.ui.console

import android.text.format.DateFormat
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Today
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import com.binge.designsystem.component.BingeFilledButton
import com.binge.designsystem.component.BingeTextButton
import com.binge.designsystem.component.ClockTimeRow
import com.binge.designsystem.component.ItemGroup
import com.binge.designsystem.component.ListItem
import com.cooper.binge.companion.jellyfin.R
import com.cooper.binge.companion.jellyfin.server.AccessSchedule
import com.cooper.binge.companion.jellyfin.server.POLICY_SCHEDULES
import com.cooper.binge.companion.jellyfin.server.SCHEDULE_DAYS
import com.cooper.binge.companion.jellyfin.server.asAccessSchedule
import com.cooper.binge.companion.jellyfin.server.json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlin.math.roundToInt
import com.binge.designsystem.R as DesR

private const val MINUTES_PER_SCHEDULE_HOUR = 60
private const val MINUTES_PER_DAY = 1440
private val ScheduleDays =
    listOf("Everyday", "Weekday", "Weekend") + SCHEDULE_DAYS.filterNot { it in setOf("Everyday", "Weekday", "Weekend") }

/** Common days first, then one scrollable form with both minute-precision times and a docked Save. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ScheduleForm(
    actions: UsersActions,
    onClose: () -> Unit,
    startStep: Int = 0,
    startDay: String = "Everyday",
    startHour: Int = 0,
    original: AccessSchedule? = null,
    is24Hour: Boolean = DateFormat.is24HourFormat(LocalContext.current),
) {
    var choosingDay by rememberSaveable { mutableStateOf(original == null && startStep == 0) }
    var day by rememberSaveable { mutableStateOf(original?.day ?: startDay) }
    val fromMinutes = ((original?.startHour ?: startHour.toDouble()) * MINUTES_PER_SCHEDULE_HOUR).roundToInt()
    val untilMinutes = ((original?.endHour ?: 24.0) * MINUTES_PER_SCHEDULE_HOUR).roundToInt() % MINUTES_PER_DAY
    var from by rememberSaveable { mutableIntStateOf(fromMinutes) }
    var untilClock by rememberSaveable { mutableIntStateOf(untilMinutes) }
    // Midnight as the end means the end of the day.
    val until = if (untilClock == 0) MINUTES_PER_DAY else untilClock
    val span = if (until > from) from to until else null
    BackHandler(enabled = !choosingDay && !WindowInsets.isImeVisible) { choosingDay = true }
    Column {
        Column(
            modifier =
                Modifier
                    .weight(
                        1f,
                        fill = false,
                    ).verticalScroll(rememberScrollState())
                    .padding(dimensionResource(DesR.dimen.padding_m)),
            verticalArrangement = Arrangement.spacedBy(dimensionResource(DesR.dimen.padding_m)),
        ) {
            Text(
                stringResource(if (choosingDay) R.string.users_schedule_day else R.string.users_schedules),
                style = MaterialTheme.typography.titleLarge,
            )
            if (choosingDay) {
                ScheduleDayList { value ->
                    day = value
                    choosingDay = false
                }
            } else {
                BingeTextButton(label = stringResource(dayLabel(day)), onClick = { choosingDay = true })
                ClockTimeRow(stringResource(R.string.users_schedule_start), from, { from = it }, is24Hour)
                ClockTimeRow(stringResource(R.string.users_schedule_end), untilClock, { untilClock = it }, is24Hour)
                Text(stringResource(R.string.users_schedule_midnight), style = MaterialTheme.typography.bodyMedium)
                if (span == null) ScheduleOrderError()
            }
        }
        if (!choosingDay) {
            ScheduleSave(
                isNew = original == null,
                span = span,
            ) { start, end ->
                actions.onList(POLICY_SCHEDULES) { it.withSchedule(original, AccessSchedule(day, start, end)) }
                onClose()
            }
        }
        BingeTextButton(label = stringResource(R.string.users_cancel), onClick = onClose, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun ScheduleDayList(onPick: (String) -> Unit) {
    ItemGroup(
        title = null,
        rows =
            ScheduleDays.map { value ->
                ListItem(icon = Icons.Outlined.Today, label = stringResource(dayLabel(value)), onClick = { onPick(value) })
            },
    )
}

@Composable
private fun ScheduleOrderError() = Text(stringResource(R.string.users_schedule_order), color = MaterialTheme.colorScheme.error)

@Composable
private fun ScheduleSave(
    isNew: Boolean,
    span: Pair<Int, Int>?,
    onSave: (start: Double, end: Double) -> Unit,
) {
    BingeFilledButton(
        label = stringResource(if (isNew) R.string.users_add_action_short else R.string.library_save),
        enabled = span != null,
        onClick = {
            if (span != null) {
                val (start, end) = span
                onSave(start.toDouble() / MINUTES_PER_SCHEDULE_HOUR, end.toDouble() / MINUTES_PER_SCHEDULE_HOUR)
            }
        },
        modifier = Modifier.fillMaxWidth().padding(horizontal = dimensionResource(DesR.dimen.padding_m)),
    )
}

/** Replace by value against the latest policy; retain unknown fields and collapse an edit equal to another row. */
internal fun List<JsonElement>.withSchedule(
    original: AccessSchedule?,
    changed: AccessSchedule,
): List<JsonElement> {
    val old = indexOfFirst { it.asAccessSchedule() == original }.takeIf { original != null } ?: -1
    if (original != null && old < 0) return this
    val duplicate = indexOfFirst { it.asAccessSchedule() == changed }
    if (duplicate >= 0 && duplicate != old) return if (old >= 0) filterIndexed { index, _ -> index != old } else this
    if (old < 0) return this + changed.json()
    return mapIndexed { index, value -> if (index == old) JsonObject((value as JsonObject) + changed.json()) else value }
}
