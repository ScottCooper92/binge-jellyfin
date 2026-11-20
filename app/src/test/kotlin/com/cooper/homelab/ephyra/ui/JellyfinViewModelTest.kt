package com.cooper.homelab.ephyra.ui

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.binge.companion.kit.SecretCipher
import com.cooper.homelab.core.server.connection.CallResult
import com.cooper.homelab.core.server.network.CleartextConsent
import com.cooper.homelab.core.server.network.CleartextGate
import com.cooper.homelab.core.server.network.CleartextGuard
import com.cooper.homelab.core.telemetry.Analytics
import com.cooper.homelab.ephyra.FakeItem
import com.cooper.homelab.ephyra.FakeJellyfin
import com.cooper.homelab.ephyra.PlainCipher
import com.cooper.homelab.ephyra.auth.SessionStore
import com.cooper.homelab.ephyra.auth.forLibrary
import com.cooper.homelab.ephyra.demo.DEMO_ORIGIN
import com.cooper.homelab.ephyra.demo.DEMO_TOKEN
import com.cooper.homelab.ephyra.demo.DemoTransport
import com.cooper.homelab.ephyra.server.Connection
import com.cooper.homelab.ephyra.server.DiscardLogger
import com.cooper.homelab.ephyra.server.DiscoveredServer
import com.cooper.homelab.ephyra.server.ForgotPasswordAction
import com.cooper.homelab.ephyra.server.LibraryIndex
import com.cooper.homelab.ephyra.server.SAVED_SERVER
import com.cooper.homelab.ephyra.session
import com.cooper.homelab.ephyra.testSessionStore
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
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
import java.io.IOException
import java.net.ServerSocket
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLHandshakeException
import javax.net.ssl.SSLProtocolException

