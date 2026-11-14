package com.cooper.binge.companion.jellyfin.notifications

/** An [ActivityNotifier] that records what it was asked to post. */
class FakeNotifier(
    var mayPost: Boolean = true,
) : ActivityNotifier {
    val summaries = mutableListOf<ActivitySummary>()
    var connectionProblems = 0
    var connectionCancels = 0
    var activityCancels = 0

    override fun canPost(): Boolean = mayPost

    override suspend fun notifyActivity(summary: ActivitySummary) {
        summaries += summary
    }

    override suspend fun notifyConnectionProblem() {
        connectionProblems++
    }

    override suspend fun cancelConnectionProblem() {
        connectionCancels++
    }

    override fun cancelActivity() {
        activityCancels++
    }
}

/** A [NotificationScheduler] that records its calls. */
class FakeScheduler : NotificationScheduler {
    val calls = mutableListOf<String>()

    override fun schedule(intervalMinutes: Int) {
        calls += "schedule $intervalMinutes"
    }

    override fun cancel() {
        calls += "cancel"
    }
}

/** One activity-log entry as the server writes it, newest ids last. */
fun activityJson(
    id: Int,
    type: String,
    severity: String = "Information",
    date: String = "2026-11-01T10:%02d:00Z".format(id % 60),
): String =
    """{"Id":$id,"Name":"Entry $id with a secret address 203.0.113.9","Type":"$type","Severity":"$severity","Date":"$date","ShortOverview":"IP address: 203.0.113.9"}"""
