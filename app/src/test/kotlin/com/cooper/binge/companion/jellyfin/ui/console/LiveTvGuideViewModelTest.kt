package com.cooper.binge.companion.jellyfin.ui.console

import androidx.lifecycle.ViewModel
import com.cooper.binge.companion.jellyfin.FakeJellyfin
import com.cooper.binge.companion.jellyfin.server.Connection
import com.cooper.binge.companion.jellyfin.server.GuideProviderSummary
import com.cooper.binge.companion.jellyfin.session
import com.cooper.binge.companion.jellyfin.testSessionStore
import com.cooper.binge.companion.jellyfin.ui.ViewModelTeardown
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class LiveTvGuideViewModelTest {
    private val jellyfin = FakeJellyfin()
    private val sessions = testSessionStore()
    private val connection = Connection(OkHttpClient(), sessions, "Jellyfin Companion", "Test", "1.0", Dispatchers.IO)
    private val teardown = ViewModelTeardown()
    private var changed = 0

    private fun <T : ViewModel> made(viewModel: T): T = teardown.made(viewModel)

    @Before
    fun main() {
        Dispatchers.setMain(Dispatchers.Unconfined)
        runBlocking { sessions.save(jellyfin.session()) }
    }

    @After
    fun close() = teardown.close(jellyfin)

    private fun vm() = made(LiveTvGuideViewModel(connection) { changed++ })

    private fun LiveTvGuideViewModel.until(check: (GuideUiState) -> Boolean) =
        runBlocking {
            withTimeout(TIMEOUT_MILLIS) { state.first(check) }
        }

    private val xmltv = GuideProviderSummary("g1", "xmltv", "/config/guide.xml")
    private val schedulesDirect = GuideProviderSummary("g2", "SchedulesDirect", "alice")

    private fun providers() =
        jellyfin.liveTvConfiguration.let {
            kotlinx.serialization.json.Json
                .parseToJsonElement(it)
                .jsonObject
                .getValue("ListingProviders")
                .jsonArray
        }

    @Test
    fun `an XMLTV guide is added by its address, the server's answer closes the form, and the page is told to read again`() {
        val vm = vm()
        vm.add()
        vm.save(GuideForm(path = " https://guide.example/xmltv.xml "))
        val done = vm.until { it.queued }
        assertNull(done.form)
        assertFalse(done.saving)
        assertEquals(1, changed)
        val sent = jellyfin.lastBody(PROVIDERS)
        assertEquals("xmltv", sent.getValue("Type").jsonPrimitive.content)
        assertEquals("https://guide.example/xmltv.xml", sent.getValue("Path").jsonPrimitive.content)
        assertEquals(3, providers().size)
    }

    @Test
    fun `editing an XMLTV guide opens its path as the server holds it, and a save sends the provider whole`() {
        val vm = vm()
        vm.edit(xmltv)
        val form = vm.until { it.form != null }.form
        assertEquals("/config/guide.xml", form?.path)
        vm.save(GuideForm("g1", "/config/other.xml"))
        vm.until { it.queued }
        val sent = jellyfin.lastBody(PROVIDERS)
        assertEquals("g1", sent.getValue("Id").jsonPrimitive.content)
        assertEquals("/config/other.xml", sent.getValue("Path").jsonPrimitive.content)
    }

    @Test
    fun `a Schedules Direct account opens without a field, and nothing of it is sent`() {
        val vm = vm()
        vm.edit(schedulesDirect)
        val form = vm.until { it.form != null }.form
        assertFalse(form?.isXmlTv ?: true)
        vm.save(form ?: return)
        assertEquals(0, jellyfin.requests.count { it.url.encodedPath == PROVIDERS && it.method == "POST" })
    }

    @Test
    fun `a guide the server cannot read is not saved, keeps the form, and says so`() {
        jellyfin.listingSaveStatus = 500
        val vm = vm()
        vm.add()
        vm.save(GuideForm(path = "/nowhere.xml"))
        val failed = vm.until { it.problem != null }
        assertEquals(GuideProblem.NotSaved, failed.problem)
        assertTrue(failed.form != null)
        assertFalse(failed.saving)
        assertEquals(0, changed)
        vm.dismissProblem()
        assertNull(vm.until { it.problem == null }.problem)
    }

    @Test
    fun `a save the server refuses the user for is not blamed on the address, and keeps the form`() {
        jellyfin.listingSaveStatus = 401
        val vm = vm()
        vm.add()
        vm.save(GuideForm(path = "/guide.xml"))
        val failed = vm.until { it.failed != null }
        assertNull(failed.problem)
        assertEquals(ConsoleFailure.Refused, failed.failed)
        assertTrue(failed.form != null)
        assertFalse(failed.saving)
        vm.dismissProblem()
        assertNull(vm.until { it.failed == null }.failed)
    }

    @Test
    fun `a server that cannot be reached is said to be unreachable, not unreadable`() {
        val vm = vm()
        jellyfin.close()
        vm.add()
        vm.save(GuideForm(path = "/guide.xml"))
        val failed = vm.until { it.failed != null }
        assertNull(failed.problem)
        assertEquals(ConsoleFailure.Unreachable, failed.failed)
    }

    /**
     * The page is told before the change reads as done, so a test (or a screen) that sees `queued` knows the callback has run. The
     * callback is held on a latch here, which makes the order observable without a sleep: it had run after `queued` was published
     * before, and a reader on another thread could see `queued` and a count still at 0 (#299).
     */
    @Test
    fun `a change reads as done only after the page has been told`() {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val vm =
            made(
                LiveTvGuideViewModel(connection, {
                    entered.countDown()
                    release.await()
                    changed++
                }),
            )
        vm.add()
        vm.save(GuideForm(path = "https://guide.example/xmltv.xml"))
        assertTrue(entered.await(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS))
        assertFalse(vm.state.value.queued)
        release.countDown()
        vm.until { it.queued }
        assertEquals(1, changed)
    }

    @Test
    fun `an empty path sends nothing`() {
        val vm = vm()
        vm.add()
        vm.save(GuideForm(path = "  "))
        assertEquals(0, jellyfin.requests.count { it.url.encodedPath == PROVIDERS && it.method == "POST" })
    }

    @Test
    fun `removing asks first, and sends nothing until it is confirmed`() {
        val vm = vm()
        vm.askRemove(GuideForm(id = "g1", name = "/config/guide.xml"))
        assertEquals(GuideRemoval("g1", "/config/guide.xml"), vm.until { it.removing != null }.removing)
        vm.cancelRemove()
        assertNull(vm.until { it.removing == null }.removing)
        assertEquals(0, jellyfin.requests.count { it.method == "DELETE" })
        vm.askRemove(GuideForm(id = "g1", name = "/config/guide.xml"))
        vm.remove()
        val done = vm.until { it.queued }
        assertNull(done.form)
        assertEquals(
            "g1",
            jellyfin.requests
                .last { it.method == "DELETE" }
                .url
                .queryParameter("id"),
        )
        assertEquals(
            listOf("g2"),
            providers().map {
                it.jsonObject
                    .getValue("Id")
                    .jsonPrimitive.content
            },
        )
        assertEquals(1, changed)
    }

    @Test
    fun `a form keeps its path out of its text, which an address may carry an account in`() {
        assertFalse(GuideForm(path = "https://u:secret@guide.example/x.xml?key=hunter2").toString().contains("secret"))
    }

    private companion object {
        const val PROVIDERS = "/LiveTv/ListingProviders"
        const val TIMEOUT_MILLIS = 10_000L
    }
}
