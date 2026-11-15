package com.cooper.binge.companion.jellyfin.notifications

/** Whether WorkManager should count the run done, or schedule a backed-off retry. */
enum class PollOutcome { Succeed, Retry }

/**
 * What one check's result does, kept out of the worker so it is testable without a Context. A refused sign-in cannot be
 * retried into success, so the user is told and the poll is paused until they sign in again. A user who is no longer an
 * administrator has the switch turned off for them.
 */
class PollReactor(
    private val notifier: ActivityNotifier,
    private val scheduler: NotificationScheduler,
    private val prefs: NotificationPrefs,
) {
    suspend fun react(result: CheckResult): PollOutcome =
        when (result) {
            CheckResult.Ok -> PollOutcome.Succeed
            CheckResult.TransientFailure -> PollOutcome.Retry
            CheckResult.AuthFailure -> {
                notifier.notifyConnectionProblem()
                scheduler.cancel()
                prefs.setPausedForAuthFailure(true)
                PollOutcome.Succeed
            }
            CheckResult.NotAdministrator -> {
                scheduler.cancel()
                prefs.setEnabled(false)
                PollOutcome.Succeed
            }
        }
}
