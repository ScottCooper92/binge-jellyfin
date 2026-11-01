package com.cooper.binge.companion.jellyfin.ui.console

import android.text.format.DateUtils
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Login
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.Extension
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import com.binge.designsystem.component.BingeLoadingIndicator
import com.binge.designsystem.component.BingePullToRefresh
import com.binge.designsystem.component.BingeTextButton
import com.binge.designsystem.component.ItemGroup
import com.binge.designsystem.component.ListItem
import com.binge.designsystem.template.BingeScreenScaffold
import com.binge.designsystem.template.ScreenBar
import com.binge.designsystem.template.ScreenBody
import com.binge.designsystem.template.screenListPadding
import com.binge.designsystem.theme.BingeSentiment
import com.binge.designsystem.theme.fill
import com.cooper.binge.companion.jellyfin.R
import com.cooper.binge.companion.jellyfin.server.ActivityEntry
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
) {
    BingeScreenScaffold(title = stringResource(R.string.activity_title), bar = ScreenBar.Small, onBack = actions.onBack) { padding ->
        ScreenBody(padding) { _ ->
            val inner = padding.screenListPadding()
            when (state) {
                ActivityUiState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { BingeLoadingIndicator() }
                is ActivityUiState.Failed -> ConsoleFailed(state.failure, actions.onRetry, inner)
                is ActivityUiState.Ready ->
                    BingePullToRefresh(
                        isRefreshing = state.refreshing,
                        onRefresh = actions.onRefresh,
                        indicatorTopInset = padding.calculateTopPadding(),
                    ) { Entries(state, actions, inner, now, zone) }
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
) {
    val locale = LocalConfiguration.current.locales[0] ?: Locale.ROOT
    val time = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale).withZone(zone)
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = inner,
        verticalArrangement = Arrangement.spacedBy(dimensionResource(DesR.dimen.padding_l)),
    ) {
        if (state.entries.isEmpty()) item { ConsoleEmptyScreen(stringResource(R.string.activity_empty), Icons.Outlined.History) }
        state.entries.groupBy { day(it.dateMillis, zone) }.forEach { (day, entries) ->
            item(key = "day-$day") {
                ItemGroup(title = dayTitle(day, now), rows = entries.map { it.row(time) })
            }
        }
        if (state.hasMore || state.moreFailed != null) item(key = "more") { More(state, actions) }
    }
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
            Text(stringResource(failed.message), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
            BingeTextButton(label = stringResource(R.string.console_retry), onClick = actions.onLoadMore)
        }
    }
}

@Composable
private fun ActivityEntry.row(time: DateTimeFormatter): ListItem =
    ListItem(
        icon = activityIcon(type),
        iconTint = severity.tint(),
        label = name,
        detail = listOfNotNull(dateMillis?.let { time.format(Instant.ofEpochMilli(it)) }, overview).joinToString(" · "),
        supportingMaxLines = 2,
        clickable = false,
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
