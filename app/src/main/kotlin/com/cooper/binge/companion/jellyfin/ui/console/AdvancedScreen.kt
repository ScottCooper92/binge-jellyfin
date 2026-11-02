package com.cooper.binge.companion.jellyfin.ui.console

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Analytics
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.Cast
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.HourglassEmpty
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.binge.designsystem.component.ItemGroup
import com.binge.designsystem.component.ListItem
import com.binge.designsystem.template.FormScreen
import com.binge.designsystem.template.ScreenBar
import com.binge.designsystem.template.formSaveStatus
import com.cooper.binge.companion.jellyfin.R
import com.cooper.binge.companion.jellyfin.server.ACTIVITY_LOG_RETENTION_DAYS
import com.cooper.binge.companion.jellyfin.server.ALLOW_CLIENT_LOG_UPLOAD
import com.cooper.binge.companion.jellyfin.server.AdvancedSettings
import com.cooper.binge.companion.jellyfin.server.CACHE_SIZE
import com.cooper.binge.companion.jellyfin.server.CAST_RECEIVER_APPLICATIONS
import com.cooper.binge.companion.jellyfin.server.CORS_HOSTS
import com.cooper.binge.companion.jellyfin.server.ENABLE_LEGACY_AUTHORIZATION
import com.cooper.binge.companion.jellyfin.server.ENABLE_METRICS
import com.cooper.binge.companion.jellyfin.server.ENABLE_SLOW_RESPONSE_WARNING
import com.cooper.binge.companion.jellyfin.server.IMAGE_EXTRACTION_TIMEOUT_MS
import com.cooper.binge.companion.jellyfin.server.INACTIVE_SESSION_THRESHOLD
import com.cooper.binge.companion.jellyfin.server.LIBRARY_MONITOR_DELAY
import com.cooper.binge.companion.jellyfin.server.LIBRARY_SCAN_CONCURRENCY
import com.cooper.binge.companion.jellyfin.server.LIBRARY_UPDATE_DURATION
import com.cooper.binge.companion.jellyfin.server.PARALLEL_IMAGE_ENCODING_LIMIT
import com.cooper.binge.companion.jellyfin.server.SLOW_RESPONSE_THRESHOLD_MS
import com.binge.designsystem.R as DesR

/** What the Advanced page can ask of its view model, and of the console. */
data class AdvancedActions(
    val onEdit: (AdvancedNumber?) -> Unit = {},
    val onSave: (AdvancedNumber, String) -> Unit = { _, _ -> },
    val onEditList: (AdvancedList?) -> Unit = {},
    val onSaveList: (AdvancedList, String) -> Unit = { _, _ -> },
    val onSlowResponseWarning: (Boolean) -> Unit = {},
    val onMetrics: (Boolean) -> Unit = {},
    val onAllowClientLogUpload: (Boolean) -> Unit = {},
    val onLegacyAuthorization: (Boolean) -> Unit = {},
    val onOpenServer: () -> Unit = {},
    val onRetry: () -> Unit = {},
    val onRetrySave: () -> Unit = {},
    val onBack: () -> Unit = {},
)

/**
 * What the server's release has, from its profile: the 10.9 options ([performance]) and the 10.11 ones ([cacheAndLegacyAuth]).
 * A row shows when its release has it and the server sent its key.
 */
data class AdvancedGates(
    val performance: Boolean = true,
    val cacheAndLegacyAuth: Boolean = true,
)

/** Lines a row's detail may wrap to, so it reads in full at large fonts. */
private const val DETAIL_LINES = 6

private const val LOADING_ROWS = 10

/**
 * The server's advanced options: how it scans, caches and encodes, how long it keeps its logs, and which clients it
 * answers. Each change saves as it's made; a number or a list saves when its sheet does. A row shows only if the server
 * has the option, so an older server shows fewer. The options that apply after a restart say so, and the last row opens
 * the Server page.
 */
@Composable
fun AdvancedScreen(
    state: AdvancedUiState,
    actions: AdvancedActions,
    gates: AdvancedGates = AdvancedGates(),
) {
    val host = remember { SnackbarHostState() }
    val ready = state as? AdvancedUiState.Ready
    FormScreen(
        title = stringResource(R.string.advanced_title),
        bar = ScreenBar.Small,
        onBack = actions.onBack,
        snackbarHostState = host,
        saveStatus = ready?.save?.let { formSaveStatus(it.status, it.failed, actions.onRetrySave) },
        notReady =
            when (state) {
                AdvancedUiState.Loading -> { padding -> ConsoleLoading(padding, LOADING_ROWS) }
                is AdvancedUiState.Failed -> { padding -> ConsoleFailureScreen(state.failure, actions.onRetry, Modifier.padding(padding)) }
                is AdvancedUiState.Ready -> null
            },
    ) { _ ->
        if (ready != null) {
            AdvancedBody(ready.settings.within(gates), actions)
            ready.editing?.let { NumberSheet(it, ready.settings, actions) }
            ready.editingList?.let { ListSheet(it, ready.settings, actions) }
            RefusedMessage(ready.refused, host)
        }
    }
}

