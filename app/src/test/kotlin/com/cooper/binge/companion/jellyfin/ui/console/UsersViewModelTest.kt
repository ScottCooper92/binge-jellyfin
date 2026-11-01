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

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class UsersViewModelTest {
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

    private fun UsersViewModel.settled(check: (UsersUiState) -> Boolean = { it !is UsersUiState.Loading }) =
        runBlocking { withTimeout(TIMEOUT_MILLIS) { state.first(check) } }

    private fun UsersViewModel.ready(check: (UsersUiState.Ready) -> Boolean = { true }) =
        settled { it is UsersUiState.Ready && check(it) } as UsersUiState.Ready

    private fun policy(id: String) =
        jellyfin.serverUsers
            .getValue(id)
            .getValue("Policy")
            .jsonObject

    private fun UsersViewModel.setLibrary(
        id: String,
        on: Boolean,
    ) = editList("EnabledFolders", memberChange(id, on))

    private fun UsersViewModel.setAllLibraries(on: Boolean) = setPolicy("EnableAllFolders", kotlinx.serialization.json.JsonPrimitive(on))

    private fun saves() = jellyfin.requests.count { it.url.encodedPath.endsWith("/Policy") }

    private fun UsersViewModel.openSam() = ready().users.first { it.name == "Sam" }.also(::open)

    @Test
    fun `reads the users by name and the libraries, knowing which is me`() {
        val ready = made(UsersViewModel(connection)).ready()
        assertEquals(listOf("Claude", "Sam"), ready.users.map { it.name })
        assertEquals("user-1", ready.me)
        assertEquals(2, ready.libraries.size)
        val sam = ready.users[1]
        assertFalse(sam.allLibraries)
        assertEquals(setOf("lib-movies"), sam.libraries)
    }

    @Test
    fun `each switch saves the whole policy with that one change, as it's made`() {
        val vm = made(UsersViewModel(connection))
        vm.openSam()
        vm.setAdministrator(true)
        vm.ready { it.openUser?.administrator == true }
        runBlocking { withTimeout(TIMEOUT_MILLIS) { while (saves() < 1) kotlinx.coroutines.delay(POLL_MILLIS) } }
        vm.setLibrary("lib-shows", true)
        vm.setCanSignIn(false)
        runBlocking { withTimeout(TIMEOUT_MILLIS) { while (saves() < 3) kotlinx.coroutines.delay(POLL_MILLIS) } }
        val saved = policy("user-2")
        assertEquals("true", saved.getValue("IsAdministrator").jsonPrimitive.content)
        assertEquals("true", saved.getValue("IsDisabled").jsonPrimitive.content)
        assertEquals(
            listOf(
                "lib-movies",
                "lib-shows",
            ),
            saved.getValue("EnabledFolders").toString().removeSurrounding("[", "]").split(",").map {
                it.trim('"')
            },
        )
        // What the page doesn't show goes back as it was.
        assertEquals("3", saved.getValue("MaxActiveSessions").jsonPrimitive.content)
        vm.setLibrary("lib-movies", false)
        vm.setAllLibraries(true)
        runBlocking { withTimeout(TIMEOUT_MILLIS) { while (saves() < 5) kotlinx.coroutines.delay(POLL_MILLIS) } }
        assertEquals("true", policy("user-2").getValue("EnableAllFolders").jsonPrimitive.content)
        assertEquals("[\"lib-shows\"]", policy("user-2").getValue("EnabledFolders").toString())
    }

    @Test
    fun `a change the server refuses goes back, and says so`() {
        val vm = made(UsersViewModel(connection))
        vm.openSam()
        jellyfin.status["/Users/user-2/Policy"] = 403
        vm.setAdministrator(true)
        val back = vm.ready { it.actionFailed != null }
        assertEquals(ConsoleFailure.Refused, back.actionFailed)
        assertFalse(back.openUser!!.administrator)
    }

    @Test
    fun `two library switches made before the first answer are both put back when both are refused`() {
        val vm = made(UsersViewModel(connection))
        vm.openSam()
        jellyfin.status["/Users/user-2/Policy"] = 503
        vm.setAllLibraries(false)
        vm.setLibrary("lib-shows", true)
        vm.setLibrary("lib-extra", true)
        val back = vm.ready { it.openUser?.libraries == setOf("lib-movies") && it.actionFailed != null }
        assertEquals(setOf("lib-movies"), back.openUser!!.libraries)
        assertEquals(
            setOf("lib-movies"),
            policy("user-2").getValue("EnabledFolders").let { folders ->
                (folders as kotlinx.serialization.json.JsonArray).map { it.jsonPrimitive.content }.toSet()
            },
        )
    }

    @Test
    fun `my own sign-in and administrator switches can't be changed, nor can I be deleted`() {
        val vm = made(UsersViewModel(connection))
        vm.open(vm.ready().users.first())
        vm.setAdministrator(false)
        vm.setCanSignIn(false)
        vm.show(UserDialog.Delete)
        vm.delete()
        assertEquals(0, saves())
        assertEquals(0, jellyfin.requests.count { it.method == "DELETE" })
        assertTrue("user-1" in jellyfin.serverUsers)
    }

    @Test
    fun `a new user is created, added in order and opened`() {
        val vm = made(UsersViewModel(connection))
        vm.ready()
        vm.show(UserDialog.Create)
        vm.create("  Ana ", "secret")
        val made = vm.ready { it.done == UserDone.Created }
        assertEquals(listOf("Ana", "Claude", "Sam"), made.users.map { it.name })
        assertEquals("Ana", made.openUser?.name)
        assertNull(made.dialog)
        assertEquals(
            "secret",
            jellyfin
                .lastBody("/Users/New")
                .getValue("Password")
                .jsonPrimitive.content,
        )
        // A blank name isn't sent.
        vm.create(" ", "x")
        assertEquals(1, jellyfin.hits("/Users/New"))
    }

    @Test
    fun `an administrator sets a password without the old one, and deletes a user after a confirm`() {
        val vm = made(UsersViewModel(connection))
        vm.openSam()
        vm.show(UserDialog.Password)
        vm.setPassword("new-one")
        vm.ready { it.done == UserDone.PasswordSet }
        assertEquals("new-one", jellyfin.adminPasswords["user-2"])
        assertTrue(vm.ready().openUser?.hasPassword == true)
        vm.show(UserDialog.Delete)
        vm.delete()
        val gone = vm.ready { it.done == UserDone.Deleted }
        assertEquals(listOf("Claude"), gone.users.map { it.name })
        assertNull(gone.open)
    }

    @Test
    fun `the signed-in user's own password is left to the Password page`() {
        val vm = made(UsersViewModel(connection))
        val me = vm.ready().users.first { it.id == "user-1" }
        vm.open(me)
        vm.setPassword("mine")
        assertEquals(0, jellyfin.hits("/Users/Password"))
        assertNull(vm.ready().done)
    }

    @Test
    fun `a create that fails says so, and a page that can't read offers Try again`() {
        val vm = made(UsersViewModel(connection))
        vm.ready()
        jellyfin.status["/Users/New"] = 400
        vm.create("Ana", "")
        assertEquals(ConsoleFailure.Refused, vm.ready { it.actionFailed != null }.actionFailed)
        jellyfin.status["/Users"] = 403
        vm.load()
        assertEquals(UsersUiState.Failed(ConsoleFailure.Refused), vm.settled())
        jellyfin.status.remove("/Users")
        vm.load()
        assertEquals(2, vm.ready().users.size)
    }

    private companion object {
        const val TIMEOUT_MILLIS = 10_000L
        const val POLL_MILLIS = 10L
    }
}
