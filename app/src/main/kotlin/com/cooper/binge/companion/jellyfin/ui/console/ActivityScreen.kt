package com.cooper.binge.companion.jellyfin.ui.console

import android.text.format.DateUtils
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Login
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.FilterListOff
import androidx.compose.material.icons.outlined.GppBad
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material.icons.outlined.Subtitles
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.binge.designsystem.component.BingeLoadingIndicator
import com.binge.designsystem.component.BingePullToRefresh
import com.binge.designsystem.component.BingeSearchField
import com.binge.designsystem.component.BingeTextButton
import com.binge.designsystem.component.DiscoverQuickClearChip
import com.binge.designsystem.component.ExpressiveIconButton
import com.binge.designsystem.component.ItemGroup
import com.binge.designsystem.component.ListItem
import com.binge.designsystem.component.QuickClearTone
import com.binge.designsystem.layout.LayoutAnchors
import com.binge.designsystem.layout.layoutAnchorIf
import com.binge.designsystem.template.BingeScreenScaffold
import com.binge.designsystem.template.EmptyScreen
import com.binge.designsystem.template.ScreenAction
import com.binge.designsystem.template.ScreenBar
import com.binge.designsystem.template.ScreenBody
import com.binge.designsystem.template.screenListPadding
import com.binge.designsystem.theme.BingeSentiment
import com.binge.designsystem.theme.fill
import com.cooper.binge.companion.jellyfin.R
import com.cooper.binge.companion.jellyfin.server.ActivityEntry
import com.cooper.binge.companion.jellyfin.server.ActivityFilter
import com.cooper.binge.companion.jellyfin.server.ActivityWho
import com.cooper.binge.companion.jellyfin.server.Severity
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import com.binge.designsystem.R as DesR

/** What the activity page can ask of its view model, and of the console. */
data class ActivityActions(
    val onRefresh: () -> Unit = {},
    val onLoadMore: () -> Unit = {},
    val onOpen: (Long?) -> Unit = {},
    /** Opens a user's page, or null where the user cannot manage users (the sheet then offers no such action). */
    val onOpenUser: ((String) -> Unit)? = null,
    val onRetry: () -> Unit = {},
    val onBack: () -> Unit = {},
)

/**
 * The server's activity log, newest first, grouped by day: who signed in or failed to, what played,
 * what changed. An error is marked in the error colour, a warning in the caution colour. Scrolling to
 * the end reads the next page, and pulling down reads the newest again.
 */
@Composable
fun ActivityScreen(
    state: ActivityUiState,
    actions: ActivityActions,
    now: Long = System.currentTimeMillis(),
    zone: ZoneId = ZoneId.systemDefault(),
    filters: ActivityFilters = ActivityFilters(),
    filterActions: ActivityFilterActions = ActivityFilterActions(),
    /** Whether the server takes the filters beyond start time and the system or user split (Jellyfin 12). */
    extendedFilters: Boolean = false,
) {
    // Held above the loading, failed and ready branches, so a reload does not drop where the user was. A new filter is another list: its
    // state starts at the top, because a list keeps the item it showed first by its key, and with the chips now above it, that would
    // hold the chips in the space under the top bar.
    val listState = key(filters.applied) { rememberLazyListState() }
    BingeScreenScaffold(
        title = stringResource(R.string.activity_title),
        bar = ScreenBar.Small,
        onBack = actions.onBack,
        actions = {
            if (state is ActivityUiState.Ready || !filters.applied.isEmpty) {
                ExpressiveIconButton(
                    onClick = filterActions.onOpen,
                    icon = Icons.Outlined.FilterList,
                    contentDescription =
                        if (filters.applied.count == 0) {
                            stringResource(R.string.activity_filters)
                        } else {
                            stringResource(R.string.activity_filters_active, filters.applied.count)
                        },
                )
            }
        },
    ) { padding ->
        ScreenBody(padding) { _ ->
            ActivityBody(state, actions, padding, now, zone, listState, filters, filterActions, extendedFilters)
            ActivityFilterSheet(filters, extendedFilters, filterActions)
            (state as? ActivityUiState.Ready)?.openEntry?.let { entry ->
                ActivityDetailSheet(entry, zone, onOpenUser = actions.onOpenUser) { actions.onOpen(null) }
            }
        }
    }
}

/**
 * The page under its bar: the search field where the server has one, and the list, its loading or its failure below it. The field
 * stays put while the list loads, so typing is not interrupted by the list being replaced.
 */
