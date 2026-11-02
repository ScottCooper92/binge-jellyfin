package com.cooper.binge.companion.jellyfin.ui.console

import android.graphics.Bitmap
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
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.ByteArrayOutputStream

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class ProfilePictureViewModelTest {
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

    private fun ProfilePictureViewModel.settled(check: (ProfilePictureUiState) -> Boolean = { it !is ProfilePictureUiState.Loading }) =
        runBlocking { withTimeout(TIMEOUT_MILLIS) { state.first(check) } }

    private fun ProfilePictureViewModel.ready(check: (ProfilePictureUiState.Ready) -> Boolean = { true }) =
        settled { it is ProfilePictureUiState.Ready && check(it) } as ProfilePictureUiState.Ready

    private fun jpeg(side: Int = SIDE): ByteArray {
        val bitmap = Bitmap.createBitmap(side, side, Bitmap.Config.ARGB_8888).apply { eraseColor(android.graphics.Color.MAGENTA) }
        return ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }.toByteArray()
    }

    /** A view model that sends the picked file as it is, so a test follows the bytes without the image work. */
    private fun viewModel(newRoute: Boolean = true) = made(ProfilePictureViewModel(connection, newRoute, prepare = { it }))

    @Test
    fun `a user with no picture reads none`() {
        assertNull(viewModel().ready().picture)
    }

    @Test
    fun `a picture the server holds is read and shown`() {
        jellyfin.userImage = jpeg()
        assertNotNull(viewModel().ready().picture)
    }

    @Test
    fun `choosing a photo sends it as base64 text typed as an image, and shows what the server holds`() {
        val vm = viewModel()
        vm.ready()
        val file = jpeg()
        vm.choose { file }
        val shown = vm.ready { it.picture != null && !it.busy }
        assertNotNull(shown.picture)
        assertArrayEquals(file, jellyfin.userImage)
        assertEquals("image/jpeg", jellyfin.userImageType?.substringBefore(';'))
        assertEquals(1, jellyfin.requests.count { it.url.encodedPath == "/UserImage" && it.method == "POST" })
    }

    @Test
    fun `a server before 10 9 is asked at the old route`() {
        val vm = viewModel(newRoute = false)
        vm.ready()
        vm.choose { jpeg() }
        vm.ready { it.picture != null && !it.busy }
        assertEquals(0, jellyfin.hits("/UserImage"))
        assertTrue(jellyfin.hits("/Users/user-1/Images/Primary") >= 2)
    }

    @Test
    fun `removing the picture asks first, then deletes it`() {
        jellyfin.userImage = jpeg()
        val vm = viewModel()
        vm.ready()
        vm.askRemove(true)
        assertTrue(vm.ready().confirmingRemove)
        vm.remove()
        val gone = vm.ready { it.picture == null && !it.busy }
        assertFalse(gone.confirmingRemove)
        assertNull(jellyfin.userImage)
    }

    @Test
    fun `an upload the server refuses leaves the old picture and says so`() {
        val old = jpeg(SIDE)
        jellyfin.userImage = old
        val vm = viewModel()
        vm.ready { it.picture != null }
        jellyfin.status["/UserImage"] = 500
        vm.choose { jpeg() }
        val refused = vm.ready { it.refused != null && !it.busy }
        assertNotNull(refused.picture)
        assertEquals(ConsoleFailure.Refused, refused.refused)
    }

    @Test
    fun `a file the device cannot turn into a picture is said so, and nothing is sent`() {
        // Robolectric decodes any bytes to a stand-in bitmap, so the file that cannot be read is the prepare step answering null.
        val vm = made(ProfilePictureViewModel(connection, true, prepare = { null }))
        vm.ready()
        vm.choose { "not an image".toByteArray() }
        assertTrue(vm.ready { it.unreadable && !it.busy }.unreadable)
        assertEquals(0, jellyfin.requests.count { it.method == "POST" })
    }

    @Test
    fun `a picker that read nothing says so, and nothing is sent`() {
        val vm = viewModel()
        vm.ready()
        vm.choose { null }
        assertTrue(vm.ready { it.unreadable }.unreadable)
        assertEquals(0, jellyfin.requests.count { it.method == "POST" })
    }

    @Test
    fun `a picture that cannot be read fails the page, and Retry reads it`() {
        jellyfin.status["/UserImage"] = 500
        val vm = viewModel()
        assertEquals(ProfilePictureUiState.Failed(ConsoleFailure.Refused), vm.settled())
        jellyfin.status.remove("/UserImage")
        vm.load()
        vm.ready()
    }

    @Test
    fun `a photo is cropped to a square and scaled to 512 pixels`() {
        val wide = Bitmap.createBitmap(WIDE_WIDTH, WIDE_HEIGHT, Bitmap.Config.ARGB_8888).apply { eraseColor(android.graphics.Color.BLUE) }
        val bytes = ByteArrayOutputStream().also { wide.compress(Bitmap.CompressFormat.JPEG, 90, it) }.toByteArray()
        val prepared = squareAvatar(bytes)
        assertNotNull(prepared)
        val decoded = android.graphics.BitmapFactory.decodeByteArray(prepared, 0, prepared!!.size)
        assertEquals(AVATAR_SIDE, decoded.width)
        assertEquals(AVATAR_SIDE, decoded.height)
        val small = squareAvatar(jpeg(SIDE))
        assertEquals(
            SIDE,
            android.graphics.BitmapFactory
                .decodeByteArray(small, 0, small!!.size)
                .width,
        )
    }

    private companion object {
        const val SIDE = 64
        const val WIDE_WIDTH = 1600
        const val WIDE_HEIGHT = 900
        const val TIMEOUT_MILLIS = 10_000L
    }
}