@Composable
private fun AdvancedBody(
    settings: AdvancedSettings,
    actions: AdvancedActions,
) {
    Column(verticalArrangement = Arrangement.spacedBy(dimensionResource(DesR.dimen.padding_l))) {
        Text(stringResource(R.string.advanced_body), style = MaterialTheme.typography.bodyMedium)
        listOf(
            R.string.advanced_scanning to scanningRows(settings, actions),
            R.string.advanced_images to imageRows(settings, actions),
            R.string.advanced_sessions_logs to sessionRows(settings, actions),
            R.string.advanced_clients to clientRows(settings, actions),
        ).forEach { (title, rows) ->
            if (rows.isNotEmpty()) ItemGroup(title = stringResource(title), rows = rows)
        }
        if (settings.needsRestart()) {
            ItemGroup(
                title = null,
                rows =
                    listOf(
                        ListItem(
                            icon = Icons.Outlined.RestartAlt,
                            label = stringResource(R.string.advanced_restart),
                            detail = stringResource(R.string.advanced_restart_detail),
                            supportingMaxLines = DETAIL_LINES,
                            onClick = actions.onOpenServer,
                        ),
                    ),
            )
        }
    }
}

/** [this] with the options [gates] says the release lacks taken out, as if the server had not sent them. */
private fun AdvancedSettings.within(gates: AdvancedGates): AdvancedSettings {
    val gone =
        buildSet {
            if (!gates.performance) {
                addAll(
                    listOf(LIBRARY_UPDATE_DURATION, PARALLEL_IMAGE_ENCODING_LIMIT, INACTIVE_SESSION_THRESHOLD, CAST_RECEIVER_APPLICATIONS),
                )
            }
            if (!gates.cacheAndLegacyAuth) addAll(listOf(CACHE_SIZE, ENABLE_LEGACY_AUTHORIZATION))
        }
    return copy(present = present - gone)
}

/** Whether a shown row only takes effect once the server restarts, so the page offers the way to the Server page. */
private fun AdvancedSettings.needsRestart(): Boolean = has(ENABLE_METRICS) || has(CACHE_SIZE) || has(PARALLEL_IMAGE_ENCODING_LIMIT)

@Composable
private fun scanningRows(
    settings: AdvancedSettings,
    actions: AdvancedActions,
): List<ListItem> =
    listOfNotNull(
        numberRow(settings, AdvancedNumber.MonitorDelay, LIBRARY_MONITOR_DELAY, Icons.Outlined.Timer, actions),
        numberRow(settings, AdvancedNumber.UpdateDuration, LIBRARY_UPDATE_DURATION, Icons.Outlined.Schedule, actions),
        numberRow(settings, AdvancedNumber.ScanConcurrency, LIBRARY_SCAN_CONCURRENCY, Icons.Outlined.Sync, actions),
    )

@Composable
private fun imageRows(
    settings: AdvancedSettings,
    actions: AdvancedActions,
): List<ListItem> =
    listOfNotNull(
        numberRow(settings, AdvancedNumber.ImageEncodingLimit, PARALLEL_IMAGE_ENCODING_LIMIT, Icons.Outlined.Image, actions),
        numberRow(settings, AdvancedNumber.ExtractionTimeout, IMAGE_EXTRACTION_TIMEOUT_MS, Icons.Outlined.HourglassEmpty, actions),
        numberRow(settings, AdvancedNumber.CacheSize, CACHE_SIZE, Icons.Outlined.Memory, actions),
    )

@Composable
private fun sessionRows(
    settings: AdvancedSettings,
    actions: AdvancedActions,
): List<ListItem> =
    listOfNotNull(
        numberRow(settings, AdvancedNumber.RetentionDays, ACTIVITY_LOG_RETENTION_DAYS, Icons.Outlined.History, actions),
        numberRow(settings, AdvancedNumber.InactiveThreshold, INACTIVE_SESSION_THRESHOLD, Icons.Outlined.Storage, actions),
        switchRow(
            settings.has(ENABLE_SLOW_RESPONSE_WARNING),
            Icons.Outlined.Warning,
            R.string.advanced_slow_warning,
            R.string.advanced_slow_warning_detail,
            settings.slowResponseWarning,
            actions.onSlowResponseWarning,
        ),
        numberRow(settings, AdvancedNumber.SlowThreshold, SLOW_RESPONSE_THRESHOLD_MS, Icons.Outlined.Speed, actions),
        switchRow(
            settings.has(ALLOW_CLIENT_LOG_UPLOAD),
            Icons.Outlined.BugReport,
            R.string.advanced_client_logs,
            R.string.advanced_client_logs_detail,
            settings.allowClientLogUpload,
            actions.onAllowClientLogUpload,
        ),
        switchRow(
            settings.has(ENABLE_METRICS),
            Icons.Outlined.Analytics,
            R.string.advanced_metrics,
            R.string.advanced_metrics_detail,
            settings.metrics,
            actions.onMetrics,
        ),
    )

