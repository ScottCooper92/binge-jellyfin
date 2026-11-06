package com.cooper.binge.companion.jellyfin.ui.console

import android.text.format.DateFormat
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.binge.designsystem.component.BingeAdaptiveSheet
import com.binge.designsystem.component.BingeFilledButton
import com.binge.designsystem.component.BingeFilterChip
import com.binge.designsystem.component.BingeNumberSlider
import com.binge.designsystem.component.BingeSheetHeader
import com.binge.designsystem.component.BingeSheetKind
import com.binge.designsystem.component.BingeTimeInput
import com.binge.designsystem.component.ExpressiveIconButton
import com.binge.designsystem.component.ItemGroup
import com.binge.designsystem.component.ListItem
import com.binge.designsystem.component.WrappingRow
import com.binge.designsystem.component.timeOfDayMinutes
import com.binge.designsystem.template.EmptyScreen
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

private const val MILLIS_PER_TICK = 10_000L
internal const val MINUTES_PER_HOUR = 60

/** One quarter-hour through one day, at quarter-hour stops. */
private val INTERVAL_MINUTES = 15..1440
private const val INTERVAL_STEP_MINUTES = 15

/** Marks the time-of-day fields, for the test that they are centred. */
internal const val TRIGGER_TIME_TAG = "trigger-time"

/** The time limits offered, in hours, beside none. */
private val LIMIT_HOURS = 1..24

private const val DEFAULT_HOUR = 3
private const val DEFAULT_INTERVAL_HOURS = 6

/** One task's triggers: each described, removable in place, and Add trigger. */
@Composable
internal fun TaskTriggers(
    task: ServerTask,
    state: TasksUiState.Ready,
    actions: TasksActions,
    inner: PaddingValues,
    listState: LazyListState,
) {
    val timeLabel = rememberTimeLabel()
    if (task.triggers.isEmpty()) {
        // Nothing yet: one screen that says so, with the one thing to do about it.
        Box(Modifier.fillMaxSize().padding(inner)) {
            EmptyScreen(
                message = stringResource(R.string.tasks_triggers_empty),
                title = stringResource(R.string.tasks_triggers_empty_title),
                icon = Icons.Outlined.Schedule,
            )
        }
        if (state.adding) AddTriggerSheet(actions)
        return
    }
    LazyColumn(
        state = listState,
        contentPadding = inner,
        verticalArrangement = Arrangement.spacedBy(dimensionResource(DesR.dimen.padding_l)),
    ) {
        if (task.description.isNotBlank()) item { Text(task.description, style = MaterialTheme.typography.bodyMedium) }
        item {
            ItemGroup(
                title = stringResource(R.string.tasks_triggers),
                rows = task.triggers.mapIndexed { index, trigger -> trigger.row(index, state.saving, actions, timeLabel) },
            )
        }
    }
    if (state.adding) AddTriggerSheet(actions)
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

/** The new trigger sheet: a task sheet over the page, closed by its own Add or by dismissing it. */
@Composable
internal fun AddTriggerSheet(actions: TasksActions) {
    BingeAdaptiveSheet(kind = BingeSheetKind.Task, onDismissRequest = { actions.onAskAdd(false) }) {
        AddTriggerForm(actions)
    }
}

/**
 * A new trigger's form: its kind, then what that kind needs (a time of day, a day, how often), and an
 * optional time limit. The time is entered in the form itself, in the device's 24-hour setting.
 * It is the content of [AddTriggerSheet], and draws without a window for a frame.
 */
@Composable
internal fun AddTriggerForm(
    actions: TasksActions,
    startKind: TriggerKind = TriggerKind.Daily,
    is24Hour: Boolean = DateFormat.is24HourFormat(LocalContext.current),
) {
    var kindIndex by rememberSaveable { mutableIntStateOf(startKind.ordinal) }
    var hourText by rememberSaveable { mutableStateOf(DEFAULT_HOUR.toString().padStart(2, '0')) }
    var minuteText by rememberSaveable { mutableStateOf("00") }
    var afternoon by rememberSaveable { mutableStateOf(false) }
    val minutes = timeOfDayMinutes(hourText, minuteText, is24Hour, afternoon)
    var dayIndex by rememberSaveable { mutableIntStateOf(0) }
    var intervalTicks by rememberSaveable { mutableLongStateOf(DEFAULT_INTERVAL_HOURS * TICKS_PER_HOUR) }
    var limitHours by rememberSaveable { mutableIntStateOf(0) }
    val kind = TriggerKind.entries[kindIndex]
    val limit = limitHours.takeIf { it > 0 }?.let { it * TICKS_PER_HOUR }
    Column {
        BingeSheetHeader(
            title = stringResource(R.string.tasks_trigger_new_title),
            subtitle = stringResource(R.string.tasks_trigger_new_body),
        )
        // The form can be taller than the sheet (a small screen, a large font, the keyboard), so the fields scroll
        // and Add stays under them, in view.
        Column(
            modifier =
                Modifier
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(dimensionResource(DesR.dimen.padding_m)),
        ) {
            Column(
                modifier = Modifier.padding(horizontal = dimensionResource(DesR.dimen.padding_m)),
                verticalArrangement = Arrangement.spacedBy(dimensionResource(DesR.dimen.padding_m)),
            ) {
                KindChips(kind) { kindIndex = it.ordinal }
                if (kind == TriggerKind.Daily || kind == TriggerKind.Weekly) {
                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        BingeTimeInput(
                            hour = hourText,
                            minute = minuteText,
                            onHour = { hourText = it },
                            onMinute = { minuteText = it },
                            is24Hour = is24Hour,
                            afternoon = afternoon,
                            onAfternoon = { afternoon = it },
                            modifier = Modifier.testTag(TRIGGER_TIME_TAG),
                        )
                    }
                }
            }
            if (kind == TriggerKind.Weekly) DayChips(dayIndex) { dayIndex = it }
            Column(
                modifier = Modifier.padding(horizontal = dimensionResource(DesR.dimen.padding_m)),
                verticalArrangement = Arrangement.spacedBy(dimensionResource(DesR.dimen.padding_m)),
            ) {
                if (kind == TriggerKind.Interval) IntervalSlider(intervalTicks) { intervalTicks = it }
                LimitSlider(limitHours) { limitHours = it }
            }
        }
        BingeFilledButton(
            label = stringResource(R.string.tasks_trigger_create),
            // A time of day has to be a real one; the other kinds have none.
            enabled = minutes != null || (kind != TriggerKind.Daily && kind != TriggerKind.Weekly),
            onClick = {
                val timeTicks = { (minutes ?: 0) * TICKS_PER_MINUTE }
                actions.onAddTrigger(
                    when (kind) {
                        TriggerKind.Daily -> TaskTrigger.daily(timeTicks(), limit)
                        TriggerKind.Weekly -> TaskTrigger.weekly(TRIGGER_DAYS[dayIndex], timeTicks(), limit)
                        TriggerKind.Interval -> TaskTrigger.interval(intervalTicks, limit)
                        TriggerKind.Startup -> TaskTrigger.startup(limit)
                    },
                )
            },
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = dimensionResource(DesR.dimen.padding_m), vertical = dimensionResource(DesR.dimen.padding_m)),
        )
    }
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

