package com.cooper.binge.companion.jellyfin.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Article
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.Coffee
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Healing
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Science
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.binge.designsystem.component.ItemGroup
import com.binge.designsystem.component.ListItem
import com.binge.designsystem.component.ListItemDestination
import com.binge.designsystem.template.BingeScreenScaffold
import com.binge.designsystem.template.ScreenBar
import com.binge.designsystem.template.ScreenBody
import com.binge.designsystem.template.screenListPadding
import com.cooper.binge.companion.jellyfin.BuildConfig
import com.cooper.binge.companion.jellyfin.R
import com.cooper.binge.companion.jellyfin.feedback.installBugReportUrl
import com.cooper.binge.companion.jellyfin.telemetry.AnalyticsConsent
import com.cooper.binge.companion.jellyfin.telemetry.TelemetryPrefs
import com.cooper.binge.companion.jellyfin.tip.TipEvent
import com.cooper.binge.companion.jellyfin.tip.TipUiState
import com.cooper.binge.companion.jellyfin.ui.console.ConsoleMessage
import com.cooper.binge.companion.jellyfin.ui.console.ConsolePage
import com.cooper.binge.companion.jellyfin.ui.console.TipResults
import com.cooper.binge.companion.jellyfin.ui.console.TipSheet
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch
import com.binge.designsystem.R as DesR

/** The hosted privacy policy page, built from `docs/listing/privacy.md` by `privacy-policy.yml`: the URL Play links to. */
private const val PRIVACY_POLICY_URL = "https://scottcooper92.github.io/binge-jellyfin/privacy/"

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
    titled: Boolean = true,
    onOpenTip: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    ItemGroup(
        title = if (titled) stringResource(R.string.settings_group_app) else null,
        rows =
            listOfNotNull(
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
                // The tip jar's row: only once Play has priced every step (see `TipUiState.Ready`).
                onOpenTip?.let {
                    ListItem(
                        icon = Icons.Outlined.Coffee,
                        label = stringResource(R.string.tip_row),
                        onClick = it,
                    )
                },
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
 * The "This app" page: what the app reports and feedback, the tip jar, then About. Opened from the home's This app row, for
 * everyone. The tip jar's row shows only once Play has priced every step ([tip] is [TipUiState.Ready]); [tipEvents] says how a
 * tip ended.
 */
@Composable
internal fun ThisAppScreen(
    actions: Actions,
    onBack: () -> Unit,
    tip: TipUiState = TipUiState.Unavailable,
    tipEvents: Flow<TipEvent> = emptyFlow(),
    onGiveTip: (Int) -> Unit = {},
) {
    val snackbarHostState = remember { SnackbarHostState() }
    var failed by remember { mutableStateOf(false) }
    var tipOpen by remember { mutableStateOf(false) }
    TipResults(tipEvents, snackbarHostState, onFailed = { failed = true })
    if (failed) {
        ConsoleMessage(
            title = stringResource(R.string.tip_failed_title),
            message = stringResource(R.string.tip_failed_body),
            onDismiss = { failed = false },
        )
    }
    val prices = (tip as? TipUiState.Ready)?.prices
    if (tipOpen && prices != null) {
        TipSheet(
            prices = prices,
            onGive = {
                tipOpen = false
                onGiveTip(it)
            },
            onDismiss = { tipOpen = false },
        )
    }
    BingeScreenScaffold(
        title = stringResource(R.string.settings_group_app),
        bar = ScreenBar.Small,
        onBack = onBack,
        snackbarHostState = snackbarHostState,
    ) { padding ->
        ScreenBody(padding) { _ ->
            LazyColumn(
                contentPadding = padding.screenListPadding(),
                verticalArrangement = Arrangement.spacedBy(dimensionResource(DesR.dimen.padding_l)),
            ) {
                item { AppGroup(actions.appSettings, actions, titled = false, onOpenTip = prices?.let { { tipOpen = true } }) }
                actions.notifications?.let { notifications -> item { NotificationsGroup(notifications, actions) } }
                item { AboutGroup(actions) }
            }
        }
    }
}

/** The home's way to the This app page, a row of its own after the server's pages. Everyone has it. */
internal fun LazyListScope.thisAppRow(actions: Actions) {
    item {
        ItemGroup(
            title = null,
            rows =
                listOf(
                    ListItem(
                        icon = Icons.Outlined.Tune,
                        label = stringResource(R.string.settings_group_app),
                        detail = stringResource(R.string.this_app_detail),
                        selected = actions.isOpenBeside(ConsolePage.ThisApp),
                        onClick = { actions.onOpenPage(ConsolePage.ThisApp) },
                    ),
                ),
        )
    }
}

/** What ends an ordinary user's home: the This app row, and Sign out last. */
internal fun LazyListScope.homeFoot(actions: Actions) {
    thisAppRow(actions)
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
