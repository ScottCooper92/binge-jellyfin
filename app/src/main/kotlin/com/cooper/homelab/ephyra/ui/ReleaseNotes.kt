package com.cooper.homelab.ephyra.ui

import android.content.Context
import android.content.Intent
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.NewReleases
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.net.toUri
import com.binge.designsystem.component.ListItem
import com.binge.designsystem.component.ListItemDestination
import com.cooper.homelab.ephyra.R

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
 * The row that opens Jellyfin's release notes for the version that is running, in the browser: its page, or the list of releases for a
 * build with no release tag. It is marked as leaving the app, so a screen reader says it opens in the browser. No row is offered
 * without a version to name, or where no browser can open it: [canOpen] says whether one can, and [open] opens it. The newer version's
 * notes are one tap on the Update available pill of the server card.
 */
@Composable
internal fun releaseNotesItems(
    version: String,
    canOpen: (String) -> Boolean = LocalContext.current::canOpen,
    open: (String) -> Unit = LocalContext.current::openInBrowser,
): List<ListItem> {
    val running = version.trim()
    val url = releaseNotesUrl(running)
    val openable = remember(url) { canOpen(url) }
    if (!openable || running.isEmpty()) return emptyList()
    return listOf(
        ListItem(
            icon = Icons.Outlined.NewReleases,
            label = stringResource(R.string.release_notes),
            detail = running,
            destination = ListItemDestination.External,
            onClick = { open(url) },
        ),
    )
}
