package com.cooper.binge.companion.jellyfin.notifications

import com.cooper.binge.companion.jellyfin.auth.JellyfinSession
import com.cooper.binge.companion.jellyfin.server.ActivityEntry
import com.cooper.binge.companion.jellyfin.server.ActivityFilter
import com.cooper.binge.companion.jellyfin.server.Connection
import com.cooper.binge.companion.jellyfin.server.JellyfinHttpException
import com.cooper.binge.companion.jellyfin.server.Severity
import com.cooper.binge.companion.jellyfin.server.activity
import com.cooper.binge.companion.jellyfin.server.me
import java.io.IOException

/** What one check of the activity log found, as counts only: no name, address or id leaves the check. */
data class ActivitySummary(
    val failedSignIns: Int = 0,
    val lockouts: Int = 0,
    /** Entries the server marked Warning or Error that are neither of the above: failed tasks, plugin failures. */
    val problems: Int = 0,
) {
    val isEmpty: Boolean get() = failedSignIns == 0 && lockouts == 0 && problems == 0
}

/** How a check ended, for [PollReactor]. */
enum class CheckResult {
    Ok,

    /** The server could not be reached, or answered with an error: try again later. */
    TransientFailure,

    /** The server refused the saved sign-in. */
    AuthFailure,

    /** The user is not (or is no longer) an administrator: there is nothing for them to watch. */
    NotAdministrator,
}

/** Jellyfin's `Type` for a failed sign-in and for a user the server locked out. */
internal const val TYPE_AUTH_FAILED = "AuthenticationFailed"
internal const val TYPE_LOCKED_OUT = "UserLockedOut"

/** The newest entries one check reads. More than this in one interval counts as this many. */
internal const val PAGE = 100

/**
 * One poll: asks the server for the activity log since the last check and tells the user what is in it. The first check of a
 * server and user only seeds the cursor. Only an administrator's check does anything: the log is theirs alone.
 */
class ActivityChecker(
    private val connection: Connection,
    private val prefs: NotificationPrefs,
    private val notifier: ActivityNotifier,
) {
    suspend fun check(): CheckResult {
        // Nothing posted could be shown, so no request is made.
        if (!notifier.canPost()) return CheckResult.Ok
        val (session, client) = connection.signedIn() ?: return CheckResult.Ok
        return try {
            if (!client.me().isAdministrator) return CheckResult.NotAdministrator
            val owner = session.owner()
            val cursor = prefs.cursor(owner)
            val page =
                if (cursor == null) {
                    client.activity(0, 1)
                } else {
                    client.activity(0, PAGE, ActivityFilter(fromMillis = cursor.dateMillis))
                }
            val fresh = if (cursor == null) emptyList() else page.entries.filter { it.id > cursor.id }
            summarise(fresh).takeUnless { it.isEmpty }?.let { notifier.notifyActivity(it) }
            page.entries.maxByOrNull { it.id }?.let { prefs.setCursor(owner, ActivityCursor(it.id, it.dateMillis)) }
            CheckResult.Ok
        } catch (e: JellyfinHttpException) {
            if (e.code == HTTP_UNAUTHORIZED) CheckResult.AuthFailure else CheckResult.TransientFailure
        } catch (_: IOException) {
            CheckResult.TransientFailure
        }
    }

    private fun summarise(entries: List<ActivityEntry>): ActivitySummary =
        ActivitySummary(
            failedSignIns = entries.count { it.type == TYPE_AUTH_FAILED },
            lockouts = entries.count { it.type == TYPE_LOCKED_OUT },
            problems =
                entries.count {
                    it.type != TYPE_AUTH_FAILED && it.type != TYPE_LOCKED_OUT && it.severity != Severity.Information
                },
        )

    /** The server and user the cursor belongs to, so another account's log is never read from this one's place. */
    private fun JellyfinSession.owner(): String = "$serverId/$userId"

    private companion object {
        const val HTTP_UNAUTHORIZED = 401
    }
}
