package com.cooper.binge.companion.jellyfin.feedback

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import androidx.core.net.toUri

/** Opens [url] in the browser; where the device has none there is nothing to show, and nothing was sent anywhere. */
fun Context.openInBrowser(url: String) {
    try {
        startActivity(Intent(Intent.ACTION_VIEW, url.toUri()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (_: ActivityNotFoundException) {
        // No browser: the report simply cannot be opened from here.
    }
}

private const val DEBUG_APPLICATION_ID_SUFFIX = ".debug"

/**
 * Opens this app's Play Store listing, in the Play app where it is installed and in the browser where it is not. A
 * debug build's `.debug` suffix is dropped, so a development build opens the real listing.
 */
fun Context.openPlayStoreListing() = openOnPlay("https://play.google.com/store/apps/details?id=${releasePackage()}")

/** Opens the page where a tester opts in to this app's beta, on the same terms as [openPlayStoreListing]. */
fun Context.openTestingOptIn() = openOnPlay("https://play.google.com/apps/testing/${releasePackage()}")

private fun Context.releasePackage(): String = packageName.removeSuffix(DEBUG_APPLICATION_ID_SUFFIX)

private fun Context.openOnPlay(url: String) {
    try {
        startActivity(Intent(Intent.ACTION_VIEW, url.toUri()).setPackage("com.android.vending").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (_: ActivityNotFoundException) {
        openInBrowser(url)
    }
}
