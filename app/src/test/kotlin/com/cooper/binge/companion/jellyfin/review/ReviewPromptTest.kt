package com.cooper.binge.companion.jellyfin.review

import android.app.Activity
import com.cooper.binge.companion.jellyfin.InMemoryDataStore
import com.cooper.binge.companion.jellyfin.flags.FixedFeatureFlags
import com.cooper.binge.companion.jellyfin.flags.Flags
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import kotlin.time.Duration.Companion.days

private val DAY = 1.days.inWholeMilliseconds
private const val NOW = 1_800_000_000_000L

@RunWith(RobolectricTestRunner::class)
class ReviewPromptTest {
    private val activity: Activity = Robolectric.buildActivity(Activity::class.java).get()
    private val store = ReviewSignalStore(InMemoryDataStore())
    private val asked = mutableListOf<Activity>()

    private fun prompt(
        scope: TestScope,
        flags: Flags = Flags(reviewPrompt = true),
    ) = ReviewPrompt(store, { asked += it }, FixedFeatureFlags(flags), scope).apply { clock = { NOW } }

    private suspend fun earn() {
        store.stampFirstLaunchIfUnset(NOW - 20 * DAY)
        repeat(5) { store.incrementPositiveActionCount() }
    }

    @Test
    fun `the first launch is stamped once and never moves`() =
        runTest(UnconfinedTestDispatcher()) {
            val prompt = prompt(this)
            prompt.start()
            prompt.clock = { NOW + 5 * DAY }
            prompt.start()
            assertEquals(NOW, store.signals().firstLaunchEpochMillis)
        }

    @Test
    fun `a positive action is counted`() =
        runTest(UnconfinedTestDispatcher()) {
            val prompt = prompt(this)
            prompt.recordPositiveAction()
            prompt.recordPositiveAction()
            assertEquals(2, store.signals().positiveActionCount)
        }

    @Test
    fun `an earned prompt is asked once, and the ask starts the cooldown`() =
        runTest(UnconfinedTestDispatcher()) {
            earn()
            val prompt = prompt(this)
            prompt.maybeAsk(activity)
            prompt.maybeAsk(activity)
            assertEquals(listOf(activity), asked)
            assertEquals(NOW, store.signals().lastPromptEpochMillis)
        }

    @Test
    fun `nothing is asked until it is earned`() =
        runTest(UnconfinedTestDispatcher()) {
            store.stampFirstLaunchIfUnset(NOW - 20 * DAY)
            repeat(4) { store.incrementPositiveActionCount() }
            prompt(this).maybeAsk(activity)
            assertEquals(emptyList<Activity>(), asked)
            assertEquals(0L, store.signals().lastPromptEpochMillis)
        }

    @Test
    fun `nothing is asked while the flag is off`() =
        runTest(UnconfinedTestDispatcher()) {
            earn()
            prompt(this, flags = Flags(reviewPrompt = false)).maybeAsk(activity)
            assertEquals(emptyList<Activity>(), asked)
        }

    @Test
    fun `nothing is asked in a session that had a tip`() =
        runTest(UnconfinedTestDispatcher()) {
            earn()
            val prompt = prompt(this)
            prompt.suppressForThisSession()
            prompt.maybeAsk(activity)
            assertEquals(emptyList<Activity>(), asked)
        }
}