@Composable
private fun clientRows(
    settings: AdvancedSettings,
    actions: AdvancedActions,
): List<ListItem> =
    listOfNotNull(
        ListItem(
            icon = Icons.Outlined.Public,
            label = stringResource(R.string.advanced_cors),
            detail = hostsLabel(settings.corsHosts),
            onClick = { actions.onEditList(AdvancedList.CorsHosts) },
        ).takeIf { settings.has(CORS_HOSTS) },
        ListItem(
            icon = Icons.Outlined.Cast,
            label = stringResource(R.string.advanced_cast),
            detail = pluralStringResource(R.plurals.advanced_cast_count, settings.castReceivers.size, settings.castReceivers.size),
            onClick = { actions.onEditList(AdvancedList.CastReceivers) },
        ).takeIf { settings.has(CAST_RECEIVER_APPLICATIONS) },
        switchRow(
            settings.has(ENABLE_LEGACY_AUTHORIZATION),
            Icons.Outlined.Lock,
            R.string.advanced_legacy_auth,
            R.string.advanced_legacy_auth_detail,
            settings.legacyAuthorization,
            actions.onLegacyAuthorization,
        ),
    )

@Composable
private fun hostsLabel(hosts: List<String>): String =
    when {
        hosts.isEmpty() -> stringResource(R.string.advanced_cors_none)
        hosts == listOf("*") -> stringResource(R.string.advanced_cors_any)
        else -> hosts.joinToString(", ")
    }

@Composable
private fun switchRow(
    present: Boolean,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: Int,
    detail: Int,
    value: Boolean,
    onChange: (Boolean) -> Unit,
): ListItem? =
    ListItem(
        icon = icon,
        label = stringResource(label),
        detail = stringResource(detail),
        supportingMaxLines = DETAIL_LINES,
        toggled = value,
        onClick = { onChange(!value) },
    ).takeIf { present }

@Composable
private fun numberRow(
    settings: AdvancedSettings,
    number: AdvancedNumber,
    key: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    actions: AdvancedActions,
): ListItem? =
    ListItem(
        icon = icon,
        label = stringResource(number.title()),
        detail = numberValue(number, settings),
        supportingMaxLines = DETAIL_LINES,
        onClick = { actions.onEdit(number) },
    ).takeIf { settings.has(key) }

internal fun AdvancedNumber.title(): Int =
    when (this) {
        AdvancedNumber.RetentionDays -> R.string.advanced_retention
        AdvancedNumber.MonitorDelay -> R.string.advanced_monitor_delay
        AdvancedNumber.UpdateDuration -> R.string.advanced_update_duration
        AdvancedNumber.ScanConcurrency -> R.string.advanced_scan_concurrency
        AdvancedNumber.ImageEncodingLimit -> R.string.advanced_image_limit
        AdvancedNumber.ExtractionTimeout -> R.string.advanced_extraction_timeout
        AdvancedNumber.SlowThreshold -> R.string.advanced_slow_threshold
        AdvancedNumber.InactiveThreshold -> R.string.advanced_inactive
        AdvancedNumber.CacheSize -> R.string.advanced_cache
    }

/** A number as the row shows it: with its unit, and a word where 0 means something other than none. */
@Composable
private fun numberValue(
    number: AdvancedNumber,
    settings: AdvancedSettings,
): String =
    when (number) {
        AdvancedNumber.RetentionDays ->
            settings.activityLogRetentionDays?.let { pluralStringResource(R.plurals.advanced_days, it, it) }
                ?: stringResource(R.string.advanced_not_set)
        AdvancedNumber.MonitorDelay -> stringResource(R.string.advanced_seconds, settings.libraryMonitorDelay)
        AdvancedNumber.UpdateDuration -> stringResource(R.string.advanced_seconds, settings.libraryUpdateDuration)
        AdvancedNumber.ScanConcurrency ->
            settings.libraryScanConcurrency.takeIf { it > 0 }?.toString() ?: stringResource(R.string.advanced_automatic)
        AdvancedNumber.ImageEncodingLimit ->
            settings.parallelImageEncodingLimit.takeIf { it > 0 }?.toString() ?: stringResource(R.string.advanced_automatic)
        AdvancedNumber.ExtractionTimeout ->
            settings.imageExtractionTimeoutMs.takeIf { it > 0 }?.let { stringResource(R.string.advanced_millis, it) }
                ?: stringResource(R.string.advanced_default)
        AdvancedNumber.SlowThreshold -> stringResource(R.string.advanced_millis, settings.slowResponseThresholdMs.toInt())
        AdvancedNumber.InactiveThreshold ->
            settings.inactiveSessionThreshold.takeIf { it > 0 }?.let { stringResource(R.string.advanced_minutes, it) }
                ?: stringResource(R.string.advanced_off)
        AdvancedNumber.CacheSize -> pluralStringResource(R.plurals.advanced_items, settings.cacheSize, settings.cacheSize)
    }
