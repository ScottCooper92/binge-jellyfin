package com.cooper.homelab.ephyra.notifications

import com.binge.companion.sdk.attempt
import com.cooper.homelab.core.foundation.Logger
import com.cooper.homelab.core.notifications.CheckResult
import com.cooper.homelab.core.notifications.Feed
import com.cooper.homelab.core.notifications.FeedCursorStore
import com.cooper.homelab.core.notifications.FeedRead
import com.cooper.homelab.core.notifications.FeedRunner
import com.cooper.homelab.core.notifications.LongCursorCodec
import com.cooper.homelab.ephyra.auth.ServerIdentity
import com.cooper.homelab.ephyra.server.ActivityEntry
import com.cooper.homelab.ephyra.server.Connection
import com.cooper.homelab.ephyra.server.JellyfinClient
import com.cooper.homelab.ephyra.server.JellyfinHttpException
import com.cooper.homelab.ephyra.server.Severity
import com.cooper.homelab.ephyra.server.activity
import com.cooper.homelab.ephyra.server.me

/** The name the activity feed's cursor is kept under: a cursor's key is the server, the user on it, and this. */
internal const val ACTIVITY_SIGNAL = "activity"

/** What one check of the activity log found, as counts only: no name, address or id leaves the check. */
data class ActivitySummary(
    val failedSignIns: Int = 0,
    val lockouts: Int = 0,
    /** Entries the server marked Warning or Error that are neither of the above: failed tasks, plugin failures. */
    val problems: Int = 0,
) {
    val isEmpty: Boolean get() = failedSignIns == 0 && lockouts == 0 && problems == 0
}

/** Jellyfin's `Type` for a failed sign-in and for a user the server locked out. */
internal const val TYPE_AUTH_FAILED = "AuthenticationFailed"
internal const val TYPE_LOCKED_OUT = "UserLockedOut"

/** The newest entries one check reads. More than this in one interval counts as this many. */
internal const val PAGE = 100

/**
 * The activity log as a shared-library [Feed]: the cursor is the newest entry's id, and what is announced is the entries past it.
 * The log is read newest first, so a burst of more than [PAGE] entries in one interval counts as [PAGE].
 */
internal class ActivityFeed(
    private val client: JellyfinClient,
    private val notifier: ActivityNotifier,
) : Feed<ActivityEntry, Long> {
    override val signal: String = ACTIVITY_SIGNAL

    /** The newest id now, or 0 for an empty log, so the first entry to arrive is announced. */
    override suspend fun seed(): Long = client.activity(0, 1).entries.maxOfOrNull { it.id } ?: 0L

    override suspend fun fetchSince(cursor: Long): FeedRead<ActivityEntry, Long> {
        val entries = client.activity(0, PAGE).entries
        return FeedRead(entries.filter { it.id > cursor }, maxOf(cursor, entries.maxOfOrNull { it.id } ?: cursor))
    }

    override suspend fun announce(rows: List<ActivityEntry>) {
        summarise(rows).takeUnless { it.isEmpty }?.let { notifier.notifyActivity(it) }
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
}

/**
 * One poll: reads the activity log since the last check and tells the user what is in it, through the shared [FeedRunner]. The
 * first check of a server and user only seeds the cursor. Only an administrator's check does anything: the log is theirs alone,
 * and a user who is no longer one stops the poll.
 */
class ActivityChecker(
    private val connection: Connection,
    private val store: FeedCursorStore,
    private val legacy: LegacyActivityCursor,
    private val serverIdentity: ServerIdentity,
    private val notifier: ActivityNotifier,
    private val logger: Logger,
) {
    suspend fun check(): CheckResult {
        // Nothing posted could be shown, so no request is made.
        if (!notifier.canPost()) return CheckResult.Ok
        val (session, client) = connection.signedIn() ?: return CheckResult.Ok
        val administrator = attempt { client.me().isAdministrator }
        administrator.exceptionOrNull()?.let { return classify(it) }
        if (administrator.getOrNull() != true) return CheckResult.StopPolling
        val server = serverIdentity.currentServerId()
        legacy.importInto(store, server, session)
        return FeedRunner(ActivityFeed(client, notifier), LongCursorCodec, store, server, session.userId, ::classify, logger).run()
    }

    private fun classify(failure: Throwable): CheckResult =
        if (failure is JellyfinHttpException && failure.code == HTTP_UNAUTHORIZED) CheckResult.AuthFailure else CheckResult.TransientFailure

    private companion object {
        const val HTTP_UNAUTHORIZED = 401
    }
}
