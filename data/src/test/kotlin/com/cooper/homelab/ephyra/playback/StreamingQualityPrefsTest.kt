package com.cooper.homelab.ephyra.playback

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** The device's own cap on the streaming bitrate: what an install holds on disk, and which cap a connection gets. */
class StreamingQualityPrefsTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val wifiKey = intPreferencesKey("max_bitrate_wifi_mbps")
    private val mobileKey = intPreferencesKey("max_bitrate_mobile_mbps")

    private fun TestScope.store() =
        PreferenceDataStoreFactory.create(scope = backgroundScope) {
            folder.newFile("streaming_quality.preferences_pb")
        }

    @Test
    fun `an install that never chose is on auto for both, and nothing is written for it`() =
        runTest {
            val store = store()
            val prefs = StreamingQualityPrefs(store)
            assertEquals(0, prefs.maxMbps(StreamingNetwork.Wifi).first())
            assertEquals(0, prefs.maxMbps(StreamingNetwork.Mobile).first())
            assertEquals(emptyMap<Any, Any>(), store.data.first().asMap())
        }

    @Test
    fun `each connection keeps its own choice under its own key`() =
        runTest {
            val store = store()
            val prefs = StreamingQualityPrefs(store)
            prefs.setMaxMbps(StreamingNetwork.Wifi, 20)
            prefs.setMaxMbps(StreamingNetwork.Mobile, 4)
            assertEquals(20, store.data.first()[wifiKey])
            assertEquals(4, store.data.first()[mobileKey])
            assertEquals(20, prefs.maxMbps(StreamingNetwork.Wifi).first())
            assertEquals(4, prefs.maxMbps(StreamingNetwork.Mobile).first())
        }

    @Test
    fun `a choice a shipped build wrote is read as it was written`() =
        runTest {
            val store = store()
            store.edit {
                it[wifiKey] = 8
                it[mobileKey] = 2
            }
            val prefs = StreamingQualityPrefs(store)
            assertEquals(8, prefs.maxMbps(StreamingNetwork.Wifi).first())
            assertEquals(2, prefs.maxMbps(StreamingNetwork.Mobile).first())
        }

    @Test
    fun `choosing auto removes the key`() =
        runTest {
            val store = store()
            val prefs = StreamingQualityPrefs(store)
            prefs.setMaxMbps(StreamingNetwork.Wifi, 10)
            prefs.setMaxMbps(StreamingNetwork.Wifi, 0)
            assertNull(store.data.first()[wifiKey])
            assertEquals(0, prefs.maxMbps(StreamingNetwork.Wifi).first())
        }

    @Test
    fun `the cap is the one for the connection the device is on, in bits a second, and none for auto`() =
        runTest {
            val prefs = StreamingQualityPrefs(store())
            prefs.setMaxMbps(StreamingNetwork.Wifi, 20)
            var network = StreamingNetwork.Wifi
            val cap = prefs.cap { network }
            assertEquals(20_000_000L, cap.maxBitrateBps())
            network = StreamingNetwork.Mobile
            assertNull(cap.maxBitrateBps())
            prefs.setMaxMbps(StreamingNetwork.Mobile, 4)
            assertEquals(4_000_000L, cap.maxBitrateBps())
        }

    @Test
    fun `the picker offers auto first and then the steps downward`() {
        assertEquals(listOf(0, 40, 20, 10, 8, 4, 2, 1), STREAMING_QUALITY_STEPS_MBPS)
    }
}
