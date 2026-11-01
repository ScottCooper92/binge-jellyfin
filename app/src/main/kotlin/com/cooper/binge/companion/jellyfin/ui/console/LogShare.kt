package com.cooper.binge.companion.jellyfin.ui.console

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.cooper.binge.companion.jellyfin.R
import java.io.File

/**
 * The Android share sheet for a log, as a `.log` file under the cache directory, handed over by
 * `FileProvider` so the receiving app reads that one file and nothing else. The [text] is what the
 * page showed, already stripped of credentials; no URL, and no token, goes with it.
 */
internal fun shareLogIntent(
    context: Context,
    name: String,
    text: String,
): Intent {
    val file = writeSharedLog(File(context.cacheDir, SHARED_DIR), name, text)
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.logs", file)
    val send =
        Intent(Intent.ACTION_SEND)
            .setType("text/plain")
            .putExtra(Intent.EXTRA_STREAM, uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    return Intent.createChooser(send, context.getString(R.string.logs_share_title))
}

/** Writes [text] to a `.log` file named for [name] in [dir], first emptying [dir] of the last one shared. */
internal fun writeSharedLog(
    dir: File,
    name: String,
    text: String,
): File {
    dir.mkdirs()
    dir.listFiles()?.forEach { it.delete() }
    val safe = name.trim().replace(Regex("[^A-Za-z0-9._-]"), "_").ifBlank { "log" }
    return File(dir, if (safe.endsWith(".log")) safe else "$safe.log").also { it.writeText(text) }
}

private const val SHARED_DIR = "logs"
