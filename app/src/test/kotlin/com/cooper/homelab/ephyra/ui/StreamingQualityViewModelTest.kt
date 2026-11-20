package com.cooper.homelab.ephyra.ui

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.intPreferencesKey
import com.cooper.homelab.ephyra.FakeJellyfin
import com.cooper.homelab.ephyra.playback.StreamingNetwork
import com.cooper.homelab.ephyra.playback.StreamingQualityPrefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** The device's streaming quality choices: read from its preferences, and a pick writes only its own key. */
@OptIn(ExperimentalCoroutinesApi::class)
class StreamingQualityViewModelTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val wifiKey = intPreferencesKey("max_bitrate_wifi_mbps")
    private val mobileKey = intPreferencesKey("max_bitrate_mobile_mbps")

    private val jellyfin = FakeJellyfin()
    private val teardown = ViewModelTeardown()

    @Before
    fun main() = Dispatchers.setMain(Dispatchers.Unconfined)

    @After
    fun close() = teardown.close(jellyfin)

    private fun vm(prefs: StreamingQualityPrefs) = teardown.made(StreamingQualityViewModel(prefs))

    private fun TestScope.store() =
        PreferenceDataStoreFactory.create(scope = backgroundScope) {
            folder.newFile("streaming_quality.preferences_pb")
        }

    @Test
    fun `a fresh install reads auto on both`() =
        runTest {
            val vm = vm(StreamingQualityPrefs(store()))
            assertEquals(StreamingQualitySettings(0, 0), vm.settings.first { it != null })
        }

    @Test
    fun `a pick on Wi-Fi writes the Wi-Fi key and leaves the mobile one`() =
        runTest {
            val store = store()
            val vm = vm(StreamingQualityPrefs(store))
            vm.set(StreamingNetwork.Wifi, 20)
            assertEquals(StreamingQualitySettings(20, 0), vm.settings.first { it?.wifiMbps == 20 })
            assertEquals(20, store.data.first()[wifiKey])
            assertNull(store.data.first()[mobileKey])
        }

    @Test
    fun `a pick on mobile data writes the mobile key, and auto takes it away again`() =
        runTest {
            val store = store()
            val vm = vm(StreamingQualityPrefs(store))
            vm.set(StreamingNetwork.Mobile, 4)
            assertEquals(StreamingQualitySettings(0, 4), vm.settings.first { it?.mobileMbps == 4 })
            vm.set(StreamingNetwork.Mobile, 0)
            assertEquals(StreamingQualitySettings(0, 0), vm.settings.first { it?.mobileMbps == 0 })
            assertNull(store.data.first()[mobileKey])
        }
}
