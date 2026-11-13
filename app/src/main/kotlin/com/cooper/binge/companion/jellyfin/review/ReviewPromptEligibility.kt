package com.cooper.binge.companion.jellyfin.review

import kotlin.time.Duration.Companion.days

/** The persisted evidence the review prompt decides on. */
data class ReviewSignals(
    val firstLaunchEpochMillis: Long = 0,
    val positiveActionCount: Int = 0,
    val lastPromptEpochMillis: Long = 0,
)

/** What a user must have done and waited before the prompt may be asked. */
data class ReviewPromptThresholds(
    val minDays: Int = MIN_DAYS,
    val minActions: Int = MIN_ACTIONS,
    val cooldownDays: Int = COOLDOWN_DAYS,
) {
    companion object {
        /** A console is opened now and then, so it waits longer than a daily app would. */
        const val MIN_DAYS = 14
        const val MIN_ACTIONS = 5
        const val COOLDOWN_DAYS = 120
    }
}

/**
 * Whether the user has earned the question: enough real actions, enough time since the first launch, and long enough
 * since the last ask. Pure, so it is tested without Android or Play. Play still decides whether to show anything.
 */
object ReviewPromptEligibility {
    fun isEligible(
        signals: ReviewSignals,
        thresholds: ReviewPromptThresholds,
        nowEpochMillis: Long,
    ): Boolean {
        if (signals.positiveActionCount < thresholds.minActions) return false
        // An unstamped first launch is this launch, not an old install: it holds the prompt back.
        if (signals.firstLaunchEpochMillis <= 0L) return false
        if (elapsedDays(signals.firstLaunchEpochMillis, nowEpochMillis) < thresholds.minDays) return false
        if (signals.lastPromptEpochMillis <= 0L) return true
        return elapsedDays(signals.lastPromptEpochMillis, nowEpochMillis) >= thresholds.cooldownDays
    }

    /** Whole days from [fromEpochMillis] to [nowEpochMillis], never negative, so a clock set backwards cannot let the prompt through. */
    private fun elapsedDays(
        fromEpochMillis: Long,
        nowEpochMillis: Long,
    ): Long = (nowEpochMillis - fromEpochMillis).coerceAtLeast(0) / 1.days.inWholeMilliseconds
}
