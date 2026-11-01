package com.cooper.binge.companion.jellyfin.ui.console

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Schedule
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
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.binge.designsystem.component.BingeAdaptiveSheet
import com.binge.designsystem.component.BingeChoice
import com.binge.designsystem.component.BingeChoiceList
import com.binge.designsystem.component.BingeChoiceSheet
import com.binge.designsystem.component.BingeSheetKind
import com.binge.designsystem.component.BingeTextButton
import com.binge.designsystem.component.ItemGroup
import com.binge.designsystem.component.ListItem
import com.binge.designsystem.component.TextEntrySurface
import com.cooper.binge.companion.jellyfin.R
import com.cooper.binge.companion.jellyfin.server.AccessSchedule
import com.cooper.binge.companion.jellyfin.server.POLICY_ALLOWED_TAGS
import com.cooper.binge.companion.jellyfin.server.POLICY_BITRATE_LIMIT
import com.cooper.binge.companion.jellyfin.server.POLICY_BLOCKED_TAGS
import com.cooper.binge.companion.jellyfin.server.POLICY_LOCKOUT
import com.cooper.binge.companion.jellyfin.server.POLICY_MAX_RATING
import com.cooper.binge.companion.jellyfin.server.POLICY_MAX_SESSIONS
import com.cooper.binge.companion.jellyfin.server.POLICY_MAX_SUB_RATING
import com.cooper.binge.companion.jellyfin.server.POLICY_SCHEDULES
import com.cooper.binge.companion.jellyfin.server.POLICY_SYNC_PLAY
import com.cooper.binge.companion.jellyfin.server.SCHEDULE_DAYS
import com.cooper.binge.companion.jellyfin.server.SYNC_PLAY_ACCESS
import com.cooper.binge.companion.jellyfin.server.int
import com.cooper.binge.companion.jellyfin.server.json
import com.cooper.binge.companion.jellyfin.server.string
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import com.binge.designsystem.R as DesR

/** The choices a section opens in a sheet. */
internal enum class PolicyPicker { Lockout, Sessions, Bitrate, SyncPlay, Rating, SubRating }

/** What a section adds to: a list of tags, or the access schedule. */
internal enum class PolicyAdd { BlockedTag, AllowedTag, Schedule }

private const val BITS_PER_MEGABIT = 1_000_000
private const val NEVER = -1
private val LOCKOUT_PRESETS = listOf(0, NEVER, 3, 5, 10, 20)
private val SESSION_PRESETS = listOf(0, 1, 2, 3, 5, 10)
private val BITRATE_PRESETS_MBPS = listOf(0, 1, 2, 4, 6, 8, 10, 15, 20, 40, 60, 80)
private const val HOURS_IN_DAY = 24
private const val MINUTES_IN_HOUR = 60

/** A list with [id] in it or out of it, for a setting that is a list of ids. */
internal fun memberChange(
    id: String,
    on: Boolean,
): (List<JsonElement>) -> List<JsonElement> =
    { list ->
        val without = list.filterNot { (it as? JsonPrimitive)?.content == id }
        if (on) without + JsonPrimitive(id) else without
    }

@Composable
internal fun lockoutLabel(attempts: Int): String =
    when {
        attempts == 0 -> stringResource(R.string.users_lockout_default)
        attempts < 0 -> stringResource(R.string.users_lockout_never)
        else -> pluralStringResource(R.plurals.users_lockout_after, attempts, attempts)
    }

@Composable
internal fun sessionsLabel(sessions: Int): String =
    if (sessions <=
        0
    ) {
        stringResource(R.string.users_no_limit)
    } else {
        pluralStringResource(R.plurals.users_sessions_at_once, sessions, sessions)
    }

/** A bitrate in bits per second, as megabits. */
@Composable
internal fun bitrateLabel(bits: Int): String {
    if (bits <= 0) return stringResource(R.string.users_no_limit)
    val mbps = bits.toDouble() / BITS_PER_MEGABIT
    val shown = if (bits % BITS_PER_MEGABIT == 0) (bits / BITS_PER_MEGABIT).toString() else "%.1f".format(java.util.Locale.ROOT, mbps)
    return stringResource(R.string.users_mbps, shown)
}

@Composable
internal fun syncPlayLabel(value: String): String =
    when (value) {
        "CreateAndJoinGroups" -> stringResource(R.string.users_sync_play_create)
        "JoinGroups" -> stringResource(R.string.users_sync_play_join)
        "None" -> stringResource(R.string.users_sync_play_none)
        else -> value
    }

