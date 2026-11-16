package com.cooper.binge.companion.jellyfin.feedback

import android.os.Build
import com.cooper.binge.companion.jellyfin.BuildConfig
import com.cooper.binge.companion.jellyfin.server.JellyfinVersion
import com.cooper.homelab.core.feedback.BugReport
import com.cooper.homelab.core.feedback.bugReportUrl

/**
 * Where a bug in this app is reported: a new issue on the public app-feedback repository, through its Ephyra form
 * (`ephyra-bug.yml`), which adds the `app: ephyra` label. This repository's own issues are not public. A fork points this at
 * its own repository.
 */
const val BUG_REPORT_REPOSITORY = "https://github.com/ScottCooper92/app-feedback"

private const val BUG_TEMPLATE = "ephyra-bug.yml"

/**
 * The bug form with the facts a report is prefilled with: the app, the device and, when it is known, the server's release.
 * Never the server's address or the user's name, because the issue is public. The keys are the form's field ids, and `server`
 * is left out when the release is not known.
 */
internal fun ephyraBugReport(
    appVersion: String,
    device: String,
    androidVersion: String,
    server: String? = null,
): BugReport =
    BugReport(
        template = BUG_TEMPLATE,
        fields =
            mapOf(
                "app-version" to appVersion,
                "device" to device,
                "android-version" to androidVersion,
                "server" to server,
            ),
    )

/** This install's [DeviceReport], with the connected server's release when [serverVersion] is known, and the link it makes. */
fun installBugReportUrl(serverVersion: JellyfinVersion? = null): String =
    bugReportUrl(
        BUG_REPORT_REPOSITORY,
        ephyraBugReport(
            appVersion = "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
            device = "${Build.MANUFACTURER} ${Build.MODEL}",
            androidVersion = "${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
            server = serverVersion?.let { "Jellyfin ${it.major}.${it.minor}.${it.patch}" },
        ),
    )
