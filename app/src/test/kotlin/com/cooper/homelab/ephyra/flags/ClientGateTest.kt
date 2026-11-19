package com.cooper.homelab.ephyra.flags

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.cooper.homelab.core.flags.FixedFeatureFlags
import com.cooper.homelab.core.flags.Flags
import com.cooper.homelab.ephyra.BuildConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** The client is the remote flag AND the user's own preference, read from this one place. */
@OptIn(ExperimentalCoroutinesApi::class)
class ClientGateTest {
    @get:Rule
    val folder = TemporaryFolder()

    private var files = 0

    private suspend fun TestScope.prefs(stored: Boolean?): ClientPrefs {
        val store = PreferenceDataStoreFactory.create(scope = backgroundScope) { folder.newFile("c${files++}.preferences_pb") }
        return ClientPrefs(store).also { if (stored != null) it.setEnabled(stored) }
    }

    private fun TestScope.gate(
        flag: Boolean,
        prefs: ClientPrefs,
    ): PreferenceGate =
        FlagPreferenceGate(
            FixedFeatureFlags(Flags.of(ClientEnabled to flag)),
            ClientEnabled,
            prefs.enabled,
            prefs::setEnabled,
            CoroutineScope(UnconfinedTestDispatcher(testScheduler)),
        )

    @Test
    fun `the client flag is off by default, and so is a build that asks for nothing`() {
        assertFalse(Flags()[ClientEnabled])
        assertFalse(BuildConfig.CLIENT_ENABLED)
        assertTrue(ClientEnabled in RemoteFlagKeys)
    }

    @Test
    fun `the preference defaults to on`() =
        runTest {
            assertTrue(prefs(stored = null).enabled.first())
        }

    @Test
    fun `the four flag and preference cases`() =
        runTest {
            val cases =
                listOf(Triple(false, false, false), Triple(false, true, false), Triple(true, false, false), Triple(true, true, true))
            for ((flag, pref, expected) in cases) {
                assertEquals("flag=$flag pref=$pref", expected, gate(flag, prefs(stored = pref)).resolved.first { it != null })
            }
        }

    @Test
    fun `an unset preference with the flag on is on`() =
        runTest {
            assertTrue(gate(flag = true, prefs = prefs(stored = null)).resolved.first { it != null }!!)
        }

    @Test
    fun `the row is offered only while the remote flag is on`() =
        runTest {
            assertFalse(gate(false, prefs(null)).offered.first())
            assertTrue(gate(true, prefs(null)).offered.first())
        }

    @Test
    fun `an opted-out user never observes the client on, so a first value is not mistaken for a toggle`() =
        runTest {
            val gate = gate(flag = true, prefs = prefs(stored = false))
            val seen = mutableListOf<Boolean?>()
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { gate.resolved.collect { seen += it } }

            assertEquals(false, gate.resolved.first { it != null })
            assertFalse("the value is false from its first non-null state: $seen", seen.any { it == true })
            assertFalse(seen.drop(1).any { it == null })
        }

    @Test
    fun `turning it off and on again emits each change once, and never goes back to unread`() =
        runTest {
            val gate = gate(flag = true, prefs = prefs(stored = null))
            val seen = mutableListOf<Boolean?>()
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { gate.resolved.collect { seen += it } }

            gate.setPreference(false)
            gate.setPreference(true)

            assertEquals(listOf(true, false, true), seen.filterNotNull())
            assertFalse(seen.dropWhile { it == null }.any { it == null })
        }

    @Test
    fun `before it is read the gate falls back to the flag alone`() =
        runTest {
            val gate = gate(flag = true, prefs = prefs(stored = null))
            assertEquals(true, gate.current())
        }

    @Test
    fun `a gate built without one is off and offers nothing`() =
        runTest {
            assertFalse(NoPreferenceGate.current())
            assertFalse(NoPreferenceGate.offered.first())
        }
}
