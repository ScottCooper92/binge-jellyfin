package com.cooper.binge.companion.jellyfin.feedback

import android.os.Build
import com.cooper.binge.companion.jellyfin.BuildConfig
import com.cooper.binge.companion.jellyfin.server.JellyfinVersion
import java.net.URLEncoder

/**
 * Where a bug in this app is reported: a new issue on the public app-feedback repository, through its Ephyra form
 * (`ephyra-bug.yml`), which adds the `app: ephyra` label. This repository's own issues are not public. A fork points this at
 * its own repository.
 */
const val BUG_REPORT_REPOSITORY = "https://github.com/ScottCooper92/app-feedback"

private const val BUG_TEMPLATE = "ephyra-bug.yml"

/**
 * The facts a report is prefilled with: the app, the device and, when it is known, the server's release. Never the server's
 * address or the user's name, because the issue is public.
 */
data class DeviceReport(
    val appVersion: String,
    val device: String,
    val androidVersion: String,
    val server: String? = null,
)

/**
 * A new-issue link on [repository] that opens the bug form with [report] filled in. The keys are the form's field ids: an
 * issue form ignores a `body=`, so each field is prefilled by its own. `server` is left out when the release is not known.
 */
fun bugReportUrl(
    report: DeviceReport,
    repository: String = BUG_REPORT_REPOSITORY,
): String {
    val query =
        listOfNotNull(
            "template" to BUG_TEMPLATE,
            "app-version" to report.appVersion,
            "device" to report.device,
            "android-version" to report.androidVersion,
            report.server?.let { "server" to it },
        ).joinToString("&") { (key, value) -> "$key=${URLEncoder.encode(value, Charsets.UTF_8.name())}" }
    return "${repository.trimEnd('/')}/issues/new?$query"
}

/** This install's [DeviceReport], with the connected server's release when [serverVersion] is known, and the link it makes. */
fun installBugReportUrl(serverVersion: JellyfinVersion? = null): String =
    bugReportUrl(
        DeviceReport(
            appVersion = "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
            device = "${Build.MANUFACTURER} ${Build.MODEL}",
            androidVersion = "${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
            server = serverVersion?.let { "Jellyfin ${it.major}.${it.minor}.${it.patch}" },
        ),
    )
