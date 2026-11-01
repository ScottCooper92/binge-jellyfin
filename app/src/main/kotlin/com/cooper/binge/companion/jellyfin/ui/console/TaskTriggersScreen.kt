package com.cooper.binge.companion.jellyfin.ui.console

import android.app.TimePickerDialog
import android.text.format.DateFormat
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.DateRange
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.PowerSettingsNew
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Today
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.binge.designsystem.component.BingeFilterChip
import com.binge.designsystem.component.BingeOutlinedButton
import com.binge.designsystem.component.ExpressiveIconButton
import com.binge.designsystem.component.ItemGroup
import com.binge.designsystem.component.ListItem
import com.binge.designsystem.component.WrappingRow
import com.cooper.binge.companion.jellyfin.R
import com.cooper.binge.companion.jellyfin.server.ServerTask
import com.cooper.binge.companion.jellyfin.server.TICKS_PER_HOUR
import com.cooper.binge.companion.jellyfin.server.TICKS_PER_MINUTE
import com.cooper.binge.companion.jellyfin.server.TICKS_PER_SECOND
import com.cooper.binge.companion.jellyfin.server.TRIGGER_DAYS
import com.cooper.binge.companion.jellyfin.server.TaskTrigger
import com.cooper.binge.companion.jellyfin.server.TriggerKind
import java.time.DayOfWeek
import java.time.format.TextStyle
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import com.binge.designsystem.R as DesR

/** Lines a failed change's message takes, so it never moves the list. */
private const val STATUS_LINES = 2

private const val MILLIS_PER_TICK = 10_000L
private const val MINUTES_PER_HOUR = 60

/** The run-every choices: 15, 30 and 45 minutes, then whole hours. */
private val INTERVAL_MINUTES = listOf(15, 30, 45)
private val INTERVAL_HOURS = listOf(1, 2, 3, 4, 6, 8, 12, 24)

/** The time limits offered, in hours, beside none. */
private val LIMIT_HOURS = listOf(1, 2, 4, 6, 8, 12, 24)

private const val DEFAULT_HOUR = 3
private const val DEFAULT_INTERVAL_HOURS = 6

