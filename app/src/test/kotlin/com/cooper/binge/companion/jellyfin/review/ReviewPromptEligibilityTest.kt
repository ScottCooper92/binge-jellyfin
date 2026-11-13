package com.cooper.binge.companion.jellyfin.review

import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.time.Duration.Companion.days

private val DAY = 1.days.inWholeMilliseconds
private const val NOW = 1_800_000_000_000L
private val THRESHOLDS = ReviewPromptThresholds(minDays = 14, minActions = 5, cooldownDays = 120)

private fun eligible(
    signals: ReviewSignals,
    thresholds: ReviewPromptThresholds = THRESHOLDS,
) = ReviewPromptEligibility.isEligible(signals, thresholds, NOW)

class ReviewPromptEligibilityTest {
    private val earned = ReviewSignals(firstLaunchEpochMillis = NOW - 20 * DAY, positiveActionCount = 5)

    @Test
    fun `a user who has waited and done enough is eligible`() {
        assertEquals(true, eligible(earned))
    }

    @Test
    fun `too few actions holds it back`() {
        assertEquals(false, eligible(earned.copy(positiveActionCount = 4)))
    }

    @Test
    fun `too few days holds it back, and the day it is earned is enough`() {
        assertEquals(false, eligible(earned.copy(firstLaunchEpochMillis = NOW - 13 * DAY)))
        assertEquals(true, eligible(earned.copy(firstLaunchEpochMillis = NOW - 14 * DAY)))
    }

    @Test
    fun `an unstamped first launch is not an old install`() {
        assertEquals(false, eligible(earned.copy(firstLaunchEpochMillis = 0)))
    }

    @Test
    fun `a recent ask holds it back until the cooldown is over`() {
        assertEquals(false, eligible(earned.copy(lastPromptEpochMillis = NOW - 119 * DAY)))
        assertEquals(true, eligible(earned.copy(lastPromptEpochMillis = NOW - 120 * DAY)))
    }

    @Test
    fun `a clock set backwards never lets it through`() {
        assertEquals(false, eligible(earned.copy(firstLaunchEpochMillis = NOW + 30 * DAY)))
        assertEquals(false, eligible(earned.copy(lastPromptEpochMillis = NOW + 30 * DAY)))
    }

    @Test
    fun `a console waits longer than a daily app would`() {
        assertEquals(14, ReviewPromptThresholds.MIN_DAYS)
        assertEquals(5, ReviewPromptThresholds.MIN_ACTIONS)
        assertEquals(120, ReviewPromptThresholds.COOLDOWN_DAYS)
    }
}
