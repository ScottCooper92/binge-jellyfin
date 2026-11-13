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
