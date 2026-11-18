package com.cooper.binge.companion.jellyfin.ui.search

import androidx.lifecycle.ViewModel
import com.cooper.binge.companion.jellyfin.FakeJellyfin
import com.cooper.binge.companion.jellyfin.server.Connection
import com.cooper.binge.companion.jellyfin.server.DiscardLogger
import com.cooper.binge.companion.jellyfin.session
import com.cooper.binge.companion.jellyfin.testSessionStore
import com.cooper.binge.companion.jellyfin.ui.ViewModelTeardown
import com.cooper.binge.companion.jellyfin.ui.console.ConsoleFailure
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The search's switches share one copy of the user's configuration. Jellyfin has no partial update, so each write sends all of
 * it: these tests hold that a write from the search never puts back what was saved somewhere else, which is what two copies of it,
 * one per page, did without anything saying so.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class SearchSwitchesViewModelTest {
    private val jellyfin = FakeJellyfin()
    private val sessions = testSessionStore()
    private val teardown = ViewModelTeardown()
    private val connection =
        teardown.made(Connection(OkHttpClient(), sessions, "Jellyfin Companion", "Test", "1.0", Dispatchers.IO, DiscardLogger))

    private fun <T : ViewModel> made(viewModel: T): T = teardown.made(viewModel)

    @Before
    fun main() {
        Dispatchers.setMain(Dispatchers.Unconfined)
        runBlocking { sessions.save(jellyfin.session()) }
    }

    @After
    fun close() = teardown.close(jellyfin)

    private fun SearchSwitchesViewModel.settled(check: (SearchSwitchesState) -> Boolean = { it is SearchSwitchesState.Ready }) =
        runBlocking { withTimeout(TIMEOUT_MILLIS) { state.first(check) } }

    private fun SearchSwitchesViewModel.ready() = settled() as SearchSwitchesState.Ready

    private fun started() = made(SearchSwitchesViewModel(connection)).also { it.refresh() }

    /** What the fake server holds, which is what any other device or page would read. */
    private fun server(): JsonObject = Json.parseToJsonElement(jellyfin.userConfiguration).jsonObject

    private fun awaitServer(check: (JsonObject) -> Boolean) {
        runBlocking {
            withTimeout(TIMEOUT_MILLIS) {
                while (!check(server())) delay(POLL_MILLIS)
            }
        }
    }

    private fun JsonObject.on(key: String) = getValue(key).jsonPrimitive.boolean

    @Test
    fun `nothing is read until a switch asks`() {
        val vm = made(SearchSwitchesViewModel(connection))
        assertEquals(SearchSwitchesState.Idle, vm.state.value)
        assertTrue(jellyfin.requests.isEmpty())
    }

    @Test
    fun `reads the configuration once for a playback switch and a home switch alike`() {
        val ready = started().ready()
        assertTrue(ready.playback.autoplayNextEpisode)
        assertTrue(ready.home.hidePlayedInLatest)
    }

    @Test
    fun `flipping a playback switch and then a home switch keeps both on the server`() {
        val vm = started()
        vm.ready()
        vm.setAutoplayNextEpisode(false)
        awaitServer { it.containsKey("EnableNextEpisodeAutoPlay") && !it.on("EnableNextEpisodeAutoPlay") }
        vm.setDisplayMissingEpisodes(true)
        awaitServer { it.containsKey("DisplayMissingEpisodes") && it.on("DisplayMissingEpisodes") }

        // The second write carried the first change, not the configuration as it was read: that was the loss with two copies.
        assertFalse(server().on("EnableNextEpisodeAutoPlay"))
        assertTrue(server().on("DisplayMissingEpisodes"))
        val ready = vm.ready()
        assertFalse(ready.playback.autoplayNextEpisode)
        assertTrue(ready.home.displayMissingEpisodes)
    }

    @Test
    fun `a change saved elsewhere survives a flip after the search is read again`() {
        val vm = started()
        vm.ready()
        // The Playback page, opened over the search, saved a new audio language, and the search is shown again.
        jellyfin.userConfiguration =
            jellyfin.userConfiguration.replace("\"AudioLanguagePreference\":\"eng\"", "\"AudioLanguagePreference\":\"fra\"")
        assertTrue(server().getValue("AudioLanguagePreference").jsonPrimitive.content == "fra")
        vm.refresh()
        vm.settled {
            (it as? SearchSwitchesState.Ready)
                ?.configuration
                ?.get("AudioLanguagePreference")
                ?.jsonPrimitive
                ?.content == "fra"
        }

        vm.setRememberSubtitles(false)
        awaitServer { it.containsKey("RememberSubtitleSelections") && !it.on("RememberSubtitleSelections") }

        assertEquals("fra", server().getValue("AudioLanguagePreference").jsonPrimitive.content)
    }

    @Test
    fun `a flip still waiting out its delay is sent before the search is read again, not dropped by it`() {
        val vm = started()
        vm.ready()
        vm.setAutoplayNextEpisode(false)
        // Straight away, well inside the delay before a change is written: the state does not say a write is under way yet.
        vm.refresh()
        awaitServer { it.containsKey("EnableNextEpisodeAutoPlay") && !it.on("EnableNextEpisodeAutoPlay") }
        assertFalse(vm.ready().playback.autoplayNextEpisode)
    }

    @Test
    fun `a flip is not built on the old copy while the search reads again, so a page's change is not written back`() {
        val vm = started()
        vm.ready()
        // The Playback page, opened over the search, saved a new audio language; the search is shown again and reads.
        jellyfin.userConfiguration =
            jellyfin.userConfiguration.replace("\"AudioLanguagePreference\":\"eng\"", "\"AudioLanguagePreference\":\"fra\"")
        val held = jellyfin.hold("/Users/Me")
        vm.refresh()
        held.awaitArrival()
        assertTrue((vm.state.value as SearchSwitchesState.Ready).reading)

        // The copy is older than the server's: a flip now would write the old language back, so it is not taken.
        vm.setDisplayMissingEpisodes(true)
        held.release()
        vm.settled { (it as? SearchSwitchesState.Ready)?.reading == false }
        assertFalse(vm.ready().home.displayMissingEpisodes)
        assertEquals(
            "fra",
            vm
                .ready()
                .configuration
                .getValue("AudioLanguagePreference")
                .jsonPrimitive.content,
        )

        // Once the read is in the same flip is built on the new copy, and the page's change stays.
        vm.setDisplayMissingEpisodes(true)
        awaitServer { it.containsKey("DisplayMissingEpisodes") && it.on("DisplayMissingEpisodes") }
        assertEquals("fra", server().getValue("AudioLanguagePreference").jsonPrimitive.content)
    }

    @Test
    fun `a change kept unsent is put back on the new copy, so Retry does not write the old one over a page's change`() {
        val vm = started()
        vm.ready()
        jellyfin.dropped += "/Users/Configuration"
        vm.setDisplayCollectionsView(true)
        vm.settled { (it as? SearchSwitchesState.Ready)?.save?.failed == true }
        // The server is back, and a page changed the audio language meanwhile.
        jellyfin.dropped.clear()
        jellyfin.userConfiguration =
            jellyfin.userConfiguration.replace("\"AudioLanguagePreference\":\"eng\"", "\"AudioLanguagePreference\":\"fra\"")

        vm.refresh()
        vm.settled { (it as? SearchSwitchesState.Ready)?.reading == false }
        val ready = vm.ready()
        assertEquals(
            "fra",
            ready.configuration
                .getValue("AudioLanguagePreference")
                .jsonPrimitive.content,
        )
        assertTrue(ready.home.displayCollectionsView)

        vm.retrySave()
        awaitServer { it.containsKey("DisplayCollectionsView") && it.on("DisplayCollectionsView") }
        assertEquals("fra", server().getValue("AudioLanguagePreference").jsonPrimitive.content)
    }

    @Test
    fun `a flip still waiting out its delay is sent when a page is opened, so the page reads it`() {
        val vm = started()
        vm.ready()
        vm.setAutoplayNextEpisode(false)
        // A result is opened inside the send delay: the page asks what is saved before it reads.
        vm.leaving()
        runBlocking { connection.awaitSaves() }
        assertFalse(server().on("EnableNextEpisodeAutoPlay"))
    }

    @Test
    fun `several switches asking at once make one read`() {
        val vm = made(SearchSwitchesViewModel(connection))
        val held = jellyfin.hold("/Users/Me")
        vm.refresh()
        held.awaitArrival()
        vm.refresh()
        vm.refresh()
        held.release()
        vm.ready()
        assertEquals(1, jellyfin.hits("/Users/Me"))
    }

    @Test
    fun `reading again keeps what is shown, so the switches do not flash back to a spinner`() {
        val vm = started()
        vm.ready()
        vm.refresh()
        assertTrue(vm.state.value is SearchSwitchesState.Ready)
    }

    @Test
    fun `a refused write is put back and said`() {
        val vm = started()
        vm.ready()
        jellyfin.status["/Users/Configuration"] = 409
        vm.setHidePlayedInLatest(false)
        val refused = vm.settled { (it as? SearchSwitchesState.Ready)?.refused != null } as SearchSwitchesState.Ready
        assertEquals(ConsoleFailure.Refused, refused.refused)
        assertTrue(refused.home.hidePlayedInLatest)
    }

    @Test
    fun `a refused change is not sent again each time the search is read again`() {
        val vm = started()
        vm.ready()
        jellyfin.status["/Users/Configuration"] = 409
        vm.setHidePlayedInLatest(false)
        vm.settled { (it as? SearchSwitchesState.Ready)?.refused != null }
        val writes = jellyfin.hits("/Users/Configuration")

        // The server would take it now, but nobody asked again: reading must not.
        jellyfin.status.remove("/Users/Configuration")
        vm.refresh()
        vm.settled { (it as? SearchSwitchesState.Ready)?.reading == false }
        Thread.sleep(SEND_WAIT_MILLIS)

        assertTrue(vm.ready().home.hidePlayedInLatest)
        assertEquals(writes, jellyfin.hits("/Users/Configuration"))
    }

    @Test
    fun `Retry while the search is being read does not send the old copy over a page's change`() {
        val vm = started()
        vm.ready()
        jellyfin.dropped += "/Users/Configuration"
        vm.setDisplayCollectionsView(true)
        vm.settled { (it as? SearchSwitchesState.Ready)?.save?.failed == true }
        jellyfin.dropped.clear()
        jellyfin.userConfiguration =
            jellyfin.userConfiguration.replace("\"AudioLanguagePreference\":\"eng\"", "\"AudioLanguagePreference\":\"fra\"")

        val held = jellyfin.hold("/Users/Me")
        vm.refresh()
        held.awaitArrival()
        vm.retrySave()
        held.release()
        vm.settled { (it as? SearchSwitchesState.Ready)?.reading == false }
        awaitServer { it.containsKey("DisplayCollectionsView") && it.on("DisplayCollectionsView") }

        assertEquals("fra", server().getValue("AudioLanguagePreference").jsonPrimitive.content)
    }

    @Test
    fun `a write that cannot reach the server keeps the change, says so, and Retry sends it`() {
        val vm = started()
        vm.ready()
        jellyfin.dropped += "/Users/Configuration"
        vm.setDisplayCollectionsView(true)
        val failed = vm.settled { (it as? SearchSwitchesState.Ready)?.save?.failed == true } as SearchSwitchesState.Ready
        assertTrue(failed.home.displayCollectionsView)
        assertEquals(null, failed.refused)
        // Reading again does not put the old value over the change that is waiting to be sent.
        vm.refresh()
        assertTrue(vm.ready().home.displayCollectionsView)
        jellyfin.dropped.clear()
        vm.retrySave()
        awaitServer { it.containsKey("DisplayCollectionsView") && it.on("DisplayCollectionsView") }
    }

    private companion object {
        const val TIMEOUT_MILLIS = 10_000L
        const val POLL_MILLIS = 20L
        const val SEND_WAIT_MILLIS = 900L
    }
}
