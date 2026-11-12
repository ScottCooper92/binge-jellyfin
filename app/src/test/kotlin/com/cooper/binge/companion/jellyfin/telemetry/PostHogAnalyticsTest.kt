package com.cooper.binge.companion.jellyfin.telemetry

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import com.cooper.binge.companion.jellyfin.InMemoryDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PostHogAnalyticsTest {
    /** Records every call in order, so a test can say what reached the SDK and when. */
    private class RecordingClient : AnalyticsClient {
        val calls = mutableListOf<String>()

        override fun optIn() {
            calls += "optIn"
        }

        override fun optOut() {
            calls += "optOut"
        }

        override fun screen(
            name: String,
            properties: Map<String, Any>,
        ) {
            calls += if (properties.isEmpty()) "screen:$name" else "screen:$name:$properties"
        }

        override fun event(
            name: String,
            properties: Map<String, Any>,
        ) {
            calls += "event:$name:$properties"
        }
    }

    private val client = RecordingClient()
    private val store = InMemoryDataStore()
    private val prefs = TelemetryPrefs(store)

    private fun TestScope.scope() = CoroutineScope(backgroundScope.coroutineContext + UnconfinedTestDispatcher(testScheduler))

    private fun TestScope.gate(store: DataStore<Preferences> = this@PostHogAnalyticsTest.store) =
        AnalyticsConsentGate(TelemetryPrefs(store), scope())

    private fun TestScope.analytics(
        client: AnalyticsClient? = this@PostHogAnalyticsTest.client,
        gate: AnalyticsConsentGate = gate(),
    ) = PostHogAnalytics(client, gate, scope())

    @Test
    fun `nothing is sent while undecided, and the client is closed against any opt-in it kept`() =
        runTest {
            analytics().screen("requests")
            assertEquals(listOf("optOut"), client.calls)
        }

    @Test
    fun `a grant opens the client before the first screen, and a decline closes it again`() =
        runTest {
            val analytics = analytics()
            prefs.setAnalyticsGranted(true)
            analytics.screen("requests")
            prefs.setAnalyticsGranted(false)
            analytics.screen("settings")
            assertEquals(listOf("optOut", "optIn", "screen:requests", "optOut"), client.calls)
        }

    @Test
    fun `an install that already agreed is opened at start, not closed first`() =
        runTest {
            prefs.setAnalyticsGranted(true)
            analytics().screen("hub")
            assertEquals(listOf("optIn", "screen:hub"), client.calls)
        }

    @Test
    fun `a screen shown before the stored answer is read is held, then sent once a grant is read`() =
        runTest {
            val unread = UnreadDataStore()
            TelemetryPrefs(unread).setAnalyticsGranted(true)
            val analytics = analytics(gate = gate(unread))
            analytics.screen("home")
            assertEquals(listOf("optOut"), client.calls)

            unread.release()
            assertEquals(listOf("optOut", "optIn", "screen:home"), client.calls)
        }

    @Test
    fun `a held screen is dropped when the stored answer is a decline`() =
        runTest {
            val unread = UnreadDataStore()
            TelemetryPrefs(unread).setAnalyticsGranted(false)
            val analytics = analytics(gate = gate(unread))
            analytics.screen("home")
            unread.release()
            analytics.screen("settings")
            assertEquals(listOf("optOut"), client.calls)
        }

    @Test
    fun `a build with no project key sends nothing and does not fail`() =
        runTest {
            prefs.setAnalyticsGranted(true)
            analytics(client = null).screen("hub")
        }

    @Test
    fun `an event is sent only once the stored answer is a grant, and never held`() =
        runTest {
            prefs.setAnalyticsGranted(true)
            analytics().event("request_moderated", mapOf("action" to "approved"))
            assertEquals(listOf("optIn", "event:request_moderated:{action=approved}"), client.calls)
        }

    @Test
    fun `an event sent while undecided is dropped, not held`() =
        runTest {
            val unread = UnreadDataStore()
            TelemetryPrefs(unread).setAnalyticsGranted(true)
            analytics(gate = gate(unread)).event("sign_in")
            assertEquals(listOf("optOut"), client.calls)
        }

    @Test
    fun `an event sent after a decline is dropped`() =
        runTest {
            prefs.setAnalyticsGranted(false)
            analytics().event("sign_in")
            assertEquals(listOf("optOut"), client.calls)
        }

    @Test
    fun `a build with no project key drops an event and does not fail`() =
        runTest {
            prefs.setAnalyticsGranted(true)
            analytics(client = null).event("sign_in")
        }

    @Test
    fun `the config starts opted out, with the SDK's own screen and deep link capture off`() {
        val config = postHogConfig("key", "https://eu.i.posthog.com")
        assertTrue(config.optOut)
        assertFalse(config.captureScreenViews)
        assertFalse(config.captureDeepLinks)
    }

    @Test
    @Suppress("DEPRECATION")
    fun `the config fetches neither remote config nor feature flags, so nothing is requested before consent`() {
        val config = postHogConfig("key", "https://eu.i.posthog.com")
        assertFalse(config.remoteConfig)
        assertFalse(config.preloadFeatureFlags)
    }

    /** A real store whose reads are held back until [release], as on a cold start before DataStore answers. */
    private class UnreadDataStore : DataStore<Preferences> {
        private val inner = InMemoryDataStore()
        private val released = MutableStateFlow(false)

        override val data: Flow<Preferences> = released.filter { it }.flatMapLatest { inner.data }

        fun release() {
            released.value = true
        }

        override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences = inner.updateData(transform)
    }
}
