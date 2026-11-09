package com.cooper.binge.companion.jellyfin.ui.console

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.PriorityHigh
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import com.binge.designsystem.component.BingeAdaptiveSheet
import com.binge.designsystem.component.BingeChoice
import com.binge.designsystem.component.BingeChoiceList
import com.binge.designsystem.component.BingeChoiceSheetContent
import com.binge.designsystem.component.BingeFilledButton
import com.binge.designsystem.component.BingeSheetKind
import com.binge.designsystem.component.BingeSheetNav
import com.binge.designsystem.component.BingeTextButton
import com.binge.designsystem.component.ListItem
import com.cooper.binge.companion.jellyfin.R
import com.cooper.binge.companion.jellyfin.server.ActivityFilter
import com.cooper.binge.companion.jellyfin.server.ActivityTimePreset
import com.cooper.binge.companion.jellyfin.server.ActivityWho
import com.cooper.binge.companion.jellyfin.server.Severity
import java.time.ZoneId
import java.util.Locale
import com.binge.designsystem.R as DesR

/** What the activity filter sheet can ask of its view model. */
data class ActivityFilterActions(
    val onOpen: () -> Unit = {},
    val onClose: () -> Unit = {},
    val onApply: () -> Unit = {},
    val onClearAll: () -> Unit = {},
    val onClear: (ActivityFilterKind) -> Unit = {},
    val onPreset: (ActivityTimePreset?) -> Unit = {},
    val onRange: (from: Long, to: Long?) -> Unit = { _, _ -> },
    val onEdit: (ActivityFilter.() -> ActivityFilter) -> Unit = {},
    val onLoadUsers: () -> Unit = {},
)

private const val MAIN_PAGE = "main"
private const val TIME_PAGE = "time"
private const val RANGE_PAGE = "range"
private const val WHO_PAGE = "who"
private const val SEVERITY_PAGE = "severity"
private const val TYPE_PAGE = "type"

/**
 * The filters, in the Discover pattern: one row per filter with what it is set to, each opening its own page over this one, whose choices
 * go to a draft as they are made. **Show results** is the only thing that changes the list. [extended] says whether the server takes the
 * filters beyond start time and the system or user split (Jellyfin 12): without it the sheet offers those two alone.
 */
@Composable
internal fun ActivityFilterSheet(
    filters: ActivityFilters,
    extended: Boolean,
    actions: ActivityFilterActions,
) {
    if (!filters.open) return
    BingeAdaptiveSheet(
        kind = BingeSheetKind.Task,
        onDismissRequest = actions.onClose,
        root = MAIN_PAGE,
        stackPagesWhenCompact = true,
    ) { nav ->
        when (nav.page) {
            TIME_PAGE -> TimePage(filters.draft, extended, nav, actions)
            RANGE_PAGE -> RangePage(filters.draft, nav, actions)
            WHO_PAGE -> WhoPage(filters, extended, nav, actions)
            SEVERITY_PAGE -> SeverityPage(filters.draft, nav, actions)
            TYPE_PAGE -> TypePage(filters.draft, nav, actions)
            else -> MainPage(filters.draft, extended, nav, actions)
        }
    }
}