internal fun unratedLabel(type: String): Int =
    when (type) {
        "Movie" -> R.string.users_unrated_movie
        "Trailer" -> R.string.users_unrated_trailer
        "Series" -> R.string.users_unrated_series
        "Music" -> R.string.users_unrated_music
        "Book" -> R.string.users_unrated_book
        "LiveTvChannel" -> R.string.users_unrated_channel
        "LiveTvProgram" -> R.string.users_unrated_program
        "ChannelContent" -> R.string.users_unrated_channel_content
        else -> R.string.users_unrated_other
    }

internal fun dayLabel(day: String): Int =
    when (day) {
        "Sunday" -> R.string.users_day_sunday
        "Monday" -> R.string.users_day_monday
        "Tuesday" -> R.string.users_day_tuesday
        "Wednesday" -> R.string.users_day_wednesday
        "Thursday" -> R.string.users_day_thursday
        "Friday" -> R.string.users_day_friday
        "Saturday" -> R.string.users_day_saturday
        "Everyday" -> R.string.users_day_every
        "Weekday" -> R.string.users_day_weekdays
        else -> R.string.users_day_weekends
    }

/** An hour of the day as a clock time: 8.5 is 08:30. */
internal fun hours(hour: Double): String {
    val minutes = (hour * MINUTES_IN_HOUR).toInt()
    return "%02d:%02d".format(java.util.Locale.ROOT, minutes / MINUTES_IN_HOUR, minutes % MINUTES_IN_HOUR)
}

@Composable
internal fun PolicyPickerSheet(
    picker: PolicyPicker,
    policy: JsonObject,
    choices: UserChoices,
    actions: UsersActions,
    onDismiss: () -> Unit,
) {
    val ratings = (choices.ratings as? Loaded.Ready)?.value.orEmpty()
    when (picker) {
        PolicyPicker.Lockout -> {
            val current = policy.int(POLICY_LOCKOUT) ?: 0
            NumberSheet(R.string.users_lockout, LOCKOUT_PRESETS, current, { lockoutLabel(it) }, onDismiss) {
                actions.onPolicy(POLICY_LOCKOUT, JsonPrimitive(it))
            }
        }
        PolicyPicker.Sessions -> {
            val current = policy.int(POLICY_MAX_SESSIONS) ?: 0
            NumberSheet(R.string.users_max_sessions, SESSION_PRESETS, current, { sessionsLabel(it) }, onDismiss) {
                actions.onPolicy(POLICY_MAX_SESSIONS, JsonPrimitive(it))
            }
        }
        PolicyPicker.Bitrate -> {
            val current = policy.int(POLICY_BITRATE_LIMIT) ?: 0
            NumberSheet(
                R.string.users_bitrate,
                BITRATE_PRESETS_MBPS.map { it * BITS_PER_MEGABIT },
                current,
                { bitrateLabel(it) },
                onDismiss,
            ) {
                actions.onPolicy(POLICY_BITRATE_LIMIT, JsonPrimitive(it))
            }
        }
        PolicyPicker.SyncPlay ->
            BingeChoiceSheet(
                title = stringResource(R.string.users_sync_play),
                choices = BingeChoiceList.Ready(SYNC_PLAY_ACCESS.map { BingeChoice(it, syncPlayLabel(it)) }),
                selected = policy.string(POLICY_SYNC_PLAY),
                onSelect = { actions.onPolicy(POLICY_SYNC_PLAY, JsonPrimitive(it)) },
                onDismissRequest = onDismiss,
            )
        PolicyPicker.Rating -> {
            val none = stringResource(R.string.users_no_limit)
            val current = policy.int(POLICY_MAX_RATING)
            val options =
                listOf(BingeChoice("", none)) +
                    ratings.sortedBy { it.value }.map { BingeChoice(it.value.toString() + ":" + it.name, it.name) }
            BingeChoiceSheet(
                title = stringResource(R.string.users_max_rating),
                choices = BingeChoiceList.Ready(options),
                selected = current?.let { c -> options.firstOrNull { it.value.substringBefore(':') == c.toString() }?.value } ?: "",
                onSelect = { value ->
                    actions.onPolicy(POLICY_MAX_RATING, value.substringBefore(':').toIntOrNull()?.let(::JsonPrimitive) ?: JsonNull)
                },
                onDismissRequest = onDismiss,
            )
        }
        PolicyPicker.SubRating -> {
            val none = stringResource(R.string.users_no_limit)
            val current = policy.int(POLICY_MAX_SUB_RATING)
            val scores = ratings.mapNotNull { it.subScore }.distinct().sorted()
            BingeChoiceSheet(
                title = stringResource(R.string.users_max_sub_rating),
                choices = BingeChoiceList.Ready(listOf(BingeChoice("", none)) + scores.map { BingeChoice(it.toString(), it.toString()) }),
                selected = current?.toString() ?: "",
                onSelect = { value -> actions.onPolicy(POLICY_MAX_SUB_RATING, value.toIntOrNull()?.let(::JsonPrimitive) ?: JsonNull) },
                onDismissRequest = onDismiss,
            )
        }
    }
}

