package com.cooper.binge.companion.jellyfin

import com.cooper.homelab.core.server.network.CleartextConsent
import com.cooper.homelab.core.server.network.DataStoreCleartextConsent
import com.cooper.homelab.core.testing.InMemoryDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SessionCleartextConsentTest {
    private class RecordingConsent : CleartextConsent {
        val retained = mutableListOf<String?>()

        override suspend fun allows(host: String): Boolean = false

        override suspend fun grant(host: String) = Unit

        override suspend fun retainOnly(host: String?) {
            retained += host
        }
    }

    @Test
    fun `only a public plain http host needs the user's agreement`() {
        assertEquals("media.example.com", insecurePublicHost("http://media.example.com:8096"))
        assertNull(insecurePublicHost("https://media.example.com"))
        assertNull(insecurePublicHost("http://192.168.1.10:8096"))
        assertNull(insecurePublicHost("http://jellyfin.local"))
        assertNull(insecurePublicHost("not an address"))
        assertNull(insecurePublicHost(null))
    }

    @Test
    fun `leaving the server drops the consent and moving to another keeps only its host`() =
        runTest {
            val consent = RecordingConsent()
            val baseUrls = MutableStateFlow<String?>("http://media.example.com:8096")
            val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
            consent.followSession(baseUrls, scope)

            baseUrls.value = null
            baseUrls.value = "http://other.example.org"
            baseUrls.value = "https://other.example.org"

            assertEquals(listOf("media.example.com", null, "other.example.org", null), consent.retained)
        }

    @Test
    fun `a change that leaves the same host asks for nothing more`() =
        runTest {
            val consent = RecordingConsent()
            val baseUrls = MutableStateFlow<String?>("http://media.example.com:8096")
            val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
            consent.followSession(baseUrls, scope)

            baseUrls.value = "http://media.example.com:8097"

            assertEquals(listOf("media.example.com"), consent.retained)
        }

    @Test
    fun `taking the agreement back leaves the real store allowing nothing`() =
        runTest {
            val consent = DataStoreCleartextConsent(InMemoryDataStore()) { null }
            consent.grant("media.example.com")
            val baseUrls = MutableStateFlow<String?>("http://media.example.com:8096")
            val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
            consent.followSession(baseUrls, scope)
            assertEquals(true, consent.allows("media.example.com"))

            // The sign-out the take-back runs clears the session.
            baseUrls.value = null

            assertEquals(false, consent.allows("media.example.com"))
        }
}