@Composable
private fun DayChips(
    selected: Int,
    onSelect: (Int) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(dimensionResource(DesR.dimen.padding_s)),
    ) {
        TRIGGER_DAYS.forEachIndexed { index, day ->
            BingeFilterChip(label = dayName(day, TextStyle.SHORT), selected = index == selected, onClick = { onSelect(index) })
        }
    }
}

@Composable
private fun IntervalSlider(
    selected: Long,
    onSelect: (Long) -> Unit,
) {
    val resources = LocalContext.current.resources
    val title = stringResource(R.string.trigger_kind_interval)
    Column(verticalArrangement = Arrangement.spacedBy(dimensionResource(DesR.dimen.padding_s))) {
        Text(title, style = MaterialTheme.typography.labelLarge)
        BingeNumberSlider(
            value = (selected / TICKS_PER_MINUTE).toInt(),
            onValueChange = { it?.let { minutes -> onSelect(minutes * TICKS_PER_MINUTE) } },
            range = INTERVAL_MINUTES,
            step = INTERVAL_STEP_MINUTES,
            format = {
                if (it % MINUTES_PER_HOUR == 0) {
                    resources.getString(R.string.trigger_hours_short, it / MINUTES_PER_HOUR)
                } else {
                    resources.getString(R.string.trigger_minutes_short, it)
                }
            },
            description = title,
        )
    }
}

/** An optional time limit: whole hours from one to 24 on a slider, whose open end is no limit. */
@Composable
private fun LimitSlider(
    hours: Int,
    onChange: (Int) -> Unit,
) {
    val resources = LocalContext.current.resources
    val title = stringResource(R.string.tasks_trigger_limit)
    Column(verticalArrangement = Arrangement.spacedBy(dimensionResource(DesR.dimen.padding_s))) {
        Text(title, style = MaterialTheme.typography.labelLarge)
        BingeNumberSlider(
            value = hours.takeIf { it > 0 },
            onValueChange = { onChange(it ?: 0) },
            range = LIMIT_HOURS,
            format = { resources.getQuantityString(R.plurals.trigger_limit_value, it, it) },
            description = title,
            openEndLabel = stringResource(R.string.tasks_trigger_no_limit),
        )
    }
}
