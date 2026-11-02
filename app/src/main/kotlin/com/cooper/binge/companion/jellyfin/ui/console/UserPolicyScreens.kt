package com.cooper.binge.companion.jellyfin.ui.console

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChildCare
import androidx.compose.material.icons.outlined.Devices
import androidx.compose.material.icons.outlined.LiveTv
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.People
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.SettingsRemote
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import com.binge.designsystem.component.ItemGroup
import com.binge.designsystem.component.ListItem
import com.cooper.binge.companion.jellyfin.R
import com.cooper.binge.companion.jellyfin.server.JellyfinServerProfile
import com.cooper.binge.companion.jellyfin.server.POLICY_ALL_CHANNELS
import com.cooper.binge.companion.jellyfin.server.POLICY_ALL_DEVICES
import com.cooper.binge.companion.jellyfin.server.POLICY_AUDIO_TRANSCODING
import com.cooper.binge.companion.jellyfin.server.POLICY_BITRATE_LIMIT
import com.cooper.binge.companion.jellyfin.server.POLICY_BLOCKED_TAGS
import com.cooper.binge.companion.jellyfin.server.POLICY_BLOCK_UNRATED
import com.cooper.binge.companion.jellyfin.server.POLICY_CONVERSION
import com.cooper.binge.companion.jellyfin.server.POLICY_DELETION
import com.cooper.binge.companion.jellyfin.server.POLICY_DOWNLOADING
import com.cooper.binge.companion.jellyfin.server.POLICY_FORCE_REMOTE_TRANSCODING
import com.cooper.binge.companion.jellyfin.server.POLICY_HIDDEN
import com.cooper.binge.companion.jellyfin.server.POLICY_LIVE_TV_ACCESS
import com.cooper.binge.companion.jellyfin.server.POLICY_LIVE_TV_MANAGEMENT
import com.cooper.binge.companion.jellyfin.server.POLICY_LOCKOUT
import com.cooper.binge.companion.jellyfin.server.POLICY_MAX_RATING
import com.cooper.binge.companion.jellyfin.server.POLICY_MAX_SESSIONS
import com.cooper.binge.companion.jellyfin.server.POLICY_PLAYBACK
import com.cooper.binge.companion.jellyfin.server.POLICY_PREFERENCE_ACCESS
import com.cooper.binge.companion.jellyfin.server.POLICY_PUBLIC_SHARING
import com.cooper.binge.companion.jellyfin.server.POLICY_REMOTE_ACCESS
import com.cooper.binge.companion.jellyfin.server.POLICY_REMOTE_CONTROL
import com.cooper.binge.companion.jellyfin.server.POLICY_REMUXING
import com.cooper.binge.companion.jellyfin.server.POLICY_SCHEDULES
import com.cooper.binge.companion.jellyfin.server.POLICY_SHARED_DEVICE_CONTROL
import com.cooper.binge.companion.jellyfin.server.POLICY_SYNC_PLAY
import com.cooper.binge.companion.jellyfin.server.POLICY_VIDEO_TRANSCODING
import com.cooper.binge.companion.jellyfin.server.ServerUser
import com.cooper.binge.companion.jellyfin.server.flag
import com.cooper.binge.companion.jellyfin.server.has
import com.cooper.binge.companion.jellyfin.server.int
import com.cooper.binge.companion.jellyfin.server.string
import kotlinx.serialization.json.JsonObject
import com.binge.designsystem.R as DesR

/** The settings pages' status line keeps two lines, so a failed change never moves the rows. */
private const val DETAIL_LINES = 4

/** The title of a section of a user's settings. */
internal fun UserSection.title(): Int =
    when (this) {
        UserSection.Access -> R.string.users_section_access
        UserSection.Playback -> R.string.users_section_playback
        UserSection.Content -> R.string.users_section_content
        UserSection.Control -> R.string.users_section_control
        UserSection.LiveTv -> R.string.users_section_live_tv
        UserSection.Devices -> R.string.users_section_devices
        UserSection.Parental -> R.string.users_section_parental
    }