/** One task's triggers: each described, removable in place, and Add trigger. */
@Composable
internal fun TaskTriggers(
    task: ServerTask,
    state: TasksUiState.Ready,
    actions: TasksActions,
    inner: PaddingValues,
) {
    val timeLabel = rememberTimeLabel()
    LazyColumn(contentPadding = inner, verticalArrangement = Arrangement.spacedBy(dimensionResource(DesR.dimen.padding_l))) {
        if (task.description.isNotBlank()) item { Text(task.description, style = MaterialTheme.typography.bodyMedium) }
        item {
            BingeOutlinedButton(
                label = stringResource(R.string.tasks_trigger_add),
                onClick = { actions.onAskAdd(true) },
                leadingIcon = Icons.Outlined.Add,
                loading = state.saving,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        item {
            if (task.triggers.isEmpty()) {
                Text(stringResource(R.string.tasks_triggers_empty), style = MaterialTheme.typography.bodyLarge)
            } else {
                ItemGroup(
                    title = stringResource(R.string.tasks_triggers),
                    rows = task.triggers.mapIndexed { index, trigger -> trigger.row(index, state.saving, actions, timeLabel) },
                )
            }
        }
        item {
            Text(
                text = state.actionFailed?.let { stringResource(R.string.tasks_trigger_not_saved, stringResource(it.message)) }.orEmpty(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                minLines = STATUS_LINES,
                maxLines = STATUS_LINES,
            )
        }
    }
    if (state.adding) AddTriggerDialog(actions)
}

@Composable
private fun TaskTrigger.row(
    index: Int,
    saving: Boolean,
    actions: TasksActions,
    timeLabel: (Long) -> String,
): ListItem =
    ListItem(
        icon = icon(),
        label = description(timeLabel),
        detail = limitDescription(),
        clickable = false,
        trailingContent =
            if (saving) {
                null
            } else {
                {
                    ExpressiveIconButton(
                        onClick = { actions.onRemoveTrigger(index) },
                        icon = Icons.Outlined.Delete,
                        contentDescription = stringResource(R.string.tasks_trigger_remove),
                        tint = MaterialTheme.colorScheme.error,
                    )
                }
            },
    )

private fun TaskTrigger.icon(): ImageVector =
    when (kind) {
        TriggerKind.Daily -> Icons.Outlined.Today
        TriggerKind.Weekly -> Icons.Outlined.DateRange
        TriggerKind.Interval -> Icons.Outlined.Repeat
        TriggerKind.Startup -> Icons.Outlined.PowerSettingsNew
        null -> Icons.Outlined.Schedule
    }

/** The trigger in words: "Every day at 03:00", "Every Monday at 03:00", "Every 6 hours", "At startup". */
@Composable
internal fun TaskTrigger.description(timeLabel: (Long) -> String): String {
    val at = timeLabel(timeOfDayTicks ?: 0)
    return when (kind) {
        TriggerKind.Daily -> stringResource(R.string.trigger_daily_at, at)
        TriggerKind.Weekly -> stringResource(R.string.trigger_weekly_at, dayName(dayOfWeek, TextStyle.FULL), at)
        TriggerKind.Interval -> intervalDescription(intervalTicks ?: 0)
        TriggerKind.Startup -> stringResource(R.string.trigger_startup)
        null -> stringResource(R.string.trigger_other, type)
    }
}

@Composable
private fun intervalDescription(ticks: Long): String =
    when {
        ticks > 0 && ticks % TICKS_PER_HOUR == 0L ->
            (ticks / TICKS_PER_HOUR).toInt().let {
                pluralStringResource(R.plurals.trigger_every_hours, it, it)
            }
        ticks > 0 && ticks % TICKS_PER_MINUTE == 0L ->
            (ticks / TICKS_PER_MINUTE).toInt().let { pluralStringResource(R.plurals.trigger_every_minutes, it, it) }
        else -> (ticks / TICKS_PER_SECOND).toInt().let { pluralStringResource(R.plurals.trigger_every_seconds, it, it) }
    }

/** How long a run may last, when the trigger says; null for no limit. */
@Composable
private fun TaskTrigger.limitDescription(): String? {
    val ticks = maxRuntimeTicks?.takeIf { it > 0 } ?: return null
    return if (ticks % TICKS_PER_HOUR == 0L) {
        (ticks / TICKS_PER_HOUR).toInt().let { pluralStringResource(R.plurals.trigger_limit_hours, it, it) }
    } else {
        (ticks / TICKS_PER_MINUTE).toInt().let { pluralStringResource(R.plurals.trigger_limit_minutes, it, it) }
    }
}

/** [day], the server's English name, in the device's language; the name itself if it isn't a day. */
@Composable
private fun dayName(
    day: String?,
    style: TextStyle,
): String {
    val locale = LocalConfiguration.current.locales[0] ?: Locale.ROOT
    val weekday = DayOfWeek.entries.firstOrNull { it.name.equals(day, ignoreCase = true) } ?: return day.orEmpty()
    return weekday.getDisplayName(style, locale)
}

/**
 * A time of day, in ticks after midnight, as the device writes times: its language, and its 24-hour
 * setting. The server's trigger times are a clock reading, so no time zone is applied.
 */
@Composable
internal fun rememberTimeLabel(): (Long) -> String {
    val context = LocalContext.current
    val locale = LocalConfiguration.current.locales[0]
    return remember(context, locale) {
        val format = DateFormat.getTimeFormat(context).apply { timeZone = TimeZone.getTimeZone("UTC") }
        val label = { ticks: Long -> format.format(Date(ticks / MILLIS_PER_TICK)) }
        label
    }
}

/**
 * A new trigger's form: its kind, then what that kind needs (a time of day, a day, how often), and an
 * optional time limit. The time comes from the platform's time picker, in the device's 24-hour setting.
 * [inWindow] false draws its body for a frame.
 */
@Composable
internal fun AddTriggerDialog(
    actions: TasksActions,
    inWindow: Boolean = true,
) {
    var kindIndex by rememberSaveable { mutableIntStateOf(0) }
    var minutes by rememberSaveable { mutableIntStateOf(DEFAULT_HOUR * MINUTES_PER_HOUR) }
    var dayIndex by rememberSaveable { mutableIntStateOf(0) }
    var intervalTicks by rememberSaveable { mutableLongStateOf(DEFAULT_INTERVAL_HOURS * TICKS_PER_HOUR) }
    var limitHours by rememberSaveable { mutableIntStateOf(0) }
    val kind = TriggerKind.entries[kindIndex]
    val limit = limitHours.takeIf { it > 0 }?.let { it * TICKS_PER_HOUR }
    val timeTicks = minutes * TICKS_PER_MINUTE
    ConsoleConfirm(
        title = stringResource(R.string.tasks_trigger_new_title),
        message = stringResource(R.string.tasks_trigger_new_body),
        confirmLabel = stringResource(R.string.tasks_trigger_create),
        onConfirm = {
            actions.onAddTrigger(
                when (kind) {
                    TriggerKind.Daily -> TaskTrigger.daily(timeTicks, limit)
                    TriggerKind.Weekly -> TaskTrigger.weekly(TRIGGER_DAYS[dayIndex], timeTicks, limit)
                    TriggerKind.Interval -> TaskTrigger.interval(intervalTicks, limit)
                    TriggerKind.Startup -> TaskTrigger.startup(limit)
                },
            )
        },
        onDismissRequest = { actions.onAskAdd(false) },
        inWindow = inWindow,
        extraContent = {
            // The form can be taller than the dialog (a small screen, a large font), so it scrolls.
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(dimensionResource(DesR.dimen.padding_m)),
            ) {
                KindChips(kind) { kindIndex = it.ordinal }
                if (kind == TriggerKind.Daily || kind == TriggerKind.Weekly) TimeButton(minutes) { minutes = it }
                if (kind == TriggerKind.Weekly) DayChips(dayIndex) { dayIndex = it }
                if (kind == TriggerKind.Interval) IntervalChips(intervalTicks) { intervalTicks = it }
                LimitChips(limitHours) { limitHours = it }
            }
        },
    )
}

@Composable
private fun KindChips(
    selected: TriggerKind,
    onSelect: (TriggerKind) -> Unit,
) {
    WrappingRow {
        TriggerKind.entries.forEach { kind ->
            val label =
                when (kind) {
                    TriggerKind.Daily -> R.string.trigger_kind_daily
                    TriggerKind.Weekly -> R.string.trigger_kind_weekly
                    TriggerKind.Interval -> R.string.trigger_kind_interval
                    TriggerKind.Startup -> R.string.trigger_startup
                }
            BingeFilterChip(label = stringResource(label), selected = kind == selected, onClick = { onSelect(kind) })
        }
    }
}

/** The time as a button that opens the platform's time picker, in the device's 24-hour setting. */
@Composable
private fun TimeButton(
    minutes: Int,
    onPicked: (Int) -> Unit,
) {
    val context = LocalContext.current
    val timeLabel = rememberTimeLabel()
    BingeOutlinedButton(
        label = timeLabel(minutes * TICKS_PER_MINUTE),
        leadingIcon = Icons.Outlined.Schedule,
        onClick = {
            TimePickerDialog(
                context,
                { _, hour, minute -> onPicked(hour * MINUTES_PER_HOUR + minute) },
                minutes / MINUTES_PER_HOUR,
                minutes % MINUTES_PER_HOUR,
                DateFormat.is24HourFormat(context),
            ).show()
        },
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun DayChips(
    selected: Int,
    onSelect: (Int) -> Unit,
) {
    WrappingRow {
        TRIGGER_DAYS.forEachIndexed { index, day ->
            BingeFilterChip(label = dayName(day, TextStyle.SHORT), selected = index == selected, onClick = { onSelect(index) })
        }
    }
}

@Composable
private fun IntervalChips(
    selected: Long,
    onSelect: (Long) -> Unit,
) {
    WrappingRow {
        INTERVAL_MINUTES.forEach { minutes ->
            val ticks = minutes * TICKS_PER_MINUTE
            BingeFilterChip(
                label = stringResource(R.string.trigger_minutes_short, minutes),
                selected = ticks == selected,
                onClick = { onSelect(ticks) },
            )
        }
        INTERVAL_HOURS.forEach { hours ->
            val ticks = hours * TICKS_PER_HOUR
            BingeFilterChip(
                label = stringResource(R.string.trigger_hours_short, hours),
                selected = ticks == selected,
                onClick = { onSelect(ticks) },
            )
        }
    }
}

/** An optional time limit: none, or a number of hours after which the server stops the run. */
@Composable
private fun LimitChips(
    selected: Int,
    onSelect: (Int) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(dimensionResource(DesR.dimen.padding_s))) {
        Text(stringResource(R.string.tasks_trigger_limit), style = MaterialTheme.typography.labelLarge)
        WrappingRow {
            BingeFilterChip(label = stringResource(R.string.tasks_trigger_no_limit), selected = selected == 0, onClick = { onSelect(0) })
            LIMIT_HOURS.forEach { hours ->
                BingeFilterChip(
                    label = stringResource(R.string.trigger_hours_short, hours),
                    selected = hours == selected,
                    onClick = { onSelect(hours) },
                )
            }
        }
    }
}
