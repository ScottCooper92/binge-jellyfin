package com.cooper.binge.companion.jellyfin.ui.console

import androidx.lifecycle.ViewModel
import com.cooper.binge.companion.jellyfin.FakeJellyfin
import com.cooper.binge.companion.jellyfin.server.Connection
import com.cooper.binge.companion.jellyfin.session
import com.cooper.binge.companion.jellyfin.testSessionStore
import com.cooper.binge.companion.jellyfin.ui.ViewModelTeardown
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class LiveTvViewModelTest {
    private val jellyfin = FakeJellyfin()
    private val sessions = testSessionStore()
    private val connection = Connection(OkHttpClient(), sessions, "Jellyfin Companion", "Test", "1.0", Dispatchers.IO)
    private val teardown = ViewModelTeardown()

    private fun <T : ViewModel> made(viewModel: T): T = teardown.made(viewModel)

    @Before
    fun main() {
        Dispatchers.setMain(Dispatchers.Unconfined)
        runBlocking { sessions.save(jellyfin.session()) }
    }

    @After
    fun close() = teardown.close(jellyfin)

    private fun LiveTvViewModel.settled() = runBlocking { withTimeout(TIMEOUT_MILLIS) { state.first { it !is LiveTvUiState.Loading } } }

    private fun guideAsked() = jellyfin.requests.any { it.url.encodedPath == "/LiveTv/GuideInfo" }

    @Test
    fun `lists the tuners and guide providers, and when the guide ends for a user with Live TV access`() {
        val ready = made(LiveTvViewModel(connection, withGuide = true)).settled() as LiveTvUiState.Ready
        assertEquals(listOf("IPTV", "http://192.168.1.50"), ready.summary.tuners.map { it.name })
        assertEquals(listOf("/config/guide.xml", "alice"), ready.summary.providers.map { it.label })
        assertNotNull(ready.summary.guideEndMillis)
    }

    @Test
    fun `a user without Live TV access is not asked for the guide, which the server would refuse`() {
        val ready = made(LiveTvViewModel(connection, withGuide = false)).settled() as LiveTvUiState.Ready
        assertEquals(2, ready.summary.tuners.size)
        assertNull(ready.summary.guideEndMillis)
        assertTrue(!guideAsked())
    }

    @Test
    fun `a guide read the server refuses leaves the rest of the page standing`() {
        jellyfin.guideInfoStatus = 403
        val ready = made(LiveTvViewModel(connection, withGuide = true)).settled() as LiveTvUiState.Ready
        assertEquals(2, ready.summary.providers.size)
        assertNull(ready.summary.guideEndMillis)
        assertTrue(guideAsked())
    }

    @Test
    fun `a configuration the server will not give is a failure the page can retry`() {
        jellyfin.liveTvConfiguration = ""
        val vm = made(LiveTvViewModel(connection, withGuide = false))
        assertTrue(vm.settled() is LiveTvUiState.Failed)
        jellyfin.liveTvConfiguration = """{"TunerHosts":[],"ListingProviders":[]}"""
        vm.load()
        val ready = vm.settled() as LiveTvUiState.Ready
        assertTrue(ready.summary.tuners.isEmpty())
    }

    private companion object {
        const val TIMEOUT_MILLIS = 10_000L
    }
}
