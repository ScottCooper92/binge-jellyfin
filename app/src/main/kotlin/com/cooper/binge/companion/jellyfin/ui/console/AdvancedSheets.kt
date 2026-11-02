package com.cooper.binge.companion.jellyfin.ui.console

import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import com.cooper.binge.companion.jellyfin.R
import com.cooper.binge.companion.jellyfin.server.AdvancedSettings

private fun AdvancedNumber.body(): Int =
    when (this) {
        AdvancedNumber.RetentionDays -> R.string.advanced_retention_body
        AdvancedNumber.MonitorDelay -> R.string.advanced_monitor_delay_body
        AdvancedNumber.UpdateDuration -> R.string.advanced_update_duration_body
        AdvancedNumber.ScanConcurrency -> R.string.advanced_scan_concurrency_body
        AdvancedNumber.ImageEncodingLimit -> R.string.advanced_image_limit_body
        AdvancedNumber.ExtractionTimeout -> R.string.advanced_extraction_timeout_body
        AdvancedNumber.SlowThreshold -> R.string.advanced_slow_threshold_body
        AdvancedNumber.InactiveThreshold -> R.string.advanced_inactive_body
        AdvancedNumber.CacheSize -> R.string.advanced_cache_body
    }

private fun AdvancedNumber.current(settings: AdvancedSettings): String =
    when (this) {
        AdvancedNumber.RetentionDays -> settings.activityLogRetentionDays?.toString().orEmpty()
        AdvancedNumber.MonitorDelay -> settings.libraryMonitorDelay.toString()
        AdvancedNumber.UpdateDuration -> settings.libraryUpdateDuration.toString()
        AdvancedNumber.ScanConcurrency -> settings.libraryScanConcurrency.toString()
        AdvancedNumber.ImageEncodingLimit -> settings.parallelImageEncodingLimit.toString()
        AdvancedNumber.ExtractionTimeout -> settings.imageExtractionTimeoutMs.toString()
        AdvancedNumber.SlowThreshold -> settings.slowResponseThresholdMs.toString()
        AdvancedNumber.InactiveThreshold -> settings.inactiveSessionThreshold.toString()
        AdvancedNumber.CacheSize -> settings.cacheSize.toString()
    }

/**
 * A number's sheet: Save keeps what's typed if it is in range, and says so if it isn't; Cancel leaves the setting as it
 * was. [inWindow] false draws the form for a frame.
 */
@Composable
internal fun NumberSheet(
    number: AdvancedNumber,
    settings: AdvancedSettings,
    actions: AdvancedActions,
    inWindow: Boolean = true,
) {
    var value by rememberSaveable(number, key = "NumberSheet.value") { mutableStateOf(number.current(settings)) }
    var tried by rememberSaveable(number, key = "NumberSheet.tried") { mutableStateOf(false) }
    val valid = number.parse(value) != null
    ConsoleSheet(onDismissRequest = { actions.onEdit(null) }, inWindow = inWindow) {
        ConsoleTextEntry(
            title = stringResource(number.title()),
            body = stringResource(number.body()),
            value = value,
            onValueChange = { value = it },
            submitLabel = stringResource(R.string.advanced_save),
            onSubmit = {
                tried = true
                if (valid) actions.onSave(number, value)
            },
            onCancel = { actions.onEdit(null) },
            error = if (tried && !valid) stringResource(R.string.advanced_number_invalid, number.min, number.max) else null,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        )
    }
}

/** A list's sheet: the hosts one per line, or the cast receivers one per line as `id, name`. [inWindow] false draws it for a frame. */
@Composable
internal fun ListSheet(
    list: AdvancedList,
    settings: AdvancedSettings,
    actions: AdvancedActions,
    inWindow: Boolean = true,
) {
    val current =
        when (list) {
            AdvancedList.CorsHosts -> settings.corsHosts.joinToString("\n")
            AdvancedList.CastReceivers -> settings.castReceivers.joinToString("\n") { "${it.id}, ${it.name}" }
        }
    var value by rememberSaveable(list, key = "ListSheet.value") { mutableStateOf(current) }
    var tried by rememberSaveable(list, key = "ListSheet.tried") { mutableStateOf(false) }
    val valid = list == AdvancedList.CorsHosts || parseCastReceivers(value) != null
    ConsoleSheet(onDismissRequest = { actions.onEditList(null) }, inWindow = inWindow) {
        ConsoleTextEntry(
            title = stringResource(if (list == AdvancedList.CorsHosts) R.string.advanced_cors else R.string.advanced_cast),
            body = stringResource(if (list == AdvancedList.CorsHosts) R.string.advanced_cors_body else R.string.advanced_cast_body),
            value = value,
            onValueChange = { value = it },
            submitLabel = stringResource(R.string.advanced_save),
            onSubmit = {
                tried = true
                if (valid) actions.onSaveList(list, value)
            },
            onCancel = { actions.onEditList(null) },
            error = if (tried && !valid) stringResource(R.string.advanced_cast_invalid) else null,
            singleLine = false,
            minLines = LIST_MIN_LINES,
            maxLines = LIST_MAX_LINES,
        )
    }
}

private const val LIST_MIN_LINES = 3
private const val LIST_MAX_LINES = 8
