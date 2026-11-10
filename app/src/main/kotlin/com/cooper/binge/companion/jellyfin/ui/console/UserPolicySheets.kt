package com.cooper.binge.companion.jellyfin.ui.console

import android.content.res.Resources
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.binge.designsystem.component.BingeAdaptiveSheet
import com.binge.designsystem.component.BingeChoice
import com.binge.designsystem.component.BingeChoiceList
import com.binge.designsystem.component.BingeChoiceSheet
import com.binge.designsystem.component.BingeSheetKind
import com.binge.designsystem.component.TextEntrySurface
import com.cooper.binge.companion.jellyfin.R
import com.cooper.binge.companion.jellyfin.server.POLICY_ALLOWED_TAGS
import com.cooper.binge.companion.jellyfin.server.POLICY_BITRATE_LIMIT
import com.cooper.binge.companion.jellyfin.server.POLICY_BLOCKED_TAGS
import com.cooper.binge.companion.jellyfin.server.POLICY_LOCKOUT
import com.cooper.binge.companion.jellyfin.server.POLICY_MAX_RATING
import com.cooper.binge.companion.jellyfin.server.POLICY_MAX_SESSIONS
import com.cooper.binge.companion.jellyfin.server.POLICY_MAX_SUB_RATING
import com.cooper.binge.companion.jellyfin.server.POLICY_SYNC_PLAY
import com.cooper.binge.companion.jellyfin.server.SYNC_PLAY_ACCESS
import com.cooper.binge.companion.jellyfin.server.int
import com.cooper.binge.companion.jellyfin.server.string
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** The choices a section opens in a sheet. */
internal enum class PolicyPicker { Lockout, Sessions, Bitrate, SyncPlay, Rating, SubRating }

/** What a section adds to: a list of tags, or the access schedule. */
internal enum class PolicyAdd { BlockedTag, AllowedTag, Schedule }

private const val BITS_PER_MEGABIT = 1_000_000
private const val NEVER = -1
private const val BITS_PER_KILOBIT = 1_000
private const val KILOBITS_PER_MEGABIT = 1_000
private val LOCKOUT_ATTEMPTS = 1..20
private val SESSIONS_AT_ONCE = 1..20
private val BITRATE_KILOBITS = 1_000..100_000

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
internal fun bitrateLabel(bits: Int): String = bitrateText(LocalContext.current.resources, bits)

/** [bitrateLabel] for a caller that is not composable: "No limit" for none, else whole megabits as such and the rest to a tenth. */
internal fun bitrateText(
    resources: Resources,
    bits: Int,
): String {
    if (bits <= 0) return resources.getString(R.string.users_no_limit)
    val mbps = bits.toDouble() / BITS_PER_MEGABIT
    val shown = if (bits % BITS_PER_MEGABIT == 0) (bits / BITS_PER_MEGABIT).toString() else "%.1f".format(java.util.Locale.ROOT, mbps)
    return resources.getString(R.string.users_mbps, shown)
}

/** A lockout count; 0, which a stored policy may still hold, is the server's default. */
internal fun lockoutText(
    resources: Resources,
    attempts: Int,
): String =
    if (attempts <= 0) {
        resources.getString(R.string.users_lockout_default)
    } else {
        resources.getQuantityString(R.plurals.users_lockout_after, attempts, attempts)
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
            val resources = LocalContext.current.resources
            // The server keeps "its default" as 3 and reads 3 back, so there is no default to switch on: a count, or never (-1).
            val current = policy.int(POLICY_LOCKOUT) ?: 0
            NumberSliderSheet(
                title = stringResource(R.string.users_lockout),
                value = current.takeIf { it >= 0 },
                range = LOCKOUT_ATTEMPTS,
                format = { lockoutText(resources, it) },
                openEndLabel = stringResource(R.string.users_lockout_never),
                onSave = { attempts -> actions.onPolicy(POLICY_LOCKOUT, JsonPrimitive(attempts ?: NEVER)) },
                onDismiss = onDismiss,
            )
        }
        PolicyPicker.Sessions -> {
            val resources = LocalContext.current.resources
            NumberSliderSheet(
                title = stringResource(R.string.users_max_sessions),
                value = (policy.int(POLICY_MAX_SESSIONS) ?: 0).takeIf { it > 0 },
                range = SESSIONS_AT_ONCE,
                format = { resources.getQuantityString(R.plurals.users_sessions_at_once, it, it) },
                openEndLabel = stringResource(R.string.users_no_limit),
                onSave = { sessions -> actions.onPolicy(POLICY_MAX_SESSIONS, JsonPrimitive(sessions ?: 0)) },
                onDismiss = onDismiss,
            )
        }
        PolicyPicker.Bitrate -> {
            val resources = LocalContext.current.resources
            // In kilobits, a step to the megabit, so a stored 2.5 Mbps reads as 2.5 between two steps and is not rewritten on open.
            val stored = (policy.int(POLICY_BITRATE_LIMIT) ?: 0).takeIf { it > 0 }
            NumberSliderSheet(
                title = stringResource(R.string.users_bitrate),
                value = stored?.let { (it / BITS_PER_KILOBIT).coerceAtLeast(1) },
                range = BITRATE_KILOBITS,
                format = { bitrateText(resources, it * BITS_PER_KILOBIT) },
                openEndLabel = stringResource(R.string.users_no_limit),
                onSave = { kilobits -> actions.onPolicy(POLICY_BITRATE_LIMIT, JsonPrimitive((kilobits ?: 0) * BITS_PER_KILOBIT)) },
                onDismiss = onDismiss,
                step = KILOBITS_PER_MEGABIT,
            )
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
