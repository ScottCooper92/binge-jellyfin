package com.cooper.binge.companion.jellyfin.ui.console

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.PersistableBundle

/**
 * Puts a new API key on the clipboard, marked sensitive so Android hides it from the clipboard preview
 * and keeps it out of any history. Called from the person's tap on Copy and nowhere else.
 */
internal fun copyApiKey(
    context: Context,
    key: String,
) {
    val clip = ClipData.newPlainText("", key)
    clip.description.extras = PersistableBundle().apply { putBoolean(IS_SENSITIVE, true) }
    (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(clip)
}

/** `ClipDescription.EXTRA_IS_SENSITIVE`, which Android 13 reads; the key is the same string on older releases, which ignore it. */
private const val IS_SENSITIVE = "android.content.extra.IS_SENSITIVE"
