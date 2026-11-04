package com.cooper.binge.companion.jellyfin.ui.console

import android.graphics.Bitmap
import com.cooper.binge.companion.jellyfin.FakeJellyfin
import com.cooper.binge.companion.jellyfin.server.Connection
import com.cooper.binge.companion.jellyfin.server.ServerUser
import com.cooper.binge.companion.jellyfin.server.users
import com.cooper.binge.companion.jellyfin.session
import com.cooper.binge.companion.jellyfin.testSessionStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.RandomAccessFile

@RunWith(RobolectricTestRunner::class)
class UserPicturesTest {
    @get:Rule
    val temporary = TemporaryFolder()

    private val jellyfin = FakeJellyfin()
    private val sessions = testSessionStore()
    private val connection = Connection(OkHttpClient(), sessions, "Jellyfin Companion", "Test", "1.0", Dispatchers.IO)
    private val user = ServerUser("user-1", "Ana", true, null, JsonObject(emptyMap()), imageTag = "picture-1")

    @Before
    fun signedIn() {
        runBlocking { sessions.save(jellyfin.session()) }
    }

    @After
    fun close() = jellyfin.close()

    private fun picture(): ByteArray {
        val bitmap = Bitmap.createBitmap(512, 512, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(android.graphics.Color.MAGENTA)
        return ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }.toByteArray()
    }

    @Test
    fun `the user list reads picture tags and treats absent or blank tags as no picture`() = runBlocking {
        jellyfin.status["/Users"] = 200
        jellyfin.statusBody["/Users"] =
            """[{"Id":"a","Name":"Ana","PrimaryImageTag":"image-a"},{"Id":"b","Name":"Ben"},{"Id":"c","Name":"Cam","PrimaryImageTag":" "}]"""
        val users = connection.consoleCall("UserPicturesTest") { _, client -> client.users() }.getOrThrow()
        assertEquals(listOf("image-a", null, null), users.map { it.imageTag })
    }

    @Test
    fun `a thumbnail is sized, tagged and authenticated in its header and cached as image bytes`() = runBlocking {
        val bytes = picture()
        jellyfin.userImage = bytes
        val directory = temporary.newFolder()
        val pictures = UserPictures(connection, directory)
        val bitmap = pictures.read(user)
        assertNotNull(bitmap)
        assertTrue(bitmap!!.width <= 128 && bitmap.height <= 128)
        val request = jellyfin.requests.single()
        assertEquals("/Users/user-1/Images/Primary", request.url.encodedPath)
        assertEquals("128", request.url.queryParameter("maxWidth"))
        assertEquals("128", request.url.queryParameter("maxHeight"))
        assertEquals("picture-1", request.url.queryParameter("tag"))
        assertTrue(request.headers["Authorization"].orEmpty().contains(jellyfin.session().token))
        assertTrue(!request.url.toString().contains(jellyfin.session().token))
        assertArrayEquals(bytes, directory.listFiles()!!.single().readBytes())
        assertNotNull(UserPictures(connection, directory).read(user))
        assertEquals(1, jellyfin.requests.size)
    }

    @Test
    fun `no tag and missing or unreadable pictures leave initials available`() = runBlocking {
        val pictures = UserPictures(connection, temporary.newFolder())
        assertNull(pictures.read(user.copy(imageTag = null)))
        assertTrue(jellyfin.requests.isEmpty())
        assertNull(pictures.read(user))
        jellyfin.userImage = byteArrayOf(1, 2, 3)
        assertNull(pictures.read(user.copy(imageTag = "broken")))
        jellyfin.status["/Users/user-1/Images/Primary"] = 500
        assertNull(pictures.read(user.copy(imageTag = "failed")))
    }

    @Test
    fun `saving a picture evicts old image files beyond the disk budget`() = runBlocking {
        val directory = temporary.newFolder()
        val old = File(directory, "old.image")
        RandomAccessFile(old, "rw").use { it.setLength(21L * 1024 * 1024) }
        old.setLastModified(1L)
        jellyfin.userImage = picture()
        assertNotNull(UserPictures(connection, directory).read(user))
        assertTrue(!old.exists())
        assertTrue(directory.listFiles()!!.sumOf { it.length() } <= 20L * 1024 * 1024)
    }

    @Test
    fun `a changed image tag and a different signed-in account do not reuse the old file`() = runBlocking {
        jellyfin.userImage = picture()
        val directory = temporary.newFolder()
        val pictures = UserPictures(connection, directory)
        assertNotNull(pictures.read(user))
        assertNotNull(pictures.read(user.copy(imageTag = "picture-2")))
        assertEquals(2, jellyfin.requests.size)
        sessions.save(jellyfin.session().copy(userId = "other-admin"))
        assertNotNull(UserPictures(connection, directory).read(user))
        assertEquals(3, jellyfin.requests.size)
    }
}
