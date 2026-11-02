package com.cooper.binge.companion.jellyfin.ui.console

import androidx.lifecycle.ViewModel
import com.binge.designsystem.SaveStatus
import com.cooper.binge.companion.jellyfin.FakeJellyfin
import com.cooper.binge.companion.jellyfin.server.CastReceiver
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
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
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

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class AdvancedViewModelTest {
    private val jellyfin = FakeJellyfin()
    private val sessions = testSessionStore()
    private val connection = Connection(OkHttpClient(), sessions, "Jellyfin Companion", "Test", "1.0", Dispatchers.IO)
    private val teardown = ViewModelTeardown()

    private fun <T : ViewModel> made(viewModel: T): T = teardown.made(viewModel)

    @Before
    fun main() {
        Dispatchers.setMain(Dispatchers.Unconfined)
        runBlocking { sessions.save(jellyfin.session()) }
        jellyfin.serverConfiguration = FULL
    }

    @After
    fun close() = teardown.close(jellyfin)

    private fun AdvancedViewModel.settled(check: (AdvancedUiState) -> Boolean = { it !is AdvancedUiState.Loading }) =
        runBlocking { withTimeout(TIMEOUT_MILLIS) { state.first(check) } }

    private fun AdvancedViewModel.ready(check: (AdvancedUiState.Ready) -> Boolean = { true }) =
        settled { it is AdvancedUiState.Ready && check(it) } as AdvancedUiState.Ready

    /** The last body [written] waited for: what a test reads its other keys from. */
    private var lastWritten = JsonObject(emptyMap())

    /**
     * Waits for a write that holds [key] as [value], not for a count of writes: the count can be reached by a repeat of an
     * earlier write (see [FakeJellyfin.awaitBody]), whose body lacks the change the test is waiting for.
     */
    private fun written(
        key: String,
        value: JsonElement,
    ) = written(key) { it == value }

    private fun written(
        key: String,
        check: (JsonElement?) -> Boolean,
    ) {
        lastWritten = jellyfin.awaitBody(CONFIGURATION) { check(it[key]) }
    }

    private fun sent(): JsonObject = lastWritten

    @Test
    fun `reads every option the server sent`() {
        val settings = made(AdvancedViewModel(connection)).ready().settings
        assertEquals(30, settings.activityLogRetentionDays)
        assertEquals(60, settings.libraryMonitorDelay)
        assertEquals(60, settings.libraryUpdateDuration)
        assertEquals(0, settings.libraryScanConcurrency)
        assertEquals(0, settings.parallelImageEncodingLimit)
        assertEquals(0, settings.imageExtractionTimeoutMs)
        assertEquals(500L, settings.slowResponseThresholdMs)
        assertEquals(listOf("*"), settings.corsHosts)
        assertEquals(listOf(CastReceiver("CC1AD845", "Default Media Receiver")), settings.castReceivers)
        assertEquals(1000, settings.cacheSize)
        assertTrue(settings.has("EnableLegacyAuthorization"))
    }

    @Test
    fun `an older server that lacks options has no row for them and no error`() {
        jellyfin.serverConfiguration = """{"ServerName":"x","LibraryMonitorDelay":60,"EnableMetrics":false}"""
        val settings = made(AdvancedViewModel(connection)).ready().settings
        assertTrue(settings.has("LibraryMonitorDelay"))
        assertFalse(settings.has("CacheSize"))
        assertFalse(settings.has("CastReceiverApplications"))
        assertNull(settings.activityLogRetentionDays)
    }

    @Test
    fun `each switch writes its own key as a boolean and nothing else changes`() {
        val vm = made(AdvancedViewModel(connection))
        vm.ready()
        vm.setSlowResponseWarning(true)
        written("EnableSlowResponseWarning", JsonPrimitive(true))
        assertEquals(true, sent().getValue("EnableSlowResponseWarning").jsonPrimitive.boolean)
        vm.setMetrics(true)
        written("EnableMetrics", JsonPrimitive(true))
        assertEquals(true, sent().getValue("EnableMetrics").jsonPrimitive.boolean)
        vm.setAllowClientLogUpload(true)
        written("AllowClientLogUpload", JsonPrimitive(true))
        assertEquals(true, sent().getValue("AllowClientLogUpload").jsonPrimitive.boolean)
        vm.setLegacyAuthorization(false)
        written("EnableLegacyAuthorization", JsonPrimitive(false))
        assertEquals(false, sent().getValue("EnableLegacyAuthorization").jsonPrimitive.boolean)
        assertEquals(true, sent().getValue("EnableSlowResponseWarning").jsonPrimitive.boolean)
        assertEquals("truenas", sent().getValue("ServerName").jsonPrimitive.content)
    }

    @Test
    fun `a number writes an integer under its key, and the slow threshold a number the server reads as a long`() {
        val vm = made(AdvancedViewModel(connection))
        vm.ready()
        vm.save(AdvancedNumber.RetentionDays, " 14 ")
        written("ActivityLogRetentionDays", JsonPrimitive(14))
        assertEquals(14, sent().getValue("ActivityLogRetentionDays").jsonPrimitive.int)
        vm.save(AdvancedNumber.SlowThreshold, "2500")
        written("SlowResponseThresholdMs", JsonPrimitive(2500))
        assertEquals(2500L, sent().getValue("SlowResponseThresholdMs").jsonPrimitive.long)
        vm.save(AdvancedNumber.CacheSize, "5000")
        written("CacheSize", JsonPrimitive(5000))
        assertEquals(5000, sent().getValue("CacheSize").jsonPrimitive.int)
        assertEquals(14, vm.ready().settings.activityLogRetentionDays)
    }

    @Test
    fun `retention has a floor of one day because the server's cleanup task cannot run without one`() {
        assertNull(AdvancedNumber.RetentionDays.parse("0"))
        assertNull(AdvancedNumber.RetentionDays.parse("-5"))
        assertEquals(1, AdvancedNumber.RetentionDays.parse("1"))
        assertEquals(3650, AdvancedNumber.RetentionDays.parse("3650"))
        assertNull(AdvancedNumber.RetentionDays.parse("3651"))
    }

    @Test
    fun `retention the server left null can be set`() {
        jellyfin.serverConfiguration = """{"ServerName":"x","ActivityLogRetentionDays":null}"""
        val vm = made(AdvancedViewModel(connection))
        val settings = vm.ready().settings
        assertTrue(settings.has("ActivityLogRetentionDays"))
        assertNull(settings.activityLogRetentionDays)
        vm.save(AdvancedNumber.RetentionDays, "30")
        written("ActivityLogRetentionDays", JsonPrimitive(30))
        assertEquals(30, sent().getValue("ActivityLogRetentionDays").jsonPrimitive.int)
    }

    @Test
    fun `zero means the server's own choice where it does, and is refused where it would delete or spin`() {
        assertEquals(0, AdvancedNumber.ScanConcurrency.parse("0"))
        assertEquals(0, AdvancedNumber.ImageEncodingLimit.parse("0"))
        assertEquals(0, AdvancedNumber.ExtractionTimeout.parse("0"))
        assertEquals(0, AdvancedNumber.InactiveThreshold.parse("0"))
        assertNull(AdvancedNumber.MonitorDelay.parse("0"))
        assertNull(AdvancedNumber.UpdateDuration.parse("0"))
        assertNull(AdvancedNumber.SlowThreshold.parse("0"))
        assertNull(AdvancedNumber.CacheSize.parse("0"))
        assertNull(AdvancedNumber.ScanConcurrency.parse("65"))
        assertNull(AdvancedNumber.ExtractionTimeout.parse("600001"))
    }

    @Test
    fun `a number out of range keeps its sheet open and saves nothing`() {
        val vm = made(AdvancedViewModel(connection))
        vm.ready()
        vm.edit(AdvancedNumber.MonitorDelay)
        for (bad in listOf("0", "3601", "abc", "", "9.5")) vm.save(AdvancedNumber.MonitorDelay, bad)
        assertEquals(AdvancedNumber.MonitorDelay, vm.ready().editing)
        assertEquals(0, jellyfin.requests.count { it.method == "POST" && it.url.encodedPath == CONFIGURATION })
    }

    @Test
    fun `hosts are read one per line or by commas without repeats, and saved as strings`() {
        assertEquals(listOf("a.example", "b.example"), parseHosts("a.example\n b.example ,a.example"))
        assertEquals(emptyList<String>(), parseHosts(" \n,"))
        val vm = made(AdvancedViewModel(connection))
        vm.ready()
        vm.saveList(AdvancedList.CorsHosts, "https://a.example\nhttps://b.example")
        written("CorsHosts") { (it as? JsonArray)?.size == 2 }
        assertEquals(
            listOf("https://a.example", "https://b.example"),
            sent().getValue("CorsHosts").jsonArray.map { it.jsonPrimitive.content },
        )
    }

    @Test
    fun `cast receivers need an id and a name on every line, and are saved with both`() {
        assertEquals(listOf(CastReceiver("A1", "One"), CastReceiver("B2", "Two, too")), parseCastReceivers("A1, One\n\nB2, Two, too"))
        assertNull(parseCastReceivers("A1"))
        assertNull(parseCastReceivers("A1,"))
        assertNull(parseCastReceivers(", Name"))
        assertEquals(emptyList<CastReceiver>(), parseCastReceivers(""))
        val vm = made(AdvancedViewModel(connection))
        vm.ready()
        vm.editList(AdvancedList.CastReceivers)
        vm.saveList(AdvancedList.CastReceivers, "A1")
        assertEquals(AdvancedList.CastReceivers, vm.ready().editingList)
        assertEquals(0, jellyfin.requests.count { it.method == "POST" && it.url.encodedPath == CONFIGURATION })
        vm.saveList(AdvancedList.CastReceivers, "A1, One")
        written("CastReceiverApplications") { (it as? JsonArray)?.size == 1 }
        val receiver =
            sent()
                .getValue("CastReceiverApplications")
                .jsonArray
                .single()
                .jsonObject
        assertEquals("A1", receiver.getValue("Id").jsonPrimitive.content)
        assertEquals("One", receiver.getValue("Name").jsonPrimitive.content)
        assertNull(vm.ready().editingList)
    }

    @Test
    fun `an option this app doesn't know is sent back as it was`() {
        jellyfin.serverConfiguration = """{"ServerName":"x","LibraryMonitorDelay":60,"FutureOption":{"a":[1,2]},"NullOne":null}"""
        val vm = made(AdvancedViewModel(connection))
        vm.ready()
        vm.save(AdvancedNumber.MonitorDelay, "90")
        written("LibraryMonitorDelay", JsonPrimitive(90))
        assertEquals(90, sent().getValue("LibraryMonitorDelay").jsonPrimitive.int)
        assertEquals(
            2,
            sent()
                .getValue("FutureOption")
                .jsonObject
                .getValue("a")
                .jsonArray.size,
        )
        assertEquals(JsonNull, sent().getValue("NullOne"))
        assertEquals(JsonPrimitive("x"), sent().getValue("ServerName"))
    }

    @Test
    fun `a save that can't reach the server keeps the change, says so, and Retry sends it`() {
        val vm = made(AdvancedViewModel(connection))
        vm.ready()
        jellyfin.dropped += CONFIGURATION
        vm.setMetrics(true)
        val failed = vm.ready { it.save.failed }
        assertTrue(failed.settings.metrics)
        assertNull(failed.refused)
        jellyfin.dropped.clear()
        vm.retrySave()
        val saved = vm.ready { it.save.status == SaveStatus.Saved }
        assertFalse(saved.save.failed)
        assertTrue(saved.settings.metrics)
    }

    @Test
    fun `a save the server refuses puts the value back and says so`() {
        val vm = made(AdvancedViewModel(connection))
        vm.ready()
        jellyfin.status[CONFIGURATION] = 500
        vm.setMetrics(true)
        val failed = vm.ready { it.refused != null }
        assertFalse(failed.settings.metrics)
        assertEquals(ConsoleFailure.Refused, failed.refused)
    }

    @Test
    fun `a page that can't read says why, and Try again reads it`() {
        jellyfin.status[CONFIGURATION] = 403
        val vm = made(AdvancedViewModel(connection))
        assertEquals(AdvancedUiState.Failed(ConsoleFailure.Refused), vm.settled())
        jellyfin.status.remove(CONFIGURATION)
        vm.load()
        assertEquals(30, vm.ready().settings.activityLogRetentionDays)
    }

    private companion object {
        const val CONFIGURATION = "/System/Configuration"
        const val TIMEOUT_MILLIS = 10_000L
        const val POLL_MILLIS = 20L
        const val FULL =
            """{"ServerName":"truenas","ActivityLogRetentionDays":30,"LibraryMonitorDelay":60,"LibraryUpdateDuration":60,""" +
                """"LibraryScanFanoutConcurrency":0,"ParallelImageEncodingLimit":0,"ImageExtractionTimeoutMs":0,""" +
                """"EnableSlowResponseWarning":true,"SlowResponseThresholdMs":500,"EnableMetrics":false,""" +
                """"AllowClientLogUpload":false,"CorsHosts":["*"],"InactiveSessionThreshold":0,""" +
                """"CastReceiverApplications":[{"Id":"CC1AD845","Name":"Default Media Receiver"}],""" +
                """"CacheSize":1000,"EnableLegacyAuthorization":true}"""
    }
}
