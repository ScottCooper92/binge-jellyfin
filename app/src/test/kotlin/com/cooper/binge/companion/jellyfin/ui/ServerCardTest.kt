package com.cooper.binge.companion.jellyfin.ui

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.cooper.binge.companion.jellyfin.FakeJellyfin
import com.cooper.binge.companion.jellyfin.server.Connection
import com.cooper.binge.companion.jellyfin.server.LibraryIndex
import com.cooper.binge.companion.jellyfin.session
import com.cooper.binge.companion.jellyfin.testSessionStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
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

/** What the home's server card reads, and who may read it, against a fake server. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class ServerCardTest {
    private val jellyfin = FakeJellyfin()
    private val sessions = testSessionStore()
    private val connection = Connection(OkHttpClient(), sessions, "Jellyfin Companion", "Test", "1.0", Dispatchers.IO)
    private val stores = mutableListOf<ViewModelStore>()

    @Before
    fun main() {
        Dispatchers.setMain(Dispatchers.Unconfined)
        runBlocking { sessions.save(jellyfin.session()) }
    }

    @After
    fun close() {
        stores.forEach { it.clear() }
        jellyfin.close()
        Dispatchers.resetMain()
    }

    private fun viewModel(): JellyfinViewModel {
        val vm = JellyfinViewModel(sessions, connection, LibraryIndex(), discover = { emptyList() })
        val store = ViewModelStore().also { stores += it }
        return ViewModelProvider(store, viewModelFactory { initializer { vm } })[JellyfinViewModel::class.java]
    }

    private fun JellyfinViewModel.signedIn(matches: (UiState.SignedIn) -> Boolean): UiState.SignedIn =
        runBlocking { withTimeout(TIMEOUT_MILLIS) { state.first { it is UiState.SignedIn && matches(it) } as UiState.SignedIn } }

    /** The card once every figure the viewer may read has come in. */
    private fun JellyfinViewModel.read(administrator: Boolean) =
        signedIn { s ->
            val c = s.card
            c.playing != null && c.movies != null && (!administrator || c.users != null && c.connected)
        }.card

    @Test
    fun `an administrator's card has the flags, the users, what is playing and the library`() {
        jellyfin.administrator = true
        jellyfin.systemInfo = INFO_WITH_FLAGS
        val card = viewModel().read(administrator = true)
        assertTrue(card.connected)
        assertTrue(card.updateAvailable)
        assertTrue(card.restartPending)
        assertTrue(card.shuttingDown)
        assertEquals(1, card.playing)
        assertEquals(jellyfin.serverUsers.size, card.users)
        assertEquals(120, card.movies)
        assertEquals(14, card.series)
    }

    @Test
    fun `an older server that sends no flags gets no chip, and no error`() {
        jellyfin.administrator = true
        jellyfin.systemInfo = """{"ServerName":"old","Version":"10.8.13","LocalAddress":"http://192.168.1.10:8096"}"""
        val card = viewModel().read(administrator = true)
        assertTrue(card.connected)
        assertFalse(card.updateAvailable)
        assertFalse(card.restartPending)
        assertFalse(card.shuttingDown)
    }

    @Test
    fun `a user who is not an administrator reads no flags and no user count`() {
        jellyfin.administrator = false
        val card = viewModel().read(administrator = false)
        assertTrue(card.connected)
        assertNull(card.users)
        assertFalse(card.updateAvailable)
        assertEquals(0, jellyfin.hits("/System/Info"))
        assertEquals(0, jellyfin.hits("/Users"))
        assertEquals(120, card.movies)
    }

    @Test
    fun `a library read that fails leaves its figures empty and the rest in place`() {
        jellyfin.administrator = true
        jellyfin.status["/Items/Counts"] = 500
        val vm = viewModel()
        val card = vm.signedIn { it.card.playing != null && it.card.users != null }.card
        runBlocking { delay(SETTLE_MILLIS) }
        assertNull(card.movies)
        assertNull(card.series)
        assertNull(vm.signedIn { true }.card.movies)
        assertTrue(card.connected)
    }

    @Test
    fun `reopening the home inside the cache window does not ask again, and a refresh does`() {
        jellyfin.administrator = true
        val vm = viewModel()
        vm.read(administrator = true)
        vm.refresh(gatesOnly = true)
        runBlocking { delay(SETTLE_MILLIS) }
        assertEquals(1, jellyfin.hits("/Items/Counts"))
        vm.refresh()
        runBlocking { withTimeout(TIMEOUT_MILLIS) { while (jellyfin.hits("/Items/Counts") < 2) delay(POLL_MILLIS) } }
        assertEquals(2, jellyfin.hits("/Items/Counts"))
    }

    private companion object {
        const val TIMEOUT_MILLIS = 10_000L
        const val SETTLE_MILLIS = 400L
        const val POLL_MILLIS = 20L
        const val INFO_WITH_FLAGS =
            """{"ServerName":"truenas","Version":"10.11.6","LocalAddress":"http://192.168.1.10:8096","HasUpdateAvailable":true,""" +
                """"HasPendingRestart":true,"IsShuttingDown":true,"CanSelfRestart":true}"""
    }
}