@Composable
private fun MainPage(
    draft: ActivityFilter,
    extended: Boolean,
    nav: BingeSheetNav,
    actions: ActivityFilterActions,
) {
    Column(verticalArrangement = Arrangement.spacedBy(dimensionResource(DesR.dimen.padding_s))) {
        Text(
            text = stringResource(R.string.activity_filters),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(dimensionResource(DesR.dimen.padding_m)),
        )
        ConsoleSheetGroup(
            title = null,
            rows =
                listOfNotNull(
                    ListItem(
                        icon = Icons.Outlined.Event,
                        label = stringResource(R.string.activity_filter_time),
                        detail = timeSummary(draft),
                        onClick = { nav.push(TIME_PAGE) },
                    ),
                    ListItem(
                        icon = Icons.Outlined.Person,
                        label = stringResource(R.string.activity_filter_who),
                        detail = whoSummary(draft),
                        onClick = {
                            actions.onLoadUsers()
                            nav.push(WHO_PAGE)
                        },
                    ),
                    ListItem(
                        icon = Icons.Outlined.PriorityHigh,
                        label = stringResource(R.string.activity_filter_severity),
                        detail = severitySummary(draft.severity),
                        onClick = { nav.push(SEVERITY_PAGE) },
                    ).takeIf { extended },
                    ListItem(
                        icon = Icons.Outlined.Category,
                        label = stringResource(R.string.activity_filter_type),
                        detail = typeSummary(draft.type),
                        onClick = { nav.push(TYPE_PAGE) },
                    ).takeIf { extended },
                ),
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(dimensionResource(DesR.dimen.padding_m)),
            horizontalArrangement = Arrangement.spacedBy(dimensionResource(DesR.dimen.padding_s)),
        ) {
            BingeTextButton(
                label = stringResource(R.string.activity_filters_clear),
                onClick = actions.onClearAll,
                enabled = !draft.isEmpty,
                modifier = Modifier.weight(1f),
            )
            BingeFilledButton(
                label = stringResource(R.string.activity_filters_show),
                onClick = actions.onApply,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

private const val ANY_CHOICE = "any"
private const val CUSTOM_CHOICE = "custom"

@Composable
private fun TimePage(
    draft: ActivityFilter,
    extended: Boolean,
    nav: BingeSheetNav,
    actions: ActivityFilterActions,
) {
    val choices =
        listOfNotNull(
            BingeChoice(ANY_CHOICE, stringResource(R.string.activity_time_any), icon = Icons.Outlined.Event),
            BingeChoice(ActivityTimePreset.Today.name, stringResource(R.string.activity_time_today), icon = Icons.Outlined.Event),
            BingeChoice(ActivityTimePreset.Last7Days.name, stringResource(R.string.activity_time_7), icon = Icons.Outlined.Event),
            BingeChoice(ActivityTimePreset.Last30Days.name, stringResource(R.string.activity_time_30), icon = Icons.Outlined.Event),
            BingeChoice(CUSTOM_CHOICE, stringResource(R.string.activity_time_custom), icon = Icons.Outlined.Event).takeIf { extended },
        )
    val selected =
        when {
            draft.preset != null -> draft.preset.name
            draft.fromMillis != null || draft.toMillis != null -> CUSTOM_CHOICE
            else -> ANY_CHOICE
        }
    BingeChoiceSheetContent(
        title = stringResource(R.string.activity_filter_time),
        choices = BingeChoiceList.Ready(choices),
        selected = selected,
        onSelect = { key ->
            when (key) {
                ANY_CHOICE -> {
                    actions.onPreset(null)
                    nav.pop()
                }
                CUSTOM_CHOICE -> nav.push(RANGE_PAGE)
                else -> {
                    actions.onPreset(ActivityTimePreset.valueOf(key))
                    nav.pop()
                }
            }
        },
    )
}

/** A start and an end day, from the platform's range picker. The end is optional, and the day it names is included. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RangePage(
    draft: ActivityFilter,
    nav: BingeSheetNav,
    actions: ActivityFilterActions,
) {
    val zone = ZoneId.systemDefault()
    val state =
        rememberDateRangePickerState(
            initialSelectedStartDateMillis = draft.fromMillis?.takeIf { draft.preset == null }?.let { utcDayStart(it, zone) },
            initialSelectedEndDateMillis = draft.toMillis?.let { utcDayStart(it, zone) },
        )
    Column(verticalArrangement = Arrangement.spacedBy(dimensionResource(DesR.dimen.padding_s))) {
        Text(
            text = stringResource(R.string.activity_range_title),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(dimensionResource(DesR.dimen.padding_m)),
        )
        DateRangePicker(state = state, showModeToggle = false, title = null, headline = null, modifier = Modifier.weight(1f, fill = false))
        BingeFilledButton(
            label = stringResource(R.string.activity_range_use),
            enabled = state.selectedStartDateMillis != null,
            onClick = {
                val start = state.selectedStartDateMillis ?: return@BingeFilledButton
                actions.onRange(localDayStart(start, zone), state.selectedEndDateMillis?.let { localDayEnd(it, zone) })
                nav.pop()
                nav.pop()
            },
            modifier = Modifier.fillMaxWidth().padding(dimensionResource(DesR.dimen.padding_m)),
        )
    }
}

private const val SYSTEM_CHOICE = "system"
private const val USERS_CHOICE = "users"
private const val USER_PREFIX = "user:"

@Composable
private fun WhoPage(
    filters: ActivityFilters,
    extended: Boolean,
    nav: BingeSheetNav,
    actions: ActivityFilterActions,
) {
    val draft = filters.draft
    val users = filters.users
    LaunchedEffect(extended) { if (extended) actions.onLoadUsers() }
    val base =
        listOf(
            BingeChoice(ANY_CHOICE, stringResource(R.string.activity_who_any), icon = Icons.Outlined.Person),
            BingeChoice(SYSTEM_CHOICE, stringResource(R.string.activity_who_system), icon = Icons.Outlined.Person),
            BingeChoice(USERS_CHOICE, stringResource(R.string.activity_who_users), icon = Icons.Outlined.Person),
        )
    val choices: BingeChoiceList<String> =
        when {
            !extended -> BingeChoiceList.Ready(base)
            users is ActivityUsers.Ready ->
                BingeChoiceList.Ready(base + users.names.map { BingeChoice(USER_PREFIX + it, it, icon = Icons.Outlined.Person) })
            users is ActivityUsers.Failed ->
                BingeChoiceList.Failed(
                    message = stringResource(R.string.activity_who_users_failed),
                    retryLabel = stringResource(R.string.console_retry),
                    onRetry = actions.onLoadUsers,
                )
            else -> BingeChoiceList.Loading
        }
    val selected =
        when {
            draft.username != null -> USER_PREFIX + draft.username
            draft.who == ActivityWho.System -> SYSTEM_CHOICE
            draft.who == ActivityWho.Users -> USERS_CHOICE
            else -> ANY_CHOICE
        }
    BingeChoiceSheetContent(
        title = stringResource(R.string.activity_filter_who),
        choices = choices,
        selected = selected,
        onSelect = { key ->
            actions.onEdit {
                when {
                    key == SYSTEM_CHOICE -> copy(who = ActivityWho.System, username = null)
                    key == USERS_CHOICE -> copy(who = ActivityWho.Users, username = null)
                    key.startsWith(USER_PREFIX) -> copy(who = ActivityWho.Users, username = key.removePrefix(USER_PREFIX))
                    else -> copy(who = ActivityWho.Anyone, username = null)
                }
            }
            nav.pop()
        },
    )
}

@Composable
private fun SeverityPage(
    draft: ActivityFilter,
    nav: BingeSheetNav,
    actions: ActivityFilterActions,
) {
    val choices =
        listOf(
            BingeChoice(ANY_CHOICE, stringResource(R.string.activity_severity_any), icon = Icons.Outlined.PriorityHigh),
            BingeChoice(Severity.Error.name, stringResource(R.string.activity_severity_error), icon = Icons.Outlined.PriorityHigh),
            BingeChoice(Severity.Warning.name, stringResource(R.string.activity_severity_warning), icon = Icons.Outlined.PriorityHigh),
            BingeChoice(
                Severity.Information.name,
                stringResource(R.string.activity_severity_information),
                icon = Icons.Outlined.PriorityHigh,
            ),
        )
    BingeChoiceSheetContent(
        title = stringResource(R.string.activity_filter_severity),
        choices = BingeChoiceList.Ready(choices),
        selected = draft.severity?.name ?: ANY_CHOICE,
        onSelect = { key ->
            actions.onEdit { copy(severity = Severity.entries.firstOrNull { it.name == key }) }
            nav.pop()
        },
    )
}

/** The types the page has icons for: sign-ins, playback, tasks, plugins, subtitles and the changes to users. */
internal val ACTIVITY_TYPES =
    listOf(
        "SessionStarted",
        "SessionEnded",
        "AuthenticationFailed",
        "UserLockedOut",
        "UserCreated",
        "UserDeleted",
        "UserPasswordChanged",
        "VideoPlayback",
        "VideoPlaybackStopped",
        "AudioPlayback",
        "AudioPlaybackStopped",
        "ScheduledTaskFailed",
        "PluginInstalled",
        "PluginUninstalled",
        "PluginUpdated",
        "SubtitleDownloadFailure",
    )

/** A type's identifier as words: `VideoPlaybackStopped` as "Video playback stopped". The server's names are not translated. */
internal fun String.typeWords(): String =
    replace(Regex("(?<=[a-z])(?=[A-Z])"), " ").lowercase().replaceFirstChar { it.titlecase(Locale.getDefault()) }

@Composable
private fun TypePage(
    draft: ActivityFilter,
    nav: BingeSheetNav,
    actions: ActivityFilterActions,
) {
    val choices =
        listOf(BingeChoice(ANY_CHOICE, stringResource(R.string.activity_type_any), icon = Icons.Outlined.Category)) +
            ACTIVITY_TYPES.map { BingeChoice(it, it.typeWords(), icon = activityIcon(it)) }
    BingeChoiceSheetContent(
        title = stringResource(R.string.activity_filter_type),
        choices = BingeChoiceList.Ready(choices),
        selected = draft.type ?: ANY_CHOICE,
        onSelect = { key ->
            actions.onEdit { copy(type = key.takeIf { it != ANY_CHOICE }) }
            nav.pop()
        },
    )
}
