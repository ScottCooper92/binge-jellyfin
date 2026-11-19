package com.cooper.homelab.ephyra.server

import com.cooper.homelab.ephyra.FakeItem
import com.cooper.homelab.ephyra.FakeJellyfin
import com.cooper.homelab.ephyra.session
import com.cooper.homelab.ephyra.testSessionStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

/** The event socket against the fake server's: auth by header, keep-alive, the two events, and a reconnect. */
@RunWith(RobolectricTestRunner::class)
class ServerEventsTest {
    private val jellyfin = FakeJellyfin()
    private val sessions = testSessionStore()
    private val connection = Connection(OkHttpClient(), sessions, "Jellyfin Companion", "Test", "1.0", Dispatchers.IO, DiscardLogger)
    private val index = LibraryIndex()
    private val scope = CoroutineScope(SupervisorJob())

    @After
    fun close() {
        scope.cancel()
        connection.close()
        jellyfin.close()
    }

    /** The library pages the index reads (the titles with an id); its one count of those without is not a page. */
    private fun pageReads() = jellyfin.requests.count { it.url.encodedPath == "/Items" && it.url.queryParameter("hasTmdbId") == "true" }

    private fun awaitUntil(check: () -> Boolean) {
        val deadline = System.currentTimeMillis() + TIMEOUT_MILLIS
        while (!check()) {
            check(System.currentTimeMillis() < deadline) { "timed out" }
            Thread.sleep(POLL_MILLIS)
        }
    }

    @Test
    fun `the socket is opened with the header, answers the keep-alive and passes on the events`() {
        runBlocking { sessions.save(jellyfin.session()) }
        jellyfin.keepAliveSeconds = 1
        val received = LinkedBlockingQueue<ServerEvent>()
        val events = ServerEvents(connection, index, scope, Dispatchers.IO) { 50L }
        scope.launch { events.events.collect { received.put(it) } }

        awaitUntil { jellyfin.sockets.isNotEmpty() }
        val upgrade = jellyfin.requests.last { it.url.encodedPath == "/socket" }
        assertTrue(upgrade.headers["Authorization"].orEmpty().contains("Token=\"token-1\""))
        assertFalse(upgrade.url.toString().contains("ApiKey"))

        // A one-second keep-alive is answered every half second.
        awaitUntil { jellyfin.socketMessages.count { it.contains("KeepAlive") } >= 2 }

        jellyfin.push("""{"MessageType":"UserDataChanged","Data":{"UserId":"user-1"}}""")
        jellyfin.push("""{"MessageType":"LibraryChanged","Data":{"ItemsAdded":["x"]}}""")
        jellyfin.push("""{"MessageType":"Something else"}""")
        jellyfin.push("not json")
        assertEquals(ServerEvent.USER_DATA_CHANGED, received.poll(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS))
        assertEquals(ServerEvent.LIBRARY_CHANGED, received.poll(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS))
    }

    @Test
    fun `the user data in a message is passed on, with unknown fields ignored, and the old flow says what it always did`() {
        runBlocking { sessions.save(jellyfin.session()) }
        val events = ServerEvents(connection, index, scope, Dispatchers.IO) { 50L }
        val oldFlow = LinkedBlockingQueue<ServerEvent>()
        val changes = LinkedBlockingQueue<List<UserDataChange>>()
        scope.launch { events.events.collect { oldFlow.put(it) } }
        scope.launch { events.userDataChanges.collect { changes.put(it) } }
        awaitUntil { jellyfin.sockets.isNotEmpty() }

        jellyfin.push(
            """{"MessageType":"UserDataChanged","MessageId":"x","Data":{"UserId":"user-1","Extra":1,"UserDataList":[
              {"ItemId":"abcd","Played":true,"IsFavorite":false,"PlaybackPositionTicks":0,"UnplayedItemCount":2,"Key":"k","Other":[1]},
              {"ItemId":"efgh","IsFavorite":true},{"Played":true}]}}""",
        )
        val list = changes.poll(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)!!
        assertEquals(listOf("abcd", "efgh"), list.map { it.itemId })
        assertEquals(UserDataChange("user-1", "abcd", played = true, favourite = false, positionTicks = 0, unplayedCount = 2), list.first())
        assertNull(list.last().played)
        assertEquals(true, list.last().favourite)
        assertEquals(ServerEvent.USER_DATA_CHANGED, oldFlow.poll(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS))

        // A body of the wrong shape, a message of another type and one with no list give no change; the old flow still says so.
        jellyfin.push("""{"MessageType":"UserDataChanged","Data":{"UserDataList":"nope"}}""")
        jellyfin.push("""{"MessageType":"UserDataChanged","Data":{"UserId":"user-1"}}""")
        jellyfin.push("""{"MessageType":"LibraryChanged","Data":{"ItemsAdded":["x"]}}""")
        assertEquals(ServerEvent.USER_DATA_CHANGED, oldFlow.poll(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS))
        assertEquals(ServerEvent.USER_DATA_CHANGED, oldFlow.poll(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS))
        assertEquals(ServerEvent.LIBRARY_CHANGED, oldFlow.poll(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS))
        assertNull(changes.poll(QUIET_MILLIS, TimeUnit.MILLISECONDS))
    }

