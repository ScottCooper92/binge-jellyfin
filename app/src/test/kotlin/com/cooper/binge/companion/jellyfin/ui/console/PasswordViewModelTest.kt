package com.cooper.binge.companion.jellyfin.ui.console

import com.cooper.binge.companion.jellyfin.FakeJellyfin
import com.cooper.binge.companion.jellyfin.server.Connection
import com.cooper.binge.companion.jellyfin.session
import com.cooper.binge.companion.jellyfin.testSessionStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class PasswordViewModelTest {
    private val jellyfin = FakeJellyfin()
    private val sessions = testSessionStore()
    private val connection = Connection(OkHttpClient(), sessions, "Binge Jellyfin", "Test", "1.0", Dispatchers.IO)

    @Before
    fun main() {
        Dispatchers.setMain(Dispatchers.Unconfined)
        runBlocking { sessions.save(jellyfin.session()) }
    }

    @After
    fun close() {
        Dispatchers.resetMain()
        jellyfin.close()
    }

    private fun PasswordViewModel.fill(
        current: String,
        new: String,
        confirm: String = new,
    ): PasswordViewModel {
        onCurrent(current)
        onNew(new)
        onConfirm(confirm)
        return this
    }

    private fun PasswordViewModel.resultOfChange(): PasswordUiState {
        change()
        return runBlocking { withTimeout(TIMEOUT_MILLIS) { state.first { it.result != null } } }
    }

    @Test
    fun `a change works, for this user, and empties the form`() {
        val after = PasswordViewModel(connection).fill("old-secret", "new-secret").resultOfChange()
        assertEquals(PasswordUiState(result = PasswordResult.Changed), after)
        assertEquals("new-secret", jellyfin.password)
        assertEquals(
            "user-1",
            jellyfin.requests
                .last { it.url.encodedPath == "/Users/Password" }
                .url
                .queryParameter("userId"),
        )
    }

    @Test
    fun `a wrong current password is refused in place, the form kept`() {
        val after = PasswordViewModel(connection).fill("guess", "new-secret").resultOfChange()
        assertEquals(PasswordResult.WrongCurrent, after.result)
        assertEquals("guess", after.current)
        assertFalse(after.changing)
        assertEquals("old-secret", jellyfin.password)
    }

    @Test
    fun `new passwords that don't match never reach the server`() {
        val after = PasswordViewModel(connection).fill("old-secret", "new-secret", "new-secert").resultOfChange()
        assertEquals(PasswordResult.Mismatch, after.result)
        assertEquals(0, jellyfin.hits("/Users/Password"))
    }

    @Test
    fun `Change waits for the new password twice, and an edit clears the result`() {
        val vm = PasswordViewModel(connection).fill("", "new-secret", "")
        assertFalse(vm.state.value.complete)
        vm.change()
        assertEquals(0, jellyfin.hits("/Users/Password"))
        vm.onConfirm("other")
        assertEquals(PasswordResult.Mismatch, vm.resultOfChange().result)
        vm.onConfirm("new-secret")
        assertEquals(null, vm.state.value.result)
    }

    @Test
    fun `a server failure and an unreachable one say which`() {
        jellyfin.status["/Users/Password"] = 500
        assertEquals(PasswordResult.Refused, PasswordViewModel(connection).fill("old-secret", "n").resultOfChange().result)
        runBlocking { sessions.clear() }
        assertEquals(PasswordResult.Unreachable, PasswordViewModel(connection).fill("old-secret", "n").resultOfChange().result)
    }

    private companion object {
        const val TIMEOUT_MILLIS = 10_000L
    }
}
