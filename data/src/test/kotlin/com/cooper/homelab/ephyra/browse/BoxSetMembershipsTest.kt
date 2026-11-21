package com.cooper.homelab.ephyra.browse

import com.cooper.homelab.ephyra.FakeJellyfin
import com.cooper.homelab.ephyra.FakeTitle
import com.cooper.homelab.ephyra.server.ClientIdentity
import com.cooper.homelab.ephyra.server.DiscardLogger
import com.cooper.homelab.ephyra.server.JellyfinClient
import com.cooper.homelab.ephyra.server.JellyfinServerProfile
import com.cooper.homelab.ephyra.server.JellyfinVersion
import com.cooper.homelab.ephyra.server.LibraryIndex
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class BoxSetMembershipsTest {
    private val jellyfin = FakeJellyfin()
    private val identity = ClientIdentity("Ephyra", "Pixel", "device-1", "1.0")
    private val client =
        JellyfinClient(OkHttpClient(), jellyfin.baseUrl.toHttpUrl(), identity, Dispatchers.IO, logger = DiscardLogger)
            .withToken("token-1")
    private val floor = JellyfinServerProfile(JellyfinVersion(10, 8, 13))
    private val latest = JellyfinServerProfile(JellyfinVersion(12, 4, 0))
    private var clock = 1_000_000L
    private val memberships = BoxSetMemberships(now = { clock })

    @After
    fun close() = jellyfin.close()

    private fun repository(
        profile: JellyfinServerProfile = floor,
        held: BoxSetMemberships = memberships,
    ) = JellyfinCollectionRepository(client, "user-1", profile, held)

    private fun library() {
        jellyfin.browse.titles.add(FakeTitle("box", "Sol Levante Collection", "lib-box", "BoxSet"))
        jellyfin.browse.titles.add(FakeTitle("m1", "One", "box", year = 2020))
        jellyfin.browse.titles.add(FakeTitle("m2", "Two", "box", year = 2021))
    }

    private fun requests() = jellyfin.requests.size

    @Test
    fun `a second film page is answered without asking the server again`() =
        runBlocking {
            library()
            val collections = repository()
            assertEquals(listOf("box"), collections.collectionsOf("m1").map { it.id })
            val after = requests()
            assertTrue(after > 0)
            assertEquals(listOf("box"), collections.collectionsOf("m2").map { it.id })
            assertEquals(emptyList<String>(), collections.collectionsOf("elsewhere").map { it.id })
            assertEquals(after, requests())
        }

    @Test
    fun `a repository of another page shares what the first one read`() =
        runBlocking {
            library()
            repository().collectionsOf("m1")
            val after = requests()
            repository().collectionsOf("m2")
            assertEquals(after, requests())
        }

    @Test
    fun `a library change makes the next page read again, and it sees the new member`() =
        runBlocking {
            library()
            val collections = repository()
            assertTrue(collections.collectionsOf("m3").isEmpty())
            jellyfin.browse.titles.add(FakeTitle("m3", "Three", "box", year = 2022))
            assertTrue("held until the server says the library changed", collections.collectionsOf("m3").isEmpty())
            memberships.invalidate()
            assertEquals(listOf("box"), collections.collectionsOf("m3").map { it.id })
        }

    @Test
    fun `what is held expires after its age`() =
        runBlocking {
            library()
            val collections = repository()
            collections.collectionsOf("m1")
            val after = requests()
            clock += 14 * 60_000L
            collections.collectionsOf("m1")
            assertEquals(after, requests())
            clock += 2 * 60_000L
            collections.collectionsOf("m1")
            assertTrue(requests() > after)
        }

    @Test
    fun `a clear, as at sign-out, drops it`() =
        runBlocking {
            library()
            val collections = repository()
            collections.collectionsOf("m1")
            val after = requests()
            memberships.clear()
            collections.collectionsOf("m1")
            assertTrue(requests() > after)
        }

    @Test
    fun `another user reads their own box sets`() =
        runBlocking {
            library()
            repository().collectionsOf("m1")
            val after = requests()
            JellyfinCollectionRepository(client, "user-2", floor, memberships).collectionsOf("m1")
            assertTrue(requests() > after)
        }

    @Test
    fun `the library index clears and invalidates the box sets it holds`() =
        runBlocking {
            library()
            val index = LibraryIndex()
            val collections = repository(held = index.boxSets)
            collections.collectionsOf("m1")
            val after = requests()
            collections.collectionsOf("m1")
            assertEquals(after, requests())
            index.invalidate()
            collections.collectionsOf("m1")
            val rebuilt = requests()
            assertTrue(rebuilt > after)
            index.clear()
            collections.collectionsOf("m1")
            assertTrue(requests() > rebuilt)
        }

    @Test
    fun `a film in a box set past the first hundred is found`() =
        runBlocking {
            repeat(130) { jellyfin.browse.titles.add(FakeTitle("box-%03d".format(it), "Box %03d".format(it), "lib-box", "BoxSet")) }
            jellyfin.browse.titles.add(FakeTitle("late", "Late film", "box-125", year = 2024))
            assertEquals(listOf("box-125"), repository().collectionsOf("late").map { it.id })
        }

    @Test
    fun `pages that ask together share one read`() =
        runBlocking {
            library()
            val collections = repository()
            val answers = (1..4).map { async(Dispatchers.IO) { collections.collectionsOf("m1").map { it.id } } }.awaitAll()
            assertTrue(answers.all { it == listOf("box") })
            val boxListReads =
                jellyfin.requests.count { it.url.queryParameter("includeItemTypes") == "BoxSet" }
            assertEquals(1, boxListReads)
        }

    @Test
    fun `a read that fails is not kept, and the next page reads again`() =
        runBlocking {
            library()
            val collections = repository()
            val refused = jellyfin.hold("/Items", query = "includeItemTypes" to "BoxSet")
            refused.refuse()
            assertThrows(Exception::class.java) { runBlocking { collections.collectionsOf("m1") } }
            assertEquals(listOf("box"), collections.collectionsOf("m1").map { it.id })
        }

    @Test
    fun `from 12_0 the server's own route answers and nothing is scanned`() =
        runBlocking {
            library()
            repository(latest).collectionsOf("m1")
            assertTrue(jellyfin.requests.none { it.url.queryParameter("includeItemTypes") == "BoxSet" })
        }
}
