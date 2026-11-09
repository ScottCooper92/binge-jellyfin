package com.cooper.binge.companion.jellyfin.ui.console

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import com.cooper.binge.companion.jellyfin.R
import com.cooper.binge.companion.jellyfin.server.ActivityFilter
import com.cooper.binge.companion.jellyfin.server.ActivityTimePreset
import com.cooper.binge.companion.jellyfin.server.ActivityWho
import com.cooper.binge.companion.jellyfin.server.Severity
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

@Composable
internal fun timeSummary(filter: ActivityFilter): String =
    when {
        filter.preset == ActivityTimePreset.Today -> stringResource(R.string.activity_time_today)
        filter.preset == ActivityTimePreset.Last7Days -> stringResource(R.string.activity_time_7)
        filter.preset == ActivityTimePreset.Last30Days -> stringResource(R.string.activity_time_30)
        filter.fromMillis != null && filter.toMillis != null ->
            stringResource(R.string.activity_time_range, shortDate(filter.fromMillis), shortDate(filter.toMillis))
        filter.fromMillis != null -> stringResource(R.string.activity_time_from, shortDate(filter.fromMillis))
        filter.toMillis != null -> stringResource(R.string.activity_time_range, "…", shortDate(filter.toMillis))
        else -> stringResource(R.string.activity_time_any)
    }

@Composable
internal fun whoSummary(filter: ActivityFilter): String =
    when {
        filter.username != null -> filter.username
        filter.who == ActivityWho.System -> stringResource(R.string.activity_who_system)
        filter.who == ActivityWho.Users -> stringResource(R.string.activity_who_users)
        else -> stringResource(R.string.activity_who_any)
    }

@Composable
internal fun severitySummary(severity: Severity?): String =
    when (severity) {
        Severity.Error -> stringResource(R.string.activity_severity_error)
        Severity.Warning -> stringResource(R.string.activity_severity_warning)
        Severity.Information -> stringResource(R.string.activity_severity_information)
        null -> stringResource(R.string.activity_severity_any)
    }

@Composable
internal fun typeSummary(type: String?): String = type?.let { activityTypeLabel(it) } ?: stringResource(R.string.activity_type_any)

/** A type as people read it, from the string the type has, or the server's own name for a type this list does not know. */
@Composable
internal fun activityTypeLabel(type: String): String =
    when (type) {
        "SessionStarted" -> stringResource(R.string.activity_type_session_started)
        "SessionEnded" -> stringResource(R.string.activity_type_session_ended)
        "AuthenticationFailed" -> stringResource(R.string.activity_type_authentication_failed)
        "UserLockedOut" -> stringResource(R.string.activity_type_user_locked_out)
        "UserCreated" -> stringResource(R.string.activity_type_user_created)
        "UserDeleted" -> stringResource(R.string.activity_type_user_deleted)
        "UserPasswordChanged" -> stringResource(R.string.activity_type_user_password_changed)
        "Playback" -> stringResource(R.string.activity_type_playback)
        "PlaybackStopped" -> stringResource(R.string.activity_type_playback_stopped)
        "ScheduledTaskFailed" -> stringResource(R.string.activity_type_scheduled_task_failed)
        "PluginInstalled" -> stringResource(R.string.activity_type_plugin_installed)
        "PluginUninstalled" -> stringResource(R.string.activity_type_plugin_uninstalled)
        "PluginUpdated" -> stringResource(R.string.activity_type_plugin_updated)
        "SubtitleDownloadFailure" -> stringResource(R.string.activity_type_subtitle_download_failure)
        else -> type
    }

@Composable
private fun shortDate(millis: Long): String {
    val locale = LocalConfiguration.current.locales[0] ?: Locale.ROOT
    return DateTimeFormatter
        .ofLocalizedDate(FormatStyle.MEDIUM)
        .withLocale(locale)
        .withZone(ZoneId.systemDefault())
        .format(Instant.ofEpochMilli(millis))
}

/** The picker's value for the local day [millis] falls in: that day's midnight in UTC, which is what a date picker holds. */
internal fun utcDayStart(
    millis: Long,
    zone: ZoneId,
): Long =
    Instant
        .ofEpochMilli(millis)
        .atZone(zone)
        .toLocalDate()
        .atStartOfDay(ZoneOffset.UTC)
        .toInstant()
        .toEpochMilli()

/** The start of the local day the picker's UTC [pickerMillis] names. */
internal fun localDayStart(
    pickerMillis: Long,
    zone: ZoneId,
): Long =
    Instant
        .ofEpochMilli(pickerMillis)
        .atZone(ZoneOffset.UTC)
        .toLocalDate()
        .atStartOfDay(zone)
        .toInstant()
        .toEpochMilli()

/** The last millisecond of the local day the picker's UTC [pickerMillis] names, so the day itself is shown. */
internal fun localDayEnd(
    pickerMillis: Long,
    zone: ZoneId,
): Long =
    Instant
        .ofEpochMilli(pickerMillis)
        .atZone(ZoneOffset.UTC)
        .toLocalDate()
        .plusDays(1)
        .atStartOfDay(zone)
        .toInstant()
        .toEpochMilli() - 1
