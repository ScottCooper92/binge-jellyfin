package com.cooper.binge.companion.jellyfin.ui

import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Article
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Healing
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Science
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.binge.designsystem.component.ItemGroup
import com.binge.designsystem.component.ListItem
import com.binge.designsystem.component.ListItemDestination
import com.cooper.binge.companion.jellyfin.BuildConfig
import com.cooper.binge.companion.jellyfin.R
import com.cooper.binge.companion.jellyfin.feedback.BUG_REPORT_REPOSITORY
import com.cooper.binge.companion.jellyfin.feedback.installBugReportUrl
import com.cooper.binge.companion.jellyfin.feedback.openInBrowser
import com.cooper.binge.companion.jellyfin.feedback.openPlayStoreListing
import com.cooper.binge.companion.jellyfin.feedback.openTestingOptIn
import com.cooper.binge.companion.jellyfin.telemetry.AnalyticsConsent
import com.cooper.binge.companion.jellyfin.telemetry.TelemetryPrefs
import com.cooper.binge.companion.jellyfin.ui.console.ConsolePage
import kotlinx.coroutines.launch

/** The privacy policy as it is published today: its text in the repository, until the page Play wants is hosted. */
private const val PRIVACY_POLICY_URL = "$BUG_REPORT_REPOSITORY/blob/main/docs/listing/privacy.md"

/**
 * The user's answers about what this app reports, as the home and the account show them: usage data (PostHog, only if the user
 * agreed) and crash reports (Crashlytics, off until the user agrees). Both are the user's to change at any time.
 */
data class AppSettings(
    val shareUsageData: Boolean = false,
    val sendCrashReports: Boolean = false,
)

/** The "This app" group: Report a bug, then two switches that save as they are flipped. */
@Composable
internal fun AppGroup(
    settings: AppSettings,
    actions: Actions,
) {
    val context = LocalContext.current
    ItemGroup(
        title = stringResource(R.string.settings_group_app),
        rows =
            listOf(
                ListItem(
                    icon = Icons.Outlined.BugReport,
                    label = stringResource(R.string.settings_report_bug),
                    detail = stringResource(R.string.settings_report_bug_caption),
                    onClick = { context.openInBrowser(installBugReportUrl()) },
                ),
                ListItem(
                    icon = Icons.Outlined.BarChart,
                    label = stringResource(R.string.settings_share_usage_data),
                    detail = stringResource(R.string.settings_share_usage_data_caption),
                    toggled = settings.shareUsageData,
                    onClick = { actions.onShareUsageData(!settings.shareUsageData) },
                ),
                ListItem(
                    icon = Icons.Outlined.Healing,
                    label = stringResource(R.string.settings_send_crash_reports),
                    detail = stringResource(R.string.settings_send_crash_reports_caption),
                    toggled = settings.sendCrashReports,
                    onClick = { actions.onSendCrashReports(!settings.sendCrashReports) },
                ),
            ),
    )
}

/**
 * The About group: the app's own version (a tap opens its Play listing, which is where a rating is left), the privacy
 * policy, the open-source licences, and the beta opt-in. The server's version is in the server's own pages.
 */
@Composable
internal fun AboutGroup(actions: Actions) {
    val context = LocalContext.current
    ItemGroup(
        title = stringResource(R.string.settings_group_about),
        rows =
            listOf(
                ListItem(
                    icon = Icons.Outlined.Info,
                    label = stringResource(R.string.about_app_version),
                    detail = stringResource(R.string.about_version_format, BuildConfig.VERSION_NAME),
                    destination = ListItemDestination.External,
                    onClick = context::openPlayStoreListing,
                ),
                ListItem(
                    icon = Icons.AutoMirrored.Outlined.Article,
                    label = stringResource(R.string.about_privacy_policy),
                    destination = ListItemDestination.External,
                    onClick = { context.openInBrowser(PRIVACY_POLICY_URL) },
                ),
                ListItem(
                    icon = Icons.Outlined.Description,
                    label = stringResource(R.string.licences_title),
                    selected = actions.isOpenBeside(ConsolePage.Licences),
                    onClick = { actions.onOpenPage(ConsolePage.Licences) },
                ),
                ListItem(
                    icon = Icons.Outlined.Science,
                    label = stringResource(R.string.about_join_beta),
                    destination = ListItemDestination.External,
                    onClick = context::openTestingOptIn,
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
    item { AboutGroup(actions) }
    item { SignOutButton(actions) }
}

/** What the activity needs of the telemetry choices: the stored answer, who records one, and [actions] carrying both. */
class AppSettingsBinding(
    val consent: AnalyticsConsent?,
    val answerConsent: (Boolean) -> Unit,
    private val setUsageData: (Boolean) -> Unit,
    private val settings: AppSettings,
    private val setCrashReports: (Boolean) -> Unit,
) {
    /** [actions] with the group's state and its two ways to change it. */
    fun applyTo(actions: Actions): Actions =
        actions.copy(appSettings = settings, onShareUsageData = setUsageData, onSendCrashReports = setCrashReports)
}

/** Reads [prefs] for the life of the composition; the stored answer is null until it has been read. */
@Composable
fun rememberAppSettingsBinding(prefs: TelemetryPrefs): AppSettingsBinding {
    val consent by prefs.analyticsConsent.collectAsStateWithLifecycle(initialValue = null)
    val crashReports by prefs.crashReportingEnabled.collectAsStateWithLifecycle(initialValue = false)
    val scope = rememberCoroutineScope()
    return AppSettingsBinding(
        consent = consent,
        // The first-run question covers both, so one answer sets usage data and crash reports; Settings changes each on its own.
        answerConsent = { granted -> scope.launch { prefs.answerFirstRun(granted) } },
        setUsageData = { granted -> scope.launch { prefs.setAnalyticsGranted(granted) } },
        settings = AppSettings(shareUsageData = consent == AnalyticsConsent.GRANTED, sendCrashReports = crashReports),
        setCrashReports = { enabled -> scope.launch { prefs.setCrashReportingEnabled(enabled) } },
    )
}
