package com.cooper.homelab.ephyra.flags

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.cooper.homelab.core.flags.FixedFeatureFlags
import com.cooper.homelab.core.flags.FlagPreferenceGate
import com.cooper.homelab.core.flags.Flags
import com.cooper.homelab.core.flags.PreferenceGate
import com.cooper.homelab.ephyra.BuildConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * The client gate's wiring: this app's flag key, its build override and its preference store, through the shared `PreferenceGate`. The gate's
 * own cases (what is offered, that a first value is not a toggle, the fall-back) are tested with it in homelab-core.
 */
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
}
