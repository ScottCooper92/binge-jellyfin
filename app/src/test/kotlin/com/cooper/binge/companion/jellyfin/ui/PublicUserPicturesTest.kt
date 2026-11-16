package com.cooper.binge.companion.jellyfin.ui

import android.graphics.Bitmap
import com.cooper.binge.companion.jellyfin.FakeJellyfin
import com.cooper.binge.companion.jellyfin.server.Connection
import com.cooper.binge.companion.jellyfin.server.LibraryIndex
import com.cooper.binge.companion.jellyfin.testSessionStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode
import java.io.ByteArrayOutputStream

/** The sign-in step's pictures: read before any token exists, held in memory for the step, and gone when it is left. */
@OptIn(ExperimentalCoroutinesApi::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@RunWith(RobolectricTestRunner::class)
class PublicUserPicturesTest {
    private val jellyfin = FakeJellyfin()
    private val sessions = testSessionStore()
    private val teardown = ViewModelTeardown()
    private val connection =
        teardown.made(Connection(OkHttpClient(), sessions, "Jellyfin Companion", "Test", "1.0", Dispatchers.IO))

    @Before
    fun main() {
        Dispatchers.setMain(Dispatchers.Unconfined)
    }

    @After
    fun close() {
        teardown.close(jellyfin)
    }

    private fun picture(): ByteArray {
        val bitmap = Bitmap.createBitmap(512, 512, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(android.graphics.Color.MAGENTA)
        return ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }.toByteArray()
    }

    private fun signInStep(): Pair<JellyfinViewModel, UiState.SignIn> {
        val tracked = teardown.made(JellyfinViewModel(sessions, connection, LibraryIndex(), discover = { emptyList() }))
        runBlocking { withTimeout(10_000) { tracked.state.first { it is UiState.EnterServer } } }
        tracked.onAddress(jellyfin.baseUrl)
        tracked.connect()
        return tracked to await(tracked) { it is UiState.SignIn }
    }

    private fun await(
        vm: JellyfinViewModel,
        matches: (UiState) -> Boolean,
    ): UiState.SignIn = runBlocking { withTimeout(10_000) { vm.state.first(matches) as UiState.SignIn } }

    @Test
    fun `a listed account with a picture shows it, read without a token, and one without stays its initials`() {
        jellyfin.userImage = picture()
        jellyfin.publicUsers =
            """[{"Name":"Mum","Id":"u-mum","HasPassword":true,"PrimaryImageTag":"tag-1"},""" +
            """{"Name":"Kid","Id":"u-kid","HasPassword":false}]"""
        val (vm, _) = signInStep()
        val step = await(vm) { it is UiState.SignIn && it.publicPictures.isNotEmpty() }

        assertEquals(setOf("u-mum"), step.publicPictures.keys)
        assertEquals("tag-1", step.publicUsers.first { it.name == "Mum" }.imageTag)
        assertEquals(null, step.publicUsers.first { it.name == "Kid" }.imageTag)

        val reads = jellyfin.requests.filter { it.url.encodedPath.endsWith("/Images/Primary") }
        assertEquals(listOf("/Users/u-mum/Images/Primary"), reads.map { it.url.encodedPath })
        assertEquals("tag-1", reads.single().url.queryParameter("tag"))
        // No token yet: the header carries the client's identity and nothing that authenticates it.
        assertTrue(reads.all { it.headers["Authorization"]?.contains("Token=") != true })
        assertTrue(reads.all { it.url.query?.contains("api_key", ignoreCase = true) != true })
    }

    @Test
    fun `a picture that cannot be read leaves the account's initials and the step working`() {
        jellyfin.status["/Users/u-mum/Images/Primary"] = 500
        jellyfin.publicUsers = """[{"Name":"Mum","Id":"u-mum","HasPassword":true,"PrimaryImageTag":"tag-1"}]"""
        val (vm, _) = signInStep()
        val step = await(vm) { it is UiState.SignIn && it.publicUsers.isNotEmpty() }
        runBlocking { withTimeout(10_000) { while (jellyfin.hits("/Users/u-mum/Images/Primary") == 0) delay(10) } }
        assertTrue(step.publicPictures.isEmpty())
        assertTrue(vm.state.value.let { it is UiState.SignIn && it.publicPictures.isEmpty() })
    }

    @Test
    fun `leaving the step drops the pictures, and the next visit asks the server again`() {
        jellyfin.userImage = picture()
        jellyfin.publicUsers = """[{"Name":"Mum","Id":"u-mum","HasPassword":true,"PrimaryImageTag":"tag-1"}]"""
        val (vm, _) = signInStep()
        assertNotNull(await(vm) { it is UiState.SignIn && it.publicPictures.isNotEmpty() })

        vm.changeServer()
        assertFalse(vm.state.value is UiState.SignIn)

        vm.onAddress(jellyfin.baseUrl)
        vm.connect()
        val again = await(vm) { it is UiState.SignIn && it.publicPictures.isNotEmpty() }
        assertEquals(setOf("u-mum"), again.publicPictures.keys)
        assertEquals(2, jellyfin.hits("/Users/u-mum/Images/Primary"))
    }
}
