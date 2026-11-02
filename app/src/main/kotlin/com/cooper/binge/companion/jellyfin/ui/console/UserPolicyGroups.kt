package com.cooper.binge.companion.jellyfin.ui.console

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.ChildCare
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.CollectionsBookmark
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DeleteForever
import androidx.compose.material.icons.outlined.Devices
import androidx.compose.material.icons.outlined.EventBusy
import androidx.compose.material.icons.outlined.LiveTv
import androidx.compose.material.icons.outlined.Lyrics
import androidx.compose.material.icons.outlined.PhonelinkSetup
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Subtitles
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material.icons.outlined.Tag
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import com.binge.designsystem.component.BingeFilledButton
import com.binge.designsystem.component.ExpressiveIconButton
import com.binge.designsystem.component.ItemGroup
import com.binge.designsystem.component.ListItem
import com.binge.designsystem.component.ListItemDestination
import com.binge.designsystem.component.ListRowSkeleton
import com.cooper.binge.companion.jellyfin.R
import com.cooper.binge.companion.jellyfin.server.AccessSchedule
import com.cooper.binge.companion.jellyfin.server.JellyfinServerProfile
import com.cooper.binge.companion.jellyfin.server.POLICY_ALLOWED_TAGS
import com.cooper.binge.companion.jellyfin.server.POLICY_ALL_CHANNELS
import com.cooper.binge.companion.jellyfin.server.POLICY_ALL_DEVICES
import com.cooper.binge.companion.jellyfin.server.POLICY_BLOCKED_TAGS
import com.cooper.binge.companion.jellyfin.server.POLICY_BLOCK_UNRATED
import com.cooper.binge.companion.jellyfin.server.POLICY_CHANNELS
import com.cooper.binge.companion.jellyfin.server.POLICY_COLLECTIONS
import com.cooper.binge.companion.jellyfin.server.POLICY_CONVERSION
import com.cooper.binge.companion.jellyfin.server.POLICY_DELETION
import com.cooper.binge.companion.jellyfin.server.POLICY_DELETION_FOLDERS
import com.cooper.binge.companion.jellyfin.server.POLICY_DEVICES
import com.cooper.binge.companion.jellyfin.server.POLICY_DOWNLOADING
import com.cooper.binge.companion.jellyfin.server.POLICY_LIVE_TV_ACCESS
import com.cooper.binge.companion.jellyfin.server.POLICY_LIVE_TV_MANAGEMENT
import com.cooper.binge.companion.jellyfin.server.POLICY_LYRICS
import com.cooper.binge.companion.jellyfin.server.POLICY_MAX_RATING
import com.cooper.binge.companion.jellyfin.server.POLICY_MAX_SUB_RATING
import com.cooper.binge.companion.jellyfin.server.POLICY_PUBLIC_SHARING
import com.cooper.binge.companion.jellyfin.server.POLICY_SCHEDULES
import com.cooper.binge.companion.jellyfin.server.POLICY_SUBTITLES
import com.cooper.binge.companion.jellyfin.server.UNRATED_ITEM_TYPES
import com.cooper.binge.companion.jellyfin.server.accessSchedules
import com.cooper.binge.companion.jellyfin.server.asAccessSchedule
import com.cooper.binge.companion.jellyfin.server.flag
import com.cooper.binge.companion.jellyfin.server.has
import com.cooper.binge.companion.jellyfin.server.int
import com.cooper.binge.companion.jellyfin.server.string
import com.cooper.binge.companion.jellyfin.server.strings
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import com.binge.designsystem.R as DesR

@Composable
internal fun ContentSection(
    state: UsersUiState.Ready,
    policy: JsonObject,
    profile: JellyfinServerProfile,
    actions: UsersActions,
) {
    ItemGroup(
        title = null,
        rows =
            listOfNotNull(
                policy.switchRow(POLICY_DOWNLOADING, Icons.Outlined.CloudDownload, R.string.users_downloading, actions),
                policy.switchRow(POLICY_CONVERSION, Icons.Outlined.Sync, R.string.users_conversion, actions),
                policy.switchRow(POLICY_PUBLIC_SHARING, Icons.Outlined.Share, R.string.users_public_sharing, actions),
                policy.switchRow(POLICY_COLLECTIONS, Icons.Outlined.CollectionsBookmark, R.string.users_collections, actions).takeIf {
                    profile.hasContentManagementPermissions
                },
                policy.switchRow(POLICY_SUBTITLES, Icons.Outlined.Subtitles, R.string.users_subtitles, actions).takeIf {
                    profile.hasContentManagementPermissions
                },
                policy
                    .switchRow(
                        POLICY_LYRICS,
                        Icons.Outlined.Lyrics,
                        R.string.users_lyrics,
                        actions,
                    ).takeIf { profile.hasContentManagementPermissions },
            ),
    )
    if (!policy.has(POLICY_DELETION)) return
    val all = policy.flag(POLICY_DELETION)
    val chosen = policy.strings(POLICY_DELETION_FOLDERS)
    ItemGroup(
        title = stringResource(R.string.users_deletion),
        rows =
            listOf(
                ListItem(
                    icon = Icons.Outlined.DeleteForever,
                    label = stringResource(R.string.users_deletion_all),
                    toggled = all,
                    onClick = { actions.onPolicy(POLICY_DELETION, kotlinx.serialization.json.JsonPrimitive(!all)) },
                ),
            ) +
                if (all) {
                    emptyList()
                } else {
                    state.libraries.map { library ->
                        val on = library.id in chosen
                        ListItem(
                            icon = typeIcon(library.type),
                            label = library.name,
                            toggled = on,
                            onClick = { actions.onList(POLICY_DELETION_FOLDERS, memberChange(library.id, !on)) },
                        )
                    }
                },
    )
}

