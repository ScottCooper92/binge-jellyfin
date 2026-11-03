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
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonPrimitive
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

/** The login splash image: uploaded, removed, and kept in step with the branding configuration the page holds. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class SplashImageTest {
    private val jellyfin = FakeJellyfin()
    private val sessions = testSessionStore()
    private val connection = Connection(OkHttpClient(), sessions, "Jellyfin Companion", "Test", "1.0", Dispatchers.IO)
    private val teardown = ViewModelTeardown()

    private fun <T : ViewModel> made(viewModel: T): T = teardown.made(viewModel)

    @Before
    fun main() {
        Dispatchers.setMain(Dispatchers.Unconfined)
        runBlocking { sessions.save(jellyfin.session()) }
        jellyfin.administrator = true
    }

    @After
    fun close() = teardown.close(jellyfin)

    private fun ServerSettingsViewModel.ready(check: (ServerSettingsUiState.Ready) -> Boolean = { true }) =
        runBlocking {
            withTimeout(TIMEOUT_MILLIS) { state.first { it is ServerSettingsUiState.Ready && check(it) } }
        } as ServerSettingsUiState.Ready

    private fun ServerSettingsViewModel.splash(check: (SplashUiState) -> Boolean) =
        runBlocking { withTimeout(TIMEOUT_MILLIS) { splashImage.state.first(check) } }

    private fun jpeg(): ByteArray {
        val bitmap = Bitmap.createBitmap(SIDE, SIDE, Bitmap.Config.ARGB_8888).apply { eraseColor(android.graphics.Color.MAGENTA) }
        return ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }.toByteArray()
    }

    /** A view model that sends the picked file as it is, so a test follows the bytes without the image work. */
    private fun viewModel() = made(ServerSettingsViewModel(connection)).also { it.ready() }

    private fun branding() = jellyfin.lastBody("/System/Configuration/branding")

    @Test
    fun `an upload is base64 text typed as an image, and the page reads the branding again to know it is custom`() {
        val vm = viewModel()
        assertFalse(vm.ready().branding.customSplashscreen)
        val file = jpeg()
        vm.splashImage.choose { file }
        vm.splash { !it.busy }
        assertArrayEquals(file, jellyfin.splashImage)
        assertTrue(vm.ready { it.branding.customSplashscreen }.branding.customSplashscreen)
    }

    @Test
    fun `a switch touched after an upload keeps the image, because the page holds the location the server wrote`() {
        val vm = viewModel()
        vm.splashImage.choose { jpeg() }
        vm.ready { it.branding.customSplashscreen }
        vm.setSplashscreen(true)
        jellyfin.awaitBody("/System/Configuration/branding") { it["SplashscreenEnabled"]?.jsonPrimitive?.booleanOrNull == true }
        assertEquals("/data/splashscreen-upload.jpg", branding().getValue("SplashscreenLocation").jsonPrimitive.content)
    }

    @Test
    fun `reopening the sheet during a change keeps it busy`() {
        val vm = viewModel()
        val release = java.util.concurrent.CountDownLatch(1)
        vm.splashImage.open(true)
        vm.splash { !it.loading }
        vm.splashImage.choose {
            release.await()
            jpeg()
        }
        vm.splashImage.open(false)
        vm.splashImage.open(true)
        assertTrue(vm.splashImage.state.value.busy)
        release.countDown()
        vm.splash { !it.busy }
    }

    @Test
    fun `a change waiting to be saved is sent before the upload, not lost under it`() {
        val vm = viewModel()
        vm.setSplashscreen(true)
        vm.splashImage.choose { jpeg() }
        vm.splash { !it.busy }
        val order = jellyfin.requests.map { it.method + " " + it.url.encodedPath }.filter { "Splashscreen" in it || "branding" in it }
        assertTrue(order.indexOf("POST /System/Configuration/branding") < order.indexOf("POST /Branding/Splashscreen"))
        assertTrue(vm.ready().branding.splashscreen)
    }

    @Test
    fun `the preview shows what the server serves while the splash screen is on, and none while it is off`() {
        jellyfin.splashImage = jpeg()
        val vm = viewModel()
        vm.splashImage.open(true)
        assertNull(vm.splash { !it.loading }.picture)
        assertNull(vm.splashImage.state.value.refused)
        vm.splashImage.open(false)
        vm.setSplashscreen(true)
        jellyfin.awaitBody("/System/Configuration/branding") { it["SplashscreenEnabled"]?.jsonPrimitive?.booleanOrNull == true }
        vm.splashImage.open(true)
        assertNotNull(vm.splash { !it.loading }.picture)
    }

    @Test
    fun `removing asks first, deletes the image, and the page no longer says it is custom`() {
        jellyfin.splashImage = jpeg()
        jellyfin.brandingConfiguration =
            """{"LoginDisclaimer":"","CustomCss":"","SplashscreenEnabled":true,"SplashscreenLocation":"/data/x.jpg"}"""
        val vm = viewModel()
        assertTrue(vm.ready().branding.customSplashscreen)
        vm.splashImage.askRemove(true)
        assertTrue(vm.splashImage.state.value.confirmingRemove)
        vm.splashImage.remove()
        vm.splash { !it.busy && !it.confirmingRemove }
        assertNull(jellyfin.splashImage)
        assertFalse(vm.ready { !it.branding.customSplashscreen }.branding.customSplashscreen)
    }

    @Test
    fun `an upload the server refuses leaves the image as it was, and says so`() {
        jellyfin.administrator = false
        val vm = viewModel()
        vm.splashImage.choose { jpeg() }
        val state = vm.splash { !it.busy }
        assertEquals(ConsoleFailure.Refused, state.refused)
        assertNull(jellyfin.splashImage)
        assertFalse(vm.ready().branding.customSplashscreen)
    }

    @Test
    fun `a file the device cannot turn into an image is said so, and nothing is sent`() {
        val vm = made(ServerSettingsViewModel(connection))
        vm.ready()
        // Robolectric decodes any bytes to a stand-in bitmap, so the unreadable file is the prepare step answering null.
        val controller = SplashImageController(connection, kotlinx.coroutines.GlobalScope, {}, {}, prepare = { null })
        controller.choose { "not an image".toByteArray() }
        runBlocking { withTimeout(TIMEOUT_MILLIS) { controller.state.first { !it.busy && it.unreadable } } }
        assertEquals(0, jellyfin.requests.count { it.method == "POST" && it.url.encodedPath == "/Branding/Splashscreen" })
    }

    private companion object {
        const val SIDE = 64
        const val TIMEOUT_MILLIS = 10_000L
    }
}