private fun UserSection.icon(): ImageVector =
    when (this) {
        UserSection.Access -> Icons.Outlined.Lock
        UserSection.Playback -> Icons.Outlined.PlayCircle
        UserSection.Content -> Icons.Outlined.Movie
        UserSection.Control -> Icons.Outlined.SettingsRemote
        UserSection.LiveTv -> Icons.Outlined.LiveTv
        UserSection.Devices -> Icons.Outlined.Devices
        UserSection.Parental -> Icons.Outlined.ChildCare
    }

/** The keys a section reads: it is offered when the server sent any of them. */
private fun UserSection.keys(): List<String> =
    when (this) {
        UserSection.Access -> listOf(POLICY_REMOTE_ACCESS, POLICY_HIDDEN, POLICY_PREFERENCE_ACCESS, POLICY_LOCKOUT, POLICY_MAX_SESSIONS)
        UserSection.Playback ->
            listOf(
                POLICY_PLAYBACK,
                POLICY_AUDIO_TRANSCODING,
                POLICY_VIDEO_TRANSCODING,
                POLICY_REMUXING,
                POLICY_FORCE_REMOTE_TRANSCODING,
                POLICY_BITRATE_LIMIT,
            )
        UserSection.Content -> listOf(POLICY_DOWNLOADING, POLICY_DELETION, POLICY_CONVERSION, POLICY_PUBLIC_SHARING)
        UserSection.Control -> listOf(POLICY_REMOTE_CONTROL, POLICY_SHARED_DEVICE_CONTROL, POLICY_SYNC_PLAY)
        UserSection.LiveTv -> listOf(POLICY_LIVE_TV_ACCESS, POLICY_LIVE_TV_MANAGEMENT, POLICY_ALL_CHANNELS)
        UserSection.Devices -> listOf(POLICY_ALL_DEVICES)
        UserSection.Parental -> listOf(POLICY_MAX_RATING, POLICY_BLOCK_UNRATED, POLICY_BLOCKED_TAGS, POLICY_SCHEDULES)
    }

/** The rows that open a user's settings pages, for the sections this user's policy has. */
@Composable
internal fun sectionRows(
    user: ServerUser,
    actions: UsersActions,
): List<ListItem> =
    UserSection.entries
        .filter { section -> section.keys().any { user.policy.has(it) } }
        .map { section ->
            ListItem(icon = section.icon(), label = stringResource(section.title()), onClick = { actions.onSection(section) })
        }

/** One section of a user's settings, as a page of its own. [mine] is the signed-in user's own page. */
@Composable
internal fun PolicySection(
    section: UserSection,
    state: UsersUiState.Ready,
    user: ServerUser,
    choices: UserChoices,
    profile: JellyfinServerProfile,
    actions: UsersActions,
    inner: PaddingValues,
) {
    var picker by rememberSaveable { mutableStateOf<PolicyPicker?>(null) }
    var adding by rememberSaveable { mutableStateOf<PolicyAdd?>(null) }
    val policy = user.policy
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(inner),
        verticalArrangement = Arrangement.spacedBy(dimensionResource(DesR.dimen.padding_l)),
    ) {
        when (section) {
            UserSection.Access -> ItemGroup(title = null, rows = accessRows(policy, actions) { picker = it })
            UserSection.Playback -> ItemGroup(title = null, rows = playbackRows(policy, actions) { picker = it })
            UserSection.Content -> ContentSection(state, policy, profile, actions)
            UserSection.Control -> ItemGroup(title = null, rows = controlRows(policy, actions) { picker = it })
            UserSection.LiveTv -> LiveTvSection(policy, choices, actions, section)
            UserSection.Devices -> DevicesSection(policy, choices, actions, section)
            UserSection.Parental -> ParentalSection(policy, choices, profile, actions, { picker = it }) { adding = it }
        }
        PolicyStatus(state.actionFailed?.let { stringResource(R.string.users_not_changed, stringResource(it.message)) })
    }
    picker?.let { PolicyPickerSheet(it, policy, choices, actions) { picker = null } }
    adding?.let { PolicyAddSheet(it, actions) { adding = null } }
}

/** A switch for [key], or nothing when the server didn't send it. */
@Composable
internal fun JsonObject.switchRow(
    key: String,
    icon: ImageVector,
    label: Int,
    actions: UsersActions,
    detail: Int? = null,
): ListItem? {
    if (!has(key)) return null
    val on = flag(key)
    return ListItem(
        icon = icon,
        label = stringResource(label),
        detail = detail?.let { stringResource(it) },
        supportingMaxLines = DETAIL_LINES,
        toggled = on,
        onClick = { actions.onPolicy(key, kotlinx.serialization.json.JsonPrimitive(!on)) },
    )
}

