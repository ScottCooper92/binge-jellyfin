package com.cooper.binge.companion.jellyfin.ui

import android.content.Context
import android.content.Intent
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.NewReleases
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.net.toUri
import com.binge.designsystem.component.ListItem
import com.cooper.binge.companion.jellyfin.R

/** Where Jellyfin's releases are listed, newest first. */
internal const val JELLYFIN_RELEASES_URL = "https://github.com/jellyfin/jellyfin/releases"

private val RELEASE_VERSION = Regex("""\d+\.\d+(\.\d+)?""")

/**
 * The page of the release notes for [version], the version a server reports. Jellyfin tags a release with a leading `v` and the
 * version, `v10.11.6` or `v12.4`, so a version of two or three numbers opens its tag page. Anything else, a nightly with a suffix,
 * four parts, text that is not a version, or nothing, opens the list of releases instead: the version goes into the address only after
 * it passes this check, so what a server sends can never shape the address. Built from what the app holds; nothing is asked of GitHub.
 */
internal fun releaseNotesUrl(version: String?): String {
    val trimmed = version?.trim().orEmpty()
    return if (RELEASE_VERSION.matches(trimmed)) "$JELLYFIN_RELEASES_URL/tag/v$trimmed" else JELLYFIN_RELEASES_URL
}

/** Whether something on this device can open [url]: a browser. Where none can, the rows that open it are not offered. */
internal fun Context.canOpen(url: String): Boolean = packageManager.resolveActivity(Intent(Intent.ACTION_VIEW, url.toUri()), 0) != null

/**
 * The rows that open Jellyfin's release notes in the browser: **Update available** while the server says there is one, which opens the
 * list of releases (the API gives a flag, not the newer version's number), and **Release notes** for the version that is running,
 * which opens that version's page. No row is offered without a version to name, or where no browser can open it. [canOpen] says
 * whether one can, and [open] opens it.
 */
@Composable
internal fun releaseNotesItems(
    version: String,
    updateAvailable: Boolean,
    canOpen: (String) -> Boolean = LocalContext.current::canOpen,
    open: (String) -> Unit = LocalContext.current::openInBrowser,
): List<ListItem> {
    val running = version.trim()
    val url = releaseNotesUrl(running)
    val openable = remember(url) { canOpen(url) }
    if (!openable) return emptyList()
    val trailing: @Composable () -> Unit = { Icon(Icons.AutoMirrored.Outlined.OpenInNew, contentDescription = null) }
    return listOfNotNull(
        ListItem(
            icon = Icons.Outlined.SystemUpdate,
            label = stringResource(R.string.server_card_update),
            detail = stringResource(R.string.release_notes_update_detail),
            trailingContent = trailing,
            onClick = { open(JELLYFIN_RELEASES_URL) },
        ).takeIf { updateAvailable },
        ListItem(
            icon = Icons.Outlined.NewReleases,
            label = stringResource(R.string.release_notes),
            detail = running,
            trailingContent = trailing,
            onClick = { open(url) },
        ).takeIf { running.isNotEmpty() },
    )
}