/** Setup and settings against a fake server. The server calls run on a real IO thread, so each step waits for its state. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class JellyfinViewModelTest {
    private val jellyfin = FakeJellyfin()
    private val sessions: SessionStore = testSessionStore()
    private val index = LibraryIndex()
    private val connection = Connection(OkHttpClient(), sessions, "Jellyfin Companion", "Test", "1.0", Dispatchers.IO, DiscardLogger)

    @Before
    fun main() {
        Dispatchers.setMain(Dispatchers.Unconfined)
        jellyfin.items += FakeItem("m", "Companion", "Movie", tmdbId = 1084199)
    }

    /** The connections a test builds beside the shared one (the demo's, a slow one): closed with it. */
    private val extraConnections = mutableListOf<Connection>()

    @After
    fun close() {
        // Cancels what the view models still have in flight and waits for it to end, so none resumes on Main while it is reset: a
        // server call on the real IO thread can still be running when the stores are cleared, and clearing alone does not wait for it.
        val jobs = viewModels.mapNotNull { it.viewModelScope.coroutineContext[Job] }
        stores.forEach { it.clear() }
        runBlocking { withTimeout(TEARDOWN_MILLIS) { jobs.forEach { it.join() } } }
        connection.close()
        extraConnections.forEach { it.close() }
        jellyfin.close()
        Dispatchers.resetMain()
    }

    private var found: List<DiscoveredServer> = emptyList()

    private val stores = mutableListOf<ViewModelStore>()

    /** The view models a test made, so teardown can wait for the work each still has in flight. */
    private val viewModels = mutableListOf<JellyfinViewModel>()

    private val reported = mutableListOf<Pair<String, Map<String, Any>>>()

    private val analytics =
        object : Analytics {
            override fun screen(name: String) = Unit

            override fun event(
                name: String,
                properties: Map<String, Any>,
            ) {
                reported += name to properties
            }
        }

    private var sessionsEnded = 0

    /** A successful sign-in is counted just after the signed-in state shows, so a test waits for the count it expects. */
    private fun awaitReports(count: Int) =
        runBlocking { withTimeout(10_000) { while (synchronized(reported) { reported.size } < count) delay(5) } }

    private fun viewModel(
        over: Connection = connection,
        cleartext: CleartextConsent = CleartextConsent.None,
    ) = track(
        JellyfinViewModel(
            sessions,
            over,
            index,
            dispatcher = Dispatchers.IO,
            cipher = PlainCipher.forLibrary(),
            discover = { found },
            quickConnectPollMillis = 10L,
            analytics = analytics,
            onSessionEnded = { sessionsEnded++ },
            cleartext = cleartext,
        ),
    )

    private fun track(vm: JellyfinViewModel): JellyfinViewModel {
        viewModels += vm
        val store = ViewModelStore().also { stores += it }
        return ViewModelProvider(store, viewModelFactory { initializer { vm } })[JellyfinViewModel::class.java]
    }

    private inline fun <reified T : UiState> JellyfinViewModel.await(crossinline matches: (T) -> Boolean = { true }): T =
        runBlocking { withTimeout(10_000) { state.first { it is T && matches(it) } as T } }

    private fun JellyfinViewModel.toSignIn(): UiState.SignIn {
        await<UiState.EnterServer>()
        onAddress(jellyfin.baseUrl)
        connect()
        // Whether Quick Connect is on is read with the server, so the step is complete when it shows.
        return await<UiState.SignIn>()
    }

    private fun demoViewModel(cipher: SecretCipher = PlainCipher): JellyfinViewModel {
        val demoSessions = testSessionStore(cipher)
        val demoConnection =
            Connection(
                OkHttpClient.Builder().addInterceptor(DemoTransport()).build(),
                demoSessions,
                "Jellyfin Companion",
                "Test",
                "1.0",
                Dispatchers.IO,
                DiscardLogger,
            ).also { extraConnections += it }
        return track(
            JellyfinViewModel(
                demoSessions,
                demoConnection,
                index,
                dispatcher = Dispatchers.IO,
                cipher = PlainCipher.forLibrary(),
                discover = { found },
                analytics = analytics,
            ),
        )
    }

    @Test
    fun `a finished sign-in leaves nothing of the sign-in step behind`() {
        val vm = viewModel()
        vm.toSignIn()
        vm.onUsername("Claude")
        vm.onPassword("right")
        vm.signInWithPassword()
        vm.await<UiState.SignedIn>()
        // The typed password and the account pictures live in the step, so the step must not outlive the sign-in.
        assertEquals(UiState.Loading, vm.setupStep.value)
    }

    @Test
    fun `Try the demo signs in to the demo server with no address and no account`() {
        val vm = demoViewModel()
        vm.await<UiState.EnterServer>()
        vm.tryDemo()
        val signedIn = vm.await<UiState.SignedIn>()
        assertEquals(DEMO_ORIGIN, signedIn.session.baseUrl)
        assertEquals(DEMO_TOKEN, signedIn.session.token)
        awaitReports(1)
        assertEquals(listOf("sign_in" to mapOf<String, Any>("method" to "demo")), reported.filter { it.first == "sign_in" })
    }

    @Test
    fun `leaving the demo brings back an empty address step, not the demo's address`() {
        val vm = demoViewModel()
        vm.await<UiState.EnterServer>()
        vm.tryDemo()
        vm.await<UiState.SignedIn>()
        vm.signOut()
        assertEquals("", vm.await<UiState.EnterServer>().address)
    }

    @Test
    fun `a demo that cannot be saved ends the spinner with an error and counts the failure`() {
        val refusing =
            object : SecretCipher {
                override fun encrypt(plaintext: String): String = error("The Keystore refused")

                override fun decrypt(ciphertext: String): String? = null
            }
        val vm = demoViewModel(refusing)
        vm.await<UiState.EnterServer>()
        vm.tryDemo()
        val state = vm.await<UiState.EnterServer> { !it.checking && it.error != null }
        assertEquals(SetupError.Generic, state.error)
        assertTrue(reported.any { it.first == "sign_in_failed" && it.second["method"] == "demo" })
    }

    @Test
    fun `an address that is not one is refused before any request`() {
        val vm = viewModel()
        vm.await<UiState.EnterServer>()
        vm.onAddress("http://")
        vm.connect()
        assertEquals(SetupError.InvalidAddress, vm.await<UiState.EnterServer> { it.error != null }.error)
        assertEquals(0, jellyfin.requests.size)
    }

    @Test
    fun `a server that answers is named, and an http one is marked as not encrypted`() {
        val signIn = viewModel().toSignIn()
        assertEquals("truenas", signIn.server.serverName)
        assertTrue(signIn.cleartext)
    }

    @Test
    fun `a server that has not finished its setup says so, and checking again follows it once it has`() {
        jellyfin.startupWizardCompleted = false
        val vm = viewModel()
        val first = vm.toSignIn()
        assertTrue(first.server.setupIncomplete)
        jellyfin.startupWizardCompleted = true
        vm.changeServer(recheck = true)
        assertTrue(!vm.await<UiState.SignIn>().server.setupIncomplete)
    }

    private fun JellyfinViewModel.toStartup(): StartupPhase.Configuring {
        toSignIn()
        startup.open()
        return await<UiState.Startup> { it.phase is StartupPhase.Configuring }.phase as StartupPhase.Configuring
    }

    @Test
    fun `the wizard reads the setup configuration and the lists, and changes nothing until Save`() {
        jellyfin.startupWizardCompleted = false
        val vm = viewModel()
        val step = vm.toStartup()
        assertEquals("truenas", step.configuration.serverName)
        assertEquals("en-US", step.configuration.uiCulture)
        assertTrue(step.uiLanguages.isNotEmpty() && step.cultures.isNotEmpty() && step.countries.isNotEmpty())
        assertEquals(null, jellyfin.savedStartupConfiguration)
        assertTrue(jellyfin.requests.none { it.method == "POST" && it.url.encodedPath.startsWith("/Startup") })
    }

    @Test
    fun `Save sends what the server sent with the user's changes, and never a token`() {
        jellyfin.startupWizardCompleted = false
        jellyfin.startupConfiguration =
            """{"ServerName":"truenas","UICulture":"en-US","MetadataCountryCode":"US","PreferredMetadataLanguage":"en","Other":7}"""
        val vm = viewModel()
        vm.toStartup()
        vm.startup.onName("Living room")
        vm.startup.onUiLanguage("es")
        vm.startup.onMetadataCountry("GB")
        vm.startup.save()
        vm.await<UiState.Startup> { it.phase is StartupPhase.Account }
        val sent = Json.parseToJsonElement(jellyfin.savedStartupConfiguration.orEmpty()).jsonObject
        assertEquals("Living room", sent["ServerName"]?.jsonPrimitive?.content)
        assertEquals("es", sent["UICulture"]?.jsonPrimitive?.content)
        assertEquals("GB", sent["MetadataCountryCode"]?.jsonPrimitive?.content)
        assertEquals("en", sent["PreferredMetadataLanguage"]?.jsonPrimitive?.content)
        assertEquals(7, sent["Other"]?.jsonPrimitive?.int)
        val startupCalls = jellyfin.requests.filter { it.url.encodedPath.startsWith("/Startup") }
        assertTrue(startupCalls.isNotEmpty())
        assertTrue(startupCalls.none { it.headers["Authorization"].orEmpty().contains("Token=") })
    }

    private fun JellyfinViewModel.toAccount(): StartupPhase.Account {
        toStartup()
        startup.save()
        return await<UiState.Startup> { (it.phase as? StartupPhase.Account)?.name == "root" }.phase as StartupPhase.Account
    }

    @Test
    fun `the account step opens with the name the server suggests`() {
        jellyfin.startupWizardCompleted = false
        assertEquals("root", viewModel().toAccount().name)
    }

    @Test
    fun `the account is sent as typed, once, and the password is cleared with the step`() {
        jellyfin.startupWizardCompleted = false
        val vm = viewModel()
        vm.toAccount()
        vm.startup.account.onName("sam")
        vm.startup.account.onPassword("hunter2")
        vm.startup.account.onConfirmation("hunter2")
        vm.startup.account.save()
        val done = vm.await<UiState.Startup> { it.phase is StartupPhase.Finish }
        val sent = Json.parseToJsonElement(jellyfin.savedStartupUser.orEmpty()).jsonObject
        assertEquals("sam", sent["Name"]?.jsonPrimitive?.content)
        assertEquals("hunter2", sent["Password"]?.jsonPrimitive?.content)
        assertEquals(1, jellyfin.requests.count { it.url.encodedPath == "/Startup/User" && it.method == "POST" })
        assertTrue(done.toString().contains("hunter2").not())
        val startup = jellyfin.requests.filter { it.url.encodedPath.startsWith("/Startup") }
        assertTrue(startup.none { it.headers["Authorization"].orEmpty().contains("Token=") })
    }

    /** The wizard at its last step, with an account the fake server will let in. */
    private fun JellyfinViewModel.toFinish(): StartupPhase.Finish {
        toAccount()
        startup.account.onName("sam")
        startup.account.onPassword("right")
        startup.account.onConfirmation("right")
        startup.account.save()
        return await<UiState.Startup> { it.phase is StartupPhase.Finish }.phase as StartupPhase.Finish
    }

    private fun startupCalls() =
        jellyfin.requests
            .filter {
                it.method == "POST" && it.url.encodedPath.startsWith("/Startup")
            }.map { it.url.encodedPath }

    @Test
    fun `Finish setup saves remote access, completes the server, then signs in as the new account`() {
        jellyfin.startupWizardCompleted = false
        val vm = viewModel()
        vm.toFinish()
        vm.startup.finish.onPortMapping(true)
        vm.startup.finish.finish()
        vm.await<UiState.SignedIn>()
        assertEquals(
            listOf("/Startup/Configuration", "/Startup/User", "/Startup/RemoteAccess", "/Startup/Complete"),
            startupCalls(),
        )
        val access = Json.parseToJsonElement(jellyfin.savedStartupRemoteAccess.orEmpty()).jsonObject
        assertEquals("true", access["EnableRemoteAccess"]?.jsonPrimitive?.content)
        assertEquals("true", access["EnableAutomaticPortMapping"]?.jsonPrimitive?.content)
        // The account's password is sent to sign in once, and the token that comes back is not in any Startup request.
        val signIns = jellyfin.requests.filter { it.url.encodedPath == "/Users/AuthenticateByName" }
        assertEquals(1, signIns.size)
        val sent =
            signIns
                .single()
                .body
                ?.utf8()
                .orEmpty()
        val body = Json.parseToJsonElement(sent).jsonObject
        assertEquals("sam", body["Username"]?.jsonPrimitive?.content)
        assertTrue(
            jellyfin.requests
                .filter {
                    it.url.encodedPath.startsWith("/Startup")
                }.none { it.headers["Authorization"].orEmpty().contains("Token=") },
        )
    }

    @Test
    fun `with remote access off, port mapping is off whatever it was`() {
        jellyfin.startupWizardCompleted = false
        val vm = viewModel()
        vm.toFinish()
        vm.startup.finish.onPortMapping(true)
        vm.startup.finish.onRemoteAccess(false)
        assertTrue(!(vm.state.value as UiState.Startup).phase.let { (it as StartupPhase.Finish).portMapping })
        vm.startup.finish.finish()
        vm.await<UiState.SignedIn>()
        val access = Json.parseToJsonElement(jellyfin.savedStartupRemoteAccess.orEmpty()).jsonObject
        assertEquals("false", access["EnableRemoteAccess"]?.jsonPrimitive?.content)
        assertEquals("false", access["EnableAutomaticPortMapping"]?.jsonPrimitive?.content)
    }

    @Test
    fun `a Complete the server refuses keeps the last step, and Finish setup tries both calls again`() {
        jellyfin.startupWizardCompleted = false
        val vm = viewModel()
        vm.toFinish()
        jellyfin.status["/Startup/Complete"] = 500
        vm.startup.finish.finish()
        val step = vm.await<UiState.Startup> { (it.phase as? StartupPhase.Finish)?.error != null }.phase as StartupPhase.Finish
        assertEquals(SetupError.Generic, step.error)
        assertTrue(!step.busy)
        jellyfin.status.remove("/Startup/Complete")
        vm.startup.finish.finish()
        vm.await<UiState.SignedIn>()
        assertEquals(2, startupCalls().count { it == "/Startup/RemoteAccess" })
    }

    @Test
    fun `a Complete whose reply was lost signs in on the retry, as the server is already complete`() {
        jellyfin.startupWizardCompleted = false
        val vm = viewModel()
        vm.toFinish()
        // The server applied Complete earlier and the reply never arrived: every Startup route now answers 401.
        jellyfin.startupCompleted = true
        vm.startup.finish.finish()
        vm.await<UiState.SignedIn>()
        assertEquals(1, jellyfin.requests.count { it.url.encodedPath == "/Users/AuthenticateByName" })
        assertEquals(0, startupCalls().count { it == "/Startup/Complete" })
    }

    @Test
    fun `a 401 followed by a sign-in that fails goes to the sign-in step with the name filled in and no password`() {
        jellyfin.startupWizardCompleted = false
        val vm = viewModel()
        vm.toFinish()
        jellyfin.startupCompleted = true
        jellyfin.startupWizardCompleted = true
        jellyfin.status["/Users/AuthenticateByName"] = 500
        vm.startup.finish.finish()
        val signIn = vm.await<UiState.SignIn> { it.username == "sam" }
        assertEquals("sam", signIn.username)
        assertEquals("", signIn.password)
        assertTrue(!signIn.server.setupIncomplete)
    }

    @Test
    fun `a sign-in that fails after Complete goes to the sign-in step with the name filled in and no password`() {
        jellyfin.startupWizardCompleted = false
        val vm = viewModel()
        vm.toFinish()
        jellyfin.status["/Users/AuthenticateByName"] = 500
        vm.startup.finish.finish()
        val signIn = vm.await<UiState.SignIn>()
        assertEquals("sam", signIn.username)
        assertEquals("", signIn.password)
        assertTrue(!signIn.server.setupIncomplete)
    }

    @Test
    fun `a sign-in that fails after setup reads the server again for the step it falls back to`() {
        jellyfin.startupWizardCompleted = false
        val vm = viewModel()
        vm.toFinish()
        // The server is complete now, and has Quick Connect off: the step it falls back to is built from what it says.
        jellyfin.startupWizardCompleted = true
        jellyfin.quickConnectEnabled = false
        jellyfin.status["/Users/AuthenticateByName"] = 500
        vm.startup.finish.finish()
        val signIn = vm.await<UiState.SignIn> { !it.quickConnectOn }
        assertEquals("sam", signIn.username)
    }

    @Test
    fun `leaving the last step drops the account's password`() {
        jellyfin.startupWizardCompleted = false
        val vm = viewModel()
        vm.toFinish()
        assertTrue(!(vm.state.value as UiState.Startup).phase.toString().contains("right"))
        vm.startup.back()
        assertTrue(vm.await<UiState.SignIn>().password.isEmpty())
    }

    @Test
    fun `nothing is sent while the name is blank or the two passwords differ`() {
        jellyfin.startupWizardCompleted = false
        val vm = viewModel()
        vm.toAccount()
        vm.startup.account.onPassword("a")
        vm.startup.account.onConfirmation("b")
        vm.startup.account.save()
        vm.startup.account.onConfirmation("a")
        vm.startup.account.onName("  ")
        vm.startup.account.save()
        assertNull(jellyfin.savedStartupUser)
        assertTrue((vm.state.value as UiState.Startup).phase.let { it is StartupPhase.Account && !it.busy })
    }

    @Test
    fun `an empty password is never sent, since the server refuses it`() {
        jellyfin.startupWizardCompleted = false
        val vm = viewModel()
        vm.toAccount()
        vm.startup.account.onName("sam")
        vm.startup.account.save()
        assertNull(jellyfin.savedStartupUser)
    }

    @Test
    fun `the server's own words for a refused account are kept, to show in place of the generic sentence`() {
        jellyfin.startupWizardCompleted = false
        val vm = viewModel()
        vm.toAccount()
        vm.startup.account.onPassword("hunter2")
        vm.startup.account.onConfirmation("hunter2")
        jellyfin.status["/Startup/User"] = 400
        jellyfin.statusBody["/Startup/User"] = "Password must not be empty"
        vm.startup.account.save()
        val step = vm.await<UiState.Startup> { (it.phase as? StartupPhase.Account)?.error != null }.phase as StartupPhase.Account
        assertEquals("Password must not be empty", step.reason)
        vm.startup.account.onPassword("hunter3")
        assertNull((vm.state.value as UiState.Startup).phase.let { (it as StartupPhase.Account).reason })
    }

    @Test
    fun `a refused account keeps the step and what was typed, and the password out of the text of the step`() {
        jellyfin.startupWizardCompleted = false
        val vm = viewModel()
        vm.toAccount()
        vm.startup.account.onPassword("hunter2")
        vm.startup.account.onConfirmation("hunter2")
        jellyfin.status["/Startup/User"] = 500
        vm.startup.account.save()
        val step = vm.await<UiState.Startup> { (it.phase as? StartupPhase.Account)?.error != null }.phase as StartupPhase.Account
        assertEquals(SetupError.Generic, step.error)
        assertEquals("hunter2", step.password)
        assertTrue(!step.toString().contains("hunter2"))
    }

    @Test
    fun `leaving the wizard drops the password`() {
        jellyfin.startupWizardCompleted = false
        val vm = viewModel()
        vm.toAccount()
        vm.startup.account.onPassword("hunter2")
        vm.startup.back()
        assertTrue(!vm.await<UiState.SignIn>().toString().contains("hunter2"))
        assertTrue(vm.state.value !is UiState.Startup)
    }

    @Test
    fun `a server that sends no name gets no name field, and is sent back without one`() {
        jellyfin.startupWizardCompleted = false
        jellyfin.startupConfiguration = """{"UICulture":"en-US","MetadataCountryCode":"US","PreferredMetadataLanguage":"en"}"""
        val vm = viewModel()
        assertTrue(!vm.toStartup().configuration.hasServerName)
        vm.startup.onMetadataCountry("GB")
        vm.startup.save()
        vm.await<UiState.Startup> { it.phase is StartupPhase.Account }
        assertTrue("ServerName" !in jellyfin.savedStartupConfiguration.orEmpty())
    }

    @Test
    fun `a save the server refuses keeps the step and the choices, with the reason beside Save`() {
        jellyfin.startupWizardCompleted = false
        val vm = viewModel()
        vm.toStartup()
        vm.startup.onName("Living room")
        jellyfin.status["/Startup/Configuration"] = 500
        vm.startup.save()
        val step = vm.await<UiState.Startup> { (it.phase as? StartupPhase.Configuring)?.error != null }.phase as StartupPhase.Configuring
        assertEquals(SetupError.Generic, step.error)
        assertEquals("Living room", step.configuration.serverName)
        assertTrue(!step.busy)
    }

    @Test
    fun `a list the server refuses fails the read, and Try again reads it once the server allows it`() {
        jellyfin.startupWizardCompleted = false
        val vm = viewModel()
        vm.toSignIn()
        jellyfin.status["/Localization/Countries"] = 401
        vm.startup.open()
        vm.await<UiState.Startup> { it.phase is StartupPhase.Failed }
        jellyfin.status.remove("/Localization/Countries")
        vm.startup.load()
        vm.await<UiState.Startup> { it.phase is StartupPhase.Configuring }
    }

    @Test
    fun `the wizard opens only for a server that has not finished setup, and Back returns to the notice`() {
        val vm = viewModel()
        vm.toSignIn()
        vm.startup.open()
        assertTrue(vm.state.value is UiState.SignIn)
        jellyfin.startupWizardCompleted = false
        val other = viewModel()
        other.toStartup()
        other.startup.back()
        assertTrue(other.await<UiState.SignIn>().server.setupIncomplete)
    }

    @Test
    fun `an address that is not a Jellyfin server, or not reachable, says which`() {
        val vm = viewModel()
        vm.await<UiState.EnterServer>()
        jellyfin.status["/System/Info/Public"] = 404
        vm.onAddress(jellyfin.baseUrl)
        vm.connect()
        assertEquals(SetupError.NotJellyfin, vm.await<UiState.EnterServer> { it.error != null }.error)

        jellyfin.close()
        vm.onAddress(jellyfin.baseUrl)
        vm.connect()
        assertEquals(SetupError.Unreachable, vm.await<UiState.EnterServer> { it.error != null }.error)
    }

    @Test
    fun `plain HTTP to a public host the user has not agreed to is not read, and no request is sent`() {
        val guarded = guardedConnection()
        val vm = viewModel(guarded)
        vm.await<UiState.EnterServer>()
        vm.onAddress("http://jellyfin.example.com")
        vm.connect()

        val asked = vm.await<UiState.EnterServer> { it.insecureHost != null }
        assertFalse(asked.canConnect)
        assertFalse(asked.checking)
        assertTrue(jellyfin.requests.isEmpty())
    }

    @Test
    fun `an address that reaches a public host over plain HTTP waits for the user's agreement`() {
        val vm = viewModel()
        vm.await<UiState.EnterServer>()

        vm.onAddress("http://jellyfin.example.com")
        val asked = vm.await<UiState.EnterServer>()
        assertEquals("jellyfin.example.com", asked.insecureHost)
        assertFalse(asked.canConnect)

        vm.allowCleartext(true)
        assertTrue(vm.await<UiState.EnterServer> { it.cleartextAllowed }.canConnect)

        // The agreement was for that host: another address has not been agreed to.
        vm.onAddress("http://other.example.org")
        val other = vm.await<UiState.EnterServer>()
        assertEquals("other.example.org", other.insecureHost)
        assertFalse(other.cleartextAllowed)
    }

    /** A connection that records every address asked for and answers none of them. */
    private fun recordingConnection(asked: MutableList<HttpUrl>) =
        Connection(
            OkHttpClient
                .Builder()
                .addInterceptor { chain ->
                    asked += chain.request().url
                    throw IOException("no route")
                }.build(),
            sessions,
            "Jellyfin Companion",
            "Test",
            "1.0",
            Dispatchers.IO,
            DiscardLogger,
        ).also { extraConnections += it }

    private fun askedFor(
        typed: String,
        consent: CleartextConsent = CleartextConsent.None,
        agreed: Boolean = false,
    ): List<String> {
        val asked = CopyOnWriteArrayList<HttpUrl>()
        val vm = viewModel(recordingConnection(asked), consent)
        vm.await<UiState.EnterServer>()
        vm.onAddress(typed)
        if (agreed) vm.allowCleartext(true)
        vm.connect()
        vm.await<UiState.EnterServer> { it.error != null || !it.canConnect }
        return asked.map { "${it.scheme}://${it.host}${if (it.port == it.scheme.defaultPort()) "" else ":${it.port}"}" }.distinct()
    }

    private fun String.defaultPort() = if (this == "https") 443 else 80

    @Test
    fun `a public host typed without a scheme is tried over https, then on the server's own https port`() {
        assertEquals(listOf("https://jellyfin.example.com", "https://jellyfin.example.com:8920"), askedFor("jellyfin.example.com"))
    }

    @Test
    fun `a local host or address typed without a scheme stays plain http, with no agreement asked for`() {
        assertEquals(listOf("http://192.168.1.10", "http://192.168.1.10:8096"), askedFor("192.168.1.10"))
        assertEquals(listOf("http://jellyfin.local", "http://jellyfin.local:8096"), askedFor("jellyfin.local"))
    }

    @Test
    fun `a typed port is taken as meant and nothing else is tried`() {
        assertEquals(listOf("http://192.168.1.10:9000"), askedFor("192.168.1.10:9000"))
        assertEquals(listOf("https://jellyfin.example.com:9443"), askedFor("https://jellyfin.example.com:9443"))
    }

    @Test
    fun `plain http to a public host is read only once the user has agreed, and then as typed`() {
        assertEquals(emptyList<String>(), askedFor("http://jellyfin.example.com"))
        assertEquals(
            listOf("http://jellyfin.example.com", "http://jellyfin.example.com:8096"),
            askedFor("http://jellyfin.example.com", RecordingConsent(), agreed = true),
        )
    }

    @Test
    fun `an address that is redirected is signed in to at the address that answered`() {
        val redirector = MockWebServer()
        try {
            redirector.dispatcher =
                object : Dispatcher() {
                    override fun dispatch(request: RecordedRequest) =
                        MockResponse
                            .Builder()
                            .code(302)
                            .addHeader("Location", jellyfin.baseUrl.trimEnd('/') + request.url.encodedPath)
                            .build()
                }
            redirector.start()
            val vm = viewModel()
            vm.await<UiState.EnterServer>()
            vm.onAddress(redirector.url("/").toString())
            vm.connect()
            assertEquals(jellyfin.baseUrl.trimEnd('/'), vm.await<UiState.SignIn>().baseUrl)
        } finally {
            redirector.close()
        }
    }

    @Test
    fun `a plain http host the user agreed to earlier is found agreed to again when its server is edited`() {
        val consent = RecordingConsent().apply { granted += "jellyfin.example.com" }
        runBlocking { sessions.save(jellyfin.session().copy(baseUrl = "http://jellyfin.example.com")) }
        val vm = viewModel(cleartext = consent)
        vm.await<UiState.SignedIn>()

        vm.changeServer()

        val step = vm.await<UiState.EnterServer> { it.address == "http://jellyfin.example.com" }
        assertTrue(step.cleartextAllowed)
        assertTrue(step.canConnect)
    }

    @Test
    fun `leaving a plain HTTP public host's session brings back the row asking for agreement`() {
        runBlocking { sessions.save(jellyfin.session().copy(baseUrl = "http://jellyfin.example.com")) }
        val vm = viewModel()
        // The saved session is read in the background: signing out before it has been read lets the read land after the sign-out.
        vm.await<UiState.SignedIn>()
        vm.signOut()

        val asked = vm.await<UiState.EnterServer> { it.address == "http://jellyfin.example.com" }
        assertEquals("jellyfin.example.com", asked.insecureHost)
        assertFalse(asked.canConnect)
    }

    @Test
    fun `a session to a public plain HTTP host holds an agreement the user can take back`() {
        runBlocking { sessions.save(jellyfin.session().copy(baseUrl = "http://jellyfin.example.com")) }
        val vm = viewModel()
        assertEquals("jellyfin.example.com", vm.await<UiState.SignedIn>().consentHost)
    }

    @Test
    fun `a session that holds no agreement has none to take back and cannot ask`() {
        listOf("https://jellyfin.example.com", "http://192.168.1.10:8096").forEach { url ->
            runBlocking { sessions.save(jellyfin.session().copy(baseUrl = url)) }
            val vm = viewModel()
            assertNull(url, vm.await<UiState.SignedIn>().consentHost)
            vm.confirmTakeBack(true)
            assertFalse(url, vm.await<UiState.SignedIn>().confirmingTakeBack)
        }
    }

    @Test
    fun `taking the agreement back asks first, and confirming signs out and brings back the row asking for it`() {
        runBlocking { sessions.save(jellyfin.session().copy(baseUrl = "http://jellyfin.example.com")) }
        val vm = viewModel()
        vm.await<UiState.SignedIn>()

        vm.confirmTakeBack(true)
        assertTrue(vm.await<UiState.SignedIn> { it.confirmingTakeBack }.confirmingTakeBack)
        // Cancelling changes nothing: the session is still there.
        vm.confirmTakeBack(false)
        assertFalse(vm.await<UiState.SignedIn> { !it.confirmingTakeBack }.confirmingTakeBack)
        assertEquals("jellyfin.example.com", runBlocking { sessions.current() }?.baseUrl?.removePrefix("http://"))

        vm.confirmTakeBack(true)
        vm.signOut()
        val asked = vm.await<UiState.EnterServer> { it.address == "http://jellyfin.example.com" }
        assertEquals("jellyfin.example.com", asked.insecureHost)
        assertFalse(asked.cleartextAllowed)
        assertFalse(asked.canConnect)
        assertNull(runBlocking { sessions.current() })
    }

    @Test
    fun `an address that needs no agreement asks for none`() {
        val vm = viewModel()
        vm.await<UiState.EnterServer>()

        listOf("https://jellyfin.example.com", "192.168.1.10:8096", "jellyfin.example.com", "http://jellyfin.local").forEach {
            vm.onAddress(it)
            assertNull(it, vm.await<UiState.EnterServer>().insecureHost)
        }
    }

    @Test
    fun `agreeing is kept before the first request is sent`() {
        val consent = RecordingConsent()
        val failing =
            Connection(
                OkHttpClient.Builder().addInterceptor { throw IOException("no route") }.build(),
                sessions,
                "Jellyfin Companion",
                "Test",
                "1.0",
                Dispatchers.IO,
                DiscardLogger,
            ).also { extraConnections += it }
        val vm = viewModel(failing, consent)
        vm.await<UiState.EnterServer>()
        vm.onAddress("http://jellyfin.example.com")
        vm.allowCleartext(true)

        vm.connect()

        assertEquals(SetupError.Unreachable, vm.await<UiState.EnterServer> { it.error != null }.error)
        assertEquals(listOf("jellyfin.example.com"), consent.granted)
    }

    private fun failingWith(fail: (HttpUrl) -> IOException) =
        Connection(
            OkHttpClient
                .Builder()
                .addInterceptor { chain -> throw fail(chain.request().url) }
                .build(),
            sessions,
            "Jellyfin Companion",
            "Test",
            "1.0",
            Dispatchers.IO,
            DiscardLogger,
        ).also { extraConnections += it }

    private fun askingForCertificate() = failingWith { SSLProtocolException(CERTIFICATE_REQUIRED_ALERT) }

    /** A call whose last attempt failed on the certificate, with the attempt before it that was the server asking for one. */
    private fun askedThenRejected() =
        failingWith {
            SSLHandshakeException("Trust anchor for certification path not found").apply {
                addSuppressed(SSLProtocolException(CERTIFICATE_REQUIRED_ALERT))
            }
        }

    @Test
    fun `a server that asks for a client certificate is named in the connect step, not called unreachable`() {
        val vm = viewModel(askingForCertificate())
        vm.await<UiState.EnterServer>()
        vm.onAddress("https://jellyfin.example.com")

        vm.connect()

        assertEquals(SetupError.ClientCertificateNeeded, vm.await<UiState.EnterServer> { it.error != null }.error)
    }

    @Test
    fun `a saved server that asks for a client certificate says so on reconnect`() {
        runBlocking { sessions.save(jellyfin.session()) }
        val vm = viewModel(askingForCertificate())
        vm.refresh()
        assertEquals(
            ConnectionHealth.ClientCertificateNeeded,
            vm.await<UiState.SignedIn> { it.health.isProblem && !it.refreshing }.health,
        )
    }

    @Test
    fun `a client certificate request an earlier attempt met is not lost behind the last attempt's failure`() {
        val vm = viewModel(askedThenRejected())
        vm.await<UiState.EnterServer>()
        vm.onAddress("https://jellyfin.example.com")

        vm.connect()

        assertEquals(SetupError.ClientCertificateNeeded, vm.await<UiState.EnterServer> { it.error != null }.error)
    }

    @Test
    fun `a saved server whose earlier attempt met a client certificate request says so on reconnect`() {
        runBlocking { sessions.save(jellyfin.session()) }
        val vm = viewModel(askedThenRejected())
        vm.refresh()
        assertEquals(
            ConnectionHealth.ClientCertificateNeeded,
            vm.await<UiState.SignedIn> { it.health.isProblem && !it.refreshing }.health,
        )
    }

    @Test
    fun `a handshake that fails on its own is still called unreachable`() {
        val vm = viewModel(failingWith { SSLHandshakeException("handshake failed") })
        vm.await<UiState.EnterServer>()
        vm.onAddress("https://jellyfin.example.com")

        vm.connect()

        assertEquals(SetupError.Unreachable, vm.await<UiState.EnterServer> { it.error != null }.error)
    }

    @Test
    fun `a guard that refuses after the user agreed is a refusal, not a connection`() {
        val vm = viewModel(guardedConnection())
        vm.await<UiState.EnterServer>()
        vm.onAddress("http://jellyfin.example.com")
        vm.allowCleartext(true)
        vm.connect()

        assertEquals(SetupError.CleartextRefused, vm.await<UiState.EnterServer> { it.error != null }.error)
    }

    @Test
    fun `checking again after agreeing to a plain HTTP public host rechecks it`() {
        jellyfin.startupWizardCompleted = false
        val fake = jellyfin.baseUrl.toHttpUrl()
        val reaching =
            Connection(
                OkHttpClient
                    .Builder()
                    .addInterceptor { chain ->
                        val url =
                            chain
                                .request()
                                .url
                                .newBuilder()
                                .scheme("http")
                                .host(fake.host)
                                .port(fake.port)
                                .build()
                        chain.proceed(
                            chain
                                .request()
                                .newBuilder()
                                .url(url)
                                .build(),
                        )
                    }.build(),
                sessions,
                "Jellyfin Companion",
                "Test",
                "1.0",
                Dispatchers.IO,
                DiscardLogger,
            ).also { extraConnections += it }
        val vm = viewModel(reaching, RecordingConsent())
        vm.await<UiState.EnterServer>()
        vm.onAddress("http://jellyfin.example.com")
        vm.allowCleartext(true)
        vm.connect()
        assertTrue(vm.await<UiState.SignIn>().server.setupIncomplete)

        jellyfin.startupWizardCompleted = true
        vm.changeServer(recheck = true)

        assertFalse(vm.await<UiState.SignIn>().server.setupIncomplete)
    }

    private class RecordingConsent : CleartextConsent {
        val granted = mutableListOf<String>()

        override suspend fun allows(host: String): Boolean = host in granted

        override suspend fun grant(host: String) {
            granted += host
        }

        override suspend fun retainOnly(host: String?) = Unit
    }

    private fun guardedConnection() =
        Connection(
            OkHttpClient
                .Builder()
                .addInterceptor(
                    CleartextGuard(CleartextGate(CleartextConsent.None, publicCleartextAvailable = true)),
                ).build(),
            sessions,
            "Jellyfin Companion",
            "Test",
            "1.0",
            Dispatchers.IO,
            DiscardLogger,
        ).also { extraConnections += it }

    @Test
    fun `forgot password needs a name, sends only the name, and tells what the server answered`() {
        val vm = viewModel()
        vm.toSignIn()
        vm.forgotPassword.start()
        assertTrue(jellyfin.requests.none { it.url.encodedPath == "/Users/ForgotPassword" })

        vm.onUsername(" Mum ")
        vm.await<UiState.SignIn> { it.username == " Mum " }
        vm.forgotPassword.start()
        assertEquals(
            ForgotPasswordStep.Told(ForgotPasswordAction.ContactAdmin),
            vm.await<UiState.SignIn> { it.forgot is ForgotPasswordStep.Told }.forgot,
        )
        val sent = jellyfin.requests.last { it.url.encodedPath == "/Users/ForgotPassword" }
        assertEquals("""{"EnteredUsername":"Mum"}""", sent.body?.utf8())
        assertNull(sent.headers["Authorization"]?.takeIf { it.contains("Token=") })

        vm.forgotPassword.dismiss()
        assertNull(vm.await<UiState.SignIn> { it.forgot == null }.forgot)
        jellyfin.forgotAnswer = """{"Action":"InNetworkRequired"}"""
        vm.forgotPassword.start()
        assertEquals(
            ForgotPasswordStep.Told(ForgotPasswordAction.InNetworkRequired),
            vm.await<UiState.SignIn> { it.forgot is ForgotPasswordStep.Told }.forgot,
        )
    }

    @Test
    fun `a PIN reset asks for the PIN, keeps the sheet on a wrong one (the server's 404), and sets the password to the right one`() {
        jellyfin.forgotAnswer =
            """{"Action":"PinCode","PinFile":"/config/data/passwordreset.json","PinExpirationDate":"2026-11-16T00:00:00Z"}"""
        val vm = viewModel()
        vm.toSignIn()
        vm.onUsername("Mum")
        vm.onPassword("typed")
        vm.await<UiState.SignIn> { it.username == "Mum" && it.password == "typed" }
        vm.forgotPassword.start()
        assertEquals(
            ForgotPasswordStep.EnterPin("/config/data/passwordreset.json"),
            vm.await<UiState.SignIn> { it.forgot is ForgotPasswordStep.EnterPin }.forgot,
        )

        vm.forgotPassword.submitPin("0000")
        assertTrue(
            (
                vm.await<UiState.SignIn> {
                    (it.forgot as? ForgotPasswordStep.EnterPin)?.wrongPin == true
                }
            ).forgot is ForgotPasswordStep.EnterPin,
        )

        jellyfin.forgotPin = "AB-12"
        vm.forgotPassword.submitPin(" ab-12 ")
        val done = vm.await<UiState.SignIn> { it.forgot == ForgotPasswordStep.Done && it.password == "AB-12" }
        // The password is now the PIN as sent: upper case, as the server keeps it.
        assertEquals("AB-12", done.password)
        assertEquals("Mum", done.username)
    }

    @Test
    fun `a server that does not answer a reset says so instead of closing silently`() {
        val vm = viewModel()
        vm.toSignIn()
        vm.onUsername("Mum")
        // The name reaches the screen's state through the connect flow, which runs on another dispatcher: asking before it has
        // arrived is a reset for no one, which the flow rightly ignores.
        vm.await<UiState.SignIn> { it.username == "Mum" }
        jellyfin.status["/Users/ForgotPassword"] = 500
        vm.forgotPassword.start()
        vm.await<UiState.SignIn> { it.forgot == ForgotPasswordStep.Failed }
    }

    @Test
    fun `the server's public users are listed on sign-in, and none or a failure leaves the step as it is`() {
        jellyfin.publicUsers = PUBLIC_USERS
        val users = viewModel().also { it.toSignIn() }.await<UiState.SignIn> { it.publicUsers.isNotEmpty() }.publicUsers
        assertEquals(listOf("Kid", "Mum"), users.map { it.name })
        assertEquals(listOf(false, true), users.map { it.hasPassword })

        jellyfin.publicUsers = "[]"
        val none = viewModel().toSignIn()
        assertTrue(none.publicUsers.isEmpty())
        assertNull(none.passwordError)

        jellyfin.status["/Users/Public"] = 500
        val failed = viewModel().toSignIn()
        assertTrue(failed.publicUsers.isEmpty())
        assertNull(failed.passwordError)
    }

    @Test
    fun `picking a public user with a password fills the name and waits, one without signs in`() {
        jellyfin.publicUsers = PUBLIC_USERS
        val vm = viewModel()
        vm.toSignIn()
        val users = vm.await<UiState.SignIn> { it.publicUsers.isNotEmpty() }.publicUsers

        vm.pickPublicUser(users.first { it.name == "Mum" })
        val picked = vm.await<UiState.SignIn> { it.username == "Mum" }
        assertFalse(picked.busy)
        assertEquals("", picked.password)
        assertTrue(jellyfin.requests.none { it.url.encodedPath == "/Users/AuthenticateByName" })

        vm.pickPublicUser(users.first { it.name == "Kid" })
        vm.await<UiState.SignedIn> { it.titles != null }
        assertEquals("token-1", runBlocking { sessions.current() }?.token)
    }

    @Test
    fun `a wrong password says so, a right one signs in and counts the library`() {
        val vm = viewModel()
        vm.toSignIn()
        vm.onUsername("Claude")
        vm.onPassword("wrong")
        vm.signInWithPassword()
        assertEquals(SetupError.WrongCredentials, vm.await<UiState.SignIn> { it.passwordError != null }.passwordError)

        vm.onPassword("right")
        vm.signInWithPassword()
        val signedIn = vm.await<UiState.SignedIn> { it.titles != null }
        assertEquals("Claude", signedIn.session.userName)
        assertEquals(1, signedIn.titles)
        assertEquals("token-1", runBlocking { sessions.current() }?.token)
    }

    @Test
    fun `a sign-in is reported by its method and a failure by its category, never by name or address`() {
        val vm = viewModel()
        vm.toSignIn()
        vm.onUsername("Claude")
        vm.onPassword("wrong")
        vm.signInWithPassword()
        vm.await<UiState.SignIn> { it.passwordError != null }
        vm.onPassword("right")
        vm.signInWithPassword()
        vm.await<UiState.SignedIn> { it.titles != null }
        awaitReports(2)
        assertEquals(
            listOf(
                "sign_in_failed" to mapOf("method" to "password", "reason" to "wrong_credentials"),
                "sign_in" to mapOf("method" to "password"),
            ),
            reported,
        )
    }

    @Test
    fun `Quick Connect is reported as its own method, and signing out is reported`() {
        val vm = viewModel()
        vm.toSignIn()
        vm.startQuickConnect()
        vm.await<UiState.SignIn> { it.quickConnectCode != null }
        jellyfin.quickConnectApproved = true
        vm.await<UiState.SignedIn> { it.titles != null }
        vm.signOut()
        vm.await<UiState.EnterServer>()
        awaitReports(2)
        assertEquals(listOf("sign_in" to mapOf("method" to "quick_connect"), "sign_out" to emptyMap<String, Any>()), reported)
    }

    @Test
    fun `Quick Connect turned off on the server is reported as that reason`() {
        val vm = viewModel()
        vm.toSignIn()
        jellyfin.quickConnectEnabled = false
        vm.startQuickConnect()
        vm.await<UiState.SignIn> { it.quickConnectError != null }
        assertEquals(listOf("sign_in_failed" to mapOf("method" to "quick_connect", "reason" to "quick_connect_off")), reported)
    }

    @Test
    fun `the sign-in step offers Quick Connect while the server has it on`() {
        val vm = viewModel()
        vm.toSignIn()
        val step = vm.state.value as UiState.SignIn
        assertTrue(step.quickConnectOn)
        assertTrue(step.showsQuickConnectHint)
    }

    @Test
    fun `the sign-in step stops offering Quick Connect, and its hint, when the server has it off`() {
        jellyfin.quickConnectEnabled = false
        val vm = viewModel()
        vm.toSignIn()
        val step = vm.await<UiState.SignIn> { !it.quickConnectOn }
        assertFalse(step.showsQuickConnectHint)
        assertNull(step.quickConnectError)
    }

    @Test
    fun `a failed or unclear read of Quick Connect keeps it on offer`() {
        jellyfin.quickConnectEnabled = false
        jellyfin.status["/QuickConnect/Enabled"] = 500
        val failed = viewModel().toSignIn()
        assertTrue(failed.quickConnectOn)

        jellyfin.status.clear()
        jellyfin.notJson += "/QuickConnect/Enabled"
        val unclear = viewModel().toSignIn()
        assertTrue(unclear.quickConnectOn)
        assertTrue(unclear.showsQuickConnectHint)
    }

    @Test
    fun `Quick Connect shows the code and signs in once approved`() {
        val vm = viewModel()
        vm.toSignIn()
        vm.startQuickConnect()
        assertEquals("123456", vm.await<UiState.SignIn> { it.quickConnectCode != null }.quickConnectCode)
        jellyfin.quickConnectApproved = true
        vm.await<UiState.SignedIn>()
        assertEquals("user-1", runBlocking { sessions.current() }?.userId)
    }

    @Test
    fun `Quick Connect off on the server says so, and a cancelled code goes away`() {
        val vm = viewModel()
        vm.toSignIn()
        jellyfin.quickConnectEnabled = false
        vm.startQuickConnect()
        assertEquals(SetupError.QuickConnectOff, vm.await<UiState.SignIn> { it.quickConnectError != null }.quickConnectError)

        jellyfin.quickConnectEnabled = true
        vm.startQuickConnect()
        vm.await<UiState.SignIn> { it.quickConnectCode != null }
        vm.cancelQuickConnect()
        assertNull(vm.await<UiState.SignIn> { it.quickConnectCode == null }.quickConnectCode)
        vm.changeServer()
        assertEquals(jellyfin.baseUrl, vm.await<UiState.EnterServer>().address)
    }

    @Test
    fun `answering the local-network prompt on the server step tries the connection again`() {
        val vm = viewModel()
        vm.await<UiState.EnterServer>()
        vm.onAddress(jellyfin.baseUrl)
        vm.localNetworkAnswered()
        assertEquals(jellyfin.baseUrl, vm.await<UiState.SignIn>().baseUrl)
    }

    @Test
    fun `signing out ends the session on the server and here, and keeps the address`() {
        runBlocking { sessions.save(jellyfin.session()) }
        val vm = viewModel()
        vm.await<UiState.SignedIn> { it.titles != null }
        vm.confirmSignOut(true)
        assertTrue(vm.await<UiState.SignedIn> { it.confirmingSignOut }.confirmingSignOut)
        vm.confirmSignOut(false)
        vm.await<UiState.SignedIn> { !it.confirmingSignOut }
        vm.signOut()
        assertEquals(jellyfin.baseUrl, vm.await<UiState.EnterServer>().address)
        assertEquals(1, jellyfin.hits("/Sessions/Logout"))
        assertNull(runBlocking { sessions.current() })
        assertEquals("what was cached about the account goes with the session", 1, sessionsEnded)
    }

    @Test
    fun `a second connect while a check is in flight is ignored, whoever asks for it`() {
        val vm = viewModel()
        vm.await<UiState.EnterServer>()
        val held = jellyfin.hold("/System/Info/Public")
        vm.onAddress(jellyfin.baseUrl)
        vm.connect()
        held.awaitArrival()
        vm.connect()
        vm.chooseServer(DiscoveredServer("cabin", "Cabin", "https://jellyfin.example.com"))
        vm.tryDemo()
        assertEquals(jellyfin.baseUrl, (vm.state.value as UiState.EnterServer).address)
        held.release()
        vm.await<UiState.SignIn>()
        assertEquals("only the first check asked the server", 1, jellyfin.hits("/System/Info/Public"))
        assertNull(runBlocking { sessions.current() })
    }

    @Test
    fun `a refresh that cannot reach the server says so`() {
        runBlocking { sessions.save(jellyfin.session()) }
        val vm = viewModel()
        vm.await<UiState.SignedIn> { it.titles != null }
        jellyfin.status["/Items"] = 500
        vm.refresh()
        assertTrue(vm.await<UiState.SignedIn> { it.refreshFailed }.refreshFailed)
    }

    @Test
    fun `a server that does not answer takes the home's place until a retry reaches it`() {
        runBlocking { sessions.save(jellyfin.session()) }
        val vm = viewModel()
        assertEquals(ConnectionHealth.Healthy, vm.await<UiState.SignedIn> { it.titles != null }.health)
        jellyfin.dropped += "/Items"
        vm.refresh()
        // The live calls may show the problem a moment before the read that failed ends, and a read in flight swallows a new one.
        assertEquals(ConnectionHealth.Unreachable, vm.await<UiState.SignedIn> { it.health.isProblem && !it.refreshing }.health)
        jellyfin.dropped -= "/Items"
        vm.refresh()
        assertEquals(
            ConnectionHealth.Healthy,
            vm.await<UiState.SignedIn> { it.health == ConnectionHealth.Healthy && !it.refreshing }.health,
        )
    }

    @Test
    fun `a run of calls without an answer turns a fine home into the server's problem, and an answer clears it`() {
        runBlocking { sessions.save(jellyfin.session()) }
        val vm = viewModel()
        assertEquals(ConnectionHealth.Healthy, vm.await<UiState.SignedIn> { it.titles != null }.health)
        connection.monitor.report(SAVED_SERVER, CallResult.TransportFailure())
        // One call without an answer is a blip, as binge-seerr counts it.
        assertEquals(ConnectionHealth.Healthy, vm.await<UiState.SignedIn>().health)
        connection.monitor.report(SAVED_SERVER, CallResult.TransportFailure())
        assertEquals(ConnectionHealth.Unreachable, vm.await<UiState.SignedIn> { it.health.isProblem }.health)
        connection.monitor.report(SAVED_SERVER, CallResult.Answered(200))
        assertEquals(ConnectionHealth.Healthy, vm.await<UiState.SignedIn> { !it.health.isProblem }.health)
    }

    @Test
    fun `the live calls leave a server that answered an error, and a start's first read, to the read`() {
        runBlocking { sessions.save(jellyfin.session()) }
        val vm = viewModel()
        vm.await<UiState.SignedIn> { it.titles != null }
        jellyfin.status["/Items"] = 500
        vm.refresh()
        assertEquals(ConnectionHealth.CouldNotLoad, vm.await<UiState.SignedIn> { it.health.isProblem }.health)
        // An answered call elsewhere does not clear "couldn't load", and calls without an answer do not rename it.
        connection.monitor.report(SAVED_SERVER, CallResult.Answered(200))
        assertEquals(ConnectionHealth.CouldNotLoad, vm.await<UiState.SignedIn> { !it.refreshing }.health)
        connection.monitor.report(SAVED_SERVER, CallResult.TransportFailure())
        connection.monitor.report(SAVED_SERVER, CallResult.TransportFailure())
        assertEquals(ConnectionHealth.CouldNotLoad, vm.await<UiState.SignedIn> { !it.refreshing }.health)
    }

    @Test
    fun `a server that answers an error, or something that is not Jellyfin, could not be loaded`() {
        runBlocking { sessions.save(jellyfin.session()) }
        val vm = viewModel()
        vm.await<UiState.SignedIn> { it.titles != null }
        jellyfin.status["/Items"] = 500
        vm.refresh()
        assertEquals(ConnectionHealth.CouldNotLoad, vm.await<UiState.SignedIn> { it.health.isProblem }.health)
        jellyfin.status.remove("/Items")
        jellyfin.notJson += "/Items"
        vm.refresh()
        assertEquals(ConnectionHealth.CouldNotLoad, vm.await<UiState.SignedIn> { it.health.isProblem && !it.refreshing }.health)
    }

    @Test
    fun `editing a server that cannot be reached goes back to the server step with its address, and keeps the session`() {
        runBlocking { sessions.save(jellyfin.session()) }
        val vm = viewModel()
        vm.await<UiState.SignedIn> { it.titles != null }
        jellyfin.dropped += "/Items"
        vm.refresh()
        vm.await<UiState.SignedIn> { it.health.isProblem }
        vm.changeServer()
        assertEquals(jellyfin.baseUrl, vm.await<UiState.EnterServer>().address)
        assertEquals(jellyfin.baseUrl, runBlocking { sessions.current() }?.baseUrl)
    }

    @Test
    fun `a token the server refuses sends the user back to sign in`() {
        runBlocking { sessions.save(jellyfin.session()) }
        val vm = viewModel()
        vm.await<UiState.SignedIn> { it.titles != null }
        jellyfin.status["/Items"] = 401
        vm.refresh()
        assertEquals(jellyfin.baseUrl, vm.await<UiState.EnterServer>().address)
        assertNull(runBlocking { sessions.current() })
        assertEquals(1, sessionsEnded)
    }

    @Test
    fun `a token a console page found refused sends the user to sign in again`() {
        runBlocking { sessions.save(jellyfin.session()) }
        val vm = viewModel()
        vm.await<UiState.SignedIn> { it.titles != null }
        connection.reportRefused()
        assertEquals(jellyfin.baseUrl, vm.await<UiState.EnterServer>().address)
        assertNull(runBlocking { sessions.current() })
    }

    @Test
    fun `a refresh answered with something that is not JSON fails without crashing`() {
        runBlocking { sessions.save(jellyfin.session()) }
        val vm = viewModel()
        vm.await<UiState.SignedIn> { it.titles != null }
        jellyfin.notJson += "/Items"
        vm.refresh()
        assertTrue(vm.await<UiState.SignedIn> { it.refreshFailed }.refreshFailed)
    }

    @Test
    fun `a server address that answers a web page is not Jellyfin, and a sign-in that does is a generic failure`() {
        val vm = viewModel()
        vm.await<UiState.EnterServer>()
        jellyfin.notJson += "/System/Info/Public"
        vm.onAddress(jellyfin.baseUrl)
        vm.connect()
        assertEquals(SetupError.NotJellyfin, vm.await<UiState.EnterServer> { it.error != null }.error)

        jellyfin.notJson.clear()
        vm.connect()
        vm.await<UiState.SignIn>()
        jellyfin.notJson += "/Users/AuthenticateByName"
        vm.onUsername("Claude")
        vm.onPassword("right")
        vm.signInWithPassword()
        assertEquals(SetupError.Generic, vm.await<UiState.SignIn> { it.passwordError != null }.passwordError)
    }

    @Test
    fun `each sign-in method keeps its own failure, so switching never shows the other's`() {
        val vm = viewModel()
        vm.toSignIn()
        jellyfin.status["/Users/AuthenticateByName"] = 500
        vm.onUsername("Claude")
        vm.onPassword("right")
        vm.signInWithPassword()
        vm.await<UiState.SignIn> { it.passwordError == SetupError.Generic }
        // Switching to Quick Connect asks for a code rather than showing the password's failure.
        vm.startQuickConnect()
        val code = vm.await<UiState.SignIn> { it.quickConnectCode != null }
        assertNull(code.quickConnectError)

        // A Quick Connect failure stays with Quick Connect, and leaving it takes the failure along.
        vm.cancelQuickConnect()
        jellyfin.quickConnectEnabled = false
        vm.startQuickConnect()
        vm.await<UiState.SignIn> { it.quickConnectError == SetupError.QuickConnectOff }
        vm.cancelQuickConnect()
        val back = vm.await<UiState.SignIn> { it.quickConnectError == null }
        assertEquals(false, back.gettingCode)
        // Typing clears the password's own failure.
        vm.onPassword("again")
        assertNull((vm.state.value as UiState.SignIn).passwordError)
    }

    @Test
    fun `retrying Quick Connect drops the last failure at once and shows it's getting a code`() {
        val vm = viewModel()
        vm.toSignIn()
        jellyfin.quickConnectEnabled = false
        vm.startQuickConnect()
        vm.await<UiState.SignIn> { it.quickConnectError != null }
        jellyfin.slow += "/QuickConnect/Enabled"
        vm.startQuickConnect()
        val retrying = vm.state.value as UiState.SignIn
        assertNull(retrying.quickConnectError)
        assertTrue(retrying.gettingCode)
        vm.cancelQuickConnect()
    }

    @Test
    fun `an address that redirects signs in at, and saves, the server the redirect leads to`() {
        // A proxy in front of the server that answers every request with a 301 to the server itself,
        // as an http-to-https redirect does. OkHttp would turn the sign-in POST into a GET through it.
        val proxy = mockwebserver3.MockWebServer()
        proxy.dispatcher =
            object : mockwebserver3.Dispatcher() {
                override fun dispatch(request: mockwebserver3.RecordedRequest): mockwebserver3.MockResponse =
                    mockwebserver3.MockResponse
                        .Builder()
                        .code(301)
                        .addHeader("Location", jellyfin.baseUrl + request.url.encodedPath)
                        .build()
            }
        proxy.start()
        try {
            val vm = viewModel()
            vm.await<UiState.EnterServer>()
            vm.onAddress(proxy.url("/").toString())
            vm.connect()
            assertEquals(jellyfin.baseUrl, vm.await<UiState.SignIn>().baseUrl)

            vm.onUsername("Claude")
            vm.onPassword("right")
            vm.signInWithPassword()
            vm.await<UiState.SignedIn>()
            assertEquals(jellyfin.baseUrl, runBlocking { sessions.current() }?.baseUrl)
            // Only the first request went through the redirect.
            assertEquals(1, proxy.requestCount)
        } finally {
            proxy.close()
        }
    }

    @Test
    fun `servers found on the network are listed, and choosing one connects to it`() {
        found = listOf(DiscoveredServer(id = "server-1", name = "truenas", address = jellyfin.baseUrl))
        val vm = viewModel()
        vm.await<UiState.EnterServer>()
        vm.discoverServers()
        assertEquals(found, vm.await<UiState.EnterServer> { it.discovered.isNotEmpty() }.discovered)
        vm.onAddress(found.single().address)
        vm.connect()
        assertEquals(jellyfin.baseUrl, vm.await<UiState.SignIn>().baseUrl)
    }

    @Test
    fun `nothing found leaves the typed address as the way in, with no error`() {
        val vm = viewModel()
        vm.await<UiState.EnterServer>()
        vm.discoverServers()
        val state = vm.await<UiState.EnterServer> { !it.discovering }
        assertTrue(state.discovered.isEmpty())
        assertNull(state.error)
    }

    @Test
    fun `a failed check that outlasts the search keeps what the search found`() {
        val gate = CompletableDeferred<Unit>()
        val server = listOf(DiscoveredServer(id = "server-1", name = "truenas", address = jellyfin.baseUrl))
        // Accepts the connection and never answers, so the check fails by timeout after the search is done.
        ServerSocket(0).use { silent ->
            val slow = OkHttpClient.Builder().callTimeout(500, TimeUnit.MILLISECONDS).build()
            val vm =
                track(
                    JellyfinViewModel(
                        sessions,
                        Connection(slow, sessions, "Jellyfin Companion", "Test", "1.0", Dispatchers.IO, DiscardLogger).also {
                            extraConnections +=
                                it
                        },
                        index,
                        dispatcher = Dispatchers.IO,
                        cipher = PlainCipher.forLibrary(),
                        discover = {
                            gate.await()
                            server
                        },
                        quickConnectPollMillis = 10L,
                    ),
                )
            vm.await<UiState.EnterServer>()
            vm.discoverServers()
            vm.onAddress("http://127.0.0.1:${silent.localPort}")
            vm.connect()
            vm.await<UiState.EnterServer> { it.checking }
            gate.complete(Unit)
            val state = vm.await<UiState.EnterServer> { it.error == SetupError.Unreachable }
            assertTrue(!state.discovering)
            assertEquals(server, state.discovered)
        }
    }

    @Test
    fun `an administrator is marked as one once the library is read`() {
        jellyfin.administrator = true
        runBlocking { sessions.save(jellyfin.session()) }
        val vm = viewModel()
        assertTrue(vm.await<UiState.SignedIn> { it.isAdministrator }.isAdministrator)
    }

    @Test
    fun `the refresh reads what the server and the user allow`() {
        jellyfin.quickConnectEnabled = false
        jellyfin.policy = ""","EnableUserPreferenceAccess":false"""
        runBlocking { sessions.save(jellyfin.session()) }
        val vm = viewModel()
        val state = vm.await<UiState.SignedIn> { it.account.isRead }
        assertFalse(state.profile.quickConnect)
        assertFalse(state.account.canEditPreferences)
    }

    @Test
    fun `a refresh asked for while one is running starts no second read`() {
        runBlocking { sessions.save(jellyfin.session()) }
        val vm = viewModel()
        vm.await<UiState.SignedIn> { it.titles != null && !it.refreshing }

        fun gateReads() = jellyfin.requests.count { it.url.encodedPath == "/QuickConnect/Enabled" }
        val before = gateReads()
        // Held at the gates, the first refresh is certainly still running when the others are asked for.
        val held = jellyfin.hold("/QuickConnect/Enabled")
        vm.refresh()
        held.awaitArrival()
        assertTrue(vm.await<UiState.SignedIn>().refreshing)
        vm.refresh()
        vm.refresh()
        held.release()
        vm.await<UiState.SignedIn> { !it.refreshing }
        assertEquals(before + 1, gateReads())
    }

    @Test
    fun `the home is healthy once the server answers, before the library index is built`() {
        runBlocking { sessions.save(jellyfin.session()) }
        // The index's pages are the /Items reads that ask for titles with a TMDB id; hold the first, so the index cannot finish.
        val held = jellyfin.hold("/Items", query = "hasTmdbId" to "true")
        val vm = viewModel()
        held.awaitArrival()
        val state = vm.await<UiState.SignedIn> { it.health == ConnectionHealth.Healthy }
        assertTrue(state.refreshing)
        held.release()
        vm.await<UiState.SignedIn> { !it.refreshing && it.titles != null }
    }

    @Test
    fun `a gates-only refresh picks up a change without reading the library again`() {
        runBlocking { sessions.save(jellyfin.session()) }
        val vm = viewModel()
        vm.await<UiState.SignedIn> { it.account.isRead && !it.refreshing && it.card.tmdb != null }

        // The library index reads items that have a TMDB id. The server card's coverage count also reads /Items, without that
        // filter or with false, and its reads can land at any moment, so they are not library reads.
        fun libraryReads() = jellyfin.requests.count { it.url.encodedPath == "/Items" && it.url.queryParameter("hasTmdbId") == "true" }
        val before = libraryReads()
        jellyfin.quickConnectEnabled = false
        vm.refresh(gatesOnly = true)
        vm.await<UiState.SignedIn> { !it.profile.quickConnect }
        assertEquals(before, libraryReads())
    }
}

private const val PUBLIC_USERS =
    """[{"Name":"Kid","Id":"u-kid","HasPassword":false},{"Name":"Mum","Id":"u-mum","HasPassword":true}]"""

/** Nothing listens here, so a connection is refused at once. */
private const val DEAD_PORT = "http://127.0.0.1:1/"

/** What Android's TLS stack throws when the server asks for a client certificate and none is sent. */
private const val CERTIFICATE_REQUIRED_ALERT =
    "Read error: ssl=0x0: error:1000045c:SSL routines:OPENSSL_internal:TLSV1_ALERT_CERTIFICATE_REQUIRED"

/** How long teardown waits for a view model's cancelled work to end: a call on the fake server returns at once. */
private const val TEARDOWN_MILLIS = 10_000L
