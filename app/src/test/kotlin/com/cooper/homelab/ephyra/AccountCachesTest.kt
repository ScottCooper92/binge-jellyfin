package com.cooper.homelab.ephyra

import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** What the app keeps about the signed-in account is forgotten when its session ends, so the next account does not read it. */
@RunWith(RobolectricTestRunner::class)
class AccountCachesTest {
    private val graph = AppGraph(ApplicationProvider.getApplicationContext(), sessionsForTest = testSessionStore())

    @Test
    fun `the recent searches are forgotten when the session ends`() =
        runBlocking {
            graph.queryHistory.remember("xyz")
            assertEquals(listOf("xyz"), withTimeout(TIMEOUT_MILLIS) { graph.queryHistory.recents.first { it.isNotEmpty() } })

            graph.clearAccountCaches()

            assertEquals(emptyList<String>(), withTimeout(TIMEOUT_MILLIS) { graph.queryHistory.recents.first { it.isEmpty() } })
        }

    private companion object {
        const val TIMEOUT_MILLIS = 10_000L
    }
}