@Composable
private fun ActivityBody(
    state: ActivityUiState,
    actions: ActivityActions,
    padding: PaddingValues,
    now: Long,
    zone: ZoneId,
    listState: LazyListState,
    filters: ActivityFilters,
    filterActions: ActivityFilterActions,
    searchable: Boolean,
) {
    val inner = padding.screenListPadding()
    val direction = LocalLayoutDirection.current
    val listInner =
        if (searchable) {
            PaddingValues(
                start = inner.calculateStartPadding(direction),
                end = inner.calculateEndPadding(direction),
                bottom = inner.calculateBottomPadding(),
            )
        } else {
            inner
        }
    Column(Modifier.fillMaxSize()) {
        if (searchable) {
            BingeSearchField(
                query = filters.query,
                onQueryChange = filterActions.onSearch,
                onClear = { filterActions.onSearch("") },
                placeholder = stringResource(R.string.activity_search_hint),
                modifier =
                    Modifier.padding(
                        start = inner.calculateStartPadding(direction),
                        end = inner.calculateEndPadding(direction),
                        top = inner.calculateTopPadding(),
                        bottom = dimensionResource(DesR.dimen.padding_s),
                    ),
            )
        }
        Box(Modifier.weight(1f)) {
            when (state) {
                ActivityUiState.Loading -> ConsoleLoading(listInner, LOADING_ROWS)
                is ActivityUiState.Failed -> ConsoleFailureScreen(state.failure, actions.onRetry, Modifier.padding(listInner))
                is ActivityUiState.Ready ->
                    BingePullToRefresh(
                        isRefreshing = state.refreshing,
                        onRefresh = actions.onRefresh,
                        indicatorTopInset = if (searchable) 0.dp else padding.calculateTopPadding(),
                    ) { Entries(state, actions, listInner, now, zone, listState, filters.applied, filterActions) }
            }
        }
    }
}

@Composable
private fun Entries(
    state: ActivityUiState.Ready,
    actions: ActivityActions,
    inner: PaddingValues,
    now: Long,
    zone: ZoneId,
    listState: LazyListState,
    filter: ActivityFilter,
    filterActions: ActivityFilterActions,
) {
    val locale = LocalConfiguration.current.locales[0] ?: Locale.ROOT
    val time = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale).withZone(zone)
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = inner,
        verticalArrangement = Arrangement.spacedBy(dimensionResource(DesR.dimen.padding_l)),
    ) {
        if (!filter.isEmpty) item(key = "chips") { ActivityFilterChips(filter, filterActions.onClear) }
        if (state.entries.isEmpty()) item(key = "empty") { NothingToShow(filter, filterActions) }
        state.entries.groupBy { day(it.dateMillis, zone) }.entries.forEachIndexed { index, (day, entries) ->
            item(key = "day-$day") {
                ItemGroup(
                    title = dayTitle(day, now),
                    rows = entries.map { it.row(time) { id -> actions.onOpen(id) } },
                    // The skeleton's first plate is anchored the same way, so a test can tell the page does not jump on resolve.
                    modifier = Modifier.layoutAnchorIf(index == 0, LayoutAnchors.section(LayoutAnchors.Collection.FIRST_ITEM)),
                )
            }
        }
        if (state.hasMore || state.moreFailed != null) item(key = "more") { More(state, actions) }
    }
}

/** An empty list: the log with nothing in it, or what a filter or a search matched none of, with the way out of it. */
@Composable
private fun LazyItemScope.NothingToShow(
    filter: ActivityFilter,
    filterActions: ActivityFilterActions,
) {
    if (filter.isEmpty) {
        ConsoleEmptyScreen(stringResource(R.string.activity_empty), Icons.Outlined.History)
        return
    }
    val searchOnly = filter.query != null && filter.copy(query = null).isEmpty
    EmptyScreen(
        message = stringResource(if (searchOnly) R.string.activity_search_empty_message else R.string.activity_filtered_empty_message),
        title = stringResource(if (searchOnly) R.string.activity_search_empty_title else R.string.activity_filtered_empty_title),
        icon = Icons.Outlined.FilterListOff,
        action =
            if (searchOnly) {
                ScreenAction(
                    stringResource(R.string.activity_search_empty_clear),
                    onClick = { filterActions.onClear(ActivityFilterKind.Search) },
                )
            } else {
                ScreenAction(stringResource(R.string.activity_filtered_empty_clear), filterActions.onClearAll)
            },
        modifier = Modifier.fillParentMaxHeight(FILTERED_EMPTY_FRACTION),
    )
}

