package com.cooper.binge.companion.jellyfin.ui.console

import android.text.format.Formatter
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import com.binge.designsystem.component.BingeFilterChip
import com.binge.designsystem.component.BingeLoadingIndicator
import com.binge.designsystem.component.BingeSearchField
import com.binge.designsystem.component.BingeTextButton
import com.binge.designsystem.component.ItemGroup
import com.binge.designsystem.component.ListItem
import com.binge.designsystem.component.LogRow
import com.binge.designsystem.template.BingeScreenScaffold
import com.binge.designsystem.template.ScreenBar
import com.binge.designsystem.template.ScreenBody
import com.binge.designsystem.template.screenListPadding
import com.cooper.binge.companion.jellyfin.R
import com.cooper.binge.companion.jellyfin.server.LOG_TAIL_BYTES
import com.cooper.binge.companion.jellyfin.server.LogEntry
import com.cooper.binge.companion.jellyfin.server.LogFile
import com.cooper.binge.companion.jellyfin.server.LogLevel
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import com.binge.designsystem.R as DesR

/** What the logs page can ask of its view model, and of the console. */
data class LogsActions(
    val onOpen: (String) -> Unit = {},
    val onCloseLog: () -> Unit = {},
    val onQuery: (String) -> Unit = {},
    val onLevel: (LogLevel) -> Unit = {},
    /** Puts the text of one entry, already redacted, on the clipboard. */
    val onCopy: (String) -> Unit = {},
    /** Shares the log [name] as a file whose text is the second argument. */
    val onShare: (name: String, text: String) -> Unit = { _, _ -> },
    val onRetry: () -> Unit = {},
    val onRetryOpen: () -> Unit = {},
    val onBack: () -> Unit = {},
)

/**
 * The server's log files, newest first. Opening one shows its end as log rows, with a search, a chip per
 * level, and Share. Back from a log returns to the list.
 */
@Composable
fun LogsScreen(
    state: LogsUiState,
    actions: LogsActions,
    zone: ZoneId = ZoneId.systemDefault(),
) {
    val open = (state as? LogsUiState.Ready)?.open
    BackHandler(enabled = open != null) { actions.onCloseLog() }
    BingeScreenScaffold(
        title = open?.name ?: stringResource(R.string.logs_title),
        bar = ScreenBar.Small,
        onBack = if (open != null) actions.onCloseLog else actions.onBack,
    ) { padding ->
        ScreenBody(padding) { _ ->
            val inner = padding.screenListPadding()
            when (state) {
                LogsUiState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { BingeLoadingIndicator() }
                is LogsUiState.Failed -> ConsoleFailed(state.failure, actions.onRetry, inner)
                is LogsUiState.Ready -> if (open == null) Files(state.files, actions, inner, zone) else Reader(open, actions, inner)
            }
        }
    }
}

@Composable
private fun Files(
    files: List<LogFile>,
    actions: LogsActions,
    inner: PaddingValues,
    zone: ZoneId,
) {
    val context = LocalContext.current
    val locale = LocalConfiguration.current.locales[0] ?: Locale.ROOT
    val time = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT).withLocale(locale).withZone(zone)
    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = inner) {
        if (files.isEmpty()) {
            item { Text(stringResource(R.string.logs_empty), style = MaterialTheme.typography.bodyLarge) }
        } else {
            item {
                ItemGroup(
                    title = null,
                    rows =
                        files.map { file ->
                            ListItem(
                                icon = Icons.Outlined.Description,
                                label = file.name,
                                detail =
                                    listOfNotNull(
                                        Formatter.formatShortFileSize(context, file.sizeBytes),
                                        file.modifiedMillis?.let { time.format(Instant.ofEpochMilli(it)) },
                                    ).joinToString(" · "),
                                supportingMaxLines = 2,
                                onClick = { actions.onOpen(file.name) },
                            )
                        },
                )
            }
        }
    }
}

@Composable
private fun Reader(
    open: OpenLog,
    actions: LogsActions,
    inner: PaddingValues,
) {
    val content = open.content
    Column(
        modifier = Modifier.fillMaxSize().padding(inner),
        verticalArrangement = Arrangement.spacedBy(dimensionResource(DesR.dimen.padding_m)),
    ) {
        when (content) {
            LogContent.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { BingeLoadingIndicator() }
            is LogContent.Failed -> ConsoleFailed(content.failure, actions.onRetryOpen, PaddingValues())
            is LogContent.Ready -> {
                BingeSearchField(
                    query = open.query,
                    onQueryChange = actions.onQuery,
                    onClear = { actions.onQuery("") },
                    placeholder = stringResource(R.string.logs_search),
                )
                LevelChips(open.level, actions.onLevel)
                if (content.truncated) {
                    Text(
                        stringResource(R.string.logs_truncated, LOG_KEPT_KB),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Entries(open.visible, actions.onCopy, Modifier.weight(1f))
                BingeTextButton(
                    label = stringResource(R.string.logs_share),
                    leadingIcon = Icons.Outlined.Share,
                    onClick = { actions.onShare(open.name, open.visible.joinToString("\n") { it.text }) },
                )
                Text(
                    stringResource(R.string.logs_share_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun LevelChips(
    selected: LogLevel,
    onLevel: (LogLevel) -> Unit,
) {
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(dimensionResource(DesR.dimen.padding_s)),
    ) {
        LogLevel.entries.forEach { level ->
            BingeFilterChip(label = stringResource(level.labelRes()), selected = level == selected, onClick = { onLevel(level) })
        }
    }
}

/** The entries, oldest first, opening on the newest: a log is read from its end. */
@Composable
private fun Entries(
    entries: List<LogEntry>,
    onCopy: (String) -> Unit,
    modifier: Modifier,
) {
    val list = rememberLazyListState()
    LaunchedEffect(entries) { if (entries.isNotEmpty()) list.scrollToItem(entries.lastIndex) }
    if (entries.isEmpty()) {
        Box(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Text(stringResource(R.string.logs_no_match), style = MaterialTheme.typography.bodyLarge)
        }
        return
    }
    LazyColumn(
        state = list,
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(dimensionResource(DesR.dimen.padding_s)),
    ) {
        items(entries, key = { it.id }) { entry -> LogRow(entry.toRowEntry(), onCopy = { onCopy(entry.text) }) }
    }
}

private const val LOG_KEPT_KB = LOG_TAIL_BYTES / 1024
