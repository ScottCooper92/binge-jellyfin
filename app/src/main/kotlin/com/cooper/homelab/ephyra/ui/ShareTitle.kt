package com.cooper.homelab.ephyra.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent

/** Offers a title's public page to another app, as text: the title's name and the address. The address is never on the user's server. */
fun Context.shareTitle(
    title: String,
    url: String,
) {
    val send =
        Intent(Intent.ACTION_SEND)
            .setType("text/plain")
            .putExtra(Intent.EXTRA_SUBJECT, title)
            .putExtra(Intent.EXTRA_TEXT, "$title\n$url")
    try {
        startActivity(Intent.createChooser(send, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (_: ActivityNotFoundException) {
        // No app to share to: nothing was sent anywhere.
    }
}