/** The end of the list: the next page on its way, or why it didn't come and a way to try again. */
@Composable
private fun More(
    state: ActivityUiState.Ready,
    actions: ActivityActions,
) {
    val failed = state.moreFailed
    if (failed == null) {
        // Reaching the end asks for the next page, once per length of the list.
        LaunchedEffect(state.entries.size) { actions.onLoadMore() }
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { BingeLoadingIndicator() }
    } else {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(failed.text(), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
            BingeTextButton(label = stringResource(R.string.console_retry), onClick = actions.onLoadMore)
        }
    }
}

@Composable
private fun ActivityEntry.row(
    time: DateTimeFormatter,
    onOpen: (Long) -> Unit,
): ListItem =
    ListItem(
        icon = activityIcon(type),
        iconTint = severity.tint(),
        label = name,
        detail = listOfNotNull(dateMillis?.let { time.format(Instant.ofEpochMilli(it)) }, overview).joinToString(" · "),
        supportingMaxLines = 2,
        onClick = { onOpen(id) },
    )

@Composable
private fun Severity.tint(): Color? =
    when (this) {
        Severity.Error -> BingeSentiment.Negative.fill()
        Severity.Warning -> BingeSentiment.Caution.fill()
        Severity.Information -> null
    }

/** The start of the entry's day in [zone], or [UNDATED] for an entry with no date. */
internal fun day(
    dateMillis: Long?,
    zone: ZoneId,
): Long =
    dateMillis?.let {
        Instant
            .ofEpochMilli(it)
            .atZone(zone)
            .toLocalDate()
            .atStartOfDay(zone)
            .toInstant()
            .toEpochMilli()
    } ?: UNDATED

/** The group entries with no date fall into, last. */
internal const val UNDATED = Long.MIN_VALUE

/** "Today", "Yesterday", or the date, in the device's own words. */
@Composable
private fun dayTitle(
    day: Long,
    now: Long,
): String =
    if (day == UNDATED) {
        stringResource(R.string.activity_undated)
    } else {
        DateUtils.getRelativeTimeSpanString(day, now, DateUtils.DAY_IN_MILLIS).toString()
    }

/** An entry's icon, from its `Type`; an information mark for one this app doesn't know. */
internal fun activityIcon(type: String): ImageVector =
    when {
        type == "SessionStarted" -> Icons.AutoMirrored.Outlined.Login
        type == "SessionEnded" -> Icons.AutoMirrored.Outlined.Logout
        type == "AuthenticationFailed" || type == "UserLockedOut" -> Icons.Outlined.GppBad
        type.startsWith("Authentication") -> Icons.Outlined.Key
        type.endsWith("PlaybackStopped") -> Icons.Outlined.Stop
        type.endsWith("Playback") -> Icons.Outlined.PlayArrow
        type.startsWith("User") -> Icons.Outlined.Person
        type.startsWith("Plugin") || type.startsWith("Package") -> Icons.Outlined.Extension
        type.contains("Task") -> Icons.Outlined.Schedule
        type.contains("Subtitle") -> Icons.Outlined.Subtitles
        else -> Icons.Outlined.Info
    }

private const val LOADING_ROWS = 8
private const val FILTERED_EMPTY_FRACTION = 0.6f

/** The filters the list is narrowed by, each a chip that clears it. */
@Composable
internal fun ActivityFilterChips(
    filter: ActivityFilter,
    onClear: (ActivityFilterKind) -> Unit,
) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(dimensionResource(DesR.dimen.padding_s))) {
        if (filter.fromMillis != null || filter.toMillis != null) {
            item(key = "time") { DiscoverQuickClearChip(timeSummary(filter), QuickClearTone.Include, { onClear(ActivityFilterKind.Time) }) }
        }
        if (filter.who != ActivityWho.Anyone || filter.username != null) {
            item(key = "who") { DiscoverQuickClearChip(whoSummary(filter), QuickClearTone.Include, { onClear(ActivityFilterKind.Who) }) }
        }
        filter.severity?.let { severity ->
            item(key = "severity") {
                DiscoverQuickClearChip(severitySummary(severity), QuickClearTone.Include, { onClear(ActivityFilterKind.Severity) })
            }
        }
        filter.query?.let { query ->
            item(key = "search") {
                DiscoverQuickClearChip(
                    stringResource(R.string.activity_search_chip, query),
                    QuickClearTone.Include,
                    { onClear(ActivityFilterKind.Search) },
                )
            }
        }
        filter.type?.let { type ->
            item(key = "type") { DiscoverQuickClearChip(typeSummary(type), QuickClearTone.Include, { onClear(ActivityFilterKind.Type) }) }
        }
    }
}
