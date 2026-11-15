package com.cooper.binge.companion.jellyfin.ui.console

import androidx.lifecycle.ViewModel
import com.cooper.binge.companion.jellyfin.FakeJellyfin
import com.cooper.binge.companion.jellyfin.server.Connection
import com.cooper.binge.companion.jellyfin.server.JellyfinVersion
import com.cooper.binge.companion.jellyfin.server.PluginStatus
import com.cooper.binge.companion.jellyfin.server.ServerPlugin
import com.cooper.binge.companion.jellyfin.server.compareVersions
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class CatalogViewModelTest {
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

    private val server = JellyfinVersion.parse("10.10.7")

    private fun CatalogViewModel.ready(check: (CatalogUiState.Ready) -> Boolean = { true }) =
        runBlocking { withTimeout(TIMEOUT_MILLIS) { state.first { it is CatalogUiState.Ready && check(it) } } } as CatalogUiState.Ready

    private fun installRequests() =
        jellyfin.requests.filter { it.method == "POST" && it.url.encodedPath.startsWith("/Packages/Installed/") }

    private fun announce(
        type: String,
        name: String,
        id: String? = null,
    ) = jellyfin.push("""{"MessageType":"$type","Data":{"Name":"$name"${id?.let { ""","Guid":"$it"""" }.orEmpty()}}}""")

    private fun waitForSocket() =
        runBlocking {
            withTimeout(TIMEOUT_MILLIS) { while (jellyfin.sockets.isEmpty()) kotlinx.coroutines.delay(20) }
        }

    @Test
    fun `reads the catalog, and which of its plugins the server already has`() {
        jellyfin.plugins = jellyfin.plugins.map { it.replace("AniDB", "Playback Reporting") }
        val ready = made(CatalogViewModel(connection, server)).ready()
        assertEquals(listOf("Future Plugin", "Playback Reporting"), ready.packages.map { it.name })
        assertEquals(setOf("Playback Reporting", "Old thing"), ready.installed)
    }

    @Test
    fun `install sends the newest version built for this server, from its repository`() {
        val vm = made(CatalogViewModel(connection, server))
        val pkg = vm.ready().packages.first { it.name == "Playback Reporting" }
        vm.install(pkg)
        vm.ready { "Playback Reporting" in it.installing }
        runBlocking { withTimeout(TIMEOUT_MILLIS) { while (installRequests().isEmpty()) kotlinx.coroutines.delay(20) } }
        val request = installRequests().single()
        assertEquals("/Packages/Installed/Playback%20Reporting", request.url.encodedPath)
        assertEquals("13.0.0.0", request.url.queryParameter("version"))
        assertEquals("5c0f1d6a1e8b4b5aa5d0c7f2b8e1d3a9", request.url.queryParameter("assemblyGuid"))
        assertEquals("https://repo.example/stable.json", request.url.queryParameter("repositoryUrl"))
    }

    @Test
    fun `a chosen version is installed instead of the newest, and is the installed one once it is done`() {
        jellyfin.plugins = jellyfin.plugins.map { it.replace("AniDB", "Playback Reporting").replace("1.2.3.4", "13.0.0.0") }
        val vm = made(CatalogViewModel(connection, server))
        val ready = vm.ready()
        assertEquals("13.0.0.0", ready.installedVersions["Playback Reporting"])
        val pkg = ready.packages.first { it.name == "Playback Reporting" }
        waitForSocket()
        vm.install(pkg, pkg.versions.first { it.version == "12.0.0.0" })
        runBlocking { withTimeout(TIMEOUT_MILLIS) { while (installRequests().isEmpty()) kotlinx.coroutines.delay(20) } }
        assertEquals("12.0.0.0", installRequests().single().url.queryParameter("version"))
        assertEquals("https://repo.example/stable.json", installRequests().single().url.queryParameter("repositoryUrl"))
        announce("PackageInstallationCompleted", "Playback Reporting")
        assertEquals("12.0.0.0", vm.ready { it.outcomes.containsKey("Playback Reporting") }.installedVersions["Playback Reporting"])
    }

    @Test
    fun `the installed version is the live entry, whatever order the server lists a superseded one in`() {
        fun plugin(
            version: String,
            status: PluginStatus,
        ) = ServerPlugin("id", "Playback Reporting", version, "", status, canUninstall = true, hasSettings = false)
        val live = plugin("12.0.0.0", PluginStatus.Active)
        val old = plugin("13.0.0.0", PluginStatus.Superseded)
        assertEquals("12.0.0.0", installedVersions(listOf(live, old))["Playback Reporting"])
        assertEquals("12.0.0.0", installedVersions(listOf(old, live))["Playback Reporting"])
    }

    @Test
    fun `a version built for a newer server is refused, not sent`() {
        val vm = made(CatalogViewModel(connection, server))
        val pkg = vm.ready().packages.first { it.name == "Playback Reporting" }
        vm.install(pkg, pkg.versions.first { it.version == "14.0.0.0" })
        assertEquals(0, installRequests().size)
        assertTrue(vm.ready().installing.isEmpty())
    }

    @Test
    fun `versions are listed newest first by every part of their number`() {
        val pkg = vm().ready().packages.first { it.name == "Playback Reporting" }
        assertEquals(listOf("14.0.0.0", "13.0.0.0", "12.0.0.0"), pkg.history().map { it.version })
        val parts = pkg.versions.first().copy(version = "13.0.0.1")
        assertTrue(pkg.versions.first { it.version == "13.0.0.0" }.isOlderThan(parts.version))
        assertFalse(parts.isOlderThan("13.0.0.1"))
        assertEquals(0, compareVersions("1.2", "1.2.0.0"))
        assertEquals(1, compareVersions("1.10.0.0", "1.9.0.0"))
    }

    private fun vm() = made(CatalogViewModel(connection, server))

    @Test
    fun `a package with no version built for this server is not installed`() {
        val vm = made(CatalogViewModel(connection, server))
        vm.install(vm.ready().packages.first { it.name == "Future Plugin" })
        assertEquals(0, installRequests().size)
        assertTrue(vm.ready().installing.isEmpty())
    }

    @Test
    fun `the server's install messages carry the install's id, which Cancel sends, and its end`() {
        val vm = made(CatalogViewModel(connection, server))
        val pkg = vm.ready().packages.first { it.name == "Playback Reporting" }
        waitForSocket()
        vm.install(pkg)
        vm.ready { it.installing.containsKey("Playback Reporting") }
        vm.cancel(pkg)
        assertEquals(0, jellyfin.requests.count { it.method == "DELETE" })
        announce("PackageInstalling", "Playback Reporting", id = "install-1")
        vm.ready { it.installing["Playback Reporting"] == "install-1" }
        vm.cancel(pkg)
        runBlocking {
            withTimeout(
                TIMEOUT_MILLIS,
            ) { while (jellyfin.requests.none { it.method == "DELETE" }) kotlinx.coroutines.delay(20) }
        }
        assertEquals(
            "/Packages/Installing/install-1",
            jellyfin.requests
                .last { it.method == "DELETE" }
                .url.encodedPath,
        )
        announce("PackageInstallationCancelled", "Playback Reporting")
        val ready = vm.ready { it.outcomes["Playback Reporting"] == InstallOutcome.Cancelled }
        assertTrue(ready.installing.isEmpty())
        assertFalse("Playback Reporting" in ready.installed)
    }

    @Test
    fun `a finished install is marked installed, and a failed one is not`() {
        val vm = made(CatalogViewModel(connection, server))
        val pkg = vm.ready().packages.first { it.name == "Playback Reporting" }
        waitForSocket()
        vm.install(pkg)
        vm.ready { it.installing.containsKey("Playback Reporting") }
        announce("PackageInstallationFailed", "Playback Reporting")
        assertEquals(InstallOutcome.Failed, vm.ready { it.outcomes.containsKey("Playback Reporting") }.outcomes["Playback Reporting"])
        vm.install(pkg)
        vm.ready { it.installing.containsKey("Playback Reporting") && it.outcomes.isEmpty() }
        announce("PackageInstallationCompleted", "Playback Reporting")
        val done = vm.ready { it.outcomes["Playback Reporting"] == InstallOutcome.Done }
        assertTrue("Playback Reporting" in done.installed)
        assertTrue(done.installing.isEmpty())
    }

    @Test
    fun `an install the server refuses is not left installing, and says so`() {
        val vm = made(CatalogViewModel(connection, server))
        val pkg = vm.ready().packages.first { it.name == "Playback Reporting" }
        jellyfin.status["/Packages/Installed/Playback%20Reporting"] = 404
        jellyfin.status["/Packages/Installed/Playback Reporting"] = 404
        vm.install(pkg)
        val failed = vm.ready { it.actionFailed != null }
        assertTrue(failed.installing.isEmpty())
        // Opening a package clears it: the catalog shared with the package's screen would otherwise say it again.
        vm.itemOpened()
        assertNull(vm.ready().actionFailed)
    }

    @Test
    fun `a catalog that can't be read says why, and Try again reads it`() {
        jellyfin.status["/Packages"] = 403
        val vm = made(CatalogViewModel(connection, server))
        assertEquals(
            CatalogUiState.Failed(ConsoleFailure.Refused),
            runBlocking { withTimeout(TIMEOUT_MILLIS) { vm.state.first { it !is CatalogUiState.Loading } } },
        )
        jellyfin.status.remove("/Packages")
        vm.load()
        assertEquals(2, vm.ready().packages.size)
    }

    private companion object {
        const val TIMEOUT_MILLIS = 10_000L
    }
}
