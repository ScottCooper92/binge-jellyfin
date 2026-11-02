package com.cooper.binge.companion.jellyfin.ui.console

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cooper.binge.companion.jellyfin.FakeJellyfin
import com.cooper.binge.companion.jellyfin.server.Connection
import com.cooper.binge.companion.jellyfin.server.JellyfinServerProfile
import com.cooper.binge.companion.jellyfin.server.NetworkImpact
import com.cooper.binge.companion.jellyfin.session
import com.cooper.binge.companion.jellyfin.testSessionStore
import com.cooper.binge.companion.jellyfin.ui.ViewModelTeardown
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
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
import java.net.URI

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class NetworkViewModelTest {
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

    private fun vm(version: String = "10.10.7") = made(NetworkViewModel(connection, JellyfinServerProfile.from(version)))

    private fun NetworkViewModel.ready(check: (NetworkUiState.Ready) -> Boolean = { true }) =
        runBlocking { withTimeout(TIMEOUT_MILLIS) { state.first { it is NetworkUiState.Ready && check(it) } } } as NetworkUiState.Ready

    private fun posts(path: String) = jellyfin.requests.count { it.url.encodedPath == path && it.method == "POST" }

    private fun waitFor(
        path: String,
        count: Int,
    ) = runBlocking {
        withTimeout(TIMEOUT_MILLIS) { while (posts(path) < count) delay(POLL_MILLIS) }
    }

    private fun sent(): JsonObject = jellyfin.lastBody(NETWORK)

    /** The port the fake answers on, which is the address this app is signed in to. */
    private val appPort = URI(jellyfin.baseUrl).port

    @Test
    fun `reads the options, and keeps the certificate password out of them`() {
        val ready = vm().ready()
        assertEquals(8096, ready.settings.httpPort)
        assertEquals(listOf("veth"), ready.settings.virtualNames)
        assertTrue(ready.draft.isEmpty())
        assertFalse(ready.settings.toString().contains("s3cret"))
    }

    @Test
    fun `a change to the form waits for Save changes and writes nothing before it`() {
        val vm = vm()
        vm.ready()
        vm.saveNumber(NetworkNumber.PublicHttpPort, "8000")
        vm.setSwitch("EnableRemoteAccess", false, form = true)
        val ready = vm.ready { it.draft.size == 2 }
        assertEquals(8000, ready.settings.publicHttpPort)
        assertEquals(0, posts(NETWORK))
        // Putting a value back to what the server has drops it from the changes.
        vm.saveNumber(NetworkNumber.PublicHttpPort, "8096")
        assertEquals(setOf("EnableRemoteAccess"), vm.ready { it.draft.size == 1 }.draft)
    }

    @Test
    fun `Save writes the whole configuration, the certificate password and an unknown option back as they were`() {
        val vm = vm()
        vm.ready()
        vm.saveNumber(NetworkNumber.PublicHttpPort, "8000")
        vm.save()
        waitFor(NETWORK, 1)
        assertEquals(8000, sent().getValue("PublicHttpPort").jsonPrimitive.int)
        assertEquals("s3cret", sent().getValue("CertificatePassword").jsonPrimitive.content)
        assertEquals(
            "me",
            sent()
                .getValue("FutureOption")
                .jsonObject
                .getValue("keep")
                .jsonPrimitive.content,
        )
        val done = vm.ready { it.restartNeeded && !it.committing }
        assertTrue(done.draft.isEmpty())
    }

    @Test
    fun `a configuration that leaves the certificate password out is never given a blank one`() {
        jellyfin.networkConfiguration = """{"InternalHttpPort":8096,"PublicHttpPort":8096,"EnableHttps":false}"""
        val vm = vm()
        vm.ready()
        vm.saveNumber(NetworkNumber.PublicHttpPort, "8000")
        vm.save()
        waitFor(NETWORK, 1)
        assertFalse("CertificatePassword" in sent())
        assertFalse("CertificatePath" in sent())
    }

    @Test
    fun `an empty certificate password the server sent goes back empty, and a real one is never changed by editing the file`() {
        jellyfin.networkConfiguration =
            """{"CertificatePath":"/old.pfx","CertificatePassword":"","EnableHttps":true,"InternalHttpPort":8096}"""
        val vm = vm()
        vm.ready()
        vm.saveText(NetworkText.CertificatePath, " /new.pfx ")
        vm.save()
        // HTTPS is on, so a new certificate file asks first.
        assertEquals(setOf(NetworkImpact.Certificate), vm.ready { it.confirming != null }.confirming)
        vm.confirm()
        waitFor(NETWORK, 1)
        assertEquals("/new.pfx", sent().getValue("CertificatePath").jsonPrimitive.content)
        assertEquals("", sent().getValue("CertificatePassword").jsonPrimitive.content)
    }

    @Test
    fun `a change to the port this app uses asks first, and Cancel writes nothing`() {
        jellyfin.networkConfiguration = jellyfin.networkConfiguration.replace("\"InternalHttpPort\":8096", "\"InternalHttpPort\":$appPort")
        val vm = vm()
        vm.ready()
        vm.saveNumber(NetworkNumber.HttpPort, "9000")
        vm.save()
        val asked = vm.ready { it.confirming != null }
        assertEquals(setOf(NetworkImpact.Port), asked.confirming)
        assertEquals(0, posts(NETWORK))
        vm.dismissConfirm()
        val back = vm.ready { it.confirming == null }
        assertEquals(setOf(NetworkImpact.Port.let { "InternalHttpPort" }), back.draft)
        assertEquals(0, posts(NETWORK))
    }

    @Test
    fun `confirming writes the change, and the page says a restart is needed without restarting anything`() {
        jellyfin.networkConfiguration = jellyfin.networkConfiguration.replace("\"InternalHttpPort\":8096", "\"InternalHttpPort\":$appPort")
        val vm = vm()
        vm.ready()
        vm.saveNumber(NetworkNumber.HttpPort, "9000")
        vm.save()
        vm.ready { it.confirming != null }
        vm.confirm()
        waitFor(NETWORK, 1)
        assertEquals(9000, sent().getValue("InternalHttpPort").jsonPrimitive.int)
        val done = vm.ready { it.restartNeeded && !it.committing }
        assertNull(done.confirming)
        assertEquals(0, jellyfin.requests.count { it.url.encodedPath.contains("Restart") || it.url.encodedPath.contains("Shutdown") })
    }

    @Test
    fun `a listen list without this app's address is a severe change to confirm`() {
        val vm = vm()
        vm.ready()
        vm.saveItems(NetworkList.BindAddresses, "10.9.9.9")
        vm.save()
        val asked = vm.ready { it.confirming != null }
        assertEquals(setOf(NetworkImpact.BindAddress), asked.confirming)
        assertTrue(asked.confirming!!.all { it.severe })
    }

    @Test
    fun `a save the server refuses keeps the changes and says so`() {
        val vm = vm()
        vm.ready()
        jellyfin.status[NETWORK] = 500
        vm.saveNumber(NetworkNumber.PublicHttpPort, "8000")
        vm.save()
        val failed = vm.ready { it.formFailed != null }
        assertEquals(ConsoleFailure.Refused, failed.formFailed)
        assertEquals(setOf("PublicHttpPort"), failed.draft)
        assertFalse(failed.committing)
        assertFalse(failed.restartNeeded)
        assertEquals(8000, failed.settings.publicHttpPort)
    }

    @Test
    fun `a save that can't reach the server keeps the changes, and Save again sends them`() {
        val vm = vm()
        vm.ready()
        jellyfin.dropped += NETWORK
        vm.saveNumber(NetworkNumber.PublicHttpPort, "8000")
        vm.save()
        assertEquals(ConsoleFailure.Unreachable, vm.ready { it.formFailed != null }.formFailed)
        jellyfin.dropped.clear()
        vm.save()
        waitFor(NETWORK, 1)
        assertTrue(vm.ready { it.restartNeeded && !it.committing }.draft.isEmpty())
    }

    @Test
    fun `10_8 names its ports and IPv6 its own way, and keeps the base URL in this document`() {
        jellyfin.networkConfiguration =
            """{"HttpServerPortNumber":8096,"HttpsPortNumber":8920,"PublicPort":8096,"PublicHttpsPort":8920,"EnableIPV6":false,"BaseUrl":"","EnableHttps":false}"""
        val vm = vm("10.8.13")
        vm.ready()
        vm.saveNumber(NetworkNumber.PublicHttpPort, "8000")
        vm.setSwitch("EnableIPV6", true, form = true)
        vm.saveText(NetworkText.BaseUrl, "jf/")
        vm.save()
        // A base URL change reaches this app's address, so it asks first.
        assertEquals(setOf(NetworkImpact.BaseUrl), vm.ready { it.confirming != null }.confirming)
        vm.confirm()
        waitFor(NETWORK, 1)
        assertEquals(8000, sent().getValue("PublicPort").jsonPrimitive.int)
        assertEquals(true, sent().getValue("EnableIPV6").jsonPrimitive.boolean)
        assertEquals("/jf", sent().getValue("BaseUrl").jsonPrimitive.content)
        assertEquals(0, posts("/System/Configuration"))
    }

    @Test
    fun `from 10_9 the base URL is still written into the network configuration`() {
        val vm = vm("10.10.7")
        vm.ready()
        vm.saveText(NetworkText.BaseUrl, "/jf")
        // The app's address has no base URL, so changing it is flagged.
        vm.save()
        assertEquals(setOf(NetworkImpact.BaseUrl), vm.ready { it.confirming != null }.confirming)
        vm.confirm()
        waitFor(NETWORK, 1)
        assertEquals("/jf", sent().getValue("BaseUrl").jsonPrimitive.content)
        assertEquals(0, posts("/System/Configuration"))
        assertTrue(vm.ready { it.restartNeeded && !it.committing }.draft.isEmpty())
    }

    @Test
    fun `Save changes sends a save-as-made change still waiting first, and keeps both`() {
        val vm = vm()
        vm.ready()
        vm.setSwitch("AutoDiscovery", false, form = false)
        vm.saveNumber(NetworkNumber.PublicHttpPort, "8000")
        vm.save()
        vm.ready { it.restartNeeded && !it.committing }
        assertEquals(false, sent().getValue("AutoDiscovery").jsonPrimitive.boolean)
        assertEquals(8000, sent().getValue("PublicHttpPort").jsonPrimitive.int)
        assertEquals(false, vm.ready().settings.autoDiscovery)
    }

    @Test
    fun `the lists that can't lock anyone out save as they're made, whole configuration and all`() {
        val vm = vm()
        vm.ready()
        vm.saveItems(NetworkList.KnownProxies, "10.0.0.1\n10.0.0.2")
        waitFor(NETWORK, 1)
        assertEquals(listOf("10.0.0.1", "10.0.0.2"), sent().getValue("KnownProxies").jsonArray.map { it.jsonPrimitive.content })
        assertEquals("s3cret", sent().getValue("CertificatePassword").jsonPrimitive.content)
        vm.setSwitch("AutoDiscovery", false, form = false)
        waitFor(NETWORK, 2)
        assertEquals(false, sent().getValue("AutoDiscovery").jsonPrimitive.boolean)
        assertTrue(vm.ready().draft.isEmpty())
    }

    @Test
    fun `a new certificate password is kept as typed, held for Save, and never read back into the settings`() {
        val vm = vm()
        vm.ready()
        vm.saveText(NetworkText.CertificatePassword, " new pass ")
        val drafted = vm.ready { "CertificatePassword" in it.draft }
        assertFalse(drafted.settings.toString().contains("new pass"))
        assertFalse(drafted.settings.toString().contains("s3cret"))
        assertEquals(0, posts(NETWORK))
        vm.save()
        waitFor(NETWORK, 1)
        // Exactly as typed, spaces and all.
        assertEquals(" new pass ", sent().getValue("CertificatePassword").jsonPrimitive.content)
        assertTrue(vm.ready { it.restartNeeded && !it.committing }.draft.isEmpty())
    }

    @Test
    fun `a new password with HTTPS on asks first, because a wrong one stops the server serving HTTPS`() {
        jellyfin.networkConfiguration = jellyfin.networkConfiguration.replace("\"EnableHttps\":false", "\"EnableHttps\":true")
        val vm = vm()
        vm.ready()
        vm.saveText(NetworkText.CertificatePassword, "x")
        vm.save()
        assertEquals(setOf(NetworkImpact.Certificate), vm.ready { it.confirming != null }.confirming)
        assertEquals(0, posts(NETWORK))
    }

    @Test
    fun `a new password with HTTPS off is written without asking`() {
        val vm = vm()
        vm.ready()
        vm.saveText(NetworkText.CertificatePassword, "x")
        vm.save()
        waitFor(NETWORK, 1)
        assertNull(vm.ready().confirming)
    }

    @Test
    fun `a change that can cut this app off says to reconnect after the restart, and one that can't does not`() {
        jellyfin.networkConfiguration = jellyfin.networkConfiguration.replace("\"InternalHttpPort\":8096", "\"InternalHttpPort\":$appPort")
        val vm = vm()
        vm.ready()
        vm.saveNumber(NetworkNumber.HttpPort, "9000")
        vm.save()
        vm.ready { it.confirming != null }
        vm.confirm()
        assertTrue(vm.ready { it.restartNeeded && !it.committing }.reconnect)
        val calm = vm()
        calm.ready()
        calm.setSwitch("IsRemoteIPFilterBlacklist", true, form = true)
        calm.save()
        // Narrowing who can connect asks, but isn't one that cuts this app off.
        assertEquals(setOf(NetworkImpact.IpFilter), calm.ready { it.confirming != null }.confirming)
        calm.confirm()
        assertFalse(calm.ready { it.restartNeeded && !it.committing }.reconnect)
    }

    @Test
    fun `leaving the page while Save changes waits on an as-made write still sends the confirmed save`() {
        val vm = vm()
        vm.ready()
        // An as-made change is pending (its write waits out a short delay) when Save changes is pressed, and the page is left.
        vm.setSwitch("AutoDiscovery", false, form = false)
        vm.saveNumber(NetworkNumber.PublicHttpPort, "8000")
        vm.save()
        vm.ready { it.committing }
        vm.viewModelScope.cancel()
        waitFor(NETWORK, 1)
        assertEquals(8000, sent().getValue("PublicHttpPort").jsonPrimitive.int)
        assertEquals(false, sent().getValue("AutoDiscovery").jsonPrimitive.boolean)
    }

    @Test
    fun `an as-made change made while Save changes is writing is not written under it`() {
        val vm = vm()
        vm.ready()
        jellyfin.slow += NETWORK
        vm.saveNumber(NetworkNumber.PublicHttpPort, "8000")
        vm.save()
        vm.ready { it.committing }
        vm.setSwitch("AutoDiscovery", false, form = false)
        vm.saveItems(NetworkList.KnownProxies, "10.0.0.9")
        Thread.sleep(SETTLE_MILLIS)
        val during = vm.ready()
        assertTrue(during.settings.autoDiscovery)
        assertTrue(during.settings.knownProxies.isEmpty())
        assertEquals(1, posts(NETWORK))
        // Starting the commit closes any sheet.
        assertNull(during.sheet)
    }

    @Test
    fun `a form change made while Save changes is writing is neither written nor kept`() {
        val vm = vm()
        vm.ready()
        jellyfin.slow += NETWORK
        vm.saveNumber(NetworkNumber.PublicHttpPort, "8000")
        vm.save()
        vm.ready { it.committing }
        vm.setSwitch("EnableIPv6", false, form = true)
        vm.saveNumber(NetworkNumber.PublicHttpsPort, "9443")
        val during = vm.ready()
        assertTrue(during.draft.none { it == "EnableIPv6" || it.contains("Https", ignoreCase = true) && it.contains("Public") })
        Thread.sleep(SETTLE_MILLIS)
        assertEquals(1, posts(NETWORK))
    }

    @Test
    fun `a port out of range is never kept`() {
        val vm = vm()
        vm.ready()
        for (bad in listOf("0", "65536", "-1", "abc", "", "80.5")) vm.saveNumber(NetworkNumber.HttpPort, bad)
        assertTrue(vm.ready().draft.isEmpty())
        assertNull(NetworkNumber.HttpPort.parse("0"))
        assertEquals(1, NetworkNumber.HttpPort.parse(" 1 "))
        assertEquals(65535, NetworkNumber.HttpPort.parse("65535"))
    }

    @Test
    fun `a page that can't read says why, and Try again reads it`() {
        jellyfin.status[NETWORK] = 403
        val vm = vm()
        assertEquals(
            NetworkUiState.Failed(ConsoleFailure.Refused),
            runBlocking {
                withTimeout(TIMEOUT_MILLIS) { vm.state.first { it !is NetworkUiState.Loading } }
            },
        )
        jellyfin.status.remove(NETWORK)
        vm.load()
        assertNotNull(vm.ready())
    }

    private companion object {
        const val NETWORK = "/System/Configuration/network"
        const val TIMEOUT_MILLIS = 10_000L
        const val POLL_MILLIS = 10L
        const val SETTLE_MILLIS = 1_200L
    }
}
