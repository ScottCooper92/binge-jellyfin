package com.cooper.binge.companion.jellyfin.review

import com.cooper.binge.companion.jellyfin.InMemoryDataStore
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class ReviewSignalStoreTest {
    private val store = ReviewSignalStore(InMemoryDataStore())

    @Test
    fun `a new store has earned nothing`() =
        runTest {
            assertEquals(ReviewSignals(), store.signals())
        }

    @Test
    fun `the first launch is kept from the first stamp`() =
        runTest {
            store.stampFirstLaunchIfUnset(100)
            store.stampFirstLaunchIfUnset(900)
            assertEquals(100L, store.signals().firstLaunchEpochMillis)
        }

    @Test
    fun `actions are counted and an ask is remembered`() =
        runTest {
            store.incrementPositiveActionCount()
            store.incrementPositiveActionCount()
            store.recordPromptRequested(500)
            assertEquals(ReviewSignals(positiveActionCount = 2, lastPromptEpochMillis = 500), store.signals())
        }
}
