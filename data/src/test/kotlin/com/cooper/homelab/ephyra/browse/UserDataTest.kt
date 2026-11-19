package com.cooper.homelab.ephyra.browse

import com.cooper.homelab.ephyra.FakeJellyfin
import com.cooper.homelab.ephyra.FakeTitle
import com.cooper.homelab.ephyra.server.ClientIdentity
import com.cooper.homelab.ephyra.server.DiscardLogger
import com.cooper.homelab.ephyra.server.JellyfinClient
import com.cooper.homelab.ephyra.server.JellyfinHttpException
import com.cooper.homelab.ephyra.server.JellyfinServerProfile
import com.cooper.homelab.ephyra.server.JellyfinUnreadableAnswer
import com.cooper.homelab.ephyra.server.JellyfinVersion
import com.cooper.homelab.ephyra.server.UserDataChange
import com.cooper.homelab.ephyra.server.sameItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class UserDataTest {
    private val jellyfin = FakeJellyfin()
    private val identity = ClientIdentity("Ephyra", "Pixel", "device-1", "1.0")
    private val client =
        JellyfinClient(OkHttpClient(), jellyfin.baseUrl.toHttpUrl(), identity, Dispatchers.IO, logger = DiscardLogger)
            .withToken("token-1")
    private val latest = JellyfinServerProfile(JellyfinVersion(10, 11, 6))
    private val floor = JellyfinServerProfile(JellyfinVersion(10, 8, 13))
    private val socket = MutableSharedFlow<List<UserDataChange>>(extraBufferCapacity = 8)
    private val data = JellyfinUserDataRepository(client, "user-1", latest, socket)

    @After
    fun close() = jellyfin.close()

    private fun movie(
        id: String = "m1",
        played: Boolean = false,
    ) = jellyfin.browse.titles.add(FakeTitle(id, "Alien", "lib-movies", played = played, positionTicks = if (played) 0 else 500))

    private fun card(
        id: String = "m1",
        played: Boolean = false,
    ) = BrowseItem(id, "Alien", ItemKind.Movie, userData = ItemUserData(played = played, positionTicks = 500))

    @Test
    fun `played and unplayed are written to the user's route, and the answer is the new state`() =
        runBlocking {
            movie()
            val played = data.setPlayed("m1", true)
            assertEquals(true, played.played)
            assertEquals(0L, played.positionTicks)
            assertEquals("user-1", played.userId)
            val request = jellyfin.browse.itemRequests.last()
            assertEquals("POST", request.method)
            assertEquals("/UserPlayedItems/m1", request.url.encodedPath)
            assertEquals("user-1", request.url.queryParameter("userId"))
            assertEquals(false, data.setPlayed("m1", false).played)
            assertEquals(
                "DELETE",
                jellyfin.browse.itemRequests
                    .last()
                    .method,
            )
        }

    @Test
    fun `favourite is written on and off`() =
        runBlocking {
            movie()
            assertEquals(true, data.setFavourite("m1", true).favourite)
            assertEquals(
                "/UserFavoriteItems/m1",
                jellyfin.browse.itemRequests
                    .last()
                    .url.encodedPath,
            )
            assertEquals(false, data.setFavourite("m1", false).favourite)
            assertTrue(jellyfin.browse.titles.none { it.favourite })
        }

    @Test
    fun `an older server is written to the old routes, with the user in the path`() =
        runBlocking {
            movie()
            val old = JellyfinUserDataRepository(client, "user-1", floor, socket)
            old.setPlayed("m1", true)
            old.setFavourite("m1", true)
            val paths = jellyfin.browse.itemRequests.map { it.url.encodedPath }
            assertEquals(listOf("/Users/user-1/PlayedItems/m1", "/Users/user-1/FavoriteItems/m1"), paths)
            assertNull(
                jellyfin.browse.itemRequests
                    .first()
                    .url
                    .queryParameter("userId"),
            )
        }

    @Test
    fun `a write the server refuses publishes nothing and throws the transport's error`() {
        movie()
        val seen = mutableListOf<UserDataChange>()
        val job = kotlinx.coroutines.GlobalScope.launch(Dispatchers.Unconfined) { data.changes.toList(seen) }
        jellyfin.status["/UserPlayedItems/m1"] = 500
        assertEquals(500, assertThrows(JellyfinHttpException::class.java) { runBlocking { data.setPlayed("m1", true) } }.code)
        assertThrows(JellyfinHttpException::class.java) { runBlocking { data.setPlayed("nobody", true) } }
        assertTrue(seen.isEmpty())
        job.cancel()
    }

    @Test
    fun `an answer that is not user data is unreadable`() {
        movie()
        jellyfin.notJson += "/UserPlayedItems/m1"
        assertThrows(JellyfinUnreadableAnswer::class.java) { runBlocking { data.setPlayed("m1", true) } }
    }

    @Test
    fun `a write is published to every listener, and a socket message for the same change is the same state`() =
        runBlocking {
            movie()
            val first =
                withTimeout(TIMEOUT) {
                    val received = mutableListOf<UserDataChange>()
                    val job = launch(Dispatchers.Unconfined) { data.changes.toList(received) }
                    data.setFavourite("m1", true)
                    // The server's own message for that change arrives after the write's answer.
                    socket.emit(listOf(UserDataChange("user-1", "m1", played = false, favourite = true, positionTicks = 500)))
                    job.cancel()
                    received
                }
            assertTrue(first.isNotEmpty())
            // Applying them in order, twice over, ends in one state: nothing flickers back.
            val applied = first.fold(listOf(card())) { list, change -> list.withChanges(listOf(change)) }
            assertTrue(applied.single().userData.favourite)
            assertEquals(applied, first.fold(applied) { list, change -> list.withChanges(listOf(change)) })
        }

    @Test
    fun `a socket message is passed on for this user, with dashes in the id or not, and others are dropped`() =
        runBlocking {
            val received = mutableListOf<UserDataChange>()
            val job = launch(Dispatchers.Unconfined) { data.changes.toList(received) }
            socket.emit(
                listOf(
                    UserDataChange("user-1", "abcd-ef01", played = true),
                    UserDataChange("someone-else", "m9", played = true),
                    UserDataChange(null, "m2", favourite = true),
                ),
            )
            job.cancel()
            assertEquals(listOf("abcd-ef01", "m2"), received.map { it.itemId })
            assertTrue(received.all { it.userId == "user-1" })
            assertTrue(sameItem("ABCDEF01", "abcd-ef01"))
            assertFalse(sameItem("abcd", "abce"))
        }

    @Test
    fun `an overlay updates only the item named, keeps what a change leaves out, and keeps the list when nothing applies`() {
        val list = listOf(card("a"), card("b", played = true))
        val change = UserDataChange("user-1", "a", played = true, positionTicks = 0)
        val updated = list.withChanges(listOf(change))
        assertEquals(true, updated.first().userData.played)
        assertEquals(0L, updated.first().userData.positionTicks)
        assertSame(list.last(), updated.last())
        // A favourite change keeps the played state.
        val favourite = updated.withChanges(listOf(UserDataChange(null, "a", favourite = true)))
        assertTrue(favourite.first().userData.played && favourite.first().userData.favourite)
        assertSame(list, list.withChanges(emptyList()))
        assertSame(list, list.withChanges(listOf(UserDataChange(null, "zzz", played = true))))
        val page = Page(list, 2, 0).withChanges(listOf(change))
        assertEquals(
            true,
            page.items
                .first()
                .userData.played,
        )
        assertEquals(2, page.total)
        assertEquals(favourite.first().userData.unplayedCount, favourite.first().userData.unplayedCount)
    }

    private companion object {
        const val TIMEOUT = 10_000L
    }
}