    @Test
    fun `both flows share one socket`() {
        runBlocking { sessions.save(jellyfin.session()) }
        val events = ServerEvents(connection, index, scope, Dispatchers.IO) { 50L }
        scope.launch { events.events.collect { } }
        scope.launch { events.userDataChanges.collect { } }
        awaitUntil { jellyfin.sockets.isNotEmpty() }
        Thread.sleep(QUIET_MILLIS)
        assertEquals(1, jellyfin.requests.count { it.url.encodedPath == "/socket" })
    }

    @Test
    fun `a socket the server closes is opened again`() {
        runBlocking { sessions.save(jellyfin.session()) }
        val events = ServerEvents(connection, index, scope, Dispatchers.IO) { 50L }
        scope.launch { events.events.collect { } }
        awaitUntil { jellyfin.sockets.isNotEmpty() }
        jellyfin.sockets.first().close(1001, "going away")
        awaitUntil { jellyfin.requests.count { it.url.encodedPath == "/socket" } >= 2 }
    }

    @Test
    fun `signed out, it opens nothing`() {
        val events = ServerEvents(connection, index, scope, Dispatchers.IO) { 50L }
        scope.launch { events.events.collect { } }
        Thread.sleep(QUIET_MILLIS)
        assertTrue(jellyfin.requests.none { it.url.encodedPath == "/socket" })
    }

    @Test
    fun `one library change rebuilds the index once however many observers are listening`() {
        runBlocking { sessions.save(jellyfin.session()) }
        jellyfin.items += FakeItem("movie-5", "Five", "Movie", tmdbId = 5)
        val events = ServerEvents(connection, index, scope, Dispatchers.IO) { 50L }
        val received = LinkedBlockingQueue<ServerEvent>()
        repeat(2) { scope.launch { events.events.collect { received.put(it) } } }
        awaitUntil { jellyfin.sockets.isNotEmpty() }
        val client = runBlocking { connection.signedIn()!!.second }
        val key = IndexKey(IndexType.MOVIE, 5)
        runBlocking { index.find(client, "user-1", key) }
        val before = pageReads()

        jellyfin.push("""{"MessageType":"LibraryChanged","Data":{"ItemsAdded":["x"]}}""")
        repeat(2) { assertEquals(ServerEvent.LIBRARY_CHANGED, received.poll(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)) }
        runBlocking {
            index.find(client, "user-1", key)
            index.find(client, "user-1", key)
        }
        assertEquals(before + 1, pageReads())
    }

    @Test
    fun `a new sign-in closes the old socket and opens one with the new token`() {
        runBlocking { sessions.save(jellyfin.session()) }
        val events = ServerEvents(connection, index, scope, Dispatchers.IO) { 50L }
        scope.launch { events.events.collect { } }
        awaitUntil { jellyfin.sockets.isNotEmpty() }

        runBlocking {
            sessions.clear()
            sessions.save(jellyfin.session().copy(token = "token-2"))
        }
        awaitUntil {
            jellyfin.requests.any {
                it.url.encodedPath == "/socket" && it.headers["Authorization"].orEmpty().contains("Token=\"token-2\"")
            }
        }
    }

    private companion object {
        const val TIMEOUT_MILLIS = 10_000L
        const val POLL_MILLIS = 20L
        const val QUIET_MILLIS = 500L
    }
}
