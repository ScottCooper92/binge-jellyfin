package com.cooper.homelab.ephyra.ui

import android.content.SharedPreferences
import androidx.core.content.edit
import com.cooper.homelab.core.presentation.connect.LinkStash

/**
 * Where a Quick Connect wait is kept while the user approves the code somewhere else, so it outlives this process being killed.
 *
 * What it holds is one string: the connect flow's record of the wait (the server's address, the code, and the poll secret that
 * finishes the approval), which the flow has already sealed whole under the Keystore key the session's token uses, so nothing in it
 * is readable here. It is held only while a code is pending, and the flow clears it when the wait ends (a sign-in, a refusal, an
 * expired code, Cancel), as do a session beginning and a session ending.
 *
 * A write uses `commit`, which returns once the file is on disk: the process can be killed straight after the code is shown, and a
 * write still queued then would be the very wait this keeps. It runs off the main thread. A clear uses `apply`, because callers
 * clear on the main thread and `commit` would write and sync the file there: `apply` updates the in-memory value at once, so a
 * `read` straight after finds nothing, and Android flushes it when the activity stops, before a swipe from recents kills the
 * process. The file is private to the app and excluded from backup and device
 * transfer (`data_extraction_rules.xml`).
 */
internal class SharedPreferencesLinkStash(
    private val preferences: SharedPreferences,
) : LinkStash {
    override fun read(): String? = preferences.getString(RECORD, null)

    override fun write(value: String) = preferences.edit(commit = true) { putString(RECORD, value) }

    override fun clear() = preferences.edit { remove(RECORD) }

    private companion object {
        const val RECORD = "pending_link"
    }
}