/** The rows of a list the user picks some of, or a status while it loads or if it can't. */
@Composable
private fun <T> PickList(
    loaded: Loaded<List<T>>,
    section: UserSection,
    actions: UsersActions,
    row: @Composable (T) -> ListItem,
) {
    when (loaded) {
        Loaded.Idle, Loaded.Loading ->
            Column(verticalArrangement = Arrangement.spacedBy(dimensionResource(DesR.dimen.list_row_spacing))) {
                repeat(PICK_LIST_SKELETON_ROWS) { index ->
                    ListRowSkeleton(description = stringResource(R.string.users_loading).takeIf { index == 0 })
                }
            }
        is Loaded.Failed ->
            Column(verticalArrangement = Arrangement.spacedBy(dimensionResource(DesR.dimen.padding_m))) {
                Text(stringResource(loaded.failure.message), style = MaterialTheme.typography.bodyMedium)
                BingeFilledButton(label = stringResource(R.string.console_retry), onClick = { actions.onChoicesRetry(section) })
            }
        is Loaded.Ready ->
            ItemGroup(
                title = null,
                rows =
                    loaded.value
                        .map {
                            row(it)
                        }.ifEmpty {
                            listOf(
                                ListItem(icon = Icons.Outlined.Block, label = stringResource(R.string.users_none_here), clickable = false),
                            )
                        },
            )
    }
}

@Composable
internal fun LiveTvSection(
    policy: JsonObject,
    choices: UserChoices,
    actions: UsersActions,
    section: UserSection,
) {
    ItemGroup(
        title = null,
        rows =
            listOfNotNull(
                policy.switchRow(POLICY_LIVE_TV_ACCESS, Icons.Outlined.LiveTv, R.string.users_live_tv_access, actions),
                policy.switchRow(POLICY_LIVE_TV_MANAGEMENT, Icons.Outlined.Settings, R.string.users_live_tv_management, actions),
                policy.switchRow(POLICY_ALL_CHANNELS, Icons.Outlined.LiveTv, R.string.users_all_channels, actions),
            ),
    )
    if (policy.has(POLICY_ALL_CHANNELS) && !policy.flag(POLICY_ALL_CHANNELS)) {
        val chosen = policy.strings(POLICY_CHANNELS)
        PickList(choices.channels, section, actions) { channel ->
            val on = channel.id in chosen
            ListItem(
                icon = Icons.Outlined.LiveTv,
                label = channel.name.ifBlank { channel.id },
                toggled = on,
                onClick = { actions.onList(POLICY_CHANNELS, memberChange(channel.id, !on)) },
            )
        }
    }
}

@Composable
internal fun DevicesSection(
    policy: JsonObject,
    choices: UserChoices,
    actions: UsersActions,
    section: UserSection,
) {
    ItemGroup(
        title = null,
        rows = listOfNotNull(policy.switchRow(POLICY_ALL_DEVICES, Icons.Outlined.Devices, R.string.users_all_devices, actions)),
    )
    if (policy.has(POLICY_ALL_DEVICES) && !policy.flag(POLICY_ALL_DEVICES)) {
        val chosen = policy.strings(POLICY_DEVICES)
        PickList(choices.devices, section, actions) { device ->
            val on = device.id in chosen
            ListItem(
                icon = Icons.Outlined.PhonelinkSetup,
                label = device.displayName,
                detail = device.app.ifBlank { null },
                toggled = on,
                onClick = { actions.onList(POLICY_DEVICES, memberChange(device.id, !on)) },
            )
        }
    }
}

