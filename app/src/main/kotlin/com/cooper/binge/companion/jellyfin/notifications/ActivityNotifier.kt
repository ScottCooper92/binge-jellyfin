package com.cooper.binge.companion.jellyfin.notifications

/** What the poll tells the user, seamed off Android so the checker is tested against a fake. */
interface ActivityNotifier {
    /** Whether anything posted could be shown; the poll makes no request when it could not. */
    fun canPost(): Boolean

    /** One notification for the whole check, grouping what it found. */
    suspend fun notifyActivity(summary: ActivitySummary)

    /** The server refused the saved sign-in; the user has to sign in again. */
    suspend fun notifyConnectionProblem()

    suspend fun cancelConnectionProblem()

    /** Every activity notice posted so far, for a sign-out: they describe a server the user has left. */
    fun cancelActivity()
}
