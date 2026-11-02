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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import com.binge.designsystem.component.BingeFilterChip
import com.binge.designsystem.component.BingeSearchField
import com.binge.designsystem.component.ExpressiveIconButton
import com.binge.designsystem.component.ItemGroup
import com.binge.designsystem.component.ListItem
import com.binge.designsystem.component.LogRow
import com.binge.designsystem.template.BingeScreenScaffold
import com.binge.designsystem.template.ScreenBar
import com.binge.designsystem.template.ScreenBody
import com.binge.designsystem.template.screenListPadding
import com.cooper.binge.companion.jellyfin.R
import com.cooper.binge.companion.jellyfin.server.LOG_RETENTION_MAX_DAYS
import com.cooper.binge.companion.jellyfin.server.LOG_RETENTION_MIN_DAYS
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
    val onEditRetention: (Boolean) -> Unit = {},
    val onSaveRetention: (String) -> Unit = {},
    val onRetrySave: () -> Unit = {},
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
    val host = remember { SnackbarHostState() }
    BingeScreenScaffold(
        title = open?.name?.middleEllipsised() ?: stringResource(R.string.logs_title),
        bar = ScreenBar.Small,
        onBack = if (open != null) actions.onCloseLog else actions.onBack,
        snackbarHostState = host,
        actions = { if (open != null) ShareAction(open, actions.onShare) },
    ) { padding ->
        ScreenBody(padding) { _ ->
            val inner = padding.screenListPadding()
            when (state) {
                LogsUiState.Loading -> ConsoleLoading(inner, FILE_ROWS)
                is LogsUiState.Failed -> ConsoleFailureScreen(state.failure, actions.onRetry, Modifier.padding(inner))
                is LogsUiState.Ready -> if (open == null) Files(state, actions, inner, zone) else Reader(open, actions, inner)
            }
            RefusedMessage((state as? LogsUiState.Ready)?.refused, host)
        }
    }
}

@Composable
private fun Files(
    state: LogsUiState.Ready,
    actions: LogsActions,
    inner: PaddingValues,
    zone: ZoneId,
) {
    val files = state.files
    FilesList(files, state, actions, inner, zone)
    if (state.editingRetention) RetentionSheet(state.retention, actions)
}

@Composable
private fun FilesList(
    files: List<LogFile>,
    state: LogsUiState.Ready,
    actions: LogsActions,
    inner: PaddingValues,
    zone: ZoneId,
) {
    val context = LocalContext.current
    val locale = LocalConfiguration.current.locales[0] ?: Locale.ROOT
    val time = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT).withLocale(locale).withZone(zone)
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = inner,
        verticalArrangement = Arrangement.spacedBy(dimensionResource(DesR.dimen.padding_m)),
    ) {
        state.retention?.let { days -> item { RetentionRow(days, state.save.failed, actions) } }
        if (files.isEmpty()) {
            item { ConsoleEmptyScreen(stringResource(R.string.logs_empty), Icons.Outlined.Description) }
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
private fun RetentionRow(
    days: Int,
    unsent: Boolean,
    actions: LogsActions,
) {
    ItemGroup(
        title = null,
        rows =
            listOf(
                ListItem(
                    icon = Icons.Outlined.DeleteSweep,
                    label = stringResource(R.string.logs_retention_title),
                    detail =
                        if (unsent) {
                            stringResource(R.string.logs_retention_unsent)
                        } else {
                            pluralStringResource(R.plurals.logs_retention_days, days, days)
                        },
                    onClick = if (unsent) actions.onRetrySave else ({ actions.onEditRetention(true) }),
                ),
            ),
    )
}

/** The retention's number sheet: Save keeps what is typed if it is a whole number of days in range, and says so if it isn't. */
@Composable
internal fun RetentionSheet(
    days: Int?,
    actions: LogsActions,
    inWindow: Boolean = true,
) {
    var value by rememberSaveable(key = "RetentionSheet.value") { mutableStateOf(days?.toString().orEmpty()) }
    var tried by rememberSaveable(key = "RetentionSheet.tried") { mutableStateOf(false) }
    val valid = value.trim().toIntOrNull()?.let { it in LOG_RETENTION_MIN_DAYS..LOG_RETENTION_MAX_DAYS } == true
    ConsoleSheet(onDismissRequest = { actions.onEditRetention(false) }, inWindow = inWindow) {
        ConsoleTextEntry(
            title = stringResource(R.string.logs_retention_title),
            body = stringResource(R.string.logs_retention_body),
            value = value,
            onValueChange = { value = it },
            submitLabel = stringResource(R.string.logs_retention_save),
            onSubmit = {
                tried = true
                if (valid) actions.onSaveRetention(value)
            },
            onCancel = { actions.onEditRetention(false) },
            error =
                if (tried && !valid) {
                    stringResource(R.string.logs_retention_invalid, LOG_RETENTION_MIN_DAYS, LOG_RETENTION_MAX_DAYS)
                } else {
                    null
                },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        )
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
            LogContent.Loading -> ConsoleLoading(PaddingValues(), LOG_ROWS)
            is LogContent.Failed -> ConsoleFailureScreen(content.failure, actions.onRetryOpen)
            is LogContent.Ready -> {
                LogIntro(open.name)
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
            }
        }
    }
}

/** The log's full name, which the bar may have shortened, and what Share promises, next to the action that makes it. */
@Composable
private fun LogIntro(name: String) {
    Column(verticalArrangement = Arrangement.spacedBy(dimensionResource(DesR.dimen.padding_s))) {
        Text(name, style = MaterialTheme.typography.titleSmall)
        Text(
            stringResource(R.string.logs_share_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Share, in the bar: there once the log is read, off while it loads or if it failed. Its tooltip says what is shared. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ShareAction(
    open: OpenLog,
    onShare: (name: String, text: String) -> Unit,
) {
    TooltipBox(
        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(),
        tooltip = { PlainTooltip { Text(stringResource(R.string.logs_share_note)) } },
        state = rememberTooltipState(),
    ) {
        ExpressiveIconButton(
            onClick = { onShare(open.name, open.visible.joinToString("\n") { it.text }) },
            icon = Icons.Outlined.Share,
            contentDescription = stringResource(R.string.logs_share_action),
            enabled = open.content is LogContent.Ready,
        )
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

private const val FILE_ROWS = 6
private const val LOG_ROWS = 8
private const val LOG_KEPT_KB = LOG_TAIL_BYTES / 1024
