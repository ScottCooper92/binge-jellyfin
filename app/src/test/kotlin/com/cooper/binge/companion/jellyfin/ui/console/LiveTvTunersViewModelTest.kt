package com.cooper.binge.companion.jellyfin.ui.console

import androidx.lifecycle.ViewModel
import com.cooper.binge.companion.jellyfin.FakeJellyfin
import com.cooper.binge.companion.jellyfin.server.Connection
import com.cooper.binge.companion.jellyfin.server.TUNER_TYPE_HDHOMERUN
import com.cooper.binge.companion.jellyfin.server.TUNER_TYPE_M3U
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
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class LiveTvTunersViewModelTest {
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

    private fun LiveTvViewModel.ready(check: (LiveTvUiState.Ready) -> Boolean = { true }) =
        runBlocking { withTimeout(TIMEOUT_MILLIS) { state.first { it is LiveTvUiState.Ready && check(it) } } } as LiveTvUiState.Ready

    private fun vm() = made(LiveTvViewModel(connection, withGuide = false))

    private fun tunerPosts() = jellyfin.requests.count { it.url.encodedPath == HOSTS && it.method == "POST" }

    @Test
    fun `an HDHomeRun is added by its address, and the list shows it once the server has it`() {
        val vm = vm()
        vm.ready()
        vm.form(TunerDraft(TUNER_TYPE_HDHOMERUN, url = " http://192.168.1.60 "))
        vm.save(TunerDraft(TUNER_TYPE_HDHOMERUN, url = " http://192.168.1.60 "))
        val ready = vm.ready { it.summary.tuners.size == 3 && it.sheet == null }
        assertEquals(
            "http://192.168.1.60",
            jellyfin
                .lastBody(HOSTS)
                .getValue("Url")
                .jsonPrimitive.content,
        )
        assertEquals(
            "hdhomerun",
            jellyfin
                .lastBody(HOSTS)
                .getValue("Type")
                .jsonPrimitive.content,
        )
        assertTrue(ready.summary.tuners.any { it.address == "http://192.168.1.60" })
        assertFalse(ready.saving)
    }

    @Test
    fun `an M3U playlist carries its name, user agent and stream limit, and a blank limit is none`() {
        val vm = vm()
        vm.ready()
        vm.save(TunerDraft(TUNER_TYPE_M3U, name = "Sports", url = "https://iptv.example/list.m3u", userAgent = "VLC", streamLimit = "3"))
        vm.ready { it.summary.tuners.size == 3 && !it.saving }
        val sent = jellyfin.lastBody(HOSTS)
        assertEquals("Sports", sent.getValue("FriendlyName").jsonPrimitive.content)
        assertEquals("VLC", sent.getValue("UserAgent").jsonPrimitive.content)
        assertEquals(3, sent.getValue("TunerCount").jsonPrimitive.int)
        vm.save(TunerDraft(TUNER_TYPE_M3U, url = "https://iptv.example/two.m3u"))
        vm.ready { it.summary.tuners.size == 4 && !it.saving }
        assertEquals(
            0,
            jellyfin
                .lastBody(HOSTS)
                .getValue("TunerCount")
                .jsonPrimitive.int,
        )
    }

    @Test
    fun `an edit sends the host back whole, so a key the form does not show returns as it came`() {
        jellyfin.liveTvConfiguration =
            """{"TunerHosts":[{"Id":"t1","Type":"m3u","FriendlyName":"IPTV",""" +
            """"Url":"https://iptv.example/x.m3u?pw=abc","FutureSetting":{"keep":"me"}}],""" +
            """"ListingProviders":[]}"""
        val vm = vm()
        vm.ready()
        vm.edit("t1")
        val draft = ((vm.ready().sheet) as TunerSheet.Form).draft
        assertEquals("https://iptv.example/x.m3u?pw=abc", draft.url)
        vm.save(draft.copy(name = "News"))
        vm.ready {
            it.summary.tuners
                .firstOrNull()
                ?.name == "News" &&
                it.sheet == null
        }
        val sent = jellyfin.lastBody(HOSTS)
        assertEquals("t1", sent.getValue("Id").jsonPrimitive.content)
        assertEquals(
            "me",
            sent
                .getValue("FutureSetting")
                .jsonObject
                .getValue("keep")
                .jsonPrimitive.content,
        )
    }

    @Test
    fun `a tuner the server cannot reach is not saved, keeps the form open, and says so`() {
        jellyfin.tunerSaveStatus = 500
        val vm = vm()
        vm.ready()
        vm.form(TunerDraft(TUNER_TYPE_HDHOMERUN))
        vm.save(TunerDraft(TUNER_TYPE_HDHOMERUN, url = "http://10.0.0.9"))
        val ready = vm.ready { it.problem != null }
        assertEquals(TunerProblem.NotSaved, ready.problem)
        assertTrue(ready.sheet is TunerSheet.Form)
        assertFalse(ready.saving)
        assertEquals(2, ready.summary.tuners.size)
        vm.dismissProblem()
        assertNull(vm.ready().problem)
    }

    @Test
    fun `an edit to an address the server cannot reach is not saved and says so`() {
        jellyfin.tunerSaveStatus = 500
        val vm = vm()
        vm.ready()
        vm.edit("t1")
        val draft = ((vm.ready().sheet) as TunerSheet.Form).draft
        vm.save(draft.copy(url = "https://iptv.example/gone.m3u"))
        val ready = vm.ready { it.problem != null }
        assertEquals(TunerProblem.NotSaved, ready.problem)
        assertTrue(ready.sheet is TunerSheet.Form)
    }

    @Test
    fun `a save while the server cannot be reached names the server, not the tuner`() {
        jellyfin.dropped += HOSTS
        val vm = vm()
        vm.ready()
        vm.form(TunerDraft(TUNER_TYPE_HDHOMERUN))
        vm.save(TunerDraft(TUNER_TYPE_HDHOMERUN, url = "http://10.0.0.9"))
        assertEquals(TunerProblem.ServerUnreachable, vm.ready { it.problem != null }.problem)
    }

    @Test
    fun `a save the server refuses outright is only not changed`() {
        jellyfin.tunerSaveStatus = 403
        val vm = vm()
        vm.ready()
        vm.edit("t1")
        vm.save(((vm.ready().sheet) as TunerSheet.Form).draft.copy(name = "News"))
        assertEquals(TunerProblem.NotChanged, vm.ready { it.problem != null }.problem)
    }

    @Test
    fun `a form that cannot be submitted sends nothing`() {
        val vm = vm()
        vm.ready()
        vm.save(TunerDraft(TUNER_TYPE_HDHOMERUN, url = "  "))
        vm.save(TunerDraft(TUNER_TYPE_M3U, url = "https://x/y.m3u", streamLimit = "100"))
        vm.save(TunerDraft(TUNER_TYPE_M3U, url = "https://x/y.m3u", streamLimit = "two"))
        assertEquals(0, tunerPosts())
    }

    @Test
    fun `removing asks first, and sends nothing until it is confirmed`() {
        val vm = vm()
        vm.ready()
        vm.askRemove("t1")
        assertEquals("IPTV", vm.ready().removing?.name)
        vm.cancelRemove()
        assertNull(vm.ready().removing)
        assertEquals(0, jellyfin.requests.count { it.method == "DELETE" })
        vm.askRemove("t1")
        vm.remove()
        val ready = vm.ready { it.summary.tuners.size == 1 && !it.saving }
        assertEquals(listOf("t2"), ready.summary.tuners.map { it.id })
        assertEquals(
            "t1",
            jellyfin.requests
                .last { it.method == "DELETE" }
                .url
                .queryParameter("id"),
        )
    }

    @Test
    fun `a form remembers the sheet it was opened from, and a tuner opened from the page's row has none`() {
        val vm = vm()
        vm.ready()
        vm.sheet(TunerSheet.Choose)
        vm.form(TunerDraft(TUNER_TYPE_HDHOMERUN))
        assertEquals(TunerSheet.Choose, (vm.ready().sheet as TunerSheet.Form).from)
        val found = TunerSheet.Found(listOf(TunerDraft(TUNER_TYPE_HDHOMERUN, url = "http://192.168.1.61")))
        vm.sheet(found)
        vm.form(found.devices.first())
        assertEquals(found, (vm.ready().sheet as TunerSheet.Form).from)
        vm.sheet(null)
        vm.form(TunerDraft(TUNER_TYPE_M3U))
        assertEquals(null, (vm.ready().sheet as TunerSheet.Form).from)
    }

    @Test
    fun `finding tuners lists what the server found, and one opens as a form with its address`() {
        val vm = vm()
        vm.ready()
        vm.discover()
        val found = vm.ready { it.sheet is TunerSheet.Found }.sheet as TunerSheet.Found
        assertEquals(listOf("http://192.168.1.77"), found.devices.map { it.url })
        vm.save(found.devices.first())
        vm.ready { it.summary.tuners.size == 3 }
        assertEquals(
            "1070A1B2",
            jellyfin
                .lastBody(HOSTS)
                .getValue("DeviceId")
                .jsonPrimitive.content,
        )
    }

    @Test
    fun `finding nothing says so, and a search failure stays in the discovery sheet`() {
        jellyfin.discoveredTuners = "[]"
        val vm = vm()
        vm.ready()
        vm.discover()
        assertEquals(emptyList<TunerDraft>(), (vm.ready { it.sheet is TunerSheet.Found }.sheet as TunerSheet.Found).devices)
        jellyfin.discoveredTuners = "not json"
        vm.discover()
        val ready = vm.ready { it.sheet is TunerSheet.Failed }
        assertEquals(ConsoleFailure.Unreachable, (ready.sheet as TunerSheet.Failed).failure)
        assertNull(ready.problem)
    }

    private fun awaitDiscoveryAnswered() {
        runBlocking {
            withTimeout(TIMEOUT_MILLIS) {
                while (jellyfin.requests.none { it.url.encodedPath == "/LiveTv/Tuners/Discover" }) delay(10)
            }
            delay(DISCOVERY_SETTLE_MILLIS)
        }
    }

    @Test
    fun `a discovery that answers after the sheet changed leaves the sheet alone`() {
        val vm = vm()
        vm.ready()
        vm.discover()
        vm.edit("t1")
        awaitDiscoveryAnswered()
        assertTrue(vm.ready().sheet is TunerSheet.Form)

        jellyfin.requests.clear()
        jellyfin.discoveredTuners = "not json"
        vm.discover()
        vm.sheet(null)
        awaitDiscoveryAnswered()
        val ready = vm.ready()
        assertNull(ready.sheet)
        assertNull(ready.problem)
    }

    @Test
    fun `a draft keeps its address out of its text, which an M3U playlist's may carry an account in`() {
        val draft = TunerDraft(TUNER_TYPE_M3U, url = "https://u:secret@iptv.example/get.php?password=hunter2")
        assertFalse(draft.toString().contains("secret"))
        assertFalse(draft.toString().contains("hunter2"))
        assertNotNull(draft.applyTo(null))
    }

    private companion object {
        const val HOSTS = "/LiveTv/TunerHosts"
        const val TIMEOUT_MILLIS = 10_000L
        const val DISCOVERY_SETTLE_MILLIS = 300L
    }
}
