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
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
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

    private fun awaitPolicy(holds: (kotlinx.serialization.json.JsonObject) -> Boolean) =
        runBlocking { withTimeout(TIMEOUT_MILLIS) { while (!holds(policy("user-2"))) kotlinx.coroutines.delay(POLL_MILLIS) } }

    private fun saves() = jellyfin.requests.count { it.url.encodedPath.endsWith("/Policy") }

    private fun UsersViewModel.openSam() = ready().users.first { it.name == "Sam" }.also { select(it.id) }

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
    fun `a pull reads the users and libraries again in place, and a pull that fails keeps them and says so`() {
        val vm = made(UsersViewModel(connection))
        assertEquals(2, vm.ready().users.size)
        val sam = jellyfin.serverUsers.getValue("user-2")
        jellyfin.serverUsers["user-3"] =
            kotlinx.serialization.json.JsonObject(
                sam +
                    mapOf(
                        "Id" to kotlinx.serialization.json.JsonPrimitive("user-3"),
                        "Name" to kotlinx.serialization.json.JsonPrimitive("Tess"),
                    ),
            )
        vm.refresh()
        val fresh = vm.ready { it.users.size == 3 && !it.refreshing }
        assertNull(fresh.refreshFailed)
        jellyfin.status["/Users"] = 500
        vm.refresh()
        val failed = vm.ready { it.refreshFailed != null && !it.refreshing }
        assertEquals(3, failed.users.size)
        assertEquals(ConsoleFailure.Refused, failed.refreshFailed)
    }

    @Test
    fun `a change saved while a pull is in flight is not undone by the older read`() {
        val vm = made(UsersViewModel(connection))
        vm.openSam()
        val libraries = jellyfin.hold("/Library/VirtualFolders")
        val before = jellyfin.requests.count { it.url.encodedPath == "/Users" }
        vm.refresh()
        libraries.awaitArrival()
        // The users read has gone out and come back with the old policy; the libraries hold the pair.
        runBlocking {
            withTimeout(TIMEOUT_MILLIS) {
                while (jellyfin.requests.count { it.url.encodedPath == "/Users" } <=
                    before
                ) {
                    delay(POLL_MILLIS)
                }
            }
        }
        vm.setCanSignIn(false)
        runBlocking { withTimeout(TIMEOUT_MILLIS) { while (saves() < 1) delay(POLL_MILLIS) } }
        vm.ready { it.openUser?.disabled == true }
        libraries.release()
        val after = vm.ready { !it.refreshing }
        assertTrue(after.openUser!!.disabled)
        vm.setAdministrator(true)
        awaitPolicy { it.getValue("IsAdministrator").jsonPrimitive.content == "true" }
        assertEquals("true", policy("user-2").getValue("IsDisabled").jsonPrimitive.content)
        assertEquals("true", policy("user-2").getValue("IsAdministrator").jsonPrimitive.content)
    }

    @Test
    fun `a library the server has not given an id yet is not offered`() {
        jellyfin.libraries = JsonArray(jellyfin.libraries + buildJsonObject { put("Name", "New") })
        val ready = made(UsersViewModel(connection)).ready()
        assertEquals(2, ready.libraries.size)
        assertTrue(ready.libraries.all { it.id.isNotEmpty() })
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
        awaitPolicy {
            it.getValue("IsDisabled").jsonPrimitive.content == "true" &&
                it.getValue("EnabledFolders").toString().contains("lib-shows")
        }
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
        awaitPolicy { it.getValue("EnableAllFolders").jsonPrimitive.content == "true" }
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
        vm.select(
            vm
                .ready()
                .users
                .first()
                .id,
        )
        vm.setAdministrator(false)
        vm.setCanSignIn(false)
        vm.show(UserDialog.Delete)
        vm.delete()
        assertEquals(0, saves())
        assertEquals(0, jellyfin.requests.count { it.method == "DELETE" })
        assertTrue("user-1" in jellyfin.serverUsers)
    }

    @Test
    fun `the user just created is opened once, so Back to the list does not open them again`() {
        val vm = made(UsersViewModel(connection))
        vm.ready()
        vm.show(UserDialog.Create)
        vm.create("Ana", "secret")
        val made = vm.ready { it.done == UserDone.Created }
        assertEquals(made.open, made.created)
        vm.createdOpened()
        val after = vm.ready()
        assertNull(after.created)
        assertNull(after.done)
        assertEquals(made.open, after.open)
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
    fun `a password the server refuses is said in the form, which stays open`() {
        val vm = made(UsersViewModel(connection))
        vm.openSam()
        vm.show(UserDialog.Password)
        jellyfin.status["/Users/Password"] = 500
        vm.setPassword("new-one")
        val failed = vm.ready { it.formFailure != null }
        assertEquals(ConsoleFailure.Refused, failed.formFailure)
        assertEquals(UserDialog.Password, failed.dialog)
        assertNull(failed.done)
    }

    @Test
    fun `a second password while the first is on the wire is ignored`() {
        val vm = made(UsersViewModel(connection))
        vm.openSam()
        vm.show(UserDialog.Password)
        jellyfin.slow += "/Users/Password"
        vm.setPassword("first")
        vm.ready { it.busy }
        // busy is set before the request leaves; wait until the server has it.
        runBlocking {
            withTimeout(TIMEOUT_MILLIS) {
                while (jellyfin.hits("/Users/Password") == 0) delay(10)
            }
        }
        vm.setPassword("second")
        vm.create("Ana", "")
        assertEquals(1, jellyfin.hits("/Users/Password"))
        assertEquals(0, jellyfin.hits("/Users/New"))
    }

    @Test
    fun `the signed-in user's own password is left to the Password page`() {
        val vm = made(UsersViewModel(connection))
        val me = vm.ready().users.first { it.id == "user-1" }
        vm.select(me.id)
        vm.setPassword("mine")
        assertEquals(0, jellyfin.hits("/Users/Password"))
        assertNull(vm.ready().done)
    }

    @Test
    fun `a create that fails says so in the form, which stays open, and a page that can't read offers Try again`() {
        val vm = made(UsersViewModel(connection))
        vm.ready()
        vm.show(UserDialog.Create)
        jellyfin.status["/Users/New"] = 400
        vm.create("Ana", "")
        val failed = vm.ready { it.formFailure != null }
        assertEquals(ConsoleFailure.Refused, failed.formFailure)
        assertEquals(UserDialog.Create, failed.dialog)
        assertNull(failed.actionFailed)
        assertFalse(failed.busy)
        jellyfin.status["/Users"] = 403
        vm.load()
        assertEquals(UsersUiState.Failed(ConsoleFailure.Refused), vm.settled())
        jellyfin.status.remove("/Users")
        vm.load()
        assertEquals(2, vm.ready().users.size)
    }

    @Test
    fun `a password that fails after its form was left is said on the page, and the form is gone`() {
        val vm = made(UsersViewModel(connection))
        vm.openSam()
        vm.show(UserDialog.Password)
        val held = jellyfin.hold("/Users/Password", method = "POST")
        vm.setPassword("new-one")
        held.awaitArrival()
        // The form is dismissed while the call is still running, then the call fails.
        vm.show(null)
        held.refuse(500)
        val failed = vm.ready { it.actionFailed != null }
        assertEquals(ConsoleFailure.Refused, failed.actionFailed)
        assertNull(failed.formFailure)
        assertNull(failed.dialog)
        assertFalse(failed.busy)
    }

    @Test
    fun `a password that fails after another user's Password form opened is said on the page, not in that form`() {
        // A third user, beside Claude (who is signed in) and Sam.
        val sam2 = jellyfin.serverUsers.getValue("user-2")
        jellyfin.serverUsers["user-3"] =
            kotlinx.serialization.json.JsonObject(
                sam2 +
                    mapOf(
                        "Id" to kotlinx.serialization.json.JsonPrimitive("user-3"),
                        "Name" to kotlinx.serialization.json.JsonPrimitive("Tess"),
                    ),
            )
        val vm = made(UsersViewModel(connection))
        val sam = vm.openSam()
        vm.show(UserDialog.Password)
        val held = jellyfin.hold("/Users/Password", method = "POST")
        vm.setPassword("new-one")
        held.awaitArrival()
        // Another user is opened and their own Password form shown, then Sam's call fails.
        val other = vm.ready().users.first { it.id != sam.id && it.id != vm.ready().me }
        vm.select(other.id)
        vm.show(UserDialog.Password)
        held.refuse(500)
        val failed = vm.ready { it.actionFailed != null }
        assertEquals(ConsoleFailure.Refused, failed.actionFailed)
        assertNull(failed.formFailure)
        assertEquals(UserDialog.Password, failed.dialog)
        assertEquals(other.id, failed.open)
    }

    @Test
    fun `a create that fails after its form was left is said on the page`() {
        val vm = made(UsersViewModel(connection))
        vm.ready()
        vm.show(UserDialog.Create)
        val held = jellyfin.hold("/Users/New", method = "POST")
        vm.create("Ana", "")
        held.awaitArrival()
        vm.show(null)
        held.cut()
        val failed = vm.ready { it.actionFailed != null }
        assertEquals(ConsoleFailure.Unreachable, failed.actionFailed)
        assertNull(failed.formFailure)
        assertNull(failed.dialog)
    }

    private companion object {
        const val TIMEOUT_MILLIS = 10_000L
        const val POLL_MILLIS = 10L
    }
}