@Composable
private fun accessRows(
    policy: JsonObject,
    actions: UsersActions,
    onPick: (PolicyPicker) -> Unit,
): List<ListItem> =
    listOfNotNull(
        policy.switchRow(
            POLICY_REMOTE_ACCESS,
            Icons.Outlined.Public,
            R.string.users_remote_access,
            actions,
            R.string.users_remote_access_detail,
        ),
        policy.switchRow(POLICY_HIDDEN, Icons.Outlined.VisibilityOff, R.string.users_hidden, actions, R.string.users_hidden_detail),
        policy.switchRow(
            POLICY_PREFERENCE_ACCESS,
            Icons.Outlined.Settings,
            R.string.users_preferences,
            actions,
            R.string.users_preferences_detail,
        ),
        ListItem(
            icon = Icons.Outlined.Lock,
            label = stringResource(R.string.users_lockout),
            detail = lockoutLabel(policy.int(POLICY_LOCKOUT) ?: 0),
            onClick = { onPick(PolicyPicker.Lockout) },
        ).takeIf { policy.has(POLICY_LOCKOUT) },
        ListItem(
            icon = Icons.Outlined.People,
            label = stringResource(R.string.users_max_sessions),
            detail = sessionsLabel(policy.int(POLICY_MAX_SESSIONS) ?: 0),
            onClick = { onPick(PolicyPicker.Sessions) },
        ).takeIf { policy.has(POLICY_MAX_SESSIONS) },
    )

@Composable
private fun playbackRows(
    policy: JsonObject,
    actions: UsersActions,
    onPick: (PolicyPicker) -> Unit,
): List<ListItem> =
    listOfNotNull(
        policy.switchRow(POLICY_PLAYBACK, Icons.Outlined.PlayCircle, R.string.users_playback, actions),
        policy.switchRow(POLICY_AUDIO_TRANSCODING, Icons.Outlined.Sync, R.string.users_audio_transcoding, actions),
        policy.switchRow(POLICY_VIDEO_TRANSCODING, Icons.Outlined.Sync, R.string.users_video_transcoding, actions),
        policy.switchRow(POLICY_REMUXING, Icons.Outlined.Repeat, R.string.users_remuxing, actions, R.string.users_remuxing_detail),
        policy.switchRow(
            POLICY_FORCE_REMOTE_TRANSCODING,
            Icons.Outlined.Sync,
            R.string.users_force_remote,
            actions,
            R.string.users_force_remote_detail,
        ),
        ListItem(
            icon = Icons.Outlined.Speed,
            label = stringResource(R.string.users_bitrate),
            detail = bitrateLabel(policy.int(POLICY_BITRATE_LIMIT) ?: 0),
            onClick = { onPick(PolicyPicker.Bitrate) },
        ).takeIf { policy.has(POLICY_BITRATE_LIMIT) },
    )

@Composable
private fun controlRows(
    policy: JsonObject,
    actions: UsersActions,
    onPick: (PolicyPicker) -> Unit,
): List<ListItem> =
    listOfNotNull(
        policy.switchRow(
            POLICY_REMOTE_CONTROL,
            Icons.Outlined.SettingsRemote,
            R.string.users_remote_control,
            actions,
            R.string.users_remote_control_detail,
        ),
        policy.switchRow(
            POLICY_SHARED_DEVICE_CONTROL,
            Icons.Outlined.Devices,
            R.string.users_shared_devices,
            actions,
            R.string.users_shared_devices_detail,
        ),
        ListItem(
            icon = Icons.Outlined.People,
            label = stringResource(R.string.users_sync_play),
            detail = syncPlayLabel(policy.string(POLICY_SYNC_PLAY).orEmpty()),
            onClick = { onPick(PolicyPicker.SyncPlay) },
        ).takeIf { policy.has(POLICY_SYNC_PLAY) },
    )

/** A failed change's message, in the error colour. */
@Composable
internal fun PolicyStatus(text: String?) {
    if (text == null) return
    Text(text = text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
}