@Composable
internal fun ParentalSection(
    policy: JsonObject,
    choices: UserChoices,
    profile: JellyfinServerProfile,
    actions: UsersActions,
    onPick: (PolicyPicker) -> Unit,
    onAdd: (PolicyAdd) -> Unit,
) {
    val ratings = (choices.ratings as? Loaded.Ready)?.value.orEmpty()
    val rating = policy.int(POLICY_MAX_RATING)
    val sub = policy.int(POLICY_MAX_SUB_RATING)
    ItemGroup(
        title = null,
        rows =
            listOfNotNull(
                ListItem(
                    icon = Icons.Outlined.ChildCare,
                    label = stringResource(R.string.users_max_rating),
                    detail =
                        ratings.firstOrNull { it.value == rating }?.name ?: rating?.toString() ?: stringResource(R.string.users_no_limit),
                    onClick = { onPick(PolicyPicker.Rating) },
                    loading = choices.ratings is Loaded.Loading,
                    enabled = choices.ratings is Loaded.Ready,
                ),
                ListItem(
                    icon = Icons.Outlined.ChildCare,
                    label = stringResource(R.string.users_max_sub_rating),
                    detail = sub?.toString() ?: stringResource(R.string.users_no_limit),
                    onClick = { onPick(PolicyPicker.SubRating) },
                    enabled = choices.ratings is Loaded.Ready,
                ).takeIf { profile.hasParentalSubRating },
            ),
    )
    if (choices.ratings is Loaded.Failed) {
        PolicyStatus(stringResource(choices.ratings.failure.message))
        BingeFilledButton(label = stringResource(R.string.console_retry), onClick = { actions.onChoicesRetry(UserSection.Parental) })
    }
    if (policy.has(POLICY_BLOCK_UNRATED)) {
        val blocked = policy.strings(POLICY_BLOCK_UNRATED)
        ItemGroup(
            title = stringResource(R.string.users_block_unrated),
            rows =
                UNRATED_ITEM_TYPES.map { type ->
                    val on = type in blocked
                    ListItem(
                        icon = Icons.Outlined.Block,
                        label = stringResource(unratedLabel(type)),
                        toggled = on,
                        onClick = { actions.onList(POLICY_BLOCK_UNRATED, memberChange(type, !on)) },
                    )
                },
        )
    }
    if (policy.has(POLICY_BLOCKED_TAGS)) {
        TagGroup(R.string.users_blocked_tags, POLICY_BLOCKED_TAGS, policy, actions) { onAdd(PolicyAdd.BlockedTag) }
    }
    if (profile.hasAllowedTags && policy.has(POLICY_ALLOWED_TAGS)) {
        TagGroup(R.string.users_allowed_tags, POLICY_ALLOWED_TAGS, policy, actions) { onAdd(PolicyAdd.AllowedTag) }
    }
    if (policy.has(POLICY_SCHEDULES)) ScheduleGroup(policy, actions) { onAdd(PolicyAdd.Schedule) }
}

/** A list of tags, each with a red remove, and Add. Each change saves as it's made. */
@Composable
private fun TagGroup(
    title: Int,
    key: String,
    policy: JsonObject,
    actions: UsersActions,
    onAdd: () -> Unit,
) {
    ItemGroup(
        title = stringResource(title),
        rows =
            policy.strings(key).map { tag ->
                ListItem(
                    icon = Icons.Outlined.Tag,
                    label = tag,
                    clickable = false,
                    trailingContent = {
                        ExpressiveIconButton(
                            onClick = { actions.onList(key, memberChange(tag, false)) },
                            icon = Icons.Outlined.Delete,
                            contentDescription = stringResource(R.string.users_remove),
                            tint = MaterialTheme.colorScheme.error,
                        )
                    },
                )
            } +
                ListItem(
                    icon = Icons.Outlined.Add,
                    label = stringResource(R.string.users_add_tag),
                    destination = ListItemDestination.Action,
                    onClick = onAdd,
                ),
    )
}

/** The days and hours the user may use the server, each with a red remove, and Add. */
@Composable
private fun ScheduleGroup(
    policy: JsonObject,
    actions: UsersActions,
    onAdd: () -> Unit,
) {
    ItemGroup(
        title = stringResource(R.string.users_schedules),
        rows =
            policy.accessSchedules().map { schedule ->
                ListItem(
                    icon = Icons.Outlined.Schedule,
                    label =
                        stringResource(
                            R.string.users_schedule_row,
                            stringResource(dayLabel(schedule.day)),
                            hours(schedule.startHour),
                            hours(schedule.endHour),
                        ),
                    clickable = false,
                    trailingContent = {
                        ExpressiveIconButton(
                            onClick = { actions.onList(POLICY_SCHEDULES) { list -> list.withoutSchedule(schedule) } },
                            icon = Icons.Outlined.Delete,
                            contentDescription = stringResource(R.string.users_remove),
                            tint = MaterialTheme.colorScheme.error,
                        )
                    },
                )
            } +
                ListItem(
                    icon = Icons.Outlined.EventBusy,
                    label = stringResource(R.string.users_add_schedule),
                    destination = ListItemDestination.Action,
                    onClick = onAdd,
                ),
    )
}

private const val PICK_LIST_SKELETON_ROWS = 3

/** [this] without the first entry that is [schedule], matched by value so a stale position can't remove another. */
internal fun List<JsonElement>.withoutSchedule(schedule: AccessSchedule): List<JsonElement> {
    val at = indexOfFirst { it.asAccessSchedule() == schedule }
    return if (at < 0) this else filterIndexed { i, _ -> i != at }
}
