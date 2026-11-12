package com.cooper.binge.companion.jellyfin.ui

import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.binge.designsystem.component.ItemGroup
import com.binge.designsystem.component.ListItem
import com.cooper.binge.companion.jellyfin.R
import com.cooper.binge.companion.jellyfin.telemetry.AnalyticsConsent
import com.cooper.binge.companion.jellyfin.telemetry.TelemetryPrefs
import kotlinx.coroutines.launch

/**
 * The user's answers about what this app reports, as the home and the account show them: usage data (PostHog, only if the user
 * agreed) and crash reports (Crashlytics, on until the user turns it off). Both are the user's to change at any time.
 */
data class AppSettings(
    val shareUsageData: Boolean = false,
    val sendCrashReports: Boolean = true,
)

/** The "This app" group: two switches that save as they are flipped. */
@Composable
internal fun AppGroup(
    settings: AppSettings,
    actions: Actions,
) {
    ItemGroup(
        title = stringResource(R.string.settings_group_app),
        rows =
            listOf(
                ListItem(
                    icon = Icons.Outlined.BarChart,
                    label = stringResource(R.string.settings_share_usage_data),
                    detail = stringResource(R.string.settings_share_usage_data_caption),
                    toggled = settings.shareUsageData,
                    onClick = { actions.onShareUsageData(!settings.shareUsageData) },
                ),
                ListItem(
                    icon = Icons.Outlined.BugReport,
                    label = stringResource(R.string.settings_send_crash_reports),
                    detail = stringResource(R.string.settings_send_crash_reports_caption),
                    toggled = settings.sendCrashReports,
                    onClick = { actions.onSendCrashReports(!settings.sendCrashReports) },
                ),
            ),
    )
}

/**
 * What ends an ordinary user's home: what this app reports, and Sign out last. An administrator reaches both in the account, where
 * their own settings are.
 */
internal fun LazyListScope.homeFoot(actions: Actions) {
    item { AppGroup(actions.appSettings, actions) }
    item { SignOutButton(actions) }
}

/** What the activity needs of the telemetry choices: the stored answer, who records one, and [actions] carrying both. */
class AppSettingsBinding(
    val consent: AnalyticsConsent?,
    val answerConsent: (Boolean) -> Unit,
    private val settings: AppSettings,
    private val setCrashReports: (Boolean) -> Unit,
) {
    /** [actions] with the group's state and its two ways to change it. */
    fun applyTo(actions: Actions): Actions =
        actions.copy(appSettings = settings, onShareUsageData = answerConsent, onSendCrashReports = setCrashReports)
}

/** Reads [prefs] for the life of the composition; the stored answer is null until it has been read. */
@Composable
fun rememberAppSettingsBinding(prefs: TelemetryPrefs): AppSettingsBinding {
    val consent by prefs.analyticsConsent.collectAsStateWithLifecycle(initialValue = null)
    val crashReports by prefs.crashReportingEnabled.collectAsStateWithLifecycle(initialValue = true)
    val scope = rememberCoroutineScope()
    return AppSettingsBinding(
        consent = consent,
        answerConsent = { granted -> scope.launch { prefs.setAnalyticsGranted(granted) } },
        settings = AppSettings(shareUsageData = consent == AnalyticsConsent.GRANTED, sendCrashReports = crashReports),
        setCrashReports = { enabled -> scope.launch { prefs.setCrashReportingEnabled(enabled) } },
    )
}
