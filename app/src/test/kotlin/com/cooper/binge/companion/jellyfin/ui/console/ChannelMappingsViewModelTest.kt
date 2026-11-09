package com.cooper.binge.companion.jellyfin.ui.console

import androidx.lifecycle.ViewModel
import com.cooper.binge.companion.jellyfin.FakeJellyfin
import com.cooper.binge.companion.jellyfin.server.Connection
import com.cooper.binge.companion.jellyfin.session
import com.cooper.binge.companion.jellyfin.testSessionStore
import com.cooper.binge.companion.jellyfin.ui.ViewModelTeardown
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
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
class ChannelMappingsViewModelTest {
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

    private fun ChannelMappingsViewModel.ready(check: (ChannelMappingsUiState.Ready) -> Boolean = { true }) =
        runBlocking {
            withTimeout(TIMEOUT_MILLIS) { state.first { it is ChannelMappingsUiState.Ready && check(it) } }
        } as ChannelMappingsUiState.Ready

    private fun posts() = jellyfin.requests.filter { it.url.encodedPath == "/LiveTv/ChannelMappings" && it.method == "POST" }

    private fun posted(count: Int) = runBlocking { withTimeout(TIMEOUT_MILLIS) { while (posts().size < count) delay(POLL_MILLIS) } }

    @Test
    fun `reads the channels of the provider that was opened, and what each tuner channel is paired with`() {
        val ready = made(ChannelMappingsViewModel(connection, "g1")).ready()
        assertEquals("XMLTV", ready.options.providerName)
        assertEquals(listOf("BBC One", "BBC Two", "ITV"), ready.options.providerChannels.map { it.name })
        assertEquals(listOf("p1", "p2", null), ready.options.tunerChannels.map { it.providerChannelId })
        assertEquals(
            "g1",
            jellyfin.requests
                .first { it.url.encodedPath == "/LiveTv/ChannelMappingOptions" }
                .url
                .queryParameter("providerId"),
        )
    }

    @Test
    fun `a channel with no id is left out, and a missing name reads as the id`() {
        jellyfin.channelMappingOptions =
            """{"ProviderChannels":[{"Name":"","Id":"p9"},{"Name":"No id"}],"TunerChannels":[{"Name":"x"},{"Id":"c9","ProviderChannelId":""}]}"""
        val ready = made(ChannelMappingsViewModel(connection, "g1")).ready()
        assertEquals(listOf("p9"), ready.options.providerChannels.map { it.name })
        assertEquals(listOf("c9"), ready.options.tunerChannels.map { it.name })
        assertNull(
            ready.options.tunerChannels
                .single()
                .providerChannelId,
        )
        assertEquals("", ready.options.providerName)
    }

    @Test
    fun `a failed read is a failure the page can retry`() {
        jellyfin.channelMappingStatus = 500
        val vm = made(ChannelMappingsViewModel(connection, "g1"))
        val failed = runBlocking { withTimeout(TIMEOUT_MILLIS) { vm.state.first { it is ChannelMappingsUiState.Failed } } }
        assertNotNull(failed)
        jellyfin.channelMappingStatus = 200
        vm.load()
        vm.ready()
    }

    @Test
    fun `a pick pairs the tuner channel with the open provider channel, shows it at once and saves it`() {
        val vm = made(ChannelMappingsViewModel(connection, "g1"))
        vm.ready()
        vm.select("p3")
        vm.pick("c3")
        assertEquals(
            "p3",
            vm
                .ready()
                .options.tunerChannels
                .last()
                .providerChannelId,
        )
        posted(1)
        val body = Json.parseToJsonElement(posts().single().body!!.utf8()).jsonObject
        assertEquals("g1", body.getValue("ProviderId").jsonPrimitive.content)
        assertEquals("c3", body.getValue("TunerChannelId").jsonPrimitive.content)
        assertEquals("p3", body.getValue("ProviderChannelId").jsonPrimitive.content)
        // Read back from the server, the pairing is what the server holds.
        vm.load()
        assertEquals(
            "p3",
            vm
                .ready()
                .options.tunerChannels
                .last()
                .providerChannelId,
        )
    }

    @Test
    fun `a pick with no provider channel open, or one already paired, saves nothing`() {
        val vm = made(ChannelMappingsViewModel(connection, "g1"))
        vm.ready()
        vm.pick("c3")
        vm.select("p1")
        vm.pick("c1")
        Thread.sleep(SETTLE_MILLIS)
        assertTrue(posts().isEmpty())
        assertNull(vm.ready().refused)
    }

    @Test
    fun `a pairing the server refuses goes back to what it held, and says so`() {
        val vm = made(ChannelMappingsViewModel(connection, "g1"))
        vm.ready()
        vm.select("p3")
        jellyfin.channelMappingStatus = 500
        vm.pick("c3")
        val ready = vm.ready { it.refused != null }
        assertNull(
            ready.options.tunerChannels
                .last()
                .providerChannelId,
        )
        assertEquals(
            listOf("p1", "p2"),
            ready.options.tunerChannels
                .take(2)
                .map { it.providerChannelId },
        )
    }

    @Test
    fun `the search narrows the tuner channels by name, and choosing another channel starts it again`() {
        val vm = made(ChannelMappingsViewModel(connection, "g1"))
        vm.ready()
        vm.select("p3")
        vm.search("bbc")
        assertEquals(listOf("101 BBC One HD", "102 BBC Two HD"), vm.ready { it.query == "bbc" }.matching.map { it.name })
        vm.search("  itv ")
        assertEquals(listOf("103 ITV HD"), vm.ready { it.query.isNotBlank() && it.matching.size == 1 }.matching.map { it.name })
        vm.select("p1")
        val reset = vm.ready { it.selected == "p1" }
        assertEquals("", reset.query)
        assertEquals(3, reset.matching.size)
        vm.select(null)
        assertNull(vm.ready { it.selected == null }.selected)
    }

    private companion object {
        const val TIMEOUT_MILLIS = 10_000L
        const val POLL_MILLIS = 20L
        const val SETTLE_MILLIS = 300L
    }
}