/** A pick from [presets], and the user's own value if it is another. */
@Composable
private fun NumberSheet(
    title: Int,
    presets: List<Int>,
    current: Int,
    label: @Composable (Int) -> String,
    onDismiss: () -> Unit,
    onPick: (Int) -> Unit,
) {
    val all = (presets + current).distinct().sortedWith(compareBy { if (it < 0) Int.MAX_VALUE else it })
    BingeChoiceSheet(
        title = stringResource(title),
        choices = BingeChoiceList.Ready(all.map { BingeChoice(it.toString(), label(it)) }),
        selected = current.toString(),
        onSelect = { value -> value.toIntOrNull()?.let(onPick) },
        onDismissRequest = onDismiss,
    )
}

/** The sheet a section's Add opens. */
@Composable
internal fun PolicyAddSheet(
    add: PolicyAdd,
    actions: UsersActions,
    onDismiss: () -> Unit,
) {
    BingeAdaptiveSheet(kind = BingeSheetKind.Task, onDismissRequest = onDismiss) {
        when (add) {
            PolicyAdd.BlockedTag -> TagForm(POLICY_BLOCKED_TAGS, actions, onDismiss)
            PolicyAdd.AllowedTag -> TagForm(POLICY_ALLOWED_TAGS, actions, onDismiss)
            PolicyAdd.Schedule -> ScheduleForm(actions, onDismiss)
        }
    }
}

/** A tag to add to [key]'s list. Saves with its button, and closes. Draws without a window for a frame. */
@Composable
internal fun TagForm(
    key: String,
    actions: UsersActions,
    onClose: () -> Unit,
) {
    var tag by rememberSaveable { mutableStateOf("") }
    TextEntrySurface(
        title = stringResource(R.string.users_add_tag),
        value = tag,
        onValueChange = { tag = it },
        onSubmit = {
            actions.onList(key, memberChange(tag.trim(), true))
            onClose()
        },
        onCancel = onClose,
        submitLabel = stringResource(R.string.users_add_action_short),
        hint = stringResource(R.string.users_tag_hint),
        minLines = 1,
        maxLines = 1,
    )
}

/** A schedule in three steps: the day, when it starts and when it ends, each on the hour. Draws without a window for a frame. */
@Composable
internal fun ScheduleForm(
    actions: UsersActions,
    onClose: () -> Unit,
    startStep: Int = 0,
    startDay: String = SCHEDULE_DAYS.first(),
    startHour: Int = 0,
) {
    var step by rememberSaveable { mutableIntStateOf(startStep) }
    var day by rememberSaveable { mutableStateOf(startDay) }
    var from by rememberSaveable { mutableIntStateOf(startHour) }
    Column(
        modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(dimensionResource(DesR.dimen.padding_m)),
        verticalArrangement = Arrangement.spacedBy(dimensionResource(DesR.dimen.padding_m)),
    ) {
        Text(
            stringResource(
                when (step) {
                    0 -> R.string.users_schedule_day
                    1 -> R.string.users_schedule_start
                    else -> R.string.users_schedule_end
                },
            ),
            style = MaterialTheme.typography.titleLarge,
        )
        ItemGroup(
            title = null,
            rows =
                when (step) {
                    0 ->
                        SCHEDULE_DAYS.map { d ->
                            ListItem(icon = Icons.Outlined.Today, label = stringResource(dayLabel(d)), onClick = {
                                day = d
                                step = 1
                            })
                        }
                    1 ->
                        (0 until HOURS_IN_DAY).map { h ->
                            ListItem(icon = Icons.Outlined.Schedule, label = hours(h.toDouble()), onClick = {
                                from = h
                                step = 2
                            })
                        }
                    else ->
                        (from + 1..HOURS_IN_DAY).map { h ->
                            ListItem(
                                icon = Icons.Outlined.Schedule,
                                label = hours(h.toDouble()),
                                onClick = {
                                    actions.onList(POLICY_SCHEDULES) { it + AccessSchedule(day, from.toDouble(), h.toDouble()).json() }
                                    onClose()
                                },
                            )
                        }
                },
        )
        BingeTextButton(
            label = stringResource(if (step == 0) R.string.users_cancel else R.string.users_back),
            onClick = { if (step == 0) onClose() else step -= 1 },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
